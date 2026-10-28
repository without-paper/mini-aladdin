
package com.minialaddin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePortfolioRequest(

        @NotBlank(message = "Portfolio name cannot be blank")
        String name,

        @NotNull(message = "Initial cash is required")
        @DecimalMin(
                value = "0.0",
                message = "Initial cash cannot be negative"
        )
        BigDecimal initialCash

) {}
