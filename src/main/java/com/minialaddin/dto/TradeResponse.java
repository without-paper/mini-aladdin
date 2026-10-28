
package com.minialaddin.dto;

import com.minialaddin.model.Trade;
import com.minialaddin.model.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeResponse(
        Long id,
        Long portfolioId,
        String symbol,
        TradeSide side,
        BigDecimal quantity,
        BigDecimal price,
        Instant executedAt
) {
    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getPortfolio().getId(),
                trade.getSymbol(),
                trade.getSide(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getExecutedAt()
        );
    }
}
