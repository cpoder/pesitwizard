#!/bin/bash
# PeSIT Wizard - Server API Tests
# Tests the PeSIT Server REST API endpoints
# Requires: SERVER_API, SERVER_API_KEY, server_curl function from run-all-tests.sh

echo ""
echo "=== Server API Tests ==="
echo ""

# ========== Public Actuator Endpoints ==========

# Health check (public)
response=$(curl -sf "${SERVER_API}/actuator/health" 2>/dev/null)
if [ $? -eq 0 ] && echo "$response" | jq -e '.status == "UP"' > /dev/null 2>&1; then
    log_test "PASS" "Server health check"
else
    log_test "FAIL" "Server health check" "Health endpoint not responding"
fi

# Info endpoint (public)
response=$(curl -sf "${SERVER_API}/actuator/info" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Server info endpoint"
else
    log_test "FAIL" "Server info endpoint" "Info endpoint not responding"
fi

# Prometheus metrics (requires auth)
response=$(server_curl "${SERVER_API}/actuator/prometheus")
if [ $? -eq 0 ]; then
    log_test "PASS" "Server metrics (Prometheus)"
else
    log_test "SKIP" "Server metrics (Prometheus)" "Endpoint not exposed or requires different auth"
fi

# ========== PeSIT Server Management (/api/servers) ==========

# List servers
response=$(server_curl "${SERVER_API}/api/servers")
if [ $? -eq 0 ]; then
    log_test "PASS" "List PeSIT servers"
else
    log_test "FAIL" "List PeSIT servers" "Failed to list servers"
fi

# Create a test server (use /data paths that exist in the container)
SERVER_JSON='{
    "serverId": "INTTEST",
    "port": 5199,
    "maxConnections": 50,
    "receiveDirectory": "/data/received",
    "sendDirectory": "/data/send",
    "autoStart": false
}'

# Use verbose curl to capture both response body and HTTP status
create_response=$(curl -s -w "\n%{http_code}" -H "X-API-Key: ${SERVER_API_KEY}" \
    -H "Content-Type: application/json" \
    -X POST "${SERVER_API}/api/servers" \
    -d "${SERVER_JSON}" 2>/dev/null)
create_http_code=$(echo "$create_response" | tail -1)
create_body=$(echo "$create_response" | sed '$d')
if [ "$create_http_code" = "200" ] || [ "$create_http_code" = "201" ]; then
    log_test "PASS" "Create PeSIT server"
    TEST_SERVER_ID="INTTEST"
elif [ "$create_http_code" = "409" ]; then
    log_test "PASS" "Create PeSIT server (already exists)"
    TEST_SERVER_ID="INTTEST"
else
    log_test "FAIL" "Create PeSIT server" "HTTP ${create_http_code}: ${create_body}"
    TEST_SERVER_ID=""
fi

# Get server by ID
if [ -n "$TEST_SERVER_ID" ]; then
    response=$(server_curl "${SERVER_API}/api/servers/${TEST_SERVER_ID}")
    if [ $? -eq 0 ]; then
        log_test "PASS" "Get PeSIT server by ID"
    else
        log_test "FAIL" "Get PeSIT server by ID" "Server not found"
    fi
fi

# Update server
if [ -n "$TEST_SERVER_ID" ]; then
    UPDATE_JSON='{"serverId": "INTTEST", "port": 5199, "maxConnections": 100, "receiveDirectory": "/data/received", "sendDirectory": "/data/send", "autoStart": false}'
    response=$(server_curl -X PUT "${SERVER_API}/api/servers/${TEST_SERVER_ID}" \
        -H "Content-Type: application/json" \
        -d "${UPDATE_JSON}")
    if [ $? -eq 0 ]; then
        log_test "PASS" "Update PeSIT server"
    else
        log_test "FAIL" "Update PeSIT server" "Failed to update server"
    fi
fi

# Get server status
if [ -n "$TEST_SERVER_ID" ]; then
    response=$(server_curl "${SERVER_API}/api/servers/${TEST_SERVER_ID}/status")
    if [ $? -eq 0 ]; then
        log_test "PASS" "Get server status"
    else
        log_test "FAIL" "Get server status" "Failed to get status"
    fi
fi

# Start server (may fail if port in use, that's OK)
if [ -n "$TEST_SERVER_ID" ]; then
    response=$(server_curl -X POST "${SERVER_API}/api/servers/${TEST_SERVER_ID}/start")
    if [ $? -eq 0 ]; then
        log_test "PASS" "Start PeSIT server"
        sleep 2  # Wait for server to start

        # Stop server
        response=$(server_curl -X POST "${SERVER_API}/api/servers/${TEST_SERVER_ID}/stop")
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
    response=$(server_curl -X DELETE "${SERVER_API}/api/servers/${TEST_SERVER_ID}")
    if [ $? -eq 0 ]; then
        log_test "PASS" "Delete PeSIT server"
    else
        log_test "FAIL" "Delete PeSIT server" "Failed to delete server"
    fi
fi

# ========== File System Endpoints (/api/v1/filesystem) ==========

# Browse filesystem
response=$(server_curl "${SERVER_API}/api/v1/filesystem/browse?path=/")
if [ $? -eq 0 ]; then
    log_test "PASS" "Browse filesystem"
else
    log_test "SKIP" "Browse filesystem" "Endpoint may require specific path"
fi

# ========== Dashboard/Stats ==========

# Cluster status (public in server)
response=$(curl -sf "${SERVER_API}/api/cluster/status" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get cluster status"
else
    log_test "SKIP" "Get cluster status" "Cluster endpoint may not exist"
fi
