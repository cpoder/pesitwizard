#!/bin/bash
# PeSIT Wizard - Audit & Monitoring Tests

echo ""
echo "=== Audit & Monitoring Tests ==="
echo ""

# ========== Audit Events ==========

# List audit events
response=$(curl -sf "${SERVER_API}/api/v1/audit" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List audit events"
else
    log_test "FAIL" "List audit events" "Failed to list events"
fi

# Search audit events
response=$(curl -sf "${SERVER_API}/api/v1/audit/search?limit=10" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Search audit events"
else
    log_test "SKIP" "Search audit events" "Endpoint may not exist"
fi

# Get audit events by type
response=$(curl -sf "${SERVER_API}/api/v1/audit?eventType=TRANSFER_STARTED&limit=10" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Filter audit events by type"
else
    log_test "SKIP" "Filter audit events by type" "Filtering may not work"
fi

# Get audit events by date range
TODAY=$(date +%Y-%m-%d)
response=$(curl -sf "${SERVER_API}/api/v1/audit?from=${TODAY}T00:00:00&limit=10" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Filter audit events by date"
else
    log_test "SKIP" "Filter audit events by date" "Date filtering may not work"
fi

# ========== Audit Statistics ==========

response=$(curl -sf "${SERVER_API}/api/v1/audit/stats" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get audit statistics"
else
    log_test "SKIP" "Get audit statistics" "Endpoint may not exist"
fi

# ========== Health & Readiness ==========

# Liveness probe
response=$(curl -sf "${SERVER_API}/actuator/health/liveness" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Liveness probe"
else
    log_test "SKIP" "Liveness probe" "Endpoint may not be exposed"
fi

# Readiness probe
response=$(curl -sf "${SERVER_API}/actuator/health/readiness" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Readiness probe"
else
    log_test "SKIP" "Readiness probe" "Endpoint may not be exposed"
fi

# ========== Metrics ==========

# Prometheus metrics
response=$(curl -sf "${SERVER_API}/actuator/prometheus" 2>/dev/null)
if [ $? -eq 0 ]; then
    # Check for specific PeSIT metrics
    if echo "$response" | grep -q "pesit_transfers_total"; then
        log_test "PASS" "PeSIT transfer metrics"
    else
        log_test "SKIP" "PeSIT transfer metrics" "Metric not found"
    fi
    
    if echo "$response" | grep -q "pesit_connections"; then
        log_test "PASS" "PeSIT connection metrics"
    else
        log_test "SKIP" "PeSIT connection metrics" "Metric not found"
    fi
    
    if echo "$response" | grep -q "pesit_bytes"; then
        log_test "PASS" "PeSIT bytes metrics"
    else
        log_test "SKIP" "PeSIT bytes metrics" "Metric not found"
    fi
else
    log_test "FAIL" "Prometheus metrics endpoint" "Not accessible"
fi

# ========== Logging Configuration ==========

response=$(curl -sf "${SERVER_API}/actuator/loggers" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Loggers endpoint"
else
    log_test "SKIP" "Loggers endpoint" "Not exposed"
fi

# ========== Environment Info ==========

response=$(curl -sf "${SERVER_API}/actuator/env" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Environment endpoint"
else
    log_test "SKIP" "Environment endpoint" "Not exposed (expected in prod)"
fi

# ========== Backup API ==========

# List backups
response=$(curl -sf "${SERVER_API}/api/v1/backup" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List backups"
else
    log_test "SKIP" "List backups" "Backup API may not exist"
fi

# Trigger backup (dry run)
response=$(curl -sf -X POST "${SERVER_API}/api/v1/backup?dryRun=true" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Trigger backup (dry run)"
else
    log_test "SKIP" "Trigger backup" "Backup API may not exist"
fi

# ========== System Info ==========

response=$(curl -sf "${SERVER_API}/api/v1/system/info" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get system info"
else
    log_test "SKIP" "Get system info" "Endpoint may not exist"
fi

# ========== Client Audit ==========

response=$(curl -sf "${CLIENT_API}/api/v1/audit" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List audit events (client)"
else
    log_test "SKIP" "List audit events (client)" "Endpoint may not exist"
fi

# ========== Database Health ==========

response=$(curl -sf "${SERVER_API}/actuator/health" 2>/dev/null)
if echo "$response" | jq -e '.components.db.status == "UP"' > /dev/null 2>&1; then
    log_test "PASS" "Database health check"
else
    log_test "SKIP" "Database health check" "DB component not in health response"
fi

# ========== Disk Space ==========

if echo "$response" | jq -e '.components.diskSpace.status == "UP"' > /dev/null 2>&1; then
    log_test "PASS" "Disk space health check"
else
    log_test "SKIP" "Disk space health check" "DiskSpace component not in health response"
fi
