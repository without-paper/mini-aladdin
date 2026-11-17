
package com.minialaddin.service;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.TradeSide;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class PortfolioValuationRoundingIntegrationTest {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private MarketPriceService marketPriceService;

    @Autowired
    private MockMvc mockMvc;

    private void execute(
            Long portfolioId,
            TradeSide side,
            String price,
            String key
    ) {
        tradeService.executeTrade(
                portfolioId,
                new CreateTradeRequest(
                        "AAPL",
                        side,
                        new BigDecimal("1"),
                        new BigDecimal(price)
                ),
                key
        );
    }

    @Test
    void shouldReturnReconciledValuationThroughApi()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "Rounding Integration Fund",
                new BigDecimal("100.00")
        );

        Long id = portfolio.getId();

        // Three committed database transactions
        execute(id, TradeSide.BUY, "0.01", "rounding-buy-001");
        execute(id, TradeSide.BUY, "0.02", "rounding-buy-002");
        execute(id, TradeSide.SELL, "0.02", "rounding-sell-003");

        // Fresh quote from the actual price service
        marketPriceService.updatePrice(
                "AAPL",
                new BigDecimal("0.02")
        );

        // R3: a valid portfolio should be valuated
        // successfully and return the correct total.
        mockMvc.perform(get(
                        "/api/portfolios/{id}/valuation",
                        id
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash")
                        .value(99.99))
                .andExpect(jsonPath("$.marketValue")
                        .value(0.02))
                .andExpect(jsonPath("$.totalValue")
                        .value(100.01))
                .andExpect(jsonPath("$.totalPnl")
                        .value(0.01));
    }
}
