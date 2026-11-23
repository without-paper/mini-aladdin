
package com.minialaddin.service;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.TradeSide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import static com.minialaddin.support.MoneyMatchers.assertMoneyField;
import static com.minialaddin.support.MoneyMatchers.money;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * R3: the D-02 rounding fix, checked through the real API and database.
 * A random symbol isolates the quote from other tests, and the raw JSON
 * is checked for two-decimal values.
 */
@Tag("R3")
@Tag("integration")
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
            String symbol,
            TradeSide side,
            String price,
            String key
    ) {
        tradeService.executeTrade(
                portfolioId,
                new CreateTradeRequest(
                        symbol,
                        side,
                        new BigDecimal("1"),
                        new BigDecimal(price)
                ),
                key
        );
    }

    @Test
    @DisplayName("D-02: rounded valuation reconciles through the API")
    void shouldReturnReconciledValuationThroughApi()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "Rounding Integration Fund",
                new BigDecimal("100.00")
        );

        Long id = portfolio.getId();

        String symbol = "R" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase(Locale.ROOT);

        // Three committed database transactions
        execute(id, symbol, TradeSide.BUY, "0.01", "rounding-buy-001");
        execute(id, symbol, TradeSide.BUY, "0.02", "rounding-buy-002");
        execute(id, symbol, TradeSide.SELL, "0.02", "rounding-sell-003");

        // Fresh quote from the actual price service
        marketPriceService.updatePrice(
                symbol,
                new BigDecimal("0.02")
        );

        // R3: a valid portfolio should be valuated
        // successfully and return the correct total.
        String json = mockMvc.perform(get(
                        "/api/portfolios/{id}/valuation",
                        id
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash",
                        money("99.99")))
                .andExpect(jsonPath("$.marketValue",
                        money("0.02")))
                .andExpect(jsonPath("$.totalValue",
                        money("100.01")))
                .andExpect(jsonPath("$.totalPnl",
                        money("0.01")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertMoneyField(json, "availableCash", "99.99");
        assertMoneyField(json, "marketValue", "0.02");
        assertMoneyField(json, "totalValue", "100.01");
        assertMoneyField(json, "totalPnl", "0.01");
    }
}
