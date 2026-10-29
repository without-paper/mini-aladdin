
package com.minialaddin.service;

import com.minialaddin.dto.MarketPriceResponse;
import com.minialaddin.exception.MarketPriceNotFoundException;
import com.minialaddin.repository.MarketPriceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class MarketPriceService {

    private final MarketPriceRepository repository;

    public MarketPriceService(
            MarketPriceRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public MarketPriceResponse updatePrice(
            String symbol,
            BigDecimal price
    ) {
        String normalizedSymbol = normalizeSymbol(symbol);

        validatePrice(price);

        repository.upsertPrice(normalizedSymbol, price);

        return repository.findById(normalizedSymbol)
                .map(MarketPriceResponse::from)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Price missing after upsert"
                        )
                );
    }

    @Transactional(readOnly = true)
    public MarketPriceResponse getPrice(String symbol) {

        String normalizedSymbol = normalizeSymbol(symbol);

        return repository.findById(normalizedSymbol)
                .map(MarketPriceResponse::from)
                .orElseThrow(() ->
                        new MarketPriceNotFoundException(
                                normalizedSymbol
                        )
                );
    }

    private String normalizeSymbol(String symbol) {

        if (symbol == null) {
            throw new IllegalArgumentException(
                    "Symbol cannot be null"
            );
        }

        String result = symbol.trim()
                .toUpperCase(Locale.ROOT);

        if (!result.matches("[A-Z][A-Z0-9.]{0,19}")) {
            throw new IllegalArgumentException(
                    "Invalid stock symbol"
            );
        }

        return result;
    }

    private void validatePrice(BigDecimal price) {

        if (price == null ||
                price.signum() <= 0 ||
                price.scale() > 2 ||
                price.precision() - price.scale() > 17) {

            throw new IllegalArgumentException(
                    "Invalid market price"
            );
        }
    }
}
