
package com.minialaddin.controller;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.TradeSide;
import com.minialaddin.service.MarketPriceService;
import com.minialaddin.service.PortfolioService;
import com.minialaddin.service.TradeService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class PortfolioValuationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private MarketPriceService marketPriceService;

    private void trade(
            Long portfolioId,
            String symbol,
            TradeSide side,
            String quantity,
            String price
    ) {
        tradeService.executeTrade(
                portfolioId,
                new CreateTradeRequest(
                        symbol,
                        side,
                        new BigDecimal(quantity),
                        new BigDecimal(price)
                ),
                UUID.randomUUID().toString()
        );
    }

    @Test
    void shouldCalculateCompletePortfolioValuation() throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "M4 Valuation Test",
                new BigDecimal("10000.00")
        );

        Long id = portfolio.getId();

        trade(id, "AAPL", TradeSide.BUY, "10", "200.00");
        trade(id, "MSFT", TradeSide.BUY, "5", "300.00");
        trade(id, "AAPL", TradeSide.SELL, "3", "220.00");

        marketPriceService.updatePrice(
                "AAPL", new BigDecimal("230.00")
        );

        marketPriceService.updatePrice(
                "MSFT", new BigDecimal("320.00")
        );

        mockMvc.perform(get(
                        "/api/portfolios/{id}/valuation", id
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.availableCash").value(7160.00))
                .andExpect(jsonPath("$.marketValue").value(3210.00))
                .andExpect(jsonPath("$.totalValue").value(10370.00))
                .andExpect(jsonPath("$.realisedPnl").value(60.00))
                .andExpect(jsonPath("$.unrealisedPnl").value(310.00))
                .andExpect(jsonPath("$.totalPnl").value(370.00))
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.positions[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.positions[1].symbol").value("MSFT"));
    }

    @Test
    void shouldReturn404ForUnknownPortfolio() throws Exception {
        mockMvc.perform(get(
                        "/api/portfolios/{id}/valuation", -1L
                ))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldValueEmptyPortfolioWithoutMarketPrices()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "M4 Empty Portfolio Test",
                new BigDecimal("5000.00")
        );

        mockMvc.perform(get(
                        "/api/portfolios/{id}/valuation",
                        portfolio.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash").value(5000.00))
                .andExpect(jsonPath("$.marketValue").value(0.00))
                .andExpect(jsonPath("$.totalValue").value(5000.00))
                .andExpect(jsonPath("$.totalPnl").value(0.00))
                .andExpect(jsonPath("$.positions.length()").value(0));
    }

    @Test
    void shouldRejectValuationWhenMarketPriceMissing()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "M4 Missing Quote Test",
                new BigDecimal("10000.00")
        );

        Long id = portfolio.getId();

        String symbol = "T" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase();

        trade(
                id,
                symbol,
                TradeSide.BUY,
                "5",
                "100.00"
        );

        mockMvc.perform(get(
                        "/api/portfolios/{id}/valuation", id
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Missing market price for " + symbol
                ));
    }

}
