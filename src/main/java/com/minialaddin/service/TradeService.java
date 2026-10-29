
package com.minialaddin.service;

import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.dto.TradeExecutionResult;
import com.minialaddin.dto.TradeResponse;

import com.minialaddin.exception.IdempotencyConflictException;
import com.minialaddin.exception.PortfolioNotFoundException;
import com.minialaddin.exception.TradeRejectedException;

import com.minialaddin.metrics.TransactionalTradeMetrics;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.Trade;
import com.minialaddin.model.TradeSide;

import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;

import com.minialaddin.metrics.TradeMetrics;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class TradeService {

    private static final Logger log =
            LoggerFactory.getLogger(TradeService.class);

    private final PortfolioRepository portfolioRepository;
    private final TradeRepository tradeRepository;

    private final TradeMetrics tradeMetrics;
    private final TransactionalTradeMetrics transactionalTradeMetrics;

    public TradeService(
            PortfolioRepository portfolioRepository,
            TradeRepository tradeRepository,
            TradeMetrics tradeMetrics,
            TransactionalTradeMetrics transactionalTradeMetrics
    ) {
        this.portfolioRepository = portfolioRepository;
        this.tradeRepository = tradeRepository;
        this.tradeMetrics = tradeMetrics;
        this.transactionalTradeMetrics = transactionalTradeMetrics;
    }

    // =========================================================
    // GET: Retrieve all trades of a portfolio
    // =========================================================

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

    // =========================================================
    // POST: Execute a trade with idempotency protection
    // =========================================================

    @Transactional
    public TradeExecutionResult executeTrade(
            Long portfolioId,
            CreateTradeRequest request,
            String requestKey
    ) {

        // -----------------------------------------------------
        // 1. Validate the idempotency key
        // -----------------------------------------------------

        if (requestKey == null ||
                !requestKey.matches("[A-Za-z0-9_-]{8,128}")) {

            throw new TradeRejectedException(
                    "Invalid Idempotency-Key"
            );
        }

        if (request == null) {
            throw new TradeRejectedException(
                    "Trade request cannot be null"
            );
        }

        // -----------------------------------------------------
        // 2. Normalise and validate the trade request
        // -----------------------------------------------------

        String symbol = normalizeSymbol(request.symbol());

        TradeSide side = request.side();
        BigDecimal quantity = request.quantity();
        BigDecimal price = request.price();

        if (side == null) {
            throw new TradeRejectedException(
                    "Trade side is required"
            );
        }

        if (quantity == null ||
                quantity.signum() <= 0 ||
                quantity.scale() > 0 ||
                quantity.precision() > 19) {

            throw new TradeRejectedException(
                    "Quantity must be a positive integer"
            );
        }

        if (price == null ||
                price.signum() <= 0 ||
                price.scale() > 2 ||
                price.precision() - price.scale() > 17) {

            throw new TradeRejectedException(
                    "Invalid trade price"
            );
        }

        // -----------------------------------------------------
        // 3. Create a canonical fingerprint
        // -----------------------------------------------------

        String fingerprint = calculateFingerprint(
                symbol,
                side,
                quantity,
                price
        );

        // -----------------------------------------------------
        // 4. Lock the portfolio row
        //
        // This serialises trade execution for this portfolio.
        // -----------------------------------------------------

        Portfolio portfolio = portfolioRepository
                .findByIdForUpdate(portfolioId)
                .orElseThrow(() ->
                        new PortfolioNotFoundException(portfolioId)
                );

        // -----------------------------------------------------
        // 5. Idempotency check AFTER acquiring the lock
        // -----------------------------------------------------

        Optional<Trade> existingTrade =
                tradeRepository.findByPortfolioIdAndRequestKey(
                        portfolioId,
                        requestKey
                );

        if (existingTrade.isPresent()) {

            Trade existing = existingTrade.get();

            if (!fingerprint.equals(
                    existing.getRequestFingerprint()
            )) {

                log.warn(
                        "Idempotency conflict: portfolioId={}",
                        portfolioId
                );

                throw new IdempotencyConflictException(
                        "Idempotency-Key was already used " +
                                "with a different request"
                );
            }

            log.info(
                    "Idempotent trade replay: portfolioId={}, tradeId={}",
                    portfolioId,
                    existing.getId()
            );

            return new TradeExecutionResult(
                    TradeResponse.from(existing),
                    true
            );
        }

        // -----------------------------------------------------
        // 6. Load historical trades
        // -----------------------------------------------------

        List<Trade> history = tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(
                        portfolioId
                );

        // -----------------------------------------------------
        // 7. Calculate current cash and symbol position
        // -----------------------------------------------------

        BigDecimal availableCash = portfolio.getInitialCash();
        BigDecimal currentPosition = BigDecimal.ZERO;

        for (Trade trade : history) {

            BigDecimal amount = trade.getQuantity()
                    .multiply(trade.getPrice());

            if (trade.getSide() == TradeSide.BUY) {

                availableCash = availableCash.subtract(amount);

            } else if (trade.getSide() == TradeSide.SELL) {

                availableCash = availableCash.add(amount);

            } else {
                throw new IllegalStateException(
                        "Invalid historical trade side"
                );
            }

            if (symbol.equals(trade.getSymbol())) {

                if (trade.getSide() == TradeSide.BUY) {

                    currentPosition = currentPosition.add(
                            trade.getQuantity()
                    );

                } else {

                    currentPosition = currentPosition.subtract(
                            trade.getQuantity()
                    );
                }
            }
        }

        // -----------------------------------------------------
        // 8. Validate the new BUY or SELL
        // -----------------------------------------------------

        BigDecimal tradeAmount = quantity.multiply(price);

        if (side == TradeSide.BUY) {

            if (availableCash.compareTo(tradeAmount) < 0) {

                log.warn(
                        "Trade rejected: insufficient cash, portfolioId={}, symbol={}",
                        portfolioId,
                        symbol
                );

                tradeMetrics.recordRejected();

                throw new TradeRejectedException(
                        "Insufficient cash"
                );
            }

        } else if (side == TradeSide.SELL) {

            if (currentPosition.compareTo(quantity) < 0) {

                log.warn(
                        "Trade rejected: insufficient position, portfolioId={}, symbol={}",
                        portfolioId,
                        symbol
                );

                tradeMetrics.recordRejected();

                throw new TradeRejectedException(
                        "Insufficient position"
                );
            }

        } else {

            throw new TradeRejectedException(
                    "Unsupported trade side"
            );
        }

        // -----------------------------------------------------
        // 9. Persist the trade with idempotency metadata
        // -----------------------------------------------------

        Trade trade = new Trade(
                portfolio,
                symbol,
                side,
                quantity,
                price,
                requestKey,
                fingerprint
        );

        Trade saved = tradeRepository.saveAndFlush(trade);

        transactionalTradeMetrics.recordExecutionAfterCommit();

        // -----------------------------------------------------
        // 10. Business event logging
        //
        // The CorrelationIdFilter automatically supplies
        // correlationId through SLF4J MDC.
        // -----------------------------------------------------

        log.info(
                "Trade persisted: portfolioId={}, tradeId={}, symbol={}, side={}",
                portfolioId,
                saved.getId(),
                symbol,
                side
        );

        // -----------------------------------------------------
        // 11. Return the execution result
        // -----------------------------------------------------
        tradeMetrics.recordReplayed();

        return new TradeExecutionResult(
                TradeResponse.from(saved),
                false
        );
    }

    // =========================================================
    // Helper: Normalise stock symbols
    // =========================================================

    private String normalizeSymbol(String symbol) {

        if (symbol == null) {
            throw new TradeRejectedException(
                    "Stock symbol is required"
            );
        }

        String normalised = symbol.trim()
                .toUpperCase(Locale.ROOT);

        if (!normalised.matches("[A-Z][A-Z0-9.]{0,19}")) {

            throw new TradeRejectedException(
                    "Invalid stock symbol"
            );
        }

        return normalised;
    }

    // =========================================================
    // Helper: Calculate a SHA-256 request fingerprint
    // =========================================================

    private String calculateFingerprint(
            String symbol,
            TradeSide side,
            BigDecimal quantity,
            BigDecimal price
    ) {

        String canonicalRequest = String.join(
                "|",
                symbol,
                side.name(),
                quantity.stripTrailingZeros().toPlainString(),
                price.stripTrailingZeros().toPlainString()
        );

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    canonicalRequest.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    ex
            );
        }
    }
}
