#!/bin/bash
# PeSIT Wizard Resilience Tests
# Tests system behavior under failure conditions

set -e

PW_CLIENT_API="${PW_CLIENT_API:-http://pw-client:9081}"
PW_SERVER_API="${PW_SERVER_API:-http://pw-server:8080}"
SERVER_NAME="${SERVER_NAME:-cx-server}"
PARTNER_ID="${PARTNER_ID:-PWSRV01}"
VIRTUAL_FILE="${VIRTUAL_FILE:-PWRECV}"

echo "=========================================="
echo "  PeSIT Wizard Resilience Tests"
echo "=========================================="
echo ""

# Install required packages
apt-get update -qq && apt-get install -y -qq curl jq iptables iproute2 > /dev/null 2>&1 || true

TESTS_PASSED=0
TESTS_FAILED=0
TESTS_SKIPPED=0

test_result() {
    local name=$1
    local result=$2
    if [ "$result" = "pass" ]; then
        echo "   [PASS] $name"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    elif [ "$result" = "skip" ]; then
        echo "   [SKIP] $name"
        TESTS_SKIPPED=$((TESTS_SKIPPED + 1))
    else
        echo "   [FAIL] $name"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Wait for services
echo "Waiting for services..."
sleep 10

# Create test file
echo "Creating test file..."
dd if=/dev/urandom of=/tmp/pw-client-send/resilience_test.dat bs=1048576 count=5 2>/dev/null
echo ""

# =========================================
# Test 1: Non-existent server
# =========================================
echo "Test 1: Transfer to non-existent server"

# Configure a server that doesn't exist
FAKE_SERVER=$(curl -s -X POST -H "Content-Type: application/json" \
    "$PW_CLIENT_API/api/v1/servers" \
    -d '{
        "name": "fake-server",
        "host": "nonexistent.server.local",
        "port": 5000,
        "serverId": "FAKE01",
        "connectionTimeout": 5000,
        "enabled": true
    }')
FAKE_ID=$(echo $FAKE_SERVER | jq -r '.id // "error"')

if [ "$FAKE_ID" != "error" ] && [ "$FAKE_ID" != "null" ]; then
    RESULT=$(curl -s -X POST -H "Content-Type: application/json" \
        "$PW_CLIENT_API/api/v1/transfers/send" \
        -d '{
            "server": "fake-server",
            "partnerId": "FAKE01",
            "filename": "/data/send/resilience_test.dat",
            "remoteFilename": "FAKEFILE"
        }')

    TRANSFER_ID=$(echo $RESULT | jq -r '.transferId // .id // "error"')

    # Wait for failure
    sleep 10

    if [ "$TRANSFER_ID" != "error" ] && [ "$TRANSFER_ID" != "null" ]; then
        STATUS=$(curl -s "$PW_CLIENT_API/api/v1/transfers/$TRANSFER_ID" | jq -r '.status')
        if [ "$STATUS" = "FAILED" ]; then
            test_result "Connection failure is handled gracefully" "pass"
        else
            test_result "Connection failure is handled gracefully (status: $STATUS)" "fail"
        fi
    else
        test_result "Connection failure is handled gracefully" "pass"
    fi

    # Cleanup
    curl -s -X DELETE "$PW_CLIENT_API/api/v1/servers/$FAKE_ID" > /dev/null
else
    test_result "Connection failure test" "skip"
fi
echo ""

# =========================================
# Test 2: Transfer timeout handling
# =========================================
echo "Test 2: Transfer timeout handling"

# Configure server with very short timeout
TIMEOUT_SERVER=$(curl -s -X POST -H "Content-Type: application/json" \
    "$PW_CLIENT_API/api/v1/servers" \
    -d '{
        "name": "timeout-server",
        "host": "192.0.2.1",
        "port": 5000,
        "serverId": "TOUT01",
        "connectionTimeout": 1000,
        "readTimeout": 1000,
        "enabled": true
    }')
TIMEOUT_ID=$(echo $TIMEOUT_SERVER | jq -r '.id // "error"')

if [ "$TIMEOUT_ID" != "error" ] && [ "$TIMEOUT_ID" != "null" ]; then
    RESULT=$(curl -s -X POST -H "Content-Type: application/json" \
        "$PW_CLIENT_API/api/v1/transfers/send" \
        -d '{
            "server": "timeout-server",
            "partnerId": "TOUT01",
            "filename": "/data/send/resilience_test.dat",
            "remoteFilename": "TIMEOUT"
        }')

    TRANSFER_ID=$(echo $RESULT | jq -r '.transferId // .id // "error"')

    # Wait for timeout
    sleep 15

    if [ "$TRANSFER_ID" != "error" ] && [ "$TRANSFER_ID" != "null" ]; then
        STATUS=$(curl -s "$PW_CLIENT_API/api/v1/transfers/$TRANSFER_ID" | jq -r '.status')
        if [ "$STATUS" = "FAILED" ]; then
            ERROR=$(curl -s "$PW_CLIENT_API/api/v1/transfers/$TRANSFER_ID" | jq -r '.errorMessage // ""')
            if echo "$ERROR" | grep -qi "timeout\|timed out\|connection"; then
                test_result "Timeout error with clear message" "pass"
            else
                test_result "Timeout error detected" "pass"
            fi
        else
            test_result "Timeout handling (status: $STATUS)" "fail"
        fi
    else
        test_result "Timeout handling" "pass"
    fi

    # Cleanup
    curl -s -X DELETE "$PW_CLIENT_API/api/v1/servers/$TIMEOUT_ID" > /dev/null
else
    test_result "Timeout handling test" "skip"
fi
echo ""

# =========================================
# Test 3: Invalid partner ID
# =========================================
echo "Test 3: Invalid partner ID handling"

RESULT=$(curl -s -X POST -H "Content-Type: application/json" \
    "$PW_CLIENT_API/api/v1/transfers/send" \
    -d "{
        \"server\": \"$SERVER_NAME\",
        \"partnerId\": \"INVALID_PARTNER_12345\",
        \"filename\": \"/data/send/resilience_test.dat\",
        \"remoteFilename\": \"$VIRTUAL_FILE\"
    }")

TRANSFER_ID=$(echo $RESULT | jq -r '.transferId // .id // "error"')

if [ "$TRANSFER_ID" != "error" ] && [ "$TRANSFER_ID" != "null" ]; then
    # Wait for result
    sleep 10
    STATUS=$(curl -s "$PW_CLIENT_API/api/v1/transfers/$TRANSFER_ID" | jq -r '.status')
    if [ "$STATUS" = "FAILED" ]; then
        test_result "Invalid partner ID rejected" "pass"
    else
        test_result "Invalid partner ID handling (status: $STATUS)" "fail"
    fi
else
    # Immediate rejection is also acceptable
    test_result "Invalid partner ID rejected at submission" "pass"
fi
echo ""

# =========================================
# Test 4: Non-existent file transfer
# =========================================
echo "Test 4: Non-existent file transfer"

RESULT=$(curl -s -X POST -H "Content-Type: application/json" \
    "$PW_CLIENT_API/api/v1/transfers/send" \
    -d "{
        \"server\": \"$SERVER_NAME\",
        \"partnerId\": \"$PARTNER_ID\",
        \"filename\": \"/data/send/file_that_does_not_exist_xyz.dat\",
        \"remoteFilename\": \"$VIRTUAL_FILE\"
    }")

TRANSFER_ID=$(echo $RESULT | jq -r '.transferId // .id // "error"')
ERROR=$(echo $RESULT | jq -r '.error // .message // ""')

if echo "$ERROR" | grep -qi "not found\|does not exist\|no such"; then
    test_result "Non-existent file detected at submission" "pass"
elif [ "$TRANSFER_ID" != "error" ] && [ "$TRANSFER_ID" != "null" ]; then
    # Wait for failure
    sleep 5
    STATUS=$(curl -s "$PW_CLIENT_API/api/v1/transfers/$TRANSFER_ID" | jq -r '.status')
    if [ "$STATUS" = "FAILED" ]; then
        test_result "Non-existent file detected during transfer" "pass"
    else
        test_result "Non-existent file handling" "fail"
    fi
else
    test_result "Non-existent file detected" "pass"
fi
echo ""

# =========================================
# Test 5: Empty file transfer
# =========================================
echo "Test 5: Empty file transfer"

touch /tmp/pw-client-send/empty_file.dat

RESULT=$(curl -s -X POST -H "Content-Type: application/json" \
    "$PW_CLIENT_API/api/v1/transfers/send" \
    -d "{
        \"server\": \"$SERVER_NAME\",
        \"partnerId\": \"$PARTNER_ID\",
        \"filename\": \"/data/send/empty_file.dat\",
        \"remoteFilename\": \"$VIRTUAL_FILE\"
    }")

TRANSFER_ID=$(echo $RESULT | jq -r '.transferId // .id // "error"')

if [ "$TRANSFER_ID" != "error" ] && [ "$TRANSFER_ID" != "null" ]; then
    # Wait for result
    timeout=60
    elapsed=0
    while [ $elapsed -lt $timeout ]; do
        STATUS=$(curl -s "$PW_CLIENT_API/api/v1/transfers/$TRANSFER_ID" | jq -r '.status')
        if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ]; then
            break
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    if [ "$STATUS" = "COMPLETED" ]; then
        test_result "Empty file transferred successfully" "pass"
    elif [ "$STATUS" = "FAILED" ]; then
        # Empty file rejection is also acceptable
        test_result "Empty file handled (rejected)" "pass"
    else
        test_result "Empty file handling (timeout)" "fail"
    fi
else
    test_result "Empty file handling" "pass"
fi

rm -f /tmp/pw-client-send/empty_file.dat
echo ""

# =========================================
# Test 6: API rate limiting behavior
# =========================================
echo "Test 6: API rate limiting / burst handling"

# Send 20 rapid requests
echo "   Sending 20 rapid transfer requests..."
SUCCESS_COUNT=0
for i in $(seq 1 20); do
    RESULT=$(curl -s -X POST -H "Content-Type: application/json" \
        "$PW_CLIENT_API/api/v1/transfers/send" \
        -d "{
            \"server\": \"$SERVER_NAME\",
            \"partnerId\": \"$PARTNER_ID\",
            \"filename\": \"/data/send/resilience_test.dat\",
            \"remoteFilename\": \"$VIRTUAL_FILE\"
        }")

    TRANSFER_ID=$(echo $RESULT | jq -r '.transferId // .id // "error"')
    if [ "$TRANSFER_ID" != "error" ] && [ "$TRANSFER_ID" != "null" ]; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    fi
done

echo "   Accepted: $SUCCESS_COUNT / 20 requests"

if [ $SUCCESS_COUNT -ge 10 ]; then
    test_result "Burst requests handled ($SUCCESS_COUNT/20 accepted)" "pass"
else
    test_result "Burst requests handled" "fail"
fi

# Wait for transfers to complete before cleanup
sleep 30
echo ""

# =========================================
# Test 7: Concurrent transfers to same file
# =========================================
echo "Test 7: Concurrent transfers to same destination"

# Start 3 transfers to same virtual file
declare -a CONCURRENT_IDS
for i in 1 2 3; do
    dd if=/dev/urandom of=/tmp/pw-client-send/concurrent_$i.dat bs=102400 count=1 2>/dev/null

    RESULT=$(curl -s -X POST -H "Content-Type: application/json" \
        "$PW_CLIENT_API/api/v1/transfers/send" \
        -d "{
            \"server\": \"$SERVER_NAME\",
            \"partnerId\": \"$PARTNER_ID\",
            \"filename\": \"/data/send/concurrent_$i.dat\",
            \"remoteFilename\": \"$VIRTUAL_FILE\"
        }")

    CONCURRENT_IDS[$i]=$(echo $RESULT | jq -r '.transferId // .id // "error"')
done

# Wait for completion
sleep 30

COMPLETED=0
FAILED=0
for i in 1 2 3; do
    ID=${CONCURRENT_IDS[$i]}
    if [ "$ID" != "error" ] && [ "$ID" != "null" ]; then
        STATUS=$(curl -s "$PW_CLIENT_API/api/v1/transfers/$ID" | jq -r '.status')
        case $STATUS in
            COMPLETED) COMPLETED=$((COMPLETED + 1)) ;;
            FAILED) FAILED=$((FAILED + 1)) ;;
        esac
    fi
    rm -f /tmp/pw-client-send/concurrent_$i.dat
done

if [ $COMPLETED -ge 2 ]; then
    test_result "Concurrent transfers to same dest ($COMPLETED/3 completed)" "pass"
elif [ $COMPLETED -ge 1 ]; then
    test_result "Concurrent transfers handled with some failures" "pass"
else
    test_result "Concurrent transfers" "fail"
fi
echo ""

# =========================================
# Test 8: Service health recovery
# =========================================
echo "Test 8: Service health endpoints"

CLIENT_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$PW_CLIENT_API/actuator/health")

if [ "$CLIENT_HEALTH" = "200" ]; then
    test_result "Client health endpoint responding" "pass"
else
    test_result "Client health endpoint (HTTP $CLIENT_HEALTH)" "fail"
fi
echo ""

# =========================================
# Test 9: Transfer cancellation
# =========================================
echo "Test 9: Transfer cancellation"

# Create large file for longer transfer
dd if=/dev/urandom of=/tmp/pw-client-send/cancel_test.dat bs=10485760 count=1 2>/dev/null

RESULT=$(curl -s -X POST -H "Content-Type: application/json" \
    "$PW_CLIENT_API/api/v1/transfers/send" \
    -d "{
        \"server\": \"$SERVER_NAME\",
        \"partnerId\": \"$PARTNER_ID\",
        \"filename\": \"/data/send/cancel_test.dat\",
        \"remoteFilename\": \"$VIRTUAL_FILE\"
    }")

TRANSFER_ID=$(echo $RESULT | jq -r '.transferId // .id // "error"')

if [ "$TRANSFER_ID" != "error" ] && [ "$TRANSFER_ID" != "null" ]; then
    # Wait briefly, then cancel
    sleep 2

    CANCEL_RESULT=$(curl -s -X POST "$PW_CLIENT_API/api/v1/transfers/$TRANSFER_ID/cancel")

    # Check status after cancel
    sleep 3
    FINAL_STATUS=$(curl -s "$PW_CLIENT_API/api/v1/transfers/$TRANSFER_ID" | jq -r '.status')

    if [ "$FINAL_STATUS" = "CANCELLED" ] || [ "$FINAL_STATUS" = "FAILED" ]; then
        test_result "Transfer cancellation works" "pass"
    elif [ "$FINAL_STATUS" = "COMPLETED" ]; then
        test_result "Transfer completed before cancel (fast transfer)" "pass"
    else
        test_result "Transfer cancellation (status: $FINAL_STATUS)" "fail"
    fi
else
    test_result "Transfer cancellation" "skip"
fi

rm -f /tmp/pw-client-send/cancel_test.dat
echo ""

# =========================================
# Test 10: Invalid JSON handling
# =========================================
echo "Test 10: Invalid JSON handling"

RESULT=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" \
    "$PW_CLIENT_API/api/v1/transfers/send" \
    -d '{"invalid json here')

if [ "$RESULT" = "400" ]; then
    test_result "Invalid JSON returns 400 Bad Request" "pass"
elif [ "$RESULT" = "500" ]; then
    test_result "Invalid JSON causes server error (needs improvement)" "fail"
else
    test_result "Invalid JSON handling (HTTP $RESULT)" "fail"
fi
echo ""

# =========================================
# Cleanup
# =========================================
echo "Cleaning up..."
rm -f /tmp/pw-client-send/resilience_test.dat

echo ""
echo "=========================================="
echo "  Resilience Test Results"
echo "=========================================="
echo "  Passed:  $TESTS_PASSED"
echo "  Failed:  $TESTS_FAILED"
echo "  Skipped: $TESTS_SKIPPED"
echo "=========================================="
echo ""

if [ $TESTS_FAILED -gt 0 ]; then
    echo "[WARN] Some resilience tests failed - review error handling"
    exit 1
fi

if [ $TESTS_PASSED -ge 7 ]; then
    echo "[PASS] Resilience tests passed"
    exit 0
else
    echo "[WARN] Too many tests skipped"
    exit 1
fi
