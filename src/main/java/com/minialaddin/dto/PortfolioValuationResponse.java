
package com.minialaddin.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioValuationResponse(
        Long portfolioId,
        String portfolioName,
        String currency,
        BigDecimal initialCash,
        BigDecimal availableCash,
        BigDecimal marketValue,
        BigDecimal totalValue,
        BigDecimal realisedPnl,
        BigDecimal unrealisedPnl,
        BigDecimal totalPnl,
        List<PositionValuationResponse> positions
) {
    public PortfolioValuationResponse {
        positions = List.copyOf(positions);
    }
}
