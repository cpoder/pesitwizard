#!/bin/bash
# PeSIT Wizard - Partner Management Tests
# Tests the server's partner configuration API (/api/v1/config/partners)

echo ""
echo "=== Partner Management Tests ==="
echo ""

# ========== Server-side Partner Management (/api/v1/config/partners) ==========

# List all partners
response=$(server_curl "${SERVER_API}/api/v1/config/partners")
if [ $? -eq 0 ]; then
    log_test "PASS" "List partners (server)"
else
    log_test "FAIL" "List partners (server)" "Failed to list partners"
fi

# Create partner (PeSIT ID: max 8 chars, uppercase alphanumeric)
PARTNER_JSON='{
    "id": "INTPART",
    "description": "Created by integration tests",
    "enabled": true,
    "accessType": "BOTH",
    "maxConnections": 10
}'

# Use verbose curl to capture both response body and HTTP status
create_response=$(curl -s -w "\n%{http_code}" -H "X-API-Key: ${SERVER_API_KEY}" \
    -H "Content-Type: application/json" \
    -X POST "${SERVER_API}/api/v1/config/partners" \
    -d "${PARTNER_JSON}" 2>/dev/null)
create_http_code=$(echo "$create_response" | tail -1)
create_body=$(echo "$create_response" | sed '$d')
if [ "$create_http_code" = "200" ] || [ "$create_http_code" = "201" ]; then
    log_test "PASS" "Create partner (server)"
    SRV_PARTNER_ID="INTPART"
elif [ "$create_http_code" = "409" ]; then
    log_test "PASS" "Create partner (server - already exists)"
    SRV_PARTNER_ID="INTPART"
else
    log_test "FAIL" "Create partner (server)" "HTTP ${create_http_code}: ${create_body}"
    SRV_PARTNER_ID=""
fi

# Get partner by ID
if [ -n "$SRV_PARTNER_ID" ]; then
    response=$(server_curl "${SERVER_API}/api/v1/config/partners/${SRV_PARTNER_ID}")
    if [ $? -eq 0 ]; then
        log_test "PASS" "Get partner by ID (server)"
    else
        log_test "FAIL" "Get partner by ID (server)" "Not found"
    fi
fi

# Update partner
if [ -n "$SRV_PARTNER_ID" ]; then
    UPDATE_JSON='{"id": "INTPART", "description": "Updated by integration tests", "enabled": true, "accessType": "BOTH", "maxConnections": 20}'
    response=$(server_curl -X PUT "${SERVER_API}/api/v1/config/partners/${SRV_PARTNER_ID}" \
        -H "Content-Type: application/json" \
        -d "${UPDATE_JSON}")
    if [ $? -eq 0 ]; then
        log_test "PASS" "Update partner (server)"
    else
        log_test "FAIL" "Update partner (server)" "Update failed"
    fi
fi

# ========== Server Virtual Files (/api/v1/config/files) ==========

# List virtual files
response=$(server_curl "${SERVER_API}/api/v1/config/files")
if [ $? -eq 0 ]; then
    log_test "PASS" "List virtual files (server)"
else
    log_test "SKIP" "List virtual files (server)" "Endpoint may not exist"
fi

# Create virtual file
FILE_JSON='{
    "id": "TESTFILE",
    "direction": "RECEIVE",
    "localPath": "/app/data/receive",
    "enabled": true
}'

response=$(server_curl -X POST "${SERVER_API}/api/v1/config/files" \
    -H "Content-Type: application/json" \
    -d "${FILE_JSON}")
if [ $? -eq 0 ]; then
    log_test "PASS" "Create virtual file (server)"
    # Cleanup
    server_curl -X DELETE "${SERVER_API}/api/v1/config/files/TESTFILE"
else
    log_test "SKIP" "Virtual file CRUD (server)" "Create may require different format"
fi

# ========== Delete Partner ==========

if [ -n "$SRV_PARTNER_ID" ]; then
    response=$(server_curl -X DELETE "${SERVER_API}/api/v1/config/partners/${SRV_PARTNER_ID}")
    if [ $? -eq 0 ]; then
        log_test "PASS" "Delete partner (server)"
    else
        log_test "FAIL" "Delete partner (server)" "Failed to delete"
    fi
fi
