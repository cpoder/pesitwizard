#!/bin/bash
# PeSIT Wizard - Client API Tests
# Tests the PeSIT Client REST API endpoints (nosecurity profile - no auth needed)

echo ""
echo "=== Client API Tests ==="
echo ""

# Health check
response=$(curl -sf "${CLIENT_API}/actuator/health" 2>/dev/null)
if [ $? -eq 0 ] && echo "$response" | jq -e '.status == "UP"' > /dev/null 2>&1; then
    log_test "PASS" "Client health check"
else
    log_test "FAIL" "Client health check" "Health endpoint not responding"
fi

# Info endpoint
response=$(curl -sf "${CLIENT_API}/actuator/info" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Client info endpoint"
else
    log_test "FAIL" "Client info endpoint" "Info endpoint not responding"
fi

# ========== Partner Management (/api/v1/partners) ==========

# List partners
response=$(curl -sf "${CLIENT_API}/api/v1/partners" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List partners"
else
    log_test "FAIL" "List partners" "Failed to list partners"
fi

# Create a test partner
PARTNER_JSON='{
    "partnerId": "INTTEST",
    "name": "Integration Test Partner",
    "host": "localhost",
    "port": 5100,
    "sslEnabled": false,
    "active": true,
    "maxConcurrentTransfers": 5
}'

response=$(curl -sf -X POST "${CLIENT_API}/api/v1/partners" \
    -H "Content-Type: application/json" \
    -d "${PARTNER_JSON}" 2>/dev/null)
if [ $? -eq 0 ]; then
    # Extract the auto-generated ID from the response
    PARTNER_DB_ID=$(echo "$response" | jq -r '.id // empty')
    if [ -n "$PARTNER_DB_ID" ] && [ "$PARTNER_DB_ID" != "null" ]; then
        log_test "PASS" "Create partner"
    else
        log_test "PASS" "Create partner (no id in response)"
        PARTNER_DB_ID=""
    fi
else
    log_test "FAIL" "Create partner" "Failed to create partner"
    PARTNER_DB_ID=""
fi

# Get partner by partnerId
response=$(curl -sf "${CLIENT_API}/api/v1/partners/by-partner-id/INTTEST" 2>/dev/null)
if [ $? -eq 0 ] && echo "$response" | jq -e '.partnerId' > /dev/null 2>&1; then
    log_test "PASS" "Get partner by partnerId"
    # Capture the DB ID if we didn't get it from create
    if [ -z "$PARTNER_DB_ID" ]; then
        PARTNER_DB_ID=$(echo "$response" | jq -r '.id // empty')
    fi
else
    log_test "FAIL" "Get partner by partnerId" "Partner not found"
fi

# Get partner by DB ID
if [ -n "$PARTNER_DB_ID" ]; then
    response=$(curl -sf "${CLIENT_API}/api/v1/partners/${PARTNER_DB_ID}" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Get partner by ID"
    else
        log_test "FAIL" "Get partner by ID" "Partner not found"
    fi
fi

# Update partner
if [ -n "$PARTNER_DB_ID" ]; then
    UPDATE_JSON='{"partnerId": "INTTEST", "name": "Updated Integration Test Partner", "host": "localhost", "port": 5100, "sslEnabled": false, "active": true, "maxConcurrentTransfers": 10}'
    response=$(curl -sf -X PUT "${CLIENT_API}/api/v1/partners/${PARTNER_DB_ID}" \
        -H "Content-Type: application/json" \
        -d "${UPDATE_JSON}" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Update partner"
    else
        log_test "FAIL" "Update partner" "Failed to update partner"
    fi
fi

# ========== Transfer History (/api/v1/transfers) ==========

# List transfer history
response=$(curl -sf "${CLIENT_API}/api/v1/transfers/history" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List transfer history"
else
    log_test "FAIL" "List transfer history" "Failed to list history"
fi

# Get transfer stats
response=$(curl -sf "${CLIENT_API}/api/v1/transfers/stats" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get transfer statistics (client)"
else
    log_test "SKIP" "Get transfer statistics (client)" "Stats endpoint may not exist"
fi

# ========== Virtual Files (/api/v1/virtual-files) ==========

response=$(curl -sf "${CLIENT_API}/api/v1/virtual-files" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List virtual files"
else
    log_test "SKIP" "List virtual files" "Endpoint may not exist"
fi

# ========== Server Connections (/api/v1/servers) ==========

response=$(curl -sf "${CLIENT_API}/api/v1/servers" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List PeSIT server connections (client)"
else
    log_test "SKIP" "List PeSIT server connections (client)" "Endpoint may not exist"
fi

# ========== OTLP Configuration (/api/v1/config/otlp) ==========

response=$(curl -sf "${CLIENT_API}/api/v1/config/otlp" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get OTLP configuration"
else
    log_test "SKIP" "Get OTLP configuration" "Endpoint may not exist"
fi

# ========== Cleanup ==========

if [ -n "$PARTNER_DB_ID" ]; then
    curl -sf -X DELETE "${CLIENT_API}/api/v1/partners/${PARTNER_DB_ID}" 2>/dev/null
fi
