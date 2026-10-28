
package com.minialaddin.repository;

import com.minialaddin.model.Portfolio;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryPortfolioRepository
        implements PortfolioRepository {

    private final Map<Long, Portfolio> portfolios =
            new ConcurrentHashMap<>();

    @Override
    public Portfolio save(Portfolio portfolio) {
        portfolios.put(portfolio.id(), portfolio);
        return portfolio;
    }

    @Override
    public Optional<Portfolio> findById(Long id) {
        return Optional.ofNullable(portfolios.get(id));
    }
}
