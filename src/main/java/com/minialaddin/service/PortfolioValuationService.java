
package com.minialaddin.service;

import com.minialaddin.dto.PortfolioSnapshot;
import com.minialaddin.dto.PortfolioValuationResponse;
import com.minialaddin.dto.PositionResponse;
import com.minialaddin.exception.PortfolioNotFoundException;
import com.minialaddin.model.MarketPrice;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.Trade;
import com.minialaddin.repository.MarketPriceRepository;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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

    private final PortfolioCalculationService positionCalculator;
    private final PortfolioValuationCalculator valuationCalculator;
    private final MarketPriceFreshnessPolicy freshnessPolicy;

    public PortfolioValuationService(
            PortfolioRepository portfolioRepository,
            TradeRepository tradeRepository,
            MarketPriceRepository marketPriceRepository,
            PortfolioCalculationService positionCalculator,
            PortfolioValuationCalculator valuationCalculator,
            MarketPriceFreshnessPolicy freshnessPolicy
    ) {
        this.portfolioRepository = portfolioRepository;
        this.tradeRepository = tradeRepository;
        this.marketPriceRepository = marketPriceRepository;
        this.positionCalculator = positionCalculator;
        this.valuationCalculator = valuationCalculator;
        this.freshnessPolicy = freshnessPolicy;
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public PortfolioValuationResponse getValuation(Long portfolioId) {

        // 1. Load the portfolio
        Portfolio portfolio = portfolioRepository
                .findById(portfolioId)
                .orElseThrow(() ->
                        new PortfolioNotFoundException(portfolioId)
                );

        // 2. Load trade history in deterministic order
        List<Trade> trades = tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(
                        portfolioId
                );

        // 3. Calculate the current non-zero positions
        PortfolioSnapshot snapshot = positionCalculator.calculate(
                portfolio.getInitialCash(),
                trades
        );

        // 4. Only request prices for open positions
        Set<String> symbols = snapshot.positions()
                .stream()
                .map(PositionResponse::symbol)
                .collect(Collectors.toSet());

        // 5. Batch load the market prices
        List<MarketPrice> quotes =
                marketPriceRepository.findAllById(symbols);

        Map<String, MarketPrice> priceMap = quotes.stream()
                .collect(Collectors.toMap(
                        MarketPrice::getSymbol,
                        Function.identity()
                ));

        // 6. Calculate financial valuation
        PortfolioValuationResponse report =
                valuationCalculator.calculate(
                        portfolio.getId(),
                        portfolio.getName(),
                        portfolio.getInitialCash(),
                        trades,
                        priceMap
                );

        // 7. Reject stale or invalid price timestamps
        freshnessPolicy.validate(report);

        // 8. Verify accounting consistency
        validateAccountingInvariant(report);

        return report;
    }

    private void validateAccountingInvariant(
            PortfolioValuationResponse report
    ) {

        // Total value must equal cash plus market value
        if (report.totalValue().compareTo(
                report.availableCash().add(report.marketValue())
        ) != 0) {
            throw new IllegalStateException(
                    "Portfolio valuation does not reconcile"
            );
        }

        // Simplified model:
        // no deposits, withdrawals, fees, dividends or FX
        if (report.totalPnl().compareTo(
                report.totalValue().subtract(report.initialCash())
        ) != 0) {
            throw new IllegalStateException(
                    "Portfolio P&L does not reconcile"
            );
        }

        // Total P&L must equal realised plus unrealised
        if (report.totalPnl().compareTo(
                report.realisedPnl().add(report.unrealisedPnl())
        ) != 0) {
            throw new IllegalStateException(
                    "P&L components do not reconcile"
            );
        }
    }
}
