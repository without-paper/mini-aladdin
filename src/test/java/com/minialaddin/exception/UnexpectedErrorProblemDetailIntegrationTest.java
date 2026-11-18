
package com.minialaddin.exception;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.PortfolioValuationResponse;
import com.minialaddin.model.Portfolio;
import com.minialaddin.service.PortfolioService;
import com.minialaddin.service.PortfolioValuationCalculator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * R6/R8 follow-up: the HTTP 500 contract.
 *
 * JaCoCo showed that GlobalExceptionHandler.handleUnexpected() was never
 * executed by the final suite, so the documented behaviour for server
 * errors (Problem Details, a generic message with no internal detail,
 * and a correlation ID for diagnosis) was unverified.
 *
 * The valuation calculator is replaced by a mock that returns a report
 * whose P&L components disagree by one cent (the shape of defect D-02).
 * The real PortfolioValuationService invariant then fails, and the
 * response is checked end to end through the real filter and handler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class UnexpectedErrorProblemDetailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioService portfolioService;

    @MockitoBean
    private PortfolioValuationCalculator valuationCalculator;

    @Test
    void shouldReturnGenericProblemDetailWhenAccountingInvariantFails()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "R6 Server Error Test",
                new BigDecimal("1000.00")
        );

        // realised (0.01) + unrealised (0.00) != total P&L (0.00)
        when(valuationCalculator.calculate(any(), any(), any(), any(), any()))
                .thenReturn(new PortfolioValuationResponse(
                        portfolio.getId(),
                        "R6 Server Error Test",
                        "USD",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("0.01"),
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"),
                        List.of()
                ));

        String correlationId = UUID.randomUUID().toString();

        String body = mockMvc.perform(
                        get(
                                "/api/portfolios/{id}/valuation",
                                portfolio.getId()
                        )
                                .header("X-Correlation-ID", correlationId)
                )
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value(
                        "Internal Server Error"
                ))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.detail").value(
                        "An unexpected error occurred"
                ))
                .andExpect(jsonPath("$.instance").value(
                        "/api/portfolios/" + portfolio.getId() + "/valuation"
                ))
                .andExpect(jsonPath("$.correlationId").value(
                        correlationId
                ))
                .andExpect(header().string(
                        "X-Correlation-ID",
                        correlationId
                ))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The internal cause must not reach the client
        assertFalse(body.contains("reconcile"), body);
        assertFalse(body.contains("IllegalStateException"), body);
        assertFalse(body.contains("com.minialaddin"), body);
    }
}
