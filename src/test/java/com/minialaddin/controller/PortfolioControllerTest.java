
package com.minialaddin.controller;
import com.minialaddin.config.TestDatabaseConfiguration;

import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;


    @Test
    void shouldCreateAndRetrievePortfolio() throws Exception {
        String body = """
        {
            "name": "Growth Fund",
            "initialCash": 100000.00
        }
        """;

        String response = mockMvc.perform(
                        post("/api/portfolios")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Growth Fund"))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = new ObjectMapper().readTree(response);
        long id = json.get("id").asLong();

        mockMvc.perform(get("/api/portfolios/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Growth Fund"));
    }


    @Test
    void shouldRejectNegativeInitialCash() throws Exception {
        String body = """
            {
                "name": "Invalid Fund",
                "initialCash": -100
            }
            """;

        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldRejectBlankPortfolioName() throws Exception {
        String body = """
            {
                "name": "",
                "initialCash": 100
            }
            """;

        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/portfolios/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }
}
