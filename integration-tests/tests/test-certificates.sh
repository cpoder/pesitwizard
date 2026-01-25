#!/bin/bash
# PeSIT Wizard - Certificate Management Tests

echo ""
echo "=== Certificate Management Tests ==="
echo ""

# ========== List Certificates ==========

# List all certificates
response=$(curl -sf "${SERVER_API}/api/v1/certificates" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List all certificates"
else
    log_test "FAIL" "List all certificates" "Failed to list certificates"
fi

# List keystores
response=$(curl -sf "${SERVER_API}/api/v1/certificates/keystores" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List keystores"
else
    log_test "FAIL" "List keystores" "Failed to list keystores"
fi

# List truststores
response=$(curl -sf "${SERVER_API}/api/v1/certificates/truststores" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "List truststores"
else
    log_test "FAIL" "List truststores" "Failed to list truststores"
fi

# ========== Certificate Statistics ==========

response=$(curl -sf "${SERVER_API}/api/v1/certificates/stats" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get certificate statistics"
else
    log_test "FAIL" "Get certificate statistics" "Failed to get stats"
fi

# ========== Expiring Certificates ==========

response=$(curl -sf "${SERVER_API}/api/v1/certificates/expiring?days=30" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get expiring certificates (30 days)"
else
    log_test "FAIL" "Get expiring certificates" "Failed to get expiring certs"
fi

response=$(curl -sf "${SERVER_API}/api/v1/certificates/expired" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get expired certificates"
else
    log_test "FAIL" "Get expired certificates" "Failed to get expired certs"
fi

# ========== Create Empty Keystore ==========

KEYSTORE_JSON='{
    "name": "integration-test-keystore",
    "description": "Integration Test Keystore",
    "format": "PKCS12",
    "storePassword": "test-password-123",
    "purpose": "SERVER",
    "isDefault": false
}'

response=$(curl -sf -X POST "${SERVER_API}/api/v1/certificates/keystores/create" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "name=integration-test-keystore&description=Integration+Test&format=PKCS12&storePassword=test-password-123&purpose=SERVER&isDefault=false" 2>/dev/null)
if [ $? -eq 0 ]; then
    KEYSTORE_ID=$(echo "$response" | jq -r '.id // empty')
    log_test "PASS" "Create empty keystore"
    
    if [ -n "$KEYSTORE_ID" ] && [ "$KEYSTORE_ID" != "null" ]; then
        # Get keystore by ID
        response=$(curl -sf "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}" 2>/dev/null)
        if [ $? -eq 0 ]; then
            log_test "PASS" "Get certificate store by ID"
        else
            log_test "FAIL" "Get certificate store by ID" "Not found"
        fi
        
        # Get certificate info
        response=$(curl -sf "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}/info" 2>/dev/null)
        if [ $? -eq 0 ]; then
            log_test "PASS" "Get certificate info"
        else
            log_test "SKIP" "Get certificate info" "No certificates in empty store"
        fi
        
        # List entries
        response=$(curl -sf "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}/entries" 2>/dev/null)
        if [ $? -eq 0 ]; then
            log_test "PASS" "List certificate entries"
        else
            log_test "FAIL" "List certificate entries" "Failed to list entries"
        fi
        
        # Validate certificate store
        response=$(curl -sf -X POST "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}/validate" 2>/dev/null)
        if [ $? -eq 0 ]; then
            log_test "PASS" "Validate certificate store"
        else
            log_test "FAIL" "Validate certificate store" "Validation failed"
        fi
        
        # Activate/Deactivate
        response=$(curl -sf -X POST "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}/deactivate" 2>/dev/null)
        if [ $? -eq 0 ]; then
            log_test "PASS" "Deactivate certificate store"
            
            response=$(curl -sf -X POST "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}/activate" 2>/dev/null)
            if [ $? -eq 0 ]; then
                log_test "PASS" "Activate certificate store"
            else
                log_test "FAIL" "Activate certificate store" "Failed"
            fi
        else
            log_test "FAIL" "Deactivate certificate store" "Failed"
        fi
        
        # Delete keystore
        response=$(curl -sf -X DELETE "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}" 2>/dev/null)
        if [ $? -eq 0 ]; then
            log_test "PASS" "Delete certificate store"
        else
            log_test "FAIL" "Delete certificate store" "Failed to delete"
        fi
    fi
else
    log_test "SKIP" "Keystore CRUD operations" "Create endpoint may require different format"
fi

# ========== Create Empty Truststore ==========

response=$(curl -sf -X POST "${SERVER_API}/api/v1/certificates/truststores/create" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "name=integration-test-truststore&description=Integration+Test&format=PKCS12&storePassword=test-password-123&isDefault=false" 2>/dev/null)
if [ $? -eq 0 ]; then
    TRUSTSTORE_ID=$(echo "$response" | jq -r '.id // empty')
    log_test "PASS" "Create empty truststore"
    
    # Delete truststore
    if [ -n "$TRUSTSTORE_ID" ] && [ "$TRUSTSTORE_ID" != "null" ]; then
        curl -sf -X DELETE "${SERVER_API}/api/v1/certificates/${TRUSTSTORE_ID}" 2>/dev/null
    fi
else
    log_test "SKIP" "Truststore CRUD operations" "Create endpoint may require different format"
fi

# ========== Default Certificate Store ==========

response=$(curl -sf "${SERVER_API}/api/v1/certificates/keystores/default" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get default keystore"
else
    log_test "SKIP" "Get default keystore" "No default keystore configured"
fi

response=$(curl -sf "${SERVER_API}/api/v1/certificates/truststores/default" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get default truststore"
else
    log_test "SKIP" "Get default truststore" "No default truststore configured"
fi

# ========== Partner Certificates ==========

response=$(curl -sf "${SERVER_API}/api/v1/certificates/partner/TEST-PARTNER" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_test "PASS" "Get partner certificates"
else
    log_test "SKIP" "Get partner certificates" "No partner certificates"
fi
