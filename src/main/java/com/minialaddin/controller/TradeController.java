
package com.minialaddin.controller;

import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.dto.TradeResponse;
import com.minialaddin.service.TradeService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/trades")
public class TradeController {

    private final TradeService service;

    public TradeController(TradeService service) {
        this.service = service;
    }

    @GetMapping
    public List<TradeResponse> getTrades(
            @PathVariable Long portfolioId
    ) {
        return service.getTrades(portfolioId);
    }

    @PostMapping
    public ResponseEntity<TradeResponse> createTrade(
            @PathVariable Long portfolioId,
            @Valid @RequestBody CreateTradeRequest request
    ) {
        TradeResponse result = service.executeTrade(
                portfolioId,
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }
}
