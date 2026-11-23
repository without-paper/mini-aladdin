#!/usr/bin/env bash
# Failure-and-recovery check for the CI release candidate (R2, R5, R6).
#
# Runs after ci-smoke.sh against the same isolated Compose project. It stops
# PostgreSQL underneath the running application and checks how the service
# fails and how it recovers, without restarting the application:
#
#   1. While the database is down, health reports DOWN (HTTP 503) and a trade
#      request fails with a generic 5xx Problem Details body that reveals no
#      internal details.
#   2. After PostgreSQL restarts, health returns to UP on its own.
#   3. The failed request left nothing behind: the portfolio has no trades,
#      retrying it with the same Idempotency-Key executes it exactly once
#      (201), and a second retry is a replay of that trade (200).
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:18080}"
COMPOSE=(docker compose -p mini-aladdin-smoke -f compose.smoke.yaml)
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# Prints the HTTP status code (000 if no response); the body goes to $1.
# While the database is down a request can take up to the connection-pool
# timeout (30 s), hence the generous limit.
http_status() {
    local output="$1"
    shift
    local code
    code=$(curl --silent --max-time 60 \
        --output "$output" --write-out "%{http_code}" "$@" || true)
    echo "${code:-000}"
}

expect_status() {
    local expected="$1"
    local output="$2"
    shift 2
    local actual
    actual=$(http_status "$output" "$@")
    if [[ "$actual" != "$expected" ]]; then
        echo "Expected HTTP $expected, got $actual"
        cat "$output" || true
        exit 1
    fi
}

# Polls the health endpoint until it returns the wanted HTTP status.
wait_for_health() {
    local wanted="$1"
    local label="$2"
    local code="000"
    for attempt in $(seq 1 30); do
        code=$(http_status "$TMP_DIR/health.json" "$BASE_URL/actuator/health")
        if [[ "$code" == "$wanted" ]]; then
            echo "Health is $label (HTTP $code)"
            return 0
        fi
        sleep 3
    done
    echo "Health did not become $label; last HTTP status $code"
    cat "$TMP_DIR/health.json" || true
    exit 1
}

wait_for_health 200 "UP"

expect_status 201 "$TMP_DIR/portfolio.json" \
    -X POST \
    -H "Content-Type: application/json" \
    -d '{"name":"CI Recovery Fund","initialCash":1000.00}' \
    "$BASE_URL/api/portfolios"

portfolio_id=$(python3 -c \
    'import json,sys; print(json.load(open(sys.argv[1]))["id"])' \
    "$TMP_DIR/portfolio.json")

TRADE_URL="$BASE_URL/api/portfolios/$portfolio_id/trades"
TRADE_BODY='{"symbol":"MSFT","side":"BUY","quantity":2,"price":10.00}'
KEY="ci-recovery-0001"

echo "Stopping PostgreSQL under the running application..."
"${COMPOSE[@]}" stop db

# 1. The outage is reported, and requests fail safely.
wait_for_health 503 "DOWN"

outage_status=$(http_status "$TMP_DIR/outage.json" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $KEY" \
    -d "$TRADE_BODY" "$TRADE_URL")

if [[ ! "$outage_status" =~ ^5[0-9][0-9]$ ]]; then
    echo "Expected a 5xx response during the outage, got $outage_status"
    cat "$TMP_DIR/outage.json" || true
    exit 1
fi

python3 - "$TMP_DIR/outage.json" <<'PY'
import json, sys
raw = open(sys.argv[1]).read()
body = json.loads(raw)
assert body["status"] >= 500, body
assert body["detail"] == "An unexpected error occurred", body
for leak in ("Exception", "org.", "java.", "SQL", "jdbc", "postgres"):
    assert leak not in raw, f"Response leaks internal detail: {leak!r}"
print("PASS: outage returns a generic 5xx Problem Details response")
PY

echo "Restarting PostgreSQL..."
"${COMPOSE[@]}" start db

# 2. The application recovers by itself.
wait_for_health 200 "UP"

# 3. Nothing was persisted during the outage, and the retry runs once.
expect_status 200 "$TMP_DIR/before-retry.json" "$TRADE_URL"

expect_status 201 "$TMP_DIR/retry.json" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $KEY" \
    -d "$TRADE_BODY" "$TRADE_URL"

expect_status 200 "$TMP_DIR/replay.json" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $KEY" \
    -d "$TRADE_BODY" "$TRADE_URL"

expect_status 200 "$TMP_DIR/after.json" "$TRADE_URL"

python3 - "$TMP_DIR" <<'PY'
import json, sys
from pathlib import Path

folder = Path(sys.argv[1])

def read(name):
    return json.loads((folder / name).read_text())

assert read("before-retry.json") == [], \
    "A trade was persisted by the request that failed during the outage"

retry = read("retry.json")
replay = read("replay.json")
assert retry["id"] == replay["id"], "The second retry created another trade"
assert len(read("after.json")) == 1, "Retries persisted more than one trade"

print("PASS: no trade persisted by the failed request")
print("PASS: retry with the same key executed exactly once; second retry replayed")
PY

echo "CI RECOVERY TEST PASSED"
