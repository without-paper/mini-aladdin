
package com.minialaddin.exception;

public class PortfolioNotFoundException
        extends RuntimeException {

    public PortfolioNotFoundException(Long id) {
        super("Portfolio with ID " + id + " not found");
    }
}
