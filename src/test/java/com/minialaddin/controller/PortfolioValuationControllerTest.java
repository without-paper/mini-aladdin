
package com.minialaddin.controller;

import com.minialaddin.config.TestDatabaseConfiguration;
import com.minialaddin.dto.CreateTradeRequest;
import com.minialaddin.model.Portfolio;
import com.minialaddin.model.TradeSide;
import com.minialaddin.service.MarketPriceService;
import com.minialaddin.service.PortfolioService;
import com.minialaddin.service.TradeService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import static com.minialaddin.support.MoneyMatchers.assertMoneyField;
import static com.minialaddin.support.MoneyMatchers.money;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * R3/R4/R6: the valuation endpoint, end to end.
 *
 * Every test uses its own random symbols. Market prices are global rows,
 * so tests that shared AAPL/MSFT could overwrite each other's quotes and
 * depend on execution order.
 */
@Tag("R3")
@Tag("R4")
@Tag("R6")
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class PortfolioValuationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private MarketPriceService marketPriceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String randomSymbol(String prefix) {
        return prefix + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase(Locale.ROOT);
    }

    private void trade(
            Long portfolioId,
            String symbol,
            TradeSide side,
            String quantity,
            String price
    ) {
        tradeService.executeTrade(
                portfolioId,
                new CreateTradeRequest(
                        symbol,
                        side,
                        new BigDecimal(quantity),
                        new BigDecimal(price)
                ),
                UUID.randomUUID().toString()
        );
    }

    /**
     * Moves a quote's timestamp relative to the database clock,
     * for example "-25 hours" or "10 minutes".
     */
    private void shiftQuoteTime(String symbol, String interval) {
        int updated = jdbcTemplate.update(
                """
                UPDATE market_prices
                SET updated_at = clock_timestamp() + CAST(? AS interval)
                WHERE symbol = ?
                """,
                interval,
                symbol
        );

        assertEquals(1, updated);
    }

    /** Portfolio holding 5 shares of {@code symbol}, quoted at 120.00. */
    private Long portfolioWithQuote(String name, String symbol) {
        Portfolio portfolio = portfolioService.createPortfolio(
                name,
                new BigDecimal("10000.00")
        );

        trade(portfolio.getId(), symbol, TradeSide.BUY, "5", "100.00");

        marketPriceService.updatePrice(symbol, new BigDecimal("120.00"));

        return portfolio.getId();
    }

    @Test
    @DisplayName("R3-V1: complete valuation is correct and serialised with two decimals")
    void shouldCalculateCompletePortfolioValuation() throws Exception {

        // Random symbols keep the original alphabetical order (A before B).
        String first = randomSymbol("VA");
        String second = randomSymbol("VB");

        Portfolio portfolio = portfolioService.createPortfolio(
                "M4 Valuation Test",
                new BigDecimal("10000.00")
        );

        Long id = portfolio.getId();

        trade(id, first, TradeSide.BUY, "10", "200.00");
        trade(id, second, TradeSide.BUY, "5", "300.00");
        trade(id, first, TradeSide.SELL, "3", "220.00");

        marketPriceService.updatePrice(
                first, new BigDecimal("230.00")
        );

        marketPriceService.updatePrice(
                second, new BigDecimal("320.00")
        );

        String json = mockMvc.perform(get(
                        "/api/portfolios/{id}/valuation", id
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.availableCash", money("7160.00")))
                .andExpect(jsonPath("$.marketValue", money("3210.00")))
                .andExpect(jsonPath("$.totalValue", money("10370.00")))
                .andExpect(jsonPath("$.realisedPnl", money("60.00")))
                .andExpect(jsonPath("$.unrealisedPnl", money("310.00")))
                .andExpect(jsonPath("$.totalPnl", money("370.00")))
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.positions[0].symbol").value(first))
                .andExpect(jsonPath("$.positions[1].symbol").value(second))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // R3 display contract: monetary values carry exactly two decimals.
        assertMoneyField(json, "availableCash", "7160.00");
        assertMoneyField(json, "marketValue", "3210.00");
        assertMoneyField(json, "totalValue", "10370.00");
        assertMoneyField(json, "realisedPnl", "60.00");
        assertMoneyField(json, "unrealisedPnl", "310.00");
        assertMoneyField(json, "totalPnl", "370.00");
    }

    @Test
    @DisplayName("R6-V2: valuation of an unknown portfolio returns 404")
    void shouldReturn404ForUnknownPortfolio() throws Exception {
        mockMvc.perform(get(
                        "/api/portfolios/{id}/valuation", -1L
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("R3-V3: an empty portfolio is valued without market prices")
    void shouldValueEmptyPortfolioWithoutMarketPrices()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "M4 Empty Portfolio Test",
                new BigDecimal("5000.00")
        );

        String json = mockMvc.perform(get(
                        "/api/portfolios/{id}/valuation",
                        portfolio.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash", money("5000.00")))
                .andExpect(jsonPath("$.marketValue", money("0.00")))
                .andExpect(jsonPath("$.totalValue", money("5000.00")))
                .andExpect(jsonPath("$.totalPnl", money("0.00")))
                .andExpect(jsonPath("$.positions.length()").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertMoneyField(json, "marketValue", "0.00");
        assertMoneyField(json, "totalPnl", "0.00");
    }

    @Test
    @DisplayName("R4-V4: valuation is rejected when an open position has no market price")
    void shouldRejectValuationWhenMarketPriceMissing()
            throws Exception {

        Portfolio portfolio = portfolioService.createPortfolio(
                "M4 Missing Quote Test",
                new BigDecimal("10000.00")
        );

        Long id = portfolio.getId();

        String symbol = randomSymbol("T");

        trade(
                id,
                symbol,
                TradeSide.BUY,
                "5",
                "100.00"
        );

        mockMvc.perform(get(
                        "/api/portfolios/{id}/valuation", id
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Missing market price for " + symbol
                ));
    }

    @Test
    @DisplayName("R4-V5: a quote older than 24 hours is rejected through the API")
    void shouldRejectStaleQuoteThroughApi() throws Exception {

        String symbol = randomSymbol("S");
        Long id = portfolioWithQuote("Stale Quote Fund", symbol);

        shiftQuoteTime(symbol, "-25 hours");

        mockMvc.perform(get("/api/portfolios/{id}/valuation", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(
                        "Market price is stale or has an invalid timestamp: "
                                + symbol
                ));
    }

    @Test
    @DisplayName("R4-V6: a quote 23 hours old is still accepted through the API")
    void shouldAcceptAgedQuoteInsideWindow() throws Exception {

        String symbol = randomSymbol("S");
        Long id = portfolioWithQuote("Aged Quote Fund", symbol);

        shiftQuoteTime(symbol, "-23 hours");

        mockMvc.perform(get("/api/portfolios/{id}/valuation", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].symbol").value(symbol))
                .andExpect(jsonPath("$.marketValue", money("600.00")));
    }

    @Test
    @DisplayName("R4-V7: a quote more than five minutes in the future is rejected through the API")
    void shouldRejectFutureQuoteThroughApi() throws Exception {

        String symbol = randomSymbol("F");
        Long id = portfolioWithQuote("Future Quote Fund", symbol);

        shiftQuoteTime(symbol, "10 minutes");

        mockMvc.perform(get("/api/portfolios/{id}/valuation", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Market price is stale or has an invalid timestamp: "
                                + symbol
                ));
    }

    @Test
    @DisplayName("R4-V8: one stale price among fresh ones blocks the whole valuation until it is refreshed")
    void shouldRejectValuationWhenOnlyOnePriceIsStale() throws Exception {

        // Two open positions; the stale one sorts second, so a valuation
        // that only checked the first position would wrongly succeed.
        String fresh = randomSymbol("PA");
        String stale = randomSymbol("PB");

        Portfolio portfolio = portfolioService.createPortfolio(
                "Partly Stale Fund",
                new BigDecimal("10000.00")
        );

        Long id = portfolio.getId();

        trade(id, fresh, TradeSide.BUY, "5", "100.00");
        trade(id, stale, TradeSide.BUY, "2", "100.00");

        marketPriceService.updatePrice(fresh, new BigDecimal("120.00"));
        marketPriceService.updatePrice(stale, new BigDecimal("110.00"));
        shiftQuoteTime(stale, "-25 hours");

        // A partly valid price set must not yield a partly valid result.
        mockMvc.perform(get("/api/portfolios/{id}/valuation", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Market price is stale or has an invalid timestamp: "
                                + stale
                ));

        // Refreshing the one bad price is enough to recover.
        marketPriceService.updatePrice(stale, new BigDecimal("110.00"));

        // Independent oracle: cash 10000 - 500 - 200 = 9300.00;
        // market value 5 x 120 + 2 x 110 = 820.00.
        mockMvc.perform(get("/api/portfolios/{id}/valuation", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.availableCash", money("9300.00")))
                .andExpect(jsonPath("$.marketValue", money("820.00")))
                .andExpect(jsonPath("$.totalValue", money("10120.00")));
    }

    @Test
    @DisplayName("R4-V9: only open positions need a price; the missing price of an open position is named")
    void shouldRequirePricesForOpenPositionsOnly() throws Exception {

        // The closed symbol sorts first and never gets a price. If closed
        // positions wrongly needed one, the error would name it instead.
        String closed = randomSymbol("QA");
        String open = randomSymbol("QB");

        Portfolio portfolio = portfolioService.createPortfolio(
                "Open Positions Only Fund",
                new BigDecimal("10000.00")
        );

        Long id = portfolio.getId();

        trade(id, closed, TradeSide.BUY, "3", "100.00");
        trade(id, closed, TradeSide.SELL, "3", "120.00");
        trade(id, open, TradeSide.BUY, "4", "50.00");

        mockMvc.perform(get("/api/portfolios/{id}/valuation", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Missing market price for " + open
                ));

        marketPriceService.updatePrice(open, new BigDecimal("55.00"));

        // Independent oracle: cash 10000 - 300 + 360 - 200 = 9860.00;
        // realised (120 - 100) x 3 = 60.00; unrealised (55 - 50) x 4 = 20.00.
        mockMvc.perform(get("/api/portfolios/{id}/valuation", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(1))
                .andExpect(jsonPath("$.positions[0].symbol").value(open))
                .andExpect(jsonPath("$.availableCash", money("9860.00")))
                .andExpect(jsonPath("$.marketValue", money("220.00")))
                .andExpect(jsonPath("$.realisedPnl", money("60.00")))
                .andExpect(jsonPath("$.unrealisedPnl", money("20.00")))
                .andExpect(jsonPath("$.totalPnl", money("80.00")))
                .andExpect(jsonPath("$.totalValue", money("10080.00")));
    }
}
