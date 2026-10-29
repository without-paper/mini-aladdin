
package com.minialaddin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private static final Logger log =
            LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId = resolveCorrelationId(
                request.getHeader(HEADER_NAME)
        );

        String previousCorrelationId = MDC.get(MDC_KEY);

        long startNanos = System.nanoTime();

        response.setHeader(HEADER_NAME, correlationId);
        request.setAttribute(MDC_KEY, correlationId);

        MDC.put(MDC_KEY, correlationId);

        try {

            log.info(
                    "HTTP request started: {} {}",
                    request.getMethod(),
                    request.getRequestURI()
            );

            filterChain.doFilter(request, response);

        } finally {

            long durationMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startNanos
            );

            log.atInfo()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", durationMs)
                    .log("HTTP request completed");

            if (previousCorrelationId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousCorrelationId);
            }
        }
    }

    private String resolveCorrelationId(String incomingId) {

        if (incomingId != null) {
            try {
                UUID parsed = UUID.fromString(incomingId);

                String canonical = parsed.toString();

                if (canonical.equals(incomingId)) {
                    return canonical;
                }

            } catch (IllegalArgumentException ignored) {
                // Invalid ID: generate a new one
            }
        }

        return UUID.randomUUID().toString();
    }
}
