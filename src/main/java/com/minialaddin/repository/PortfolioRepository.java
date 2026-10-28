
package com.minialaddin.repository;

import com.minialaddin.model.Portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository
        extends JpaRepository<Portfolio, Long> {
}
