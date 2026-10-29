
package com.minialaddin.controller;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.TradeSide;
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
class PortfolioReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeService tradeService;

    private void execute(
            Long portfolioId,
            String symbol,
            TradeSide side,
            String quantity,
            String price
    ) {
        CreateTradeRequest request = new CreateTradeRequest(
                symbol,
                side,
                new BigDecimal(quantity),
                new BigDecimal(price)
        );

        tradeService.executeTrade(
                portfolioId,
                request,
                UUID.randomUUID().toString()
        );
    }

    @Test
    void shouldReturnCorrectCashAndPositions() throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "M4 Report Test Fund",
                new BigDecimal("10000.00")
        );

        Long id = portfolio.getId();

        execute(id, "AAPL", TradeSide.BUY, "10", "200.00");
        execute(id, "MSFT", TradeSide.BUY, "5", "300.00");
        execute(id, "AAPL", TradeSide.SELL, "3", "220.00");

        mockMvc.perform(get(
                        "/api/portfolios/{id}/cash", id
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value(id))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.initialCash").value(10000.00))
                .andExpect(jsonPath("$.availableCash").value(7160.00));

        mockMvc.perform(get(
                        "/api/portfolios/{id}/positions", id
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].quantity").value(7))
                .andExpect(jsonPath("$[1].symbol").value("MSFT"))
                .andExpect(jsonPath("$[1].quantity").value(5));
    }

    @Test
    void shouldReturnNotFoundForUnknownPortfolio() throws Exception {

        mockMvc.perform(get(
                        "/api/portfolios/{id}/cash", -1L
                ))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(
                        "/api/portfolios/{id}/positions", -1L
                ))
                .andExpect(status().isNotFound());
    }
}
