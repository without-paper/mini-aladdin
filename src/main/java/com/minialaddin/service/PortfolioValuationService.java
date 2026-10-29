
package com.minialaddin.service;

import com.minialaddin.dto.PortfolioValuationResponse;
import com.minialaddin.exception.PortfolioNotFoundException;
import com.minialaddin.model.MarketPrice;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.Trade;
import com.minialaddin.repository.MarketPriceRepository;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PortfolioValuationService {

    private final PortfolioRepository portfolioRepository;
    private final TradeRepository tradeRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final PortfolioValuationCalculator calculator;

    public PortfolioValuationService(
            PortfolioRepository portfolioRepository,
            TradeRepository tradeRepository,
            MarketPriceRepository marketPriceRepository,
            PortfolioValuationCalculator calculator
    ) {
        this.portfolioRepository = portfolioRepository;
        this.tradeRepository = tradeRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public PortfolioValuationResponse getValuation(Long portfolioId) {

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() ->
                        new PortfolioNotFoundException(portfolioId)
                );

        List<Trade> trades = tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(
                        portfolioId
                );

        Set<String> symbols = trades.stream()
                .map(Trade::getSymbol)
                .collect(Collectors.toSet());

        List<MarketPrice> quotes = marketPriceRepository
                .findAllById(symbols);

        Map<String, MarketPrice> prices = quotes.stream()
                .collect(Collectors.toMap(
                        MarketPrice::getSymbol,
                        Function.identity()
                ));

        return calculator.calculate(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getInitialCash(),
                trades,
                prices
        );
    }
}
