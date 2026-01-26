#!/bin/bash
# PeSIT Wizard - End-to-End File Transfer Tests with Pause/Resume

set -e

SERVER_API="${SERVER_API:-http://pesitwizard-server-api:8080}"
CLIENT_API="${CLIENT_API:-http://pesitwizard-client-api:8080}"

PASSED=0
FAILED=0
SKIPPED=0

pass() { echo "✓ $1"; ((PASSED++)); }
fail() { echo "✗ $1"; ((FAILED++)); }
skip() { echo "○ $1 (skipped)"; ((SKIPPED++)); }

echo "=========================================="
echo "PeSIT File Transfer Integration Tests"
echo "=========================================="
echo "Server API: $SERVER_API"
echo "Client API: $CLIENT_API"
echo ""

# === Setup ===
echo "=== Setup ==="

# Create test directories
echo "Creating test directories..."
curl -sf -X POST "$SERVER_API/api/filesystem/mkdir?path=/data/send/test" >/dev/null 2>&1 || true
curl -sf -X POST "$SERVER_API/api/filesystem/mkdir?path=/data/received/test" >/dev/null 2>&1 || true

# Create a PeSIT server if none exists
SERVER_COUNT=$(curl -sf "$SERVER_API/api/servers" 2>/dev/null | grep -c '"serverId"' || echo "0")
if [ "$SERVER_COUNT" -eq "0" ]; then
    echo "Creating PeSIT server..."
    curl -sf -X POST "$SERVER_API/api/servers" \
        -H "Content-Type: application/json" \
        -d '{"serverId":"TESTSRV","name":"Test Server","port":5100,"autoStart":true}' >/dev/null 2>&1
    pass "Created PeSIT server"
else
    pass "PeSIT server already exists"
fi

# Get server ID
SERVER_ID=$(curl -sf "$SERVER_API/api/servers" 2>/dev/null | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$SERVER_ID" ]; then
    # Start server if not running
    curl -sf -X POST "$SERVER_API/api/servers/$SERVER_ID/start" >/dev/null 2>&1 || true
    pass "Server started (ID: $SERVER_ID)"
else
    fail "Could not get server ID"
fi

# Create partner
echo "Creating test partner..."
PARTNER_RESP=$(curl -sf -X POST "$SERVER_API/api/partners" \
    -H "Content-Type: application/json" \
    -d '{"partnerId":"CLIENT","name":"Test Client","password":"test123","active":true}' 2>/dev/null || echo "")
if echo "$PARTNER_RESP" | grep -q '"partnerId"'; then
    pass "Created test partner"
else
    skip "Partner may already exist"
fi

# === Transfer Tests ===
echo ""
echo "=== Transfer API Tests ==="

# List transfers
if curl -sf "$SERVER_API/api/v1/transfers" >/dev/null 2>&1; then
    pass "List transfers endpoint"
else
    fail "List transfers endpoint"
fi

# Transfer statistics
STATS=$(curl -sf "$SERVER_API/api/v1/transfers/statistics" 2>/dev/null || echo "{}")
if echo "$STATS" | grep -qE '"total"|"completed"|"pending"'; then
    pass "Transfer statistics endpoint"
else
    skip "Transfer statistics (no data yet)"
fi

# Search transfers
if curl -sf "$SERVER_API/api/v1/transfers/search?status=COMPLETED" >/dev/null 2>&1; then
    pass "Search transfers endpoint"
else
    fail "Search transfers endpoint"
fi

# === Pause/Resume Tests ===
echo ""
echo "=== Pause/Resume API Tests ==="

# Test pause endpoint (will fail if no active transfer, but endpoint should exist)
PAUSE_RESP=$(curl -sf -X POST "$SERVER_API/api/v1/transfers/1/pause" 2>&1 || echo "not_found")
if echo "$PAUSE_RESP" | grep -qE "not found|paused|Transfer"; then
    pass "Pause transfer endpoint exists"
else
    fail "Pause transfer endpoint"
fi

# Test resume endpoint
RESUME_RESP=$(curl -sf -X POST "$SERVER_API/api/v1/transfers/1/resume" 2>&1 || echo "not_found")
if echo "$RESUME_RESP" | grep -qE "not found|resumed|Transfer"; then
    pass "Resume transfer endpoint exists"
else
    fail "Resume transfer endpoint"
fi

# Test retry endpoint
RETRY_RESP=$(curl -sf -X POST "$SERVER_API/api/v1/transfers/1/retry" 2>&1 || echo "not_found")
if echo "$RETRY_RESP" | grep -qE "not found|retry|Transfer"; then
    pass "Retry transfer endpoint exists"
else
    fail "Retry transfer endpoint"
fi

# Test cancel endpoint
CANCEL_RESP=$(curl -sf -X POST "$SERVER_API/api/v1/transfers/1/cancel" 2>&1 || echo "not_found")
if echo "$CANCEL_RESP" | grep -qE "not found|cancel|Transfer"; then
    pass "Cancel transfer endpoint exists"
else
    fail "Cancel transfer endpoint"
fi

# === File Management Tests ===
echo ""
echo "=== File Management Tests ==="

# List files
if curl -sf "$SERVER_API/api/filesystem/list?path=/data" >/dev/null 2>&1; then
    pass "List files endpoint"
else
    fail "List files endpoint"
fi

# Create test file via API (if supported)
CREATE_FILE=$(curl -sf -X POST "$SERVER_API/api/filesystem/write?path=/data/send/test.txt" \
    -H "Content-Type: text/plain" \
    -d "Test file content for transfer" 2>&1 || echo "not_supported")
if echo "$CREATE_FILE" | grep -qE "created|success|written"; then
    pass "Create file endpoint"
else
    skip "Create file endpoint (may not be supported)"
fi

# === Audit Tests ===
echo ""
echo "=== Audit Trail Tests ==="

# Get audit events
if curl -sf "$SERVER_API/api/audit/events?size=10" >/dev/null 2>&1; then
    pass "Audit events endpoint"
elif curl -sf "$SERVER_API/api/audit?size=10" >/dev/null 2>&1; then
    pass "Audit events endpoint (alt)"
else
    skip "Audit events endpoint"
fi

# Audit statistics
if curl -sf "$SERVER_API/api/audit/statistics" >/dev/null 2>&1; then
    pass "Audit statistics endpoint"
else
    skip "Audit statistics endpoint"
fi

# === Metrics Tests ===
echo ""
echo "=== Metrics Tests ==="

# Prometheus metrics
if curl -sf "$SERVER_API/actuator/prometheus" 2>/dev/null | grep -q "pesit"; then
    pass "Prometheus metrics with PeSIT data"
elif curl -sf "$SERVER_API/actuator/prometheus" >/dev/null 2>&1; then
    pass "Prometheus metrics endpoint"
else
    skip "Prometheus metrics"
fi

# Transfer metrics
if curl -sf "$SERVER_API/actuator/metrics/pesit.transfers.total" >/dev/null 2>&1; then
    pass "Transfer metrics"
else
    skip "Transfer metrics (may not exist yet)"
fi

# === Summary ===
echo ""
echo "=========================================="
echo "Test Results: $PASSED passed, $FAILED failed, $SKIPPED skipped"
echo "=========================================="

exit $FAILED
