
package com.minialaddin.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PortfolioNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(
            PortfolioNotFoundException ex
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException ex
    ) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField() + ": " +
                                error.getDefaultMessage()
                )
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest()
                .body(Map.of("error", message));
    }


    @ExceptionHandler(TradeRejectedException.class)
    public ResponseEntity<Map<String, String>> handleTradeRejected(
            TradeRejectedException ex
    ) {
        return ResponseEntity
                .badRequest()
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, String>> handleIdempotencyConflict(
            IdempotencyConflictException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

}
