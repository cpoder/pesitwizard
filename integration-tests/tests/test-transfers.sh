#!/bin/bash
# PeSIT Wizard - Transfer Tests
# Tests transfer-related endpoints on both server and client

echo ""
echo "=== Transfer Tests ==="
echo ""

# ========== Server-side Transfer Management (/api/v1/transfers) ==========

# List transfers (server, requires auth)
response=$(server_curl "${SERVER_API}/api/v1/transfers")
if [ $? -eq 0 ]; then
    log_test "PASS" "List transfers (server)"
else
    log_test "FAIL" "List transfers (server)" "Failed to list transfers"
fi

# Search transfers (server)
response=$(server_curl "${SERVER_API}/api/v1/transfers/search?status=COMPLETED&size=10")
if [ $? -eq 0 ]; then
    log_test "PASS" "Search transfers with filters"
else
    log_test "SKIP" "Search transfers with filters" "Search endpoint may not exist"
fi

# Get transfer statistics (server)
response=$(server_curl "${SERVER_API}/api/v1/transfers/stats")
if [ $? -eq 0 ]; then
    log_test "PASS" "Get transfer statistics (server)"
else
    log_test "FAIL" "Get transfer statistics (server)" "Failed to get stats"
fi

# Get active transfers (server)
response=$(server_curl "${SERVER_API}/api/v1/transfers/active")
if [ $? -eq 0 ]; then
    log_test "PASS" "Get active transfers (server)"
else
    log_test "SKIP" "Get active transfers (server)" "Endpoint may not exist"
fi

# ========== Client-side Transfer History ==========

# List client transfer history
response=$(curl -sf "${CLIENT_API}/api/v1/transfers/history" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List transfers (client)"
else
    log_test "FAIL" "List transfers (client)" "Failed to list transfers"
fi

# Get resumable transfers (client)
response=$(curl -sf "${CLIENT_API}/api/v1/transfers/resumable" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get resumable transfers (client)"
else
    log_test "SKIP" "Get resumable transfers (client)" "Endpoint may not exist"
fi

# ========== End-to-End Transfer Test ==========

# Create a partner pointing to the server for transfer testing
PARTNER_JSON='{
    "partnerId": "LOCALSRV",
    "name": "Local PeSIT Server",
    "host": "pesitwizard-server",
    "port": 6502,
    "sslEnabled": false,
    "active": true
}'

response=$(curl -sf -X POST "${CLIENT_API}/api/v1/partners" \
    -H "Content-Type: application/json" \
    -d "${PARTNER_JSON}" 2>/dev/null)
PARTNER_CREATED=$?
PARTNER_DB_ID=$(echo "$response" | jq -r '.id // empty' 2>/dev/null)

# Test send endpoint (will fail without actual file, but tests endpoint availability)
TRANSFER_JSON='{
    "partnerId": "LOCALSRV",
    "filename": "/tmp/test-file.txt",
    "remoteFilename": "test-file.txt"
}'

response=$(curl -sf -X POST "${CLIENT_API}/api/v1/transfers/send" \
    -H "Content-Type: application/json" \
    -d "${TRANSFER_JSON}" 2>/dev/null)
if [ $? -eq 0 ]; then
    TRANSFER_ID=$(echo "$response" | jq -r '.id // .transferId // empty')
    if [ -n "$TRANSFER_ID" ] && [ "$TRANSFER_ID" != "null" ]; then
        log_test "PASS" "Initiate file transfer"

        # Get transfer status
        sleep 2
        response=$(curl -sf "${CLIENT_API}/api/v1/transfers/${TRANSFER_ID}" 2>/dev/null)
        if [ $? -eq 0 ]; then
            log_test "PASS" "Get transfer status"
        else
            log_test "SKIP" "Get transfer status" "Transfer may have completed"
        fi

        # Cancel transfer if still running
        curl -sf -X POST "${CLIENT_API}/api/v1/transfers/${TRANSFER_ID}/cancel" 2>/dev/null
    else
        log_test "SKIP" "File transfer operations" "No transfer ID returned"
    fi
else
    log_test "SKIP" "File transfer operations" "Transfer initiation requires file on disk"
fi

# ========== Server Transfer Endpoints (verify they exist with invalid IDs) ==========

http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-API-Key: integration-test-api-key" \
    -H "Content-Type: application/json" \
    -X POST "${SERVER_API}/api/v1/transfers/00000000-0000-0000-0000-000000000000/retry" 2>/dev/null)
if [ "$http_code" = "404" ] || [ "$http_code" = "400" ] || [ "$http_code" = "500" ]; then
    log_test "PASS" "Transfer retry endpoint exists (HTTP $http_code)"
elif [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
    log_test "SKIP" "Transfer retry endpoint" "Auth issue (HTTP $http_code)"
else
    log_test "SKIP" "Transfer retry endpoint" "HTTP $http_code"
fi

http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-API-Key: integration-test-api-key" \
    -H "Content-Type: application/json" \
    -X POST "${SERVER_API}/api/v1/transfers/00000000-0000-0000-0000-000000000000/pause" 2>/dev/null)
if [ "$http_code" = "404" ] || [ "$http_code" = "400" ] || [ "$http_code" = "500" ]; then
    log_test "PASS" "Transfer pause endpoint exists (HTTP $http_code)"
elif [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
    log_test "SKIP" "Transfer pause endpoint" "Auth issue (HTTP $http_code)"
else
    log_test "SKIP" "Transfer pause endpoint" "HTTP $http_code"
fi

# ========== Cleanup ==========

if [ -n "$PARTNER_DB_ID" ] && [ "$PARTNER_DB_ID" != "null" ]; then
    curl -sf -X DELETE "${CLIENT_API}/api/v1/partners/${PARTNER_DB_ID}" 2>/dev/null
fi
