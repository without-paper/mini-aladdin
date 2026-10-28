
package com.minialaddin.repository;

import com.minialaddin.model.Trade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByPortfolioIdOrderByExecutedAtAscIdAsc(
            Long portfolioId
    );
}
