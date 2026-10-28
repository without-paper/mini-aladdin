
package com.minialaddin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePortfolioRequest(

        @NotBlank(message = "Portfolio name cannot be blank")
        @Size(max = 255)
        String name,

        @NotNull(message = "Initial cash is required")
        @DecimalMin(value = "0.0")
        @Digits(integer = 17, fraction = 2)
        BigDecimal initialCash

) {}
