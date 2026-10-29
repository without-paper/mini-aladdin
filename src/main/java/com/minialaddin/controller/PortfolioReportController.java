
package com.minialaddin.controller;

import com.minialaddin.dto.CashResponse;
import com.minialaddin.dto.PositionResponse;
import com.minialaddin.service.PortfolioReportService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}")
public class PortfolioReportController {

    private final PortfolioReportService service;

    public PortfolioReportController(
            PortfolioReportService service
    ) {
        this.service = service;
    }

    @GetMapping("/cash")
    public CashResponse getCash(
            @PathVariable Long portfolioId
    ) {
        return service.getCash(portfolioId);
    }

    @GetMapping("/positions")
    public List<PositionResponse> getPositions(
            @PathVariable Long portfolioId
    ) {
        return service.getPositions(portfolioId);
    }
}
