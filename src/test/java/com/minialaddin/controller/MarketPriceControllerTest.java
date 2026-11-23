
package com.minialaddin.controller;

import com.minialaddin.config.TestDatabaseConfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.minialaddin.support.MoneyMatchers.money;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("R4")
@Tag("R6")
@Tag("integration")
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
    @DisplayName("R4-P1: a new market price can be stored and read back")
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
                .andExpect(jsonPath("$.price", money("230.00")))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.updatedAt").exists());

        mockMvc.perform(
                        get("/api/market-prices/{symbol}", symbol)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price", money("230.00")));
    }

    @Test
    @DisplayName("R4-P2: storing a price again replaces the existing quote")
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
                .andExpect(jsonPath("$.price", money("245.50")));
    }

    @Test
    @DisplayName("R6-P3: a negative price is rejected with 400 and not stored")
    void shouldRejectNegativePrice() throws Exception {

        String symbol = randomSymbol();

        mockMvc.perform(
                        put("/api/market-prices/{symbol}", symbol)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                        {"price": -100.00}
                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(
                        get("/api/market-prices/{symbol}", symbol)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("R6-P4: an unknown symbol returns 404")
    void shouldReturnNotFound() throws Exception {

        String symbol = randomSymbol();

        mockMvc.perform(
                        get("/api/market-prices/{symbol}", symbol)
                )
                .andExpect(status().isNotFound());
    }
}
