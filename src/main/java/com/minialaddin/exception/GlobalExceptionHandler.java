
package com.minialaddin.exception;

import com.minialaddin.config.CorrelationIdFilter;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import org.springframework.web.servlet.mvc.method.annotation
        .ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler
        extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // =========================================================
    // 404 - Resources not found
    // =========================================================

    @ExceptionHandler({
            PortfolioNotFoundException.class,
            MarketPriceNotFoundException.class
    })
    public ResponseEntity<ProblemDetail> handleNotFound(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request
        );
    }

    // =========================================================
    // 400 - Business rule validation failures
    // =========================================================

    @ExceptionHandler({
            TradeRejectedException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ProblemDetail> handleBadRequest(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request
        );
    }

    // =========================================================
    // 409 - Business conflicts
    // =========================================================

    @ExceptionHandler({
            IdempotencyConflictException.class,
            MissingMarketPriceException.class,
            StaleMarketPriceException.class
    })
    public ResponseEntity<ProblemDetail> handleConflict(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                request
        );
    }

    // =========================================================
    // 400 - Bean Validation errors
    // =========================================================

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {

        ProblemDetail detail = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );

        detail.setTitle("Validation Failed");
        detail.setDetail(
                "One or more request fields are invalid"
        );

        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null
                                ? "Invalid value"
                                : error.getDefaultMessage()
                ))
                .toList();

        detail.setProperty("errors", errors);

        return handleExceptionInternal(
                ex,
                detail,
                headers,
                HttpStatus.BAD_REQUEST,
                request
        );
    }

    // =========================================================
    // Common handling for Spring MVC exceptions
    // =========================================================

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {

        ProblemDetail detail;

        if (body instanceof ProblemDetail existing) {
            detail = existing;
        } else {
            detail = ProblemDetail.forStatus(statusCode);

            if (statusCode.is5xxServerError()) {
                detail.setDetail("An unexpected error occurred");
            } else {
                detail.setDetail("Invalid HTTP request");
            }
        }

        if (detail.getType() == null) {
            detail.setType(URI.create("about:blank"));
        }

        // Never expose internal details for a 5xx response
        if (statusCode.is5xxServerError()) {
            detail.setDetail("An unexpected error occurred");
        }

        if (request instanceof ServletWebRequest servletRequest) {

            HttpServletRequest httpRequest =
                    servletRequest.getRequest();

            detail.setInstance(
                    URI.create(httpRequest.getRequestURI())
            );
        }

        addCorrelationId(detail);

        HttpHeaders resultHeaders = new HttpHeaders();
        resultHeaders.addAll(headers);
        resultHeaders.setContentType(
                MediaType.APPLICATION_PROBLEM_JSON
        );

        return new ResponseEntity<>(
                detail,
                resultHeaders,
                statusCode
        );
    }

    // =========================================================
    // 500 - Unexpected server errors
    // =========================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {

        log.error(
                "Unhandled server error",
                ex
        );

        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request
        );
    }

    // =========================================================
    // Helper: Build a consistent ProblemDetail response
    // =========================================================

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {

        ProblemDetail detail =
                ProblemDetail.forStatus(status);

        detail.setType(URI.create("about:blank"));
        detail.setTitle(status.getReasonPhrase());
        detail.setDetail(message);
        detail.setInstance(
                URI.create(request.getRequestURI())
        );

        addCorrelationId(detail);

        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(detail);
    }

    // =========================================================
    // Helper: Attach the current request's correlation ID
    // =========================================================

    private void addCorrelationId(ProblemDetail detail) {

        String correlationId = MDC.get(
                CorrelationIdFilter.MDC_KEY
        );

        if (correlationId != null) {
            detail.setProperty(
                    "correlationId",
                    correlationId
            );
        }
    }
}
