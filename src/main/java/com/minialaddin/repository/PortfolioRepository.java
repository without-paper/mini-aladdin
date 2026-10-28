
package com.minialaddin.repository;

import com.minialaddin.model.Portfolio;

import java.util.Optional;

public interface PortfolioRepository {

    Portfolio save(Portfolio portfolio);

    Optional<Portfolio> findById(Long id);

}
