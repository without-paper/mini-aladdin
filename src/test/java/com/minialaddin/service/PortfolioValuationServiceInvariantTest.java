
package com.minialaddin.service;

import com.minialaddin.dto.PortfolioValuationResponse;
import com.minialaddin.model.Portfolio;
import com.minialaddin.repository.MarketPriceRepository;
import com.minialaddin.repository.PortfolioRepository;
import com.minialaddin.repository.TradeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LO2/LO4 follow-up: tests for the accounting-invariant instrumentation.
 *
 * validateAccountingInvariant() is what turned defect D-02 into an
 * observable failure (HTTP 500). After the D-02 fix no test triggered
 * it any more (JaCoCo: PortfolioValuationService lines 120, 130 and 139
 * unexecuted), so deleting or weakening a check would have gone
 * unnoticed. Each case below breaks exactly one invariant by one cent,
 * the same magnitude as D-02, and checks that the matching check fires.
 */
@Tag("R3")
class PortfolioValuationServiceInvariantTest {

    private static final Long PORTFOLIO_ID = 1L;

    private PortfolioValuationCalculator valuationCalculator;
    private PortfolioValuationService service;

    @BeforeEach
    void setUp() {
        PortfolioRepository portfolioRepository =
                mock(PortfolioRepository.class);
        TradeRepository tradeRepository =
                mock(TradeRepository.class);
        MarketPriceRepository marketPriceRepository =
                mock(MarketPriceRepository.class);

        valuationCalculator = mock(PortfolioValuationCalculator.class);

        when(portfolioRepository.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(new Portfolio(
                        "Invariant Fund",
                        new BigDecimal("1000.00")
                )));

        when(tradeRepository
                .findByPortfolioIdOrderByExecutedAtAscIdAsc(PORTFOLIO_ID))
                .thenReturn(List.of());

        when(marketPriceRepository.findAllById(any()))
                .thenReturn(List.of());

        service = new PortfolioValuationService(
                portfolioRepository,
                tradeRepository,
                marketPriceRepository,
                new PortfolioCalculationService(),
                valuationCalculator,
                new MarketPriceFreshnessPolicy()
        );
    }

    private PortfolioValuationResponse report(
            String availableCash,
            String marketValue,
            String totalValue,
            String realisedPnl,
            String unrealisedPnl,
            String totalPnl
    ) {
        return new PortfolioValuationResponse(
                PORTFOLIO_ID,
                "Invariant Fund",
                "USD",
                new BigDecimal("1000.00"),
                new BigDecimal(availableCash),
                new BigDecimal(marketValue),
                new BigDecimal(totalValue),
                new BigDecimal(realisedPnl),
                new BigDecimal(unrealisedPnl),
                new BigDecimal(totalPnl),
                List.of()
        );
    }

    @ParameterizedTest(name = "IV-{index}: {0}")
    @CsvSource({
            // label, cash, market, total, realised, unrealised, total P&L, expected message
            "consistent report,                   900.00, 150.00, 1050.00, 20.00, 30.00, 50.00, ''",
            "total value != cash + market value,  900.00, 150.00, 1050.01, 20.01, 30.00, 50.01, Portfolio valuation does not reconcile",
            "total P&L != total value - initial,  900.00, 150.00, 1050.00, 20.01, 30.00, 50.01, Portfolio P&L does not reconcile",
            "realised + unrealised != total P&L,  900.00, 150.00, 1050.00, 20.00, 30.01, 50.00, P&L components do not reconcile"
    })
    void shouldEnforceEachAccountingInvariant(
            String label,
            String availableCash,
            String marketValue,
            String totalValue,
            String realisedPnl,
            String unrealisedPnl,
            String totalPnl,
            String expectedMessage
    ) {
        PortfolioValuationResponse calculated = report(
                availableCash,
                marketValue,
                totalValue,
                realisedPnl,
                unrealisedPnl,
                totalPnl
        );

        when(valuationCalculator.calculate(any(), any(), any(), any(), any()))
                .thenReturn(calculated);

        if (expectedMessage.isEmpty()) {
            PortfolioValuationResponse result =
                    assertDoesNotThrow(() -> service.getValuation(PORTFOLIO_ID));

            assertSame(calculated, result);

        } else {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.getValuation(PORTFOLIO_ID),
                    label
            );

            assertEquals(expectedMessage, ex.getMessage());
        }
    }
}
