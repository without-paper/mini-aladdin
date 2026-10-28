
package com.minialaddin.model;

import java.math.BigDecimal;

public record Portfolio(
        Long id,
        String name,
        BigDecimal initialCash
) {}
