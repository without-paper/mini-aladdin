
package com.minialaddin.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.slf4j.MDC;

import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CorrelationIdFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        MDC.remove(CorrelationIdFilter.MDC_KEY);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @RestController
    static class TestController {

        @GetMapping("/test-correlation")
        ResponseEntity<String> test() {

            return ResponseEntity.ok(
                    MDC.get(CorrelationIdFilter.MDC_KEY)
            );
        }
    }

    @Test
    void shouldGenerateCorrelationIdWhenMissing()
            throws Exception {

        var result = mockMvc.perform(
                        get("/test-correlation")
                )
                .andExpect(status().isOk())
                .andReturn();

        String id = result.getResponse()
                .getHeader("X-Correlation-ID");

        assertNotNull(id);

        assertEquals(
                id,
                UUID.fromString(id).toString()
        );

        assertEquals(
                id,
                result.getResponse().getContentAsString()
        );
    }

    @Test
    void shouldPreserveValidIncomingCorrelationId()
            throws Exception {

        String id = UUID.randomUUID().toString();

        mockMvc.perform(
                        get("/test-correlation")
                                .header("X-Correlation-ID", id)
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "X-Correlation-ID",
                        id
                ))
                .andExpect(content().string(id));
    }

    @Test
    void shouldReplaceInvalidCorrelationId()
            throws Exception {

        var result = mockMvc.perform(
                        get("/test-correlation")
                                .header(
                                        "X-Correlation-ID",
                                        "invalid-id"
                                )
                )
                .andExpect(status().isOk())
                .andReturn();

        String returnedId = result.getResponse()
                .getHeader("X-Correlation-ID");

        assertNotNull(returnedId);
        assertNotEquals("invalid-id", returnedId);

        assertEquals(
                returnedId,
                UUID.fromString(returnedId).toString()
        );
    }

    @Test
    void shouldGenerateDifferentIdsForDifferentRequests()
            throws Exception {

        String firstId = mockMvc.perform(
                        get("/test-correlation")
                )
                .andReturn()
                .getResponse()
                .getHeader("X-Correlation-ID");

        String secondId = mockMvc.perform(
                        get("/test-correlation")
                )
                .andReturn()
                .getResponse()
                .getHeader("X-Correlation-ID");

        assertNotNull(firstId);
        assertNotNull(secondId);

        assertNotEquals(firstId, secondId);
    }

    @Test
    void shouldCleanMdcAfterRequest()
            throws Exception {

        mockMvc.perform(
                        get("/test-correlation")
                )
                .andExpect(status().isOk());

        assertNull(
                MDC.get(CorrelationIdFilter.MDC_KEY)
        );
    }

    @Test
    void shouldRestorePreviousMdcValue()
            throws Exception {

        MDC.put(
                CorrelationIdFilter.MDC_KEY,
                "outer-context"
        );

        try {

            mockMvc.perform(
                            get("/test-correlation")
                    )
                    .andExpect(status().isOk());

            assertEquals(
                    "outer-context",
                    MDC.get(CorrelationIdFilter.MDC_KEY)
            );

        } finally {

            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
