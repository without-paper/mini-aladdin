
package com.minialaddin.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSnapshot(
        BigDecimal availableCash,
        List<PositionResponse> positions
) {
    public PortfolioSnapshot {
        positions = List.copyOf(positions);
    }
}
