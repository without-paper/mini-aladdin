
package com.minialaddin.exception;

public class MarketPriceNotFoundException
        extends RuntimeException {

    public MarketPriceNotFoundException(String symbol) {
        super("Market price not found for " + symbol);
    }
}
