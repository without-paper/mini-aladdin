
package com.minialaddin.controller;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.model.Portfolio;
import com.minialaddin.repository.TradeRepository;
import com.minialaddin.service.PortfolioService;

import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static com.minialaddin.support.MoneyMatchers.money;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R2/R6: trade requests that are partly valid, or correctly formatted but
 * sitting exactly on a length limit.
 *
 * These are the inputs a real client is most likely to get slightly wrong.
 * Each test checks the HTTP outcome and that a rejected request leaves no
 * trade behind; the partly valid case also checks that the rejection does
 * not consume the Idempotency-Key.
 */
@Tag("R2")
@Tag("R6")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class TradeRequestEdgeCaseIntegrationTest {

    private static final String VALID_BODY = """
            {"symbol":"AAPL","side":"BUY","quantity":2,"price":100.00}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    private Long newPortfolio(String name) {
        Portfolio portfolio = portfolioService.createPortfolio(
                name,
                new BigDecimal("1000.00")
        );
        return portfolio.getId();
    }

    private ResultActions postTrade(Long portfolioId, String key, String body)
            throws Exception {
        return mockMvc.perform(post("/api/portfolios/{id}/trades", portfolioId)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private int tradeCount(Long portfolioId) {
        return tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(portfolioId)
                .size();
    }

    private double executedCount() {
        return meterRegistry.get("mini_aladdin.trades.executed")
                .counter()
                .count();
    }

    @Test
    @DisplayName("R6-E1: a partly valid request is rejected as a whole, names every bad field and leaves the key unused")
    void shouldRejectPartlyValidRequestWithoutSideEffects() throws Exception {

        Long id = newPortfolio("Partly Valid Request Fund");
        double executedBefore = executedCount();

        // Symbol and side are valid; a fractional share and a third
        // decimal place in the price are not.
        postTrade(id, "partly-valid-0001", """
                {"symbol":"AAPL","side":"BUY","quantity":2.5,"price":100.005}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("quantity", "price")));

        // Nothing was written or counted.
        assertEquals(0, tradeCount(id));
        assertEquals(executedBefore, executedCount());

        // The rejected request did not bind the key: the corrected
        // request with the same key executes normally.
        postTrade(id, "partly-valid-0001", VALID_BODY)
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/portfolios/{id}/cash", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash", money("800.00")));
    }

    @ParameterizedTest(name = "{0}: Idempotency-Key of {1} characters -> HTTP {2}")
    @CsvSource({
            "BV-K1, 7, 400",
            "BV-K2, 8, 201",
            "BV-K3, 128, 201",
            "BV-K4, 129, 400"
    })
    void shouldEnforceIdempotencyKeyLengthLimits(
            String caseId,
            int length,
            int expectedStatus
    ) throws Exception {

        Long id = newPortfolio("Key Length Fund " + caseId);

        // Only the length varies; every character is allowed.
        String key = "k".repeat(length);

        ResultActions result = postTrade(id, key, VALID_BODY)
                .andExpect(status().is(expectedStatus));

        if (expectedStatus == 400) {
            result.andExpect(jsonPath("$.detail")
                    .value("Invalid Idempotency-Key"));
            assertEquals(0, tradeCount(id));
        } else {
            // 128 characters must also fit the request_key column.
            assertEquals(1, tradeCount(id));
        }
    }

    @ParameterizedTest(name = "{0}: symbol of {1} characters -> HTTP {2}")
    @CsvSource({
            "BV-S1, 20, 201",
            "BV-S2, 21, 400"
    })
    void shouldEnforceSymbolLengthLimit(
            String caseId,
            int length,
            int expectedStatus
    ) throws Exception {

        Long id = newPortfolio("Symbol Length Fund " + caseId);

        // A well-formed symbol: a letter followed by letters and digits.
        String symbol = "S" + "1".repeat(length - 1);

        ResultActions result = postTrade(id, "symbol-length-" + length, """
                {"symbol":"%s","side":"BUY","quantity":1,"price":10.00}
                """.formatted(symbol))
                .andExpect(status().is(expectedStatus));

        if (expectedStatus == 400) {
            // Rejected by validation, not by the 20-character database
            // column (which would surface as a server error).
            result.andExpect(jsonPath("$.errors[*].field",
                    everyItem(equalTo("symbol"))));
            assertEquals(0, tradeCount(id));
        } else {
            result.andExpect(jsonPath("$.symbol").value(symbol));
            assertEquals(1, tradeCount(id));
        }
    }
}
