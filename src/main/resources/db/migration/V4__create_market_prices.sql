
CREATE TABLE market_prices (

    symbol VARCHAR(20) PRIMARY KEY,

    price NUMERIC(19, 2) NOT NULL,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_market_price_positive
        CHECK (price > 0),

    CONSTRAINT chk_market_price_symbol
        CHECK (symbol ~ '^[A-Z][A-Z0-9.]{0,19}$')
);
