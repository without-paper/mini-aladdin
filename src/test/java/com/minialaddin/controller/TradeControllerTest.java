
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

import static com.minialaddin.support.MoneyMatchers.money;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * R1/R6: the trade history endpoint, GET /api/portfolios/{id}/trades,
 * which no other Java test called.
 */
@Tag("R1")
@Tag("R6")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioService portfolioService;

    private void postTrade(Long portfolioId, String key, String body)
            throws Exception {
        mockMvc.perform(post("/api/portfolios/{id}/trades", portfolioId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("R1-H1: trade history lists executed trades in execution order")
    void shouldListTradesInExecutionOrder() throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "Trade History Fund",
                new BigDecimal("10000.00")
        );

        Long id = portfolio.getId();

        postTrade(id, "history-key-0001", """
                {"symbol":"AAPL","side":"BUY","quantity":10,"price":200.00}
                """);
        postTrade(id, "history-key-0002", """
                {"symbol":"AAPL","side":"SELL","quantity":4,"price":210.00}
                """);

        mockMvc.perform(get("/api/portfolios/{id}/trades", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].portfolioId").value(id))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].side").value("BUY"))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[0].price", money("200.00")))
                .andExpect(jsonPath("$[0].executedAt").exists())
                .andExpect(jsonPath("$[1].side").value("SELL"))
                .andExpect(jsonPath("$[1].quantity").value(4))
                .andExpect(jsonPath("$[1].price", money("210.00")));
    }

    @Test
    @DisplayName("R6-H2: trade history of an unknown portfolio returns 404 Problem Details")
    void shouldReturnProblemDetailForUnknownPortfolio() throws Exception {

        mockMvc.perform(get("/api/portfolios/{id}/trades", -1L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.correlationId").exists());
    }
}
