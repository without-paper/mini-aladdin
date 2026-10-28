
package com.minialaddin.controller;

import com.minialaddin.dto.CreatePortfolioRequest;
import com.minialaddin.dto.PortfolioResponse;
import com.minialaddin.model.Portfolio;
import com.minialaddin.service.PortfolioService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(
            @Valid @RequestBody CreatePortfolioRequest request
    ) {
        Portfolio portfolio = service.createPortfolio(
                request.name(),
                request.initialCash()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PortfolioResponse.from(portfolio));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PortfolioResponse> getById(
            @PathVariable Long id
    ) {
        Portfolio portfolio = service.getPortfolio(id);

        return ResponseEntity.ok(
                PortfolioResponse.from(portfolio)
        );
    }
}
