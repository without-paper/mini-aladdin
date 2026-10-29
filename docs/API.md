# API Reference

**Base URL (local Compose):** `http://localhost:8080`  
**Format:** JSON over HTTP. The API has no authentication/authorisation layer in the supplied project stages. All trades and prices are simulated.

This reference is based on the supplied controller bodies, DTO records, `TradeService`, `MarketPriceFreshnessPolicy`, and `GlobalExceptionHandler`. Status codes and DTO field names below are **verified against those sources**. Numeric/timestamp values in example responses are illustrative, not recorded user data.

## Endpoint index

| Method | Path | Success | Request | Response |
|---|---|---:|---|---|
| POST | `/api/portfolios` | 201 | `CreatePortfolioRequest` | `PortfolioResponse` |
| GET | `/api/portfolios/{id}` | 200 | — | `PortfolioResponse` |
| GET | `/api/portfolios/{portfolioId}/cash` | 200 | — | `CashResponse` |
| GET | `/api/portfolios/{portfolioId}/positions` | 200 | — | `PositionResponse[]` |
| GET | `/api/portfolios/{portfolioId}/trades` | 200 | — | `TradeResponse[]` |
| POST | `/api/portfolios/{portfolioId}/trades` | 201 new / 200 replay | `CreateTradeRequest` + `Idempotency-Key` | `TradeResponse` |
| PUT | `/api/market-prices/{symbol}` | 200 | `UpdateMarketPriceRequest` | `MarketPriceResponse` |
| GET | `/api/market-prices/{symbol}` | 200 | — | `MarketPriceResponse` |
| GET | `/api/portfolios/{portfolioId}/valuation` | 200 | — | `PortfolioValuationResponse` |

## 1. Portfolios

### POST `/api/portfolios` — Create a portfolio

```http
POST /api/portfolios HTTP/1.1
Content-Type: application/json

{"name":"Demo Portfolio","initialCash":10000.00}
```

**Validation (`CreatePortfolioRequest`):** `name` is nonblank and at most 255 characters; `initialCash` is required, nonnegative (`>= 0`), with at most 17 integer digits and 2 fractional digits.

**201 Created** body (`PortfolioResponse`):

```json
{"id":1,"name":"Demo Portfolio","initialCash":10000.00}
```

`id` is assigned by persistence. The response fields are exactly `id`, `name`, and `initialCash`.

### GET `/api/portfolios/{id}` — Read a portfolio

**200 OK:** same `PortfolioResponse` schema. **404** when the portfolio does not exist.

### GET `/api/portfolios/{portfolioId}/cash` — Available cash

**200 OK** (`CashResponse`):

```json
{
  "portfolioId":1,
  "currency":"USD",
  "initialCash":10000.00,
  "availableCash":9800.00
}
```

The record has exactly those four fields. Values shown are examples.

### GET `/api/portfolios/{portfolioId}/positions` — Holdings

**200 OK**: a JSON array of `PositionResponse` objects:

```json
[{"symbol":"AAPL","quantity":2}]
```

Each position has exactly `symbol` and `quantity`. An empty portfolio may return an empty array.

## 2. Simulated trades

### POST `/api/portfolios/{portfolioId}/trades`

**Required header:** `Idempotency-Key`. The `TradeService` accepts `[A-Za-z0-9_-]{8,128}` (8–128 ASCII letters, digits, `_`, `-`). A missing header is handled as a Spring MVC request error; an invalid supplied key raises `TradeRejectedException` (400).

```http
POST /api/portfolios/1/trades HTTP/1.1
Content-Type: application/json
Idempotency-Key: demo-buy-request-0001

{"symbol":"AAPL","side":"BUY","quantity":2,"price":100.00}
```

**Validation (`CreateTradeRequest`):**

| Field | Constraint |
|---|---|
| `symbol` | Required, nonblank, max 20 characters, regex `[A-Za-z][A-Za-z0-9.]{0,19}`; service trims and uppercases for storage/fingerprinting |
| `side` | Required `TradeSide`: `BUY` or `SELL` |
| `quantity` | Required, minimum `1`, integer only (`@Digits(integer=19, fraction=0)`); service requires positive integer |
| `price` | Required, minimum `0.01`, at most 17 integer / 2 decimal digits |

**201 Created** for a newly executed trade; **200 OK** for a matching idempotency replay. Both return **only** a `TradeResponse` body, not a `TradeExecutionResult` wrapper:

```json
{
  "id":15,
  "portfolioId":1,
  "symbol":"AAPL",
  "side":"BUY",
  "quantity":2,
  "price":100.00,
  "executedAt":"2026-10-29T19:00:00Z"
}
```

The response record has exactly the seven fields shown. `executedAt` is an `Instant` represented in JSON as a timestamp (normally ISO 8601 with Spring's Jackson setup). The example timestamp is illustrative.

**Idempotency behaviour:** the key is checked **within the selected portfolio** after acquiring its row lock. The service calculates a SHA-256 fingerprint from the normalised symbol, side, and canonical decimal quantity and price. Same key + same fingerprint returns the existing trade (200); same key + different fingerprint gives **409 Conflict**. Replays do not create additional trades.

**Business constraints:** BUY requires enough available cash; SELL requires enough shares of the symbol. Both violations return **400 Bad Request** with `detail` such as `Insufficient cash` or `Insufficient position`.

### GET `/api/portfolios/{portfolioId}/trades`

**200 OK:** an array of `TradeResponse` records sorted by `executedAt` ascending, then `id` ascending, as requested by the repository method in the supplied `TradeService`. This API currently retrieves the full list, without pagination.

## 3. Simulated market prices

### PUT `/api/market-prices/{symbol}`

```http
PUT /api/market-prices/AAPL HTTP/1.1
Content-Type: application/json

{"price":250.00}
```

**Validation (`UpdateMarketPriceRequest`):** the body has exactly one field `price`; it is required, at least `0.01`, and limited to 17 integer digits / 2 fractional digits.

**200 OK** (`MarketPriceResponse`):

```json
{
  "symbol":"AAPL",
  "price":250.00,
  "currency":"USD",
  "updatedAt":"2026-10-29T19:00:00Z"
}
```

`currency` is hard-coded as `USD` in `MarketPriceResponse.from`. The exact stored timestamp is generated by the underlying implementation and must not be supplied as a client field.

### GET `/api/market-prices/{symbol}`

**200 OK:** the same `MarketPriceResponse` schema. The `MarketPriceNotFoundException` handler produces **404** for an absent quote.

## 4. Portfolio valuation

### GET `/api/portfolios/{portfolioId}/valuation`

**200 OK** (`PortfolioValuationResponse`), example values consistent with a portfolio containing two AAPL shares purchased at $100 and marked at $250:

```json
{
  "portfolioId":1,
  "portfolioName":"Demo Portfolio",
  "currency":"USD",
  "initialCash":10000.00,
  "availableCash":9800.00,
  "marketValue":500.00,
  "totalValue":10300.00,
  "realisedPnl":0.00,
  "unrealisedPnl":300.00,
  "totalPnl":300.00,
  "positions":[
    {
      "symbol":"AAPL",
      "quantity":2,
      "averageCost":100.00,
      "costBasis":200.00,
      "marketPrice":250.00,
      "priceUpdatedAt":"2026-10-29T19:00:00Z",
      "marketValue":500.00,
      "unrealisedPnl":300.00
    }
  ]
}
```

This example documents the **exact property names** from `PortfolioValuationResponse` and `PositionValuationResponse`; calculations and rounding are illustrative, and the real response depends on stored trades and prices.

A valuation requires prices for held symbols. The `MarketPriceFreshnessPolicy` rejects a timestamp that is missing, more than 24 hours old, or more than five minutes ahead of the current UTC time. `MissingMarketPriceException` and `StaleMarketPriceException` map to **409 Conflict**.

## 5. Problem Details errors

Errors handled by `GlobalExceptionHandler` use `application/problem+json`. Example:

```json
{
  "type":"about:blank",
  "title":"Not Found",
  "status":404,
  "detail":"Portfolio with ID 99999999 not found",
  "instance":"/api/portfolios/99999999",
  "correlationId":"550e8400-e29b-41d4-a716-446655440000"
}
```

| HTTP | Sources in handler |
|---:|---|
| 400 | `TradeRejectedException`, `IllegalArgumentException`, bean validation and applicable Spring MVC request errors |
| 404 | `PortfolioNotFoundException`, `MarketPriceNotFoundException` |
| 409 | `IdempotencyConflictException`, `MissingMarketPriceException`, `StaleMarketPriceException` |
| 500 | Unexpected exceptions (generic client message: `An unexpected error occurred`) |

For a DTO validation error, the body title is `Validation Failed`, detail is `One or more request fields are invalid`, and `errors` contains objects with `field` and `message`. Error array ordering is not contractual.

The `instance` is the request URI, without any promise to include query parameters. Not every failure that occurs outside Spring MVC's exception resolution path is necessarily handled by this advice.

## 6. Correlation IDs and operational APIs

A client can set `X-Correlation-ID` to a canonical UUID. The request filter will normally return the identifier in the response header, generating one when absent/invalid. **Only error bodies handled by the global exception handler are documented to include the `correlationId` JSON extension; successful response DTOs do not contain it.**

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/metrics/{metricName}
```

These operational endpoints are intended for trusted local/monitoring environments. They are not authenticated by the functionality documented here.

## 7. API evolution notes

The current contract has no `/v1` URI prefix or OpenAPI-generated schema. Verify future implementation changes against these DTO records and controller methods, and update examples when the API changes. Quotes are simulated and the API does not place real market orders.
