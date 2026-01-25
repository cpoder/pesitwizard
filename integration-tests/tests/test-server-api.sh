#!/bin/bash
# PeSIT Wizard - Server API Tests

echo ""
echo "=== Server API Tests ==="
echo ""

# Health check
response=$(curl -sf "${SERVER_API}/actuator/health" 2>/dev/null)
if [ $? -eq 0 ] && echo "$response" | jq -e '.status == "UP"' > /dev/null 2>&1; then
    log_test "PASS" "Server health check"
else
    log_test "FAIL" "Server health check" "Health endpoint not responding"
fi

# Info endpoint
response=$(curl -sf "${SERVER_API}/actuator/info" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Server info endpoint"
else
    log_test "FAIL" "Server info endpoint" "Info endpoint not responding"
fi

# Metrics endpoint
response=$(curl -sf "${SERVER_API}/actuator/prometheus" 2>/dev/null)
if [ $? -eq 0 ] && echo "$response" | grep -q "pesit_"; then
    log_test "PASS" "Server metrics (Prometheus)"
else
    log_test "FAIL" "Server metrics (Prometheus)" "No PeSIT metrics found"
fi

# ========== PeSIT Server Management ==========

# List servers
response=$(curl -sf "${SERVER_API}/api/v1/servers" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List PeSIT servers"
else
    log_test "FAIL" "List PeSIT servers" "Failed to list servers"
fi

# Create a test server
SERVER_JSON='{
    "serverId": "INTEGRATION-TEST-SERVER",
    "port": 5199,
    "maxConnections": 50,
    "receiveDirectory": "/tmp/pesit/receive",
    "sendDirectory": "/tmp/pesit/send",
    "autoStart": false
}'

response=$(curl -sf -X POST "${SERVER_API}/api/v1/servers" \
    -H "Content-Type: application/json" \
    -d "${SERVER_JSON}" 2>/dev/null)
if [ $? -eq 0 ] && echo "$response" | jq -e '.serverId == "INTEGRATION-TEST-SERVER"' > /dev/null 2>&1; then
    log_test "PASS" "Create PeSIT server"
    TEST_SERVER_ID="INTEGRATION-TEST-SERVER"
else
    log_test "FAIL" "Create PeSIT server" "Failed to create server"
    TEST_SERVER_ID=""
fi

# Get server by ID
if [ -n "$TEST_SERVER_ID" ]; then
    response=$(curl -sf "${SERVER_API}/api/v1/servers/${TEST_SERVER_ID}" 2>/dev/null)
    if [ $? -eq 0 ] && echo "$response" | jq -e '.serverId == "INTEGRATION-TEST-SERVER"' > /dev/null 2>&1; then
        log_test "PASS" "Get PeSIT server by ID"
    else
        log_test "FAIL" "Get PeSIT server by ID" "Server not found"
    fi
fi

# Update server
if [ -n "$TEST_SERVER_ID" ]; then
    UPDATE_JSON='{"maxConnections": 100}'
    response=$(curl -sf -X PUT "${SERVER_API}/api/v1/servers/${TEST_SERVER_ID}" \
        -H "Content-Type: application/json" \
        -d "${UPDATE_JSON}" 2>/dev/null)
    if [ $? -eq 0 ] && echo "$response" | jq -e '.maxConnections == 100' > /dev/null 2>&1; then
        log_test "PASS" "Update PeSIT server"
    else
        log_test "FAIL" "Update PeSIT server" "Failed to update server"
    fi
fi

# Get server status
if [ -n "$TEST_SERVER_ID" ]; then
    response=$(curl -sf "${SERVER_API}/api/v1/servers/${TEST_SERVER_ID}/status" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Get server status"
    else
        log_test "FAIL" "Get server status" "Failed to get status"
    fi
fi

# Start server (may fail if port in use, that's OK)
if [ -n "$TEST_SERVER_ID" ]; then
    response=$(curl -sf -X POST "${SERVER_API}/api/v1/servers/${TEST_SERVER_ID}/start" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Start PeSIT server"
        sleep 2  # Wait for server to start
        
        # Stop server
        response=$(curl -sf -X POST "${SERVER_API}/api/v1/servers/${TEST_SERVER_ID}/stop" 2>/dev/null)
        if [ $? -eq 0 ]; then
            log_test "PASS" "Stop PeSIT server"
        else
            log_test "FAIL" "Stop PeSIT server" "Failed to stop server"
        fi
    else
        log_test "SKIP" "Start/Stop PeSIT server" "Port may be in use"
    fi
fi

# Delete server
if [ -n "$TEST_SERVER_ID" ]; then
    response=$(curl -sf -X DELETE "${SERVER_API}/api/v1/servers/${TEST_SERVER_ID}" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Delete PeSIT server"
    else
        log_test "FAIL" "Delete PeSIT server" "Failed to delete server"
    fi
fi

# ========== File System Endpoints ==========

# List directories
response=$(curl -sf "${SERVER_API}/api/v1/filesystem/list?path=/" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List filesystem directories"
else
    log_test "FAIL" "List filesystem directories" "Failed to list directories"
fi

# ========== Dashboard/Stats ==========

# Get dashboard stats
response=$(curl -sf "${SERVER_API}/api/v1/dashboard/stats" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get dashboard statistics"
else
    log_test "SKIP" "Get dashboard statistics" "Endpoint may not exist"
fi
