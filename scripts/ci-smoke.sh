
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

request_expect() {
    local expected="$1"
    local output="$2"
    shift 2

    local actual
    actual=$(curl --silent --show-error \
        --max-time 15 \
        --output "$output" \
        --write-out "%{http_code}" "$@")

    if [[ "$actual" != "$expected" ]]; then
        echo "Expected HTTP $expected, got $actual"
        cat "$output"
        exit 1
    fi
}

echo "Waiting for Spring Boot and PostgreSQL health..."

ready=0
for attempt in $(seq 1 60); do
    if curl --silent --fail --max-time 3 \
        "$BASE_URL/actuator/health" 2>/dev/null |
        grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
    then
        ready=1
        break
    fi
    sleep 2
done

if [[ "$ready" -ne 1 ]]; then
    echo "Application did not become healthy"
    exit 1
fi

echo "Health check passed"

request_expect 201 "$TMP_DIR/portfolio.json" \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{"name":"CI Smoke Fund","initialCash":1000.00}' \
    "$BASE_URL/api/portfolios"

portfolio_id=$(python3 - "$TMP_DIR/portfolio.json" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    data = json.load(f)
assert data["initialCash"] == 1000
print(data["id"])
PY
)

TRADE_URL="$BASE_URL/api/portfolios/$portfolio_id/trades"
TRADE_BODY='{"symbol":"AAPL","side":"BUY","quantity":1,"price":10.00}'

request_expect 201 "$TMP_DIR/new.json" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: ci-smoke-0001" \
    -d "$TRADE_BODY" "$TRADE_URL"

request_expect 200 "$TMP_DIR/replay.json" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: ci-smoke-0001" \
    -d "$TRADE_BODY" "$TRADE_URL"

request_expect 200 "$TMP_DIR/trades.json" \
    "$TRADE_URL"

request_expect 200 "$TMP_DIR/quote.json" \
    -X PUT \
    -H "Content-Type: application/json" \
    -d '{"price":12.00}' \
    "$BASE_URL/api/market-prices/AAPL"

request_expect 200 "$TMP_DIR/valuation.json" \
    "$BASE_URL/api/portfolios/$portfolio_id/valuation"

python3 - "$TMP_DIR" <<'PY'
import json
import sys
from decimal import Decimal
from pathlib import Path

folder = Path(sys.argv[1])

def read(name):
    with (folder / name).open() as f:
        return json.load(f, parse_float=Decimal)

new_trade = read("new.json")
replayed = read("replay.json")
trades = read("trades.json")
valuation = read("valuation.json")

assert new_trade["id"] == replayed["id"], \
    "Replay created a different trade"

assert len(trades) == 1, \
    "Idempotency failed: duplicate trade persisted"

assert valuation["availableCash"] == Decimal("990.00")
assert valuation["marketValue"] == Decimal("12.00")
assert valuation["totalValue"] == Decimal("1002.00")
assert valuation["totalPnl"] == Decimal("2.00")

assert (
    valuation["realisedPnl"] + valuation["unrealisedPnl"]
    == valuation["totalPnl"]
), "P&L components do not reconcile"

print("PASS: portfolio creation")
print("PASS: trade execution and idempotent replay")
print("PASS: persisted trade count")
print("PASS: valuation and accounting reconciliation")
PY

echo "CI SMOKE TEST PASSED"
