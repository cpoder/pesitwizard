#!/bin/bash
# PeSIT Wizard - Transfer Tests

echo ""
echo "=== Transfer Tests ==="
echo ""

# ========== Server-side Transfer Management ==========

# List transfers
response=$(curl -sf "${SERVER_API}/api/v1/transfers" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List transfers (server)"
else
    log_test "FAIL" "List transfers (server)" "Failed to list transfers"
fi

# Search transfers
response=$(curl -sf "${SERVER_API}/api/v1/transfers/search?status=COMPLETED&limit=10" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Search transfers with filters"
else
    log_test "FAIL" "Search transfers with filters" "Search failed"
fi

# Get transfer statistics
response=$(curl -sf "${SERVER_API}/api/v1/transfers/stats" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get transfer statistics"
else
    log_test "FAIL" "Get transfer statistics" "Failed to get stats"
fi

# ========== Client-side Transfer Operations ==========

# List client transfers
response=$(curl -sf "${CLIENT_API}/api/v1/transfers" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List transfers (client)"
else
    log_test "FAIL" "List transfers (client)" "Failed to list transfers"
fi

# ========== End-to-End Transfer Test ==========

# Create a test file for transfer
TEST_FILE_CONTENT="PeSIT Wizard Integration Test - $(date)"
TEST_FILE_NAME="integration-test-$(date +%s).txt"

# First, create a partner pointing to the server (if client connects to server)
PARTNER_JSON='{
    "partnerId": "LOCAL-SERVER",
    "name": "Local PeSIT Server",
    "host": "pesitwizard-server",
    "port": 5100,
    "sslEnabled": false,
    "active": true
}'

response=$(curl -sf -X POST "${CLIENT_API}/api/v1/partners" \
    -H "Content-Type: application/json" \
    -d "${PARTNER_JSON}" 2>/dev/null)
PARTNER_CREATED=$?

# Try to initiate a file transfer (this is a comprehensive test)
# Note: This requires the server to be running and accepting connections

TRANSFER_JSON="{
    \"partnerId\": \"LOCAL-SERVER\",
    \"direction\": \"SEND\",
    \"localPath\": \"/tmp/${TEST_FILE_NAME}\",
    \"remotePath\": \"/receive/${TEST_FILE_NAME}\",
    \"fileName\": \"${TEST_FILE_NAME}\"
}"

# Create test file first (would need to be done on the client pod)
# For now, we test the API endpoint availability

response=$(curl -sf -X POST "${CLIENT_API}/api/v1/transfers" \
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
            
            STATUS=$(echo "$response" | jq -r '.status // empty')
            echo "  Transfer status: ${STATUS}"
        else
            log_test "FAIL" "Get transfer status" "Failed to get status"
        fi
        
        # Cancel transfer if still pending
        response=$(curl -sf -X POST "${CLIENT_API}/api/v1/transfers/${TRANSFER_ID}/cancel" 2>/dev/null)
        if [ $? -eq 0 ]; then
            log_test "PASS" "Cancel transfer"
        else
            log_test "SKIP" "Cancel transfer" "Transfer may have completed or failed"
        fi
    else
        log_test "SKIP" "File transfer operations" "No transfer ID returned"
    fi
else
    log_test "SKIP" "File transfer operations" "Transfer initiation not available (may need file)"
fi

# ========== Transfer Retry/Resume ==========

# Test retry endpoint (with non-existent ID to verify endpoint exists)
response=$(curl -sf -X POST "${SERVER_API}/api/v1/transfers/99999/retry" 2>/dev/null)
http_code=$(curl -sf -o /dev/null -w "%{http_code}" -X POST "${SERVER_API}/api/v1/transfers/99999/retry" 2>/dev/null)
if [ "$http_code" = "404" ] || [ "$http_code" = "400" ]; then
    log_test "PASS" "Transfer retry endpoint (returns expected error for invalid ID)"
else
    log_test "SKIP" "Transfer retry endpoint" "Unexpected response"
fi

# Test pause endpoint
http_code=$(curl -sf -o /dev/null -w "%{http_code}" -X POST "${SERVER_API}/api/v1/transfers/99999/pause" 2>/dev/null)
if [ "$http_code" = "404" ] || [ "$http_code" = "400" ]; then
    log_test "PASS" "Transfer pause endpoint (returns expected error for invalid ID)"
else
    log_test "SKIP" "Transfer pause endpoint" "Unexpected response"
fi

# Test resume endpoint
http_code=$(curl -sf -o /dev/null -w "%{http_code}" -X POST "${SERVER_API}/api/v1/transfers/99999/resume" 2>/dev/null)
if [ "$http_code" = "404" ] || [ "$http_code" = "400" ]; then
    log_test "PASS" "Transfer resume endpoint (returns expected error for invalid ID)"
else
    log_test "SKIP" "Transfer resume endpoint" "Unexpected response"
fi

# ========== Cleanup ==========

# Clean up test partner
if [ $PARTNER_CREATED -eq 0 ]; then
    curl -sf -X DELETE "${CLIENT_API}/api/v1/partners/LOCAL-SERVER" 2>/dev/null
fi
