
package com.minialaddin.exception;

public class StaleMarketPriceException
        extends RuntimeException {

    public StaleMarketPriceException(String symbol) {
        super("Market price is stale or has an invalid timestamp: "
                + symbol);
    }
}
