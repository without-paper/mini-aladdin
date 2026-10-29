
package com.minialaddin.dto;

import com.minialaddin.model.MarketPrice;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketPriceResponse(
        String symbol,
        BigDecimal price,
        String currency,
        Instant updatedAt
) {

    public static MarketPriceResponse from(
            MarketPrice marketPrice
    ) {
        return new MarketPriceResponse(
                marketPrice.getSymbol(),
                marketPrice.getPrice(),
                "USD",
                marketPrice.getUpdatedAt()
        );
    }
}
