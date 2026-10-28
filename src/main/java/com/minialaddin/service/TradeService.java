
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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

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
    public TradeResponse executeTrade(
            Long portfolioId,
            CreateTradeRequest request
    ) {
        // 1. Lock the Portfolio BEFORE reading trades.
        Portfolio portfolio = portfolioRepository
                .findByIdForUpdate(portfolioId)
                .orElseThrow(() ->
                        new PortfolioNotFoundException(portfolioId)
                );

        // 2. Validate and normalise input.
        if (request == null ||
                request.symbol() == null ||
                request.symbol().isBlank() ||
                request.side() == null ||
                request.quantity() == null ||
                request.price() == null) {
            throw new TradeRejectedException("Invalid trade request");
        }

        String symbol = request.symbol()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!symbol.matches("[A-Z][A-Z0-9.]{0,19}")) {
            throw new TradeRejectedException("Invalid symbol");
        }

        BigDecimal quantity = request.quantity();
        BigDecimal price = request.price();

        if (quantity.signum() <= 0 ||
                quantity.scale() > 0 && quantity.stripTrailingZeros().scale() > 0 ||
                quantity.precision() > 19) {
            throw new TradeRejectedException("Invalid quantity");
        }

        if (price.signum() <= 0 ||
                price.scale() > 2 ||
                price.precision() - price.scale() > 17) {
            throw new TradeRejectedException("Invalid price");
        }

        // 3. Read committed trade history while holding the lock.
        List<Trade> history = tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(portfolioId);

        BigDecimal availableCash = portfolio.getInitialCash();
        BigDecimal currentPosition = BigDecimal.ZERO;

        for (Trade previous : history) {
            BigDecimal amount = previous.getQuantity()
                    .multiply(previous.getPrice());

            if (previous.getSide() == TradeSide.BUY) {
                availableCash = availableCash.subtract(amount);
            } else {
                availableCash = availableCash.add(amount);
            }

            if (previous.getSymbol().equals(symbol)) {
                if (previous.getSide() == TradeSide.BUY) {
                    currentPosition = currentPosition.add(
                            previous.getQuantity()
                    );
                } else {
                    currentPosition = currentPosition.subtract(
                            previous.getQuantity()
                    );
                }
            }
        }

        // 4. Apply business rules.
        BigDecimal tradeAmount = quantity.multiply(price);

        if (request.side() == TradeSide.BUY) {
            if (availableCash.compareTo(tradeAmount) < 0) {
                throw new TradeRejectedException(
                        "Insufficient cash"
                );
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

        // 5. Save the accepted trade.
        Trade trade = new Trade(
                portfolio,
                symbol,
                request.side(),
                quantity,
                price
        );

        Trade savedTrade = tradeRepository.save(trade);

        // 6. Convert to DTO inside the transaction.
        return TradeResponse.from(savedTrade);
    }
}
