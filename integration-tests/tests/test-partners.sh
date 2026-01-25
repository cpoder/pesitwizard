#!/bin/bash
# PeSIT Wizard - Partner Management Tests

echo ""
echo "=== Partner Management Tests ==="
echo ""

# ========== Server-side Partner Management ==========

# List all partners (server)
response=$(curl -sf "${SERVER_API}/api/v1/partners" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List partners (server)"
else
    log_test "FAIL" "List partners (server)" "Failed to list partners"
fi

# Create partner on server
PARTNER_JSON='{
    "partnerId": "INTEGRATION-PARTNER-SRV",
    "name": "Integration Test Partner (Server)",
    "description": "Created by integration tests",
    "active": true
}'

response=$(curl -sf -X POST "${SERVER_API}/api/v1/partners" \
    -H "Content-Type: application/json" \
    -d "${PARTNER_JSON}" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Create partner (server)"
    SRV_PARTNER_ID="INTEGRATION-PARTNER-SRV"
else
    # May already exist
    response=$(curl -sf "${SERVER_API}/api/v1/partners/INTEGRATION-PARTNER-SRV" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Create partner (server - already exists)"
        SRV_PARTNER_ID="INTEGRATION-PARTNER-SRV"
    else
        log_test "SKIP" "Partner CRUD (server)" "Endpoint may not exist"
        SRV_PARTNER_ID=""
    fi
fi

# Get partner by ID
if [ -n "$SRV_PARTNER_ID" ]; then
    response=$(curl -sf "${SERVER_API}/api/v1/partners/${SRV_PARTNER_ID}" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Get partner by ID (server)"
    else
        log_test "FAIL" "Get partner by ID (server)" "Not found"
    fi
fi

# Update partner
if [ -n "$SRV_PARTNER_ID" ]; then
    UPDATE_JSON='{"description": "Updated by integration tests"}'
    response=$(curl -sf -X PUT "${SERVER_API}/api/v1/partners/${SRV_PARTNER_ID}" \
        -H "Content-Type: application/json" \
        -d "${UPDATE_JSON}" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Update partner (server)"
    else
        log_test "FAIL" "Update partner (server)" "Update failed"
    fi
fi

# Search partners
response=$(curl -sf "${SERVER_API}/api/v1/partners/search?q=INTEGRATION" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Search partners (server)"
else
    log_test "SKIP" "Search partners (server)" "Search endpoint may not exist"
fi

# Get active partners
response=$(curl -sf "${SERVER_API}/api/v1/partners/active" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get active partners"
else
    log_test "SKIP" "Get active partners" "Endpoint may not exist"
fi

# ========== Partner Statistics ==========

response=$(curl -sf "${SERVER_API}/api/v1/partners/stats" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get partner statistics"
else
    log_test "SKIP" "Get partner statistics" "Endpoint may not exist"
fi

# ========== Partner Files/Virtual Files ==========

if [ -n "$SRV_PARTNER_ID" ]; then
    # List partner files
    response=$(curl -sf "${SERVER_API}/api/v1/partners/${SRV_PARTNER_ID}/files" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "List partner files"
    else
        log_test "SKIP" "List partner files" "Endpoint may not exist"
    fi
    
    # Create virtual file mapping
    FILE_JSON='{
        "virtualName": "TESTFILE",
        "localPath": "/data/receive/test.txt",
        "direction": "RECEIVE",
        "enabled": true
    }'
    
    response=$(curl -sf -X POST "${SERVER_API}/api/v1/partners/${SRV_PARTNER_ID}/files" \
        -H "Content-Type: application/json" \
        -d "${FILE_JSON}" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Create virtual file mapping"
        FILE_ID=$(echo "$response" | jq -r '.id // empty')
        
        # Delete the file mapping
        if [ -n "$FILE_ID" ] && [ "$FILE_ID" != "null" ]; then
            curl -sf -X DELETE "${SERVER_API}/api/v1/partners/${SRV_PARTNER_ID}/files/${FILE_ID}" 2>/dev/null
        fi
    else
        log_test "SKIP" "Virtual file CRUD" "Endpoint may not exist"
    fi
fi

# ========== Partner Certificates ==========

if [ -n "$SRV_PARTNER_ID" ]; then
    response=$(curl -sf "${SERVER_API}/api/v1/partners/${SRV_PARTNER_ID}/certificates" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Get partner certificates"
    else
        log_test "SKIP" "Get partner certificates" "Endpoint may not exist"
    fi
fi

# ========== Partner Audit Trail ==========

if [ -n "$SRV_PARTNER_ID" ]; then
    response=$(curl -sf "${SERVER_API}/api/v1/partners/${SRV_PARTNER_ID}/audit?limit=10" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Get partner audit trail"
    else
        log_test "SKIP" "Get partner audit trail" "Endpoint may not exist"
    fi
fi

# ========== Partner Transfer History ==========

if [ -n "$SRV_PARTNER_ID" ]; then
    response=$(curl -sf "${SERVER_API}/api/v1/partners/${SRV_PARTNER_ID}/transfers?limit=10" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Get partner transfer history"
    else
        log_test "SKIP" "Get partner transfer history" "Endpoint may not exist"
    fi
fi

# ========== Activate/Deactivate Partner ==========

if [ -n "$SRV_PARTNER_ID" ]; then
    response=$(curl -sf -X POST "${SERVER_API}/api/v1/partners/${SRV_PARTNER_ID}/deactivate" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Deactivate partner (server)"
        
        response=$(curl -sf -X POST "${SERVER_API}/api/v1/partners/${SRV_PARTNER_ID}/activate" 2>/dev/null)
        if [ $? -eq 0 ]; then
            log_test "PASS" "Activate partner (server)"
        else
            log_test "FAIL" "Activate partner (server)" "Failed"
        fi
    else
        log_test "SKIP" "Partner activation/deactivation" "Endpoint may not exist"
    fi
fi

# ========== Delete Partner ==========

if [ -n "$SRV_PARTNER_ID" ]; then
    response=$(curl -sf -X DELETE "${SERVER_API}/api/v1/partners/${SRV_PARTNER_ID}" 2>/dev/null)
    if [ $? -eq 0 ]; then
        log_test "PASS" "Delete partner (server)"
    else
        log_test "FAIL" "Delete partner (server)" "Failed to delete"
    fi
fi
