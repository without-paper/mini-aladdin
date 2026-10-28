
package com.minialaddin.service;

import com.minialaddin.exception.PortfolioNotFoundException;
import com.minialaddin.model.Portfolio;
import com.minialaddin.repository.PortfolioRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PortfolioService {

    private final PortfolioRepository repository;

    public PortfolioService(PortfolioRepository repository) {
        this.repository = repository;
    }

    @Transactional
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

        Portfolio portfolio = new Portfolio(
                name,
                initialCash
        );

        return repository.save(portfolio);
    }

    @Transactional(readOnly = true)
    public Portfolio getPortfolio(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new PortfolioNotFoundException(id)
                );
    }
}
