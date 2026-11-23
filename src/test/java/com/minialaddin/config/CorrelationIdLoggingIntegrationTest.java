
package com.minialaddin.config;

import com.minialaddin.model.Portfolio;
import com.minialaddin.service.PortfolioService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R8: diagnostic logs shall let maintainers trace a request and tell
 * committed, replayed and rejected trades apart. Earlier tests checked the
 * correlation ID in response headers and error bodies only; this test
 * checks that the application's own log lines carry it.
 */
@Tag("R8")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdLoggingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioService portfolioService;

    private void postTrade(
            Long portfolioId,
            String correlationId,
            String idempotencyKey,
            String body,
            ResultMatcher expectedStatus
    ) throws Exception {
        mockMvc.perform(post("/api/portfolios/{id}/trades", portfolioId)
                        .header("X-Correlation-ID", correlationId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(expectedStatus);
    }

    private static void assertLogged(
            List<String> lines,
            String correlationId,
            String message
    ) {
        assertTrue(
                lines.stream().anyMatch(line ->
                        line.contains(correlationId)
                                && line.contains(message)),
                () -> "No log line contains both \"" + message
                        + "\" and correlation ID " + correlationId
        );
    }

    @Test
    @DisplayName("R8-L1: executed, replayed and rejected trades are logged with their correlation IDs")
    void shouldLogTradeOutcomesWithCorrelationIds(CapturedOutput output)
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "Correlation Logging Fund",
                new BigDecimal("1000.00")
        );

        Long id = portfolio.getId();

        String executedId = UUID.randomUUID().toString();
        String replayedId = UUID.randomUUID().toString();
        String rejectedId = UUID.randomUUID().toString();

        String affordable = """
                {"symbol":"AAPL","side":"BUY","quantity":2,"price":100.00}
                """;
        String tooExpensive = """
                {"symbol":"AAPL","side":"BUY","quantity":50,"price":100.00}
                """;

        postTrade(id, executedId, "logging-key-0001", affordable,
                status().isCreated());
        postTrade(id, replayedId, "logging-key-0001", affordable,
                status().isOk());
        postTrade(id, rejectedId, "logging-key-0002", tooExpensive,
                status().isBadRequest());

        List<String> lines = output.getOut().lines().toList();

        assertLogged(lines, executedId, "Trade persisted");
        assertLogged(lines, replayedId, "Idempotent trade replay");
        assertLogged(lines, rejectedId, "Trade rejected: insufficient cash");

        for (String correlationId : List.of(executedId, replayedId, rejectedId)) {
            assertLogged(lines, correlationId, "HTTP request completed");
        }
    }
}
