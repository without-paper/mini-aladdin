
package com.minialaddin.service;

import com.minialaddin.model.Portfolio;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.exception.PortfolioNotFoundException;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PortfolioService {

    private final PortfolioRepository repository;
    private final AtomicLong nextId = new AtomicLong(1);

    public PortfolioService(PortfolioRepository repository) {
        this.repository = repository;
    }

    public Portfolio createPortfolio(
            String name,
            BigDecimal initialCash
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Portfolio name cannot be blank"
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

        return repository.save(portfolio);
    }

    public Portfolio getPortfolio(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new PortfolioNotFoundException(id)
                );
    }
}
