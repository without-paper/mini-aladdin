
ALTER TABLE trades
    ADD COLUMN request_key VARCHAR(128),
    ADD COLUMN request_fingerprint VARCHAR(256);

ALTER TABLE trades
    ADD CONSTRAINT uq_trade_idempotency
    UNIQUE (portfolio_id, request_key);

ALTER TABLE trades
    ADD CONSTRAINT chk_trade_idempotency_pair
    CHECK (
        (request_key IS NULL AND request_fingerprint IS NULL)
        OR
        (request_key IS NOT NULL AND request_fingerprint IS NOT NULL)
    );
