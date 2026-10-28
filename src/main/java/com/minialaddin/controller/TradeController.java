
package com.minialaddin.controller;

import com.minialaddin.dto.TradeResponse;
import com.minialaddin.service.TradeService;

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
}
