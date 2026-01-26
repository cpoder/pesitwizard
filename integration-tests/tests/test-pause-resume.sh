#!/bin/bash
# PeSIT Wizard - Pause/Resume Transfer Tests

set -e

SERVER_API="${SERVER_API:-http://pesitwizard-server-api:8080}"

echo "=========================================="
echo "PeSIT Pause/Resume Integration Tests"
echo "=========================================="

PASSED=0
FAILED=0

pass() { echo "✓ $1"; ((PASSED++)); }
fail() { echo "✗ $1"; ((FAILED++)); }

# Setup: Ensure server is running
echo "=== Setup ==="
SERVER_STATUS=$(curl -sf "$SERVER_API/api/servers/1" 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "UNKNOWN")
echo "Server status: $SERVER_STATUS"

if [ "$SERVER_STATUS" = "RUNNING" ]; then
    pass "Server is running"
else
    curl -sf -X POST "$SERVER_API/api/servers/1/start" >/dev/null 2>&1
    pass "Server start requested"
fi

# === Pause Endpoint Tests ===
echo ""
echo "=== Pause Endpoint Tests ==="

# Test pause on non-existent transfer (should return 404)
PAUSE_404=$(curl -s -w "%{http_code}" -o /tmp/pause.json -X POST "$SERVER_API/api/v1/transfers/99999/pause" 2>/dev/null)
if [ "$PAUSE_404" = "404" ]; then
    pass "Pause returns 404 for non-existent transfer"
else
    fail "Pause should return 404 (got $PAUSE_404)"
fi

# === Resume Endpoint Tests ===
echo ""
echo "=== Resume Endpoint Tests ==="

# Test resume on non-existent transfer (should return 404)
RESUME_404=$(curl -s -w "%{http_code}" -o /tmp/resume.json -X POST "$SERVER_API/api/v1/transfers/99999/resume" 2>/dev/null)
if [ "$RESUME_404" = "404" ]; then
    pass "Resume returns 404 for non-existent transfer"
else
    fail "Resume should return 404 (got $RESUME_404)"
fi

# === Retry Endpoint Tests ===
echo ""
echo "=== Retry Endpoint Tests ==="

# Test retry on non-existent transfer (should return 404)
RETRY_404=$(curl -s -w "%{http_code}" -o /tmp/retry.json -X POST "$SERVER_API/api/v1/transfers/99999/retry" 2>/dev/null)
if [ "$RETRY_404" = "404" ]; then
    pass "Retry returns 404 for non-existent transfer"
else
    fail "Retry should return 404 (got $RETRY_404)"
fi

# === Cancel Endpoint Tests ===
echo ""
echo "=== Cancel Endpoint Tests ==="

# Test cancel on non-existent transfer (should return 404)
CANCEL_404=$(curl -s -w "%{http_code}" -o /tmp/cancel.json -X POST "$SERVER_API/api/v1/transfers/99999/cancel" 2>/dev/null)
if [ "$CANCEL_404" = "404" ]; then
    pass "Cancel returns 404 for non-existent transfer"
else
    fail "Cancel should return 404 (got $CANCEL_404)"
fi

# === Transfer Search Tests ===
echo ""
echo "=== Transfer Search Tests ==="

# Search by status
SEARCH_STATUS=$(curl -s -w "%{http_code}" -o /tmp/search.json "$SERVER_API/api/v1/transfers/search?status=PAUSED" 2>/dev/null)
if [ "$SEARCH_STATUS" = "200" ]; then
    pass "Search by status returns 200"
else
    fail "Search by status (got $SEARCH_STATUS)"
fi

# Search by partner
SEARCH_PARTNER=$(curl -s -w "%{http_code}" -o /tmp/search2.json "$SERVER_API/api/v1/transfers/search?partnerId=TEST" 2>/dev/null)
if [ "$SEARCH_PARTNER" = "200" ]; then
    pass "Search by partner returns 200"
else
    fail "Search by partner (got $SEARCH_PARTNER)"
fi

# === Transfer Statistics Tests ===
echo ""
echo "=== Statistics Tests ==="

STATS_CODE=$(curl -s -w "%{http_code}" -o /tmp/stats.json "$SERVER_API/api/v1/transfers/statistics" 2>/dev/null)
if [ "$STATS_CODE" = "200" ]; then
    pass "Statistics endpoint returns 200"
else
    fail "Statistics endpoint (got $STATS_CODE)"
fi

# === Cleanup Tests ===
echo ""
echo "=== Cleanup API Tests ==="

# Test cleanup endpoint (if exists)
CLEANUP_CODE=$(curl -s -w "%{http_code}" -o /tmp/cleanup.json -X POST "$SERVER_API/api/v1/transfers/cleanup?olderThanDays=30" 2>/dev/null)
if [ "$CLEANUP_CODE" = "200" ] || [ "$CLEANUP_CODE" = "404" ]; then
    pass "Cleanup endpoint responded ($CLEANUP_CODE)"
else
    fail "Cleanup endpoint (got $CLEANUP_CODE)"
fi

# === Summary ===
echo ""
echo "=========================================="
echo "Test Results: $PASSED passed, $FAILED failed"
echo "=========================================="

exit $FAILED
