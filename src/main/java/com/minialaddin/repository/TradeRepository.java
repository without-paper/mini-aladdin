
package com.minialaddin.repository;

import com.minialaddin.model.Trade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByPortfolioIdOrderByExecutedAtAscIdAsc(
            Long portfolioId
    );

    Optional<Trade> findByPortfolioIdAndRequestKey(
            Long portfolioId,
            String requestKey
    );

}
