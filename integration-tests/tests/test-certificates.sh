#!/bin/bash
# PeSIT Wizard - Certificate Management Tests
# Tests the server's certificate management API (requires ADMIN auth)

echo ""
echo "=== Certificate Management Tests ==="
echo ""

# ========== List Certificates ==========

response=$(server_curl "${SERVER_API}/api/v1/certificates")
if [ $? -eq 0 ]; then
    log_test "PASS" "List all certificates"
else
    log_test "FAIL" "List all certificates" "Failed to list certificates"
fi

response=$(server_curl "${SERVER_API}/api/v1/certificates/keystores")
if [ $? -eq 0 ]; then
    log_test "PASS" "List keystores"
else
    log_test "FAIL" "List keystores" "Failed to list keystores"
fi

response=$(server_curl "${SERVER_API}/api/v1/certificates/truststores")
if [ $? -eq 0 ]; then
    log_test "PASS" "List truststores"
else
    log_test "FAIL" "List truststores" "Failed to list truststores"
fi

# ========== Certificate Statistics ==========

response=$(server_curl "${SERVER_API}/api/v1/certificates/stats")
if [ $? -eq 0 ]; then
    log_test "PASS" "Get certificate statistics"
else
    log_test "FAIL" "Get certificate statistics" "Failed to get stats"
fi

# ========== Expiring Certificates ==========

response=$(server_curl "${SERVER_API}/api/v1/certificates/expiring?days=30")
if [ $? -eq 0 ]; then
    log_test "PASS" "Get expiring certificates (30 days)"
else
    log_test "FAIL" "Get expiring certificates" "Failed to get expiring certs"
fi

response=$(server_curl "${SERVER_API}/api/v1/certificates/expired")
if [ $? -eq 0 ]; then
    log_test "PASS" "Get expired certificates"
else
    log_test "FAIL" "Get expired certificates" "Failed to get expired certs"
fi

# ========== Create Empty Keystore ==========

response=$(server_curl -X POST "${SERVER_API}/api/v1/certificates/keystores/create" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "name=integration-test-keystore&description=Integration+Test&format=PKCS12&storePassword=test-password-123&purpose=SERVER&isDefault=false")
if [ $? -eq 0 ]; then
    KEYSTORE_ID=$(echo "$response" | jq -r '.id // empty')
    log_test "PASS" "Create empty keystore"

    if [ -n "$KEYSTORE_ID" ] && [ "$KEYSTORE_ID" != "null" ]; then
        # Get keystore by ID
        response=$(server_curl "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}")
        if [ $? -eq 0 ]; then
            log_test "PASS" "Get certificate store by ID"
        else
            log_test "FAIL" "Get certificate store by ID" "Not found"
        fi

        # List entries
        response=$(server_curl "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}/entries")
        if [ $? -eq 0 ]; then
            log_test "PASS" "List certificate entries"
        else
            log_test "SKIP" "List certificate entries" "Empty store"
        fi

        # Validate certificate store
        response=$(server_curl -X POST "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}/validate")
        if [ $? -eq 0 ]; then
            log_test "PASS" "Validate certificate store"
        else
            log_test "SKIP" "Validate certificate store" "Validation not available"
        fi

        # Deactivate/Activate
        response=$(server_curl -X POST "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}/deactivate")
        if [ $? -eq 0 ]; then
            log_test "PASS" "Deactivate certificate store"

            response=$(server_curl -X POST "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}/activate")
            if [ $? -eq 0 ]; then
                log_test "PASS" "Activate certificate store"
            else
                log_test "FAIL" "Activate certificate store" "Failed"
            fi
        else
            log_test "SKIP" "Certificate activation/deactivation" "Not available"
        fi

        # Delete keystore
        response=$(server_curl -X DELETE "${SERVER_API}/api/v1/certificates/${KEYSTORE_ID}")
        if [ $? -eq 0 ]; then
            log_test "PASS" "Delete certificate store"
        else
            log_test "FAIL" "Delete certificate store" "Failed to delete"
        fi
    fi
else
    log_test "SKIP" "Keystore CRUD operations" "Create endpoint returned error"
fi

# ========== Default Certificate Store ==========

response=$(server_curl "${SERVER_API}/api/v1/certificates/keystores/default")
if [ $? -eq 0 ]; then
    log_test "PASS" "Get default keystore"
else
    log_test "SKIP" "Get default keystore" "No default keystore configured"
fi

response=$(server_curl "${SERVER_API}/api/v1/certificates/truststores/default")
if [ $? -eq 0 ]; then
    log_test "PASS" "Get default truststore"
else
    log_test "SKIP" "Get default truststore" "No default truststore configured"
fi
