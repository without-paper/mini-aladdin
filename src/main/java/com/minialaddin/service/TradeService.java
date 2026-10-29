
package com.minialaddin.service;

import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.dto.TradeResponse;
import com.minialaddin.exception.PortfolioNotFoundException;
import com.minialaddin.exception.TradeRejectedException;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.Trade;
import com.minialaddin.model.TradeSide;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;
import com.minialaddin.dto.TradeExecutionResult;
import com.minialaddin.exception.IdempotencyConflictException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class TradeService {

    private final TradeRepository tradeRepository;
    private final PortfolioRepository portfolioRepository;

    public TradeService(
            TradeRepository tradeRepository,
            PortfolioRepository portfolioRepository
    ) {
        this.tradeRepository = tradeRepository;
        this.portfolioRepository = portfolioRepository;
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> getTrades(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new PortfolioNotFoundException(portfolioId);
        }

        return tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(portfolioId)
                .stream()
                .map(TradeResponse::from)
                .toList();
    }


    @Transactional
    public TradeExecutionResult executeTrade(
            Long portfolioId,
            CreateTradeRequest request,
            String requestKey
    ) {
        // 1. Validate the idempotency key.
        if (requestKey == null ||
                !requestKey.matches("[A-Za-z0-9_-]{8,128}")) {
            throw new TradeRejectedException(
                    "Invalid Idempotency-Key"
            );
        }

        // 2. Validate basic request fields.
        if (request == null ||
                request.symbol() == null ||
                request.side() == null ||
                request.quantity() == null ||
                request.price() == null) {
            throw new TradeRejectedException(
                    "Invalid trade request"
            );
        }

        String symbol = request.symbol()
                .trim()
                .toUpperCase(Locale.ROOT);

        BigDecimal quantity = request.quantity();
        BigDecimal price = request.price();

        if (!symbol.matches("[A-Z][A-Z0-9.]{0,19}")) {
            throw new TradeRejectedException("Invalid symbol");
        }

        if (quantity.signum() <= 0 ||
                quantity.stripTrailingZeros().scale() > 0 ||
                quantity.precision() - quantity.scale() > 19) {
            throw new TradeRejectedException("Invalid quantity");
        }

        if (price.signum() <= 0 ||
                price.scale() > 2 ||
                price.precision() - price.scale() > 17) {
            throw new TradeRejectedException("Invalid price");
        }

        // 3. Lock BEFORE checking previous requests or cash.
        Portfolio portfolio = portfolioRepository
                .findByIdForUpdate(portfolioId)
                .orElseThrow(() ->
                        new PortfolioNotFoundException(portfolioId)
                );

        // 4. Check idempotency inside the transaction.
        String hash = fingerprint(
                symbol, request.side(), quantity, price
        );

        Optional<Trade> previous =
                tradeRepository.findByPortfolioIdAndRequestKey(
                        portfolioId, requestKey
                );

        if (previous.isPresent()) {
            Trade existing = previous.get();

            if (!hash.equals(existing.getRequestFingerprint())) {
                throw new IdempotencyConflictException(
                        "Idempotency key was used with different parameters"
                );
            }

            return new TradeExecutionResult(
                    TradeResponse.from(existing),
                    true
            );
        }

        // 5. Read trade history while holding the lock.
        List<Trade> history = tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(portfolioId);

        BigDecimal availableCash = portfolio.getInitialCash();
        BigDecimal currentPosition = BigDecimal.ZERO;

        for (Trade previousTrade : history) {
            BigDecimal amount = previousTrade.getQuantity()
                    .multiply(previousTrade.getPrice());

            if (previousTrade.getSide() == TradeSide.BUY) {
                availableCash = availableCash.subtract(amount);
            } else {
                availableCash = availableCash.add(amount);
            }

            if (previousTrade.getSymbol().equalsIgnoreCase(symbol)) {
                if (previousTrade.getSide() == TradeSide.BUY) {
                    currentPosition = currentPosition.add(
                            previousTrade.getQuantity()
                    );
                } else {
                    currentPosition = currentPosition.subtract(
                            previousTrade.getQuantity()
                    );
                }
            }
        }

        // 6. Apply business constraints.
        BigDecimal amount = quantity.multiply(price);

        if (request.side() == TradeSide.BUY) {
            if (availableCash.compareTo(amount) < 0) {
                throw new TradeRejectedException("Insufficient cash");
            }
        } else if (request.side() == TradeSide.SELL) {
            if (currentPosition.compareTo(quantity) < 0) {
                throw new TradeRejectedException(
                        "Insufficient position for " + symbol
                );
            }
        } else {
            throw new TradeRejectedException("Unsupported trade side");
        }

        // 7. Persist accepted trade and its idempotency key together.
        Trade trade = new Trade(
                portfolio,
                symbol,
                request.side(),
                quantity,
                price,
                requestKey,
                hash
        );

        Trade saved = tradeRepository.saveAndFlush(trade);

        return new TradeExecutionResult(
                TradeResponse.from(saved),
                false
        );
    }



    private String canonicalDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String fingerprint(
            String symbol,
            TradeSide side,
            BigDecimal quantity,
            BigDecimal price
    ) {
        String canonical = String.join(
                "|",
                symbol,
                side.name(),
                canonicalDecimal(quantity),
                canonicalDecimal(price)
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] bytes = digest.digest(
                    canonical.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(bytes);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", ex
            );
        }
    }

}
