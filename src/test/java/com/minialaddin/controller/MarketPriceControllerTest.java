
package com.minialaddin.controller;

import com.minialaddin.config.TestDatabaseConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class MarketPriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String randomSymbol() {
        return "T" + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    @Test
    void shouldCreateAndRetrievePrice() throws Exception {

        String symbol = randomSymbol();

        mockMvc.perform(
                        put("/api/market-prices/{symbol}", symbol)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                        {"price": 230.00}
                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(symbol))
                .andExpect(jsonPath("$.price").value(230.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.updatedAt").exists());

        mockMvc.perform(
                        get("/api/market-prices/{symbol}", symbol)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(230.00));
    }

    @Test
    void shouldUpdateExistingPrice() throws Exception {

        String symbol = randomSymbol();

        mockMvc.perform(
                        put("/api/market-prices/{symbol}", symbol)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                        {"price": 200.00}
                        """)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        put("/api/market-prices/{symbol}", symbol)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                        {"price": 245.50}
                        """)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/market-prices/{symbol}", symbol)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(245.50));
    }

    @Test
    void shouldRejectNegativePrice() throws Exception {

        String symbol = randomSymbol();

        mockMvc.perform(
                        put("/api/market-prices/{symbol}", symbol)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                        {"price": -100.00}
                        """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFound() throws Exception {

        String symbol = randomSymbol();

        mockMvc.perform(
                        get("/api/market-prices/{symbol}", symbol)
                )
                .andExpect(status().isNotFound());
    }
}
