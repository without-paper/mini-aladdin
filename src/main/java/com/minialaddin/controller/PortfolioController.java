
package com.minialaddin.controller;

import com.minialaddin.model.Portfolio;
import com.minialaddin.service.PortfolioService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    public record CreatePortfolioRequest(
            String name,
            BigDecimal initialCash
    ) {}

    @PostMapping
    public ResponseEntity<Portfolio> create(
            @RequestBody CreatePortfolioRequest request
    ) {
        Portfolio portfolio = service.createPortfolio(
                request.name(),
                request.initialCash()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(portfolio);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Portfolio> getById(
            @PathVariable Long id
    ) {
        Portfolio portfolio = service.getPortfolio(id);

        if (portfolio == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(portfolio);
    }
}
