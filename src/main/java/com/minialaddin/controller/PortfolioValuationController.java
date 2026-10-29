
package com.minialaddin.controller;

import com.minialaddin.dto.PortfolioValuationResponse;
import com.minialaddin.service.PortfolioValuationService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/valuation")
public class PortfolioValuationController {

    private final PortfolioValuationService service;

    public PortfolioValuationController(
            PortfolioValuationService service
    ) {
        this.service = service;
    }

    @GetMapping
    public PortfolioValuationResponse getValuation(
            @PathVariable Long portfolioId
    ) {
        return service.getValuation(portfolioId);
    }
}
