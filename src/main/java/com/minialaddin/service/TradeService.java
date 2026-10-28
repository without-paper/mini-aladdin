
package com.minialaddin.service;

import com.minialaddin.dto.TradeResponse;
import com.minialaddin.exception.PortfolioNotFoundException;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TradeService {

    private final TradeRepository tradeRepository;
    private final PortfolioRepository portfolioRepository;

    public TradeService(
            TradeRepository tradeRepository,
            PortfolioRepository portfolioRepository
    ) {
        this.tradeRepository = tradeRepository;
        this.portfolioRepository = portfolioRepository;
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> getTrades(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new PortfolioNotFoundException(portfolioId);
        }

        return tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(portfolioId)
                .stream()
                .map(TradeResponse::from)
                .toList();
    }
}
