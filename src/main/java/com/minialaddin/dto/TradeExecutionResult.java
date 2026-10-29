
package com.minialaddin.dto;

public record TradeExecutionResult(
        TradeResponse trade,
        boolean replayed
) {}
