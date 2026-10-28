
package com.minialaddin.service;

import com.minialaddin.model.Portfolio;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PortfolioService {

    private final Map<Long, Portfolio> portfolios =
            new ConcurrentHashMap<>();

    private final AtomicLong nextId = new AtomicLong(1);

    public Portfolio createPortfolio(
            String name,
            BigDecimal initialCash
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Portfolio name cannot be empty"
            );
        }

        if (initialCash == null ||
                initialCash.signum() < 0) {
            throw new IllegalArgumentException(
                    "Initial cash cannot be negative"
            );
        }

        Long id = nextId.getAndIncrement();

        Portfolio portfolio = new Portfolio(
                id,
                name,
                initialCash
        );

        portfolios.put(id, portfolio);

        return portfolio;
    }

    public Portfolio getPortfolio(Long id) {
        return portfolios.get(id);
    }
}
