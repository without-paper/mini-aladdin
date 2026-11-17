
package com.minialaddin.controller;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.model.Portfolio;
import com.minialaddin.repository.TradeRepository;
import com.minialaddin.service.PortfolioService;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class TradeIdempotencyCombinatorialTest {

    private static final String EXISTING_KEY =
            "existing-key-0001";

    private static final String FRESH_KEY =
            "fresh-key-0002";

    private static final String ORIGINAL_BODY =
            """
            {"symbol":"AAPL","side":"BUY",
             "quantity":2,"price":100.00}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeRepository tradeRepository;

    private String requestBody(String choice) {
        return switch (choice) {
            case "identical" -> ORIGINAL_BODY;

            case "equivalent" -> """
                {"symbol":"aapl","side":"BUY",
                 "quantity":2,"price":100.0}
                """;

            case "different" -> """
                {"symbol":"AAPL","side":"BUY",
                 "quantity":3,"price":100.00}
                """;

            default -> throw new IllegalArgumentException(
                    "Unknown payload choice: " + choice
            );
        };
    }

    private int tradeCount(Long portfolioId) {
        return tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(
                        portfolioId
                ).size();
    }

    @ParameterizedTest(
            name = "{index}: portfolio={0}, key={1}, payload={2}"
    )
    @CsvSource({
            "A, reused, identical, 200, 1, 0",
            "A, reused, equivalent, 200, 1, 0",
            "A, reused, different, 409, 1, 0",
            "A, fresh, identical, 201, 2, 0",
            "A, fresh, equivalent, 201, 2, 0",
            "A, fresh, different, 201, 2, 0",
            "B, reused, identical, 201, 1, 1",
            "B, reused, equivalent, 201, 1, 1",
            "B, reused, different, 201, 1, 1",
            "B, fresh, identical, 201, 1, 1",
            "B, fresh, equivalent, 201, 1, 1",
            "B, fresh, different, 201, 1, 1"
    })
    void shouldRespectIdempotencyCombinations(
            String targetChoice,
            String keyChoice,
            String payloadChoice,
            int expectedStatus,
            int expectedCountA,
            int expectedCountB
    ) throws Exception {

        // Arrange: two independent portfolios
        Portfolio portfolioA = portfolioService.createPortfolio(
                "Combinatorial A",
                new BigDecimal("10000.00")
        );

        Portfolio portfolioB = portfolioService.createPortfolio(
                "Combinatorial B",
                new BigDecimal("10000.00")
        );

        // Seed an existing trade in portfolio A
        mockMvc.perform(post(
                        "/api/portfolios/{id}/trades",
                        portfolioA.getId()
                )
                        .header("Idempotency-Key", EXISTING_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORIGINAL_BODY))
                .andExpect(status().isCreated());

        Long originalTradeId = tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(
                        portfolioA.getId()
                )
                .get(0)
                .getId();

        Long targetId = targetChoice.equals("A")
                ? portfolioA.getId()
                : portfolioB.getId();

        String requestKey = keyChoice.equals("reused")
                ? EXISTING_KEY
                : FRESH_KEY;

        // Act: execute the selected test frame
        ResultActions result = mockMvc.perform(post(
                "/api/portfolios/{id}/trades",
                targetId
        )
                .header("Idempotency-Key", requestKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(payloadChoice)));

        // Assert: HTTP contract
        result.andExpect(status().is(expectedStatus));

        if (expectedStatus == 200) {
            // Replay returns the original trade
            result.andExpect(
                    jsonPath("$.id").value(originalTradeId)
            );
        } else if (expectedStatus == 409) {
            result.andExpect(
                    jsonPath("$.status").value(409)
            );
        } else {
            // A new trade is executed
            result.andExpect(
                    jsonPath("$.symbol").value("AAPL")
            );
            result.andExpect(
                    jsonPath("$.side").value("BUY")
            );
        }

        // Assert: database state
        assertEquals(
                expectedCountA,
                tradeCount(portfolioA.getId())
        );

        assertEquals(
                expectedCountB,
                tradeCount(portfolioB.getId())
        );

        // Assert: portfolio cash remains consistent
        int newTradeCost = payloadChoice.equals("different")
                ? 300
                : 200;

        boolean newTradeInA =
                targetChoice.equals("A")
                        && keyChoice.equals("fresh");

        int expectedCashA = 9800
                - (newTradeInA ? newTradeCost : 0);

        int expectedCashB = 10000
                - (targetChoice.equals("B") ? newTradeCost : 0);

        mockMvc.perform(get(
                        "/api/portfolios/{id}/cash",
                        portfolioA.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash")
                        .value((double) expectedCashA));

        mockMvc.perform(get(
                        "/api/portfolios/{id}/cash",
                        portfolioB.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash")
                        .value((double) expectedCashB));
    }

    @Test
    void shouldRejectInvalidIdempotencyKey()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "Invalid Key Test",
                new BigDecimal("10000.00")
        );

        mockMvc.perform(post(
                        "/api/portfolios/{id}/trades",
                        portfolio.getId()
                )
                        .header("Idempotency-Key", "bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORIGINAL_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertEquals(
                0,
                tradeCount(portfolio.getId())
        );
    }
}
