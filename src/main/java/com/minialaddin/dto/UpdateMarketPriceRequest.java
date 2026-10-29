
package com.minialaddin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateMarketPriceRequest(

        @NotNull(message = "Price is required")
        @DecimalMin(
                value = "0.01",
                message = "Price must be positive"
        )
        @Digits(
                integer = 17,
                fraction = 2,
                message = "Invalid price precision"
        )
        BigDecimal price

) {}
