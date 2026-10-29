
package com.minialaddin.exception;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.model.Portfolio;
import com.minialaddin.service.PortfolioService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioService portfolioService;

    @Test
    void shouldReturnProblemDetailForMissingPortfolio()
            throws Exception {

        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(
                        get("/api/portfolios/{id}", -1L)
                                .header("X-Correlation-ID", correlationId)
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.instance").value(
                        "/api/portfolios/-1"
                ))
                .andExpect(jsonPath("$.correlationId").value(
                        correlationId
                ))
                .andExpect(header().string(
                        "X-Correlation-ID", correlationId
                ));
    }

    @Test
    void shouldReturnProblemDetailForInvalidTrade()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "M5 Error Handling Test",
                new BigDecimal("1000.00")
        );

        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(
                        post(
                                "/api/portfolios/{id}/trades",
                                portfolio.getId()
                        )
                                .header("X-Correlation-ID", correlationId)
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                    {
                      "symbol": "AAPL",
                      "side": "BUY",
                      "quantity": 10,
                      "price": 200.00
                    }
                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        "Insufficient cash"
                ))
                .andExpect(jsonPath("$.correlationId").value(
                        correlationId
                ));
    }

    @Test
    void shouldReturnValidationErrors()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "M5 Validation Test",
                new BigDecimal("1000.00")
        );

        mockMvc.perform(
                        post(
                                "/api/portfolios/{id}/trades",
                                portfolio.getId()
                        )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                    {
                      "symbol": "AAPL",
                      "side": "BUY",
                      "quantity": -5,
                      "price": 200.00
                    }
                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.title").value(
                        "Validation Failed"
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void shouldHandleMissingIdempotencyHeader()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "M5 Header Test",
                new BigDecimal("1000.00")
        );

        mockMvc.perform(
                        post(
                                "/api/portfolios/{id}/trades",
                                portfolio.getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                    {
                      "symbol": "AAPL",
                      "side": "BUY",
                      "quantity": 1,
                      "price": 100.00
                    }
                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void shouldHandleMalformedJson()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "M5 JSON Test",
                new BigDecimal("1000.00")
        );

        mockMvc.perform(
                        post(
                                "/api/portfolios/{id}/trades",
                                portfolio.getId()
                        )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ invalid json ")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.correlationId").exists());
    }
}
