
package com.minialaddin.dto;

import java.math.BigDecimal;

public record CashResponse(
        Long portfolioId,
        String currency,
        BigDecimal initialCash,
        BigDecimal availableCash
) {}
