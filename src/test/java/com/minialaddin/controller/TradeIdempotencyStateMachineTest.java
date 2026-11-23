
package com.minialaddin.controller;

import com.jayway.jsonpath.JsonPath;
import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.model.Portfolio;
import com.minialaddin.repository.TradeRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static com.minialaddin.support.MoneyMatchers.money;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("R2")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class TradeIdempotencyStateMachineTest {

    private static final String KEY = "fsm-test-key-0001";

    private static final String TOO_EXPENSIVE = """
            {"symbol":"AAPL","side":"BUY",
             "quantity":3,"price":100.00}
            """;

    private static final String VALID = """
            {"symbol":"AAPL","side":"BUY",
             "quantity":2,"price":100.00}
            """;

    private static final String EQUIVALENT = """
            {"symbol":"aapl","side":"BUY",
             "quantity":2,"price":100.0}
            """;

    private static final String DIFFERENT = """
            {"symbol":"AAPL","side":"BUY",
             "quantity":1,"price":100.00}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeRepository tradeRepository;

    private ResultActions sendTrade(
            Long portfolioId,
            String body
    ) throws Exception {
        return mockMvc.perform(post(
                "/api/portfolios/{id}/trades",
                portfolioId
        )
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void assertPortfolioState(
            Long portfolioId,
            int expectedTrades,
            String expectedCash
    ) throws Exception {

        int count = tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(
                        portfolioId
                )
                .size();

        assertEquals(expectedTrades, count);

        mockMvc.perform(get(
                        "/api/portfolios/{id}/cash",
                        portfolioId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash",
                        money(expectedCash)));
    }

    @Test
    @DisplayName("FSM: idempotency key follows U->U, U->B, B->B (replay), B->B (conflict)")
    void shouldFollowIdempotencyStateMachine()
            throws Exception {

        // Initial state: U (Unbound)
        Portfolio portfolio = portfolioService.createPortfolio(
                "FSM Test Portfolio",
                new BigDecimal("200.00")
        );

        Long id = portfolio.getId();

        assertPortfolioState(id, 0, "200.00");

        // T1: U -> U
        // Insufficient cash must not bind the key.
        sendTrade(id, TOO_EXPENSIVE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Insufficient cash"));

        assertPortfolioState(id, 0, "200.00");

        // T2: U -> B
        // The same key can now execute a valid trade.
        MvcResult created = sendTrade(id, VALID)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol")
                        .value("AAPL"))
                .andExpect(jsonPath("$.quantity")
                        .value(2))
                .andReturn();

        long originalTradeId = ((Number) JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.id"
        )).longValue();

        assertPortfolioState(id, 1, "0.00");

        // T3: B -> B
        // Equivalent payload replays the original trade.
        sendTrade(id, EQUIVALENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(originalTradeId));

        assertPortfolioState(id, 1, "0.00");

        // T4: B -> B
        // Different payload conflicts with the existing binding.
        sendTrade(id, DIFFERENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status")
                        .value(409))
                .andExpect(jsonPath("$.detail")
                        .value("Idempotency-Key was already used "
                                + "with a different request"));

        assertPortfolioState(id, 1, "0.00");

        // T3 again: B -> B
        // A conflict must not corrupt the original binding.
        sendTrade(id, VALID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(originalTradeId));

        assertPortfolioState(id, 1, "0.00");

        // Final observable portfolio state
        mockMvc.perform(get(
                        "/api/portfolios/{id}/positions",
                        id
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol")
                        .value("AAPL"))
                .andExpect(jsonPath("$[0].quantity")
                        .value(2));
    }
}
