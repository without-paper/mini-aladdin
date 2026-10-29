
package com.minialaddin.service;

import com.minialaddin.dto.PortfolioSnapshot;
import com.minialaddin.dto.PositionResponse;
import com.minialaddin.model.Trade;
import com.minialaddin.model.TradeSide;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class PortfolioCalculationService {

    public PortfolioSnapshot calculate(
            BigDecimal initialCash,
            List<Trade> trades
    ) {
        if (initialCash == null || initialCash.signum() < 0) {
            throw new IllegalArgumentException(
                    "Invalid initial cash"
            );
        }

        if (trades == null) {
            throw new IllegalArgumentException(
                    "Trade history cannot be null"
            );
        }

        BigDecimal cash = initialCash;

        Map<String, BigDecimal> positions = new TreeMap<>();

        for (Trade trade : trades) {

            BigDecimal quantity = trade.getQuantity();
            BigDecimal price = trade.getPrice();
            String symbol = trade.getSymbol();
            TradeSide side = trade.getSide();

            if (symbol == null ||
                    symbol.isBlank() ||
                    side == null ||
                    quantity == null ||
                    price == null ||
                    quantity.signum() <= 0 ||
                    price.signum() <= 0) {
                throw new IllegalStateException(
                        "Invalid persisted trade data"
                );
            }

            BigDecimal amount = quantity.multiply(price);

            if (side == TradeSide.BUY) {
                cash = cash.subtract(amount);

                positions.merge(
                        symbol,
                        quantity,
                        BigDecimal::add
                );

            } else if (side == TradeSide.SELL) {
                cash = cash.add(amount);

                positions.merge(
                        symbol,
                        quantity.negate(),
                        BigDecimal::add
                );

            } else {
                throw new IllegalStateException(
                        "Unsupported trade side"
                );
            }
        }

        List<PositionResponse> result = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry
                : positions.entrySet()) {

            BigDecimal quantity = entry.getValue();

            if (quantity.signum() < 0) {
                throw new IllegalStateException(
                        "Negative position for " + entry.getKey()
                );
            }

            if (quantity.signum() != 0) {
                result.add(new PositionResponse(
                        entry.getKey(),
                        quantity
                ));
            }
        }

        if (cash.signum() < 0) {
            throw new IllegalStateException(
                    "Calculated cash balance is negative"
            );
        }

        return new PortfolioSnapshot(cash, result);
    }
}
