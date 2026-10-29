
package com.minialaddin.service;

import com.minialaddin.dto.PortfolioValuationResponse;
import com.minialaddin.dto.PositionValuationResponse;
import com.minialaddin.exception.StaleMarketPriceException;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class MarketPriceFreshnessPolicy {

    private static final Duration MAX_AGE =
            Duration.ofHours(24);

    private static final Duration MAX_FUTURE_SKEW =
            Duration.ofMinutes(5);

    private final Clock clock;

    public MarketPriceFreshnessPolicy() {
        this.clock = Clock.systemUTC();
    }

    public void validate(PortfolioValuationResponse report) {
        validateAt(report, clock.instant());
    }

    public void validateAt(
            PortfolioValuationResponse report,
            Instant now
    ) {
        for (PositionValuationResponse position
                : report.positions()) {

            Instant updatedAt = position.priceUpdatedAt();

            if (updatedAt == null) {
                throw new StaleMarketPriceException(
                        position.symbol()
                );
            }

            Instant oldestAllowed = now.minus(MAX_AGE);
            Instant newestAllowed = now.plus(MAX_FUTURE_SKEW);

            if (updatedAt.isBefore(oldestAllowed)
                    || updatedAt.isAfter(newestAllowed)) {

                throw new StaleMarketPriceException(
                        position.symbol()
                );
            }
        }
    }
}
