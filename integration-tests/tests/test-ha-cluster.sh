#!/bin/bash
# PeSIT Wizard - HA Cluster Integration Tests

set -e

SERVER_API="${SERVER_API:-http://pesitwizard-server-api:8080}"
CLIENT_API="${CLIENT_API:-http://pesitwizard-client-api:8080}"
VAULT_ADDR="${VAULT_ADDR:-http://vault:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-root}"

PASSED=0
FAILED=0

pass() { echo "✓ $1"; ((PASSED++)); }
fail() { echo "✗ $1"; ((FAILED++)); }

echo "=========================================="
echo "PeSIT Wizard HA Cluster Integration Tests"
echo "=========================================="

# HA Cluster Health
echo ""
echo "=== HA Cluster Health ==="
for i in 0 1; do
    if curl -sf "http://pesitwizard-server-api-$i.pesitwizard-server-headless:8080/actuator/health" | grep -q '"status":"UP"'; then
        pass "Server node $i is UP"
    else
        fail "Server node $i is DOWN"
    fi
done

# Vault Integration
echo ""
echo "=== Vault Integration ==="
if curl -sf -H "X-Vault-Token: $VAULT_TOKEN" "$VAULT_ADDR/v1/pesitwizard/data/database" | grep -q "password"; then
    pass "Vault secrets accessible"
else
    fail "Vault secrets not accessible"
fi

# Server API
echo ""
echo "=== Server API ==="
if curl -sf "$SERVER_API/actuator/health" | grep -q '"status":"UP"'; then
    pass "Server API healthy"
else
    fail "Server API unhealthy"
fi

# Create PeSIT Server
echo ""
echo "=== PeSIT Server Management ==="
SERVER_RESP=$(curl -sf -X POST "$SERVER_API/api/servers" \
    -H "Content-Type: application/json" \
    -d '{"serverId":"TEST01","name":"Test Server","port":5200,"autoStart":false}' 2>/dev/null || echo "")
if echo "$SERVER_RESP" | grep -q '"serverId":"TEST01"'; then
    pass "Created PeSIT server"
else
    fail "Failed to create PeSIT server"
fi

# Start Server
if curl -sf -X POST "$SERVER_API/api/servers/1/start" >/dev/null 2>&1; then
    pass "Started PeSIT server"
else
    fail "Failed to start PeSIT server (may already be running)"
fi

# Create Partner
echo ""
echo "=== Partner Management ==="
PARTNER_RESP=$(curl -sf -X POST "$SERVER_API/api/partners" \
    -H "Content-Type: application/json" \
    -d '{"partnerId":"CLNT01","name":"Test Client","password":"test123","active":true}' 2>/dev/null || echo "")
if echo "$PARTNER_RESP" | grep -q '"partnerId"'; then
    pass "Created partner"
else
    fail "Failed to create partner"
fi

# SSL/TLS Status
echo ""
echo "=== SSL/TLS Status ==="
if curl -sf "$SERVER_API/actuator/health" | grep -q '"ssl":{"status":"UP"'; then
    pass "SSL component is UP"
else
    fail "SSL component not available"
fi

# Client API
echo ""
echo "=== Client API ==="
if curl -sf "$CLIENT_API/actuator/health" | grep -q '"status":"UP"'; then
    pass "Client API healthy"
else
    fail "Client API unhealthy"
fi

# Summary
echo ""
echo "=========================================="
echo "Test Results: $PASSED passed, $FAILED failed"
echo "=========================================="

exit $FAILED
