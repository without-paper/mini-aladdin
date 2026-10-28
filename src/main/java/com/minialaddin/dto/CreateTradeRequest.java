
package com.minialaddin.dto;

import com.minialaddin.model.TradeSide;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTradeRequest(

        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9.]{0,19}")
        String symbol,

        @NotNull
        TradeSide side,

        @NotNull
        @DecimalMin(value = "1")
        @Digits(integer = 19, fraction = 0)
        BigDecimal quantity,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal price

) {}
