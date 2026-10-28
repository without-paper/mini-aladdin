
package com.minialaddin.dto;

import com.minialaddin.model.Portfolio;

import java.math.BigDecimal;

public record PortfolioResponse(
        Long id,
        String name,
        BigDecimal initialCash
) {
    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(
                portfolio.id(),
                portfolio.name(),
                portfolio.initialCash()
        );
    }
}
