
package com.minialaddin.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionValuationResponse(
        String symbol,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal costBasis,
        BigDecimal marketPrice,
        Instant priceUpdatedAt,
        BigDecimal marketValue,
        BigDecimal unrealisedPnl
) {}
