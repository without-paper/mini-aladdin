
package com.minialaddin.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class CorrelationIdIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAddCorrelationIdToRealApiResponse()
            throws Exception {

        var result = mockMvc.perform(
                        get("/api/portfolios/-1")
                )
                .andExpect(status().isNotFound())
                .andReturn();

        String id = result.getResponse()
                .getHeader("X-Correlation-ID");

        assertNotNull(id);

        assertEquals(
                id,
                UUID.fromString(id).toString()
        );
    }

    @Test
    void shouldPreserveIncomingIdAcrossRequest()
            throws Exception {

        String id = UUID.randomUUID().toString();

        mockMvc.perform(
                        get("/api/portfolios/-1")
                                .header("X-Correlation-ID", id)
                )
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                        "X-Correlation-ID",
                        id
                ));
    }
}
