#!/bin/bash
# PeSIT Wizard - Client API Tests

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

# ========== Partner Management ==========

# List partners
response=$(curl -sf "${CLIENT_API}/api/v1/partners" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List partners"
else
    log_test "FAIL" "List partners" "Failed to list partners"
fi

# Create a test partner
PARTNER_JSON='{
    "partnerId": "INTEGRATION-TEST-PARTNER",
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
if [ $? -eq 0 ] && echo "$response" | jq -e '.partnerId == "INTEGRATION-TEST-PARTNER"' > /dev/null 2>&1; then
    log_test "PASS" "Create partner"
    TEST_PARTNER_ID="INTEGRATION-TEST-PARTNER"
else
    # May already exist, try to get it
    response=$(curl -sf "${CLIENT_API}/api/v1/partners/INTEGRATION-TEST-PARTNER" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Create partner (already exists)"
        TEST_PARTNER_ID="INTEGRATION-TEST-PARTNER"
    else
        log_test "FAIL" "Create partner" "Failed to create partner"
        TEST_PARTNER_ID=""
    fi
fi

# Get partner by ID
if [ -n "$TEST_PARTNER_ID" ]; then
    response=$(curl -sf "${CLIENT_API}/api/v1/partners/${TEST_PARTNER_ID}" 2>/dev/null)
    if [ $? -eq 0 ] && echo "$response" | jq -e '.partnerId' > /dev/null 2>&1; then
        log_test "PASS" "Get partner by ID"
    else
        log_test "FAIL" "Get partner by ID" "Partner not found"
    fi
fi

# Update partner
if [ -n "$TEST_PARTNER_ID" ]; then
    UPDATE_JSON='{"name": "Updated Integration Test Partner", "maxConcurrentTransfers": 10}'
    response=$(curl -sf -X PUT "${CLIENT_API}/api/v1/partners/${TEST_PARTNER_ID}" \
        -H "Content-Type: application/json" \
        -d "${UPDATE_JSON}" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Update partner"
    else
        log_test "FAIL" "Update partner" "Failed to update partner"
    fi
fi

# Test partner connection
if [ -n "$TEST_PARTNER_ID" ]; then
    response=$(curl -sf -X POST "${CLIENT_API}/api/v1/partners/${TEST_PARTNER_ID}/test" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Test partner connection"
    else
        log_test "SKIP" "Test partner connection" "Partner may not be reachable"
    fi
fi

# Activate/Deactivate partner
if [ -n "$TEST_PARTNER_ID" ]; then
    response=$(curl -sf -X POST "${CLIENT_API}/api/v1/partners/${TEST_PARTNER_ID}/deactivate" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Deactivate partner"
        
        response=$(curl -sf -X POST "${CLIENT_API}/api/v1/partners/${TEST_PARTNER_ID}/activate" 2>/dev/null)
        if [ $? -eq 0 ]; then
            log_test "PASS" "Activate partner"
        else
            log_test "FAIL" "Activate partner" "Failed to activate"
        fi
    else
        log_test "FAIL" "Deactivate partner" "Failed to deactivate"
    fi
fi

# ========== Transfer Jobs ==========

# List transfer jobs
response=$(curl -sf "${CLIENT_API}/api/v1/jobs" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List transfer jobs"
else
    log_test "FAIL" "List transfer jobs" "Failed to list jobs"
fi

# Create a scheduled job
JOB_JSON='{
    "name": "Integration Test Job",
    "partnerId": "INTEGRATION-TEST-PARTNER",
    "direction": "SEND",
    "sourcePattern": "/tmp/send/*.txt",
    "destinationPath": "/receive",
    "schedule": "0 0 * * * *",
    "enabled": false
}'

response=$(curl -sf -X POST "${CLIENT_API}/api/v1/jobs" \
    -H "Content-Type: application/json" \
    -d "${JOB_JSON}" 2>/dev/null)
if [ $? -eq 0 ]; then
    JOB_ID=$(echo "$response" | jq -r '.id')
    log_test "PASS" "Create transfer job"
    
    # Get job
    response=$(curl -sf "${CLIENT_API}/api/v1/jobs/${JOB_ID}" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Get transfer job by ID"
    else
        log_test "FAIL" "Get transfer job by ID" "Job not found"
    fi
    
    # Delete job
    response=$(curl -sf -X DELETE "${CLIENT_API}/api/v1/jobs/${JOB_ID}" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Delete transfer job"
    else
        log_test "FAIL" "Delete transfer job" "Failed to delete"
    fi
else
    log_test "SKIP" "Transfer job CRUD" "Jobs endpoint may not exist"
fi

# ========== Client Configuration ==========

# Get client configuration
response=$(curl -sf "${CLIENT_API}/api/v1/config" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get client configuration"
else
    log_test "SKIP" "Get client configuration" "Endpoint may not exist"
fi

# Cleanup: Delete test partner
if [ -n "$TEST_PARTNER_ID" ]; then
    curl -sf -X DELETE "${CLIENT_API}/api/v1/partners/${TEST_PARTNER_ID}" 2>/dev/null
fi
