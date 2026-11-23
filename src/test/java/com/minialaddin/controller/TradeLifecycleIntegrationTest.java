
package com.minialaddin.controller;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.model.Portfolio;
import com.minialaddin.service.PortfolioService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import static com.minialaddin.support.MoneyMatchers.assertMoneyField;
import static com.minialaddin.support.MoneyMatchers.money;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R1/R3/R4: a complete business round trip through the HTTP API and
 * PostgreSQL, rather than isolated method calls.
 *
 * The portfolio buys one stock in two lots and then sells the whole holding
 * at the purchase price, so the end state is known without reading the
 * code: cash back to the starting amount, no positions and zero P&amp;L.
 * The three requests spell the symbol in three different letter cases;
 * if they were not treated as one instrument, the lots would form two
 * holdings and the final SELL would be rejected for an insufficient
 * position. The sold-out symbol never gets a market price, so the final
 * valuation also checks that a closed position does not need one (R4).
 */
@Tag("R1")
@Tag("R3")
@Tag("R4")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class TradeLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioService portfolioService;

    private void trade(
            Long portfolioId,
            String key,
            String symbol,
            String side,
            int quantity,
            String expectedSymbol
    ) throws Exception {
        mockMvc.perform(post("/api/portfolios/{id}/trades", portfolioId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"%s","side":"%s","quantity":%d,"price":100.00}
                                """.formatted(symbol, side, quantity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value(expectedSymbol));
    }

    @Test
    @DisplayName("R1-F1: buying in two lots and selling everything returns the portfolio to its starting state")
    void shouldReturnToStartingStateAfterFullRoundTrip() throws Exception {

        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 10);

        String upper = ("RT" + suffix).toUpperCase(Locale.ROOT);
        String lower = upper.toLowerCase(Locale.ROOT);
        String mixed = "Rt" + suffix.toLowerCase(Locale.ROOT);

        Portfolio portfolio = portfolioService.createPortfolio(
                "Round Trip Fund",
                new BigDecimal("10000.00")
        );

        Long id = portfolio.getId();

        // 1. Two BUY lots, spelled differently: 6 + 4 shares at 100.00.
        trade(id, "round-trip-0001", lower, "BUY", 6, upper);
        trade(id, "round-trip-0002", upper, "BUY", 4, upper);

        // 2. One holding of 10 shares, and cash reduced by exactly 1,000.00.
        mockMvc.perform(get("/api/portfolios/{id}/positions", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value(upper))
                .andExpect(jsonPath("$[0].quantity").value(10));

        mockMvc.perform(get("/api/portfolios/{id}/cash", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash", money("9000.00")));

        // 3. Sell the whole holding at the purchase price.
        trade(id, "round-trip-0003", mixed, "SELL", 10, upper);

        // 4. Back to the starting state: no holding, original cash.
        mockMvc.perform(get("/api/portfolios/{id}/positions", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        String cashJson = mockMvc.perform(get("/api/portfolios/{id}/cash", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash", money("10000.00")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertMoneyField(cashJson, "availableCash", "10000.00");

        // 5. The valuation reconciles to zero P&L, and the sold-out symbol,
        //    which has no market price at all, does not block it.
        mockMvc.perform(get("/api/portfolios/{id}/valuation", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(0))
                .andExpect(jsonPath("$.availableCash", money("10000.00")))
                .andExpect(jsonPath("$.marketValue", money("0.00")))
                .andExpect(jsonPath("$.totalValue", money("10000.00")))
                .andExpect(jsonPath("$.realisedPnl", money("0.00")))
                .andExpect(jsonPath("$.totalPnl", money("0.00")));

        // 6. The history keeps all three trades under one canonical symbol.
        mockMvc.perform(get("/api/portfolios/{id}/trades", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].symbol", everyItem(equalTo(upper))));
    }
}
