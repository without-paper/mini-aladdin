
package com.minialaddin.service;

import com.minialaddin.dto.*;
import com.minialaddin.model.MarketPrice;
import com.minialaddin.model.Trade;
import com.minialaddin.model.TradeSide;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

@Component
public class PortfolioValuationCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static class PositionState {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;
        BigDecimal realisedPnl = BigDecimal.ZERO;
    }

    public PortfolioValuationResponse calculate(
            Long portfolioId,
            String portfolioName,
            BigDecimal initialCash,
            List<Trade> trades,
            Map<String, MarketPrice> prices
    ) {
        Objects.requireNonNull(initialCash);
        Objects.requireNonNull(trades);
        Objects.requireNonNull(prices);

        if (initialCash.signum() < 0) {
            throw new IllegalArgumentException("Negative initial cash");
        }

        BigDecimal cash = initialCash;
        BigDecimal realisedPnl = BigDecimal.ZERO;

        Map<String, PositionState> states = new TreeMap<>();

        for (Trade trade : trades) {
            String symbol = trade.getSymbol();
            BigDecimal quantity = trade.getQuantity();
            BigDecimal price = trade.getPrice();
            TradeSide side = trade.getSide();

            if (symbol == null || symbol.isBlank() ||
                    quantity == null || price == null ||
                    side == null ||
                    quantity.signum() <= 0 || price.signum() <= 0) {
                throw new IllegalStateException(
                        "Invalid trade in history"
                );
            }

            PositionState state = states.computeIfAbsent(
                    symbol, k -> new PositionState()
            );

            BigDecimal amount = quantity.multiply(price);

            if (side == TradeSide.BUY) {
                cash = cash.subtract(amount);
                state.quantity = state.quantity.add(quantity);
                state.costBasis = state.costBasis.add(amount);

            } else {
                if (state.quantity.compareTo(quantity) < 0) {
                    throw new IllegalStateException(
                            "Insufficient historical position: " + symbol
                    );
                }

                BigDecimal removedCost;

                if (state.quantity.compareTo(quantity) == 0) {
                    removedCost = state.costBasis;
                } else {
                    removedCost = state.costBasis
                            .multiply(quantity)
                            .divide(state.quantity, MC);
                }

                BigDecimal pnl = amount.subtract(removedCost);

                state.realisedPnl = state.realisedPnl.add(pnl);
                realisedPnl = realisedPnl.add(pnl);

                state.quantity = state.quantity.subtract(quantity);
                state.costBasis = state.costBasis.subtract(removedCost);

                cash = cash.add(amount);

                if (state.quantity.signum() == 0) {
                    state.costBasis = BigDecimal.ZERO;
                }
            }

            if (cash.signum() < 0) {
                throw new IllegalStateException(
                        "Negative historical cash"
                );
            }
        }

        List<PositionValuationResponse> positions = new ArrayList<>();
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;

        for (Map.Entry<String, PositionState> entry : states.entrySet()) {
            String symbol = entry.getKey();
            PositionState state = entry.getValue();

            if (state.quantity.signum() == 0) {
                continue;
            }

            MarketPrice quote = prices.get(symbol);

            if (quote == null) {
                throw new com.minialaddin.exception
                        .MissingMarketPriceException(symbol);
            }

            BigDecimal averageCost = state.costBasis.divide(
                    state.quantity, MC
            );

            BigDecimal marketValue = state.quantity.multiply(
                    quote.getPrice()
            );

            BigDecimal unrealised = marketValue.subtract(
                    state.costBasis
            );

            totalMarketValue = totalMarketValue.add(marketValue);
            totalCostBasis = totalCostBasis.add(state.costBasis);

            positions.add(new PositionValuationResponse(
                    symbol,
                    state.quantity,
                    money(averageCost),
                    money(state.costBasis),
                    quote.getPrice(),
                    quote.getUpdatedAt(),
                    money(marketValue),
                    money(unrealised)
            ));
        }

        BigDecimal totalValue = cash.add(totalMarketValue);
        BigDecimal unrealisedPnl = totalMarketValue.subtract(
                totalCostBasis
        );
        BigDecimal totalPnl = realisedPnl.add(unrealisedPnl);

        return new PortfolioValuationResponse(
                portfolioId,
                portfolioName,
                "USD",
                money(initialCash),
                money(cash),
                money(totalMarketValue),
                money(totalValue),
                money(realisedPnl),
                money(unrealisedPnl),
                money(totalPnl),
                positions
        );
    }
}
