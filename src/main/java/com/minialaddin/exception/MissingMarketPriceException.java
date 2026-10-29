
package com.minialaddin.exception;

public class MissingMarketPriceException
        extends RuntimeException {

    public MissingMarketPriceException(String symbol) {
        super("Missing market price for " + symbol);
    }
}
