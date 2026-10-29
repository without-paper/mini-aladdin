
package com.minialaddin.service;

import com.minialaddin.dto.CashResponse;
import com.minialaddin.dto.PortfolioSnapshot;
import com.minialaddin.dto.PositionResponse;
import com.minialaddin.exception.PortfolioNotFoundException;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.Trade;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PortfolioReportService {

    private final PortfolioRepository portfolioRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioCalculationService calculator;

    public PortfolioReportService(
            PortfolioRepository portfolioRepository,
            TradeRepository tradeRepository,
            PortfolioCalculationService calculator
    ) {
        this.portfolioRepository = portfolioRepository;
        this.tradeRepository = tradeRepository;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public CashResponse getCash(Long portfolioId) {

        Portfolio portfolio = findPortfolio(portfolioId);

        List<Trade> trades = loadTrades(portfolioId);

        PortfolioSnapshot snapshot = calculator.calculate(
                portfolio.getInitialCash(),
                trades
        );

        return new CashResponse(
                portfolio.getId(),
                "USD",
                portfolio.getInitialCash(),
                snapshot.availableCash()
        );
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(Long portfolioId) {

        Portfolio portfolio = findPortfolio(portfolioId);

        List<Trade> trades = loadTrades(portfolioId);

        PortfolioSnapshot snapshot = calculator.calculate(
                portfolio.getInitialCash(),
                trades
        );

        return snapshot.positions();
    }

    private Portfolio findPortfolio(Long portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .orElseThrow(() ->
                        new PortfolioNotFoundException(portfolioId)
                );
    }

    private List<Trade> loadTrades(Long portfolioId) {
        return tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(
                        portfolioId
                );
    }
}
