
package com.minialaddin.controller;

import com.minialaddin.dto.MarketPriceResponse;
import com.minialaddin.dto.UpdateMarketPriceRequest;
import com.minialaddin.service.MarketPriceService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/market-prices")
public class MarketPriceController {

    private final MarketPriceService service;

    public MarketPriceController(
            MarketPriceService service
    ) {
        this.service = service;
    }

    @PutMapping("/{symbol}")
    public ResponseEntity<MarketPriceResponse> updatePrice(
            @PathVariable String symbol,
            @Valid @RequestBody UpdateMarketPriceRequest request
    ) {
        MarketPriceResponse response =
                service.updatePrice(
                        symbol,
                        request.price()
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<MarketPriceResponse> getPrice(
            @PathVariable String symbol
    ) {
        return ResponseEntity.ok(
                service.getPrice(symbol)
        );
    }
}
