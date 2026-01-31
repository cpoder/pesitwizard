#!/bin/bash
# PeSIT Wizard Concurrent Transfer Stress Test
# Tests stability under concurrent load

set -e

PW_CLIENT_API="${PW_CLIENT_API:-http://pw-client:9081}"
SERVER_NAME="${SERVER_NAME:-cx-server}"
PARTNER_ID="${PARTNER_ID:-PWSRV01}"
VIRTUAL_FILE="${VIRTUAL_FILE:-PWRECV}"
CONCURRENT="${CONCURRENT:-10}"
FILE_SIZE="${FILE_SIZE:-1048576}"  # 1MB default

echo "=========================================="
echo "  Concurrent Transfer Stress Test"
echo "=========================================="
echo ""
echo "Configuration:"
echo "  Concurrent transfers: $CONCURRENT"
echo "  File size: $((FILE_SIZE / 1024))KB"
echo ""

# Install required packages
apt-get update -qq && apt-get install -y -qq curl jq bc > /dev/null 2>&1

# Wait for services
sleep 5

# Create test files
echo "Creating test files..."
for i in $(seq 1 $CONCURRENT); do
    dd if=/dev/urandom of=/tmp/pw-client-send/stress_$i.dat bs=$FILE_SIZE count=1 2>/dev/null
done
echo "Test files created."
echo ""

# Track transfer IDs
declare -a TRANSFER_IDS
declare -a START_TIMES

echo "Starting $CONCURRENT concurrent transfers..."
start_all=$(date +%s.%N)

# Launch all transfers in parallel
for i in $(seq 1 $CONCURRENT); do
    result=$(curl -s -X POST -H "Content-Type: application/json" \
        "$PW_CLIENT_API/api/v1/transfers/send" \
        -d "{
            \"server\": \"$SERVER_NAME\",
            \"partnerId\": \"$PARTNER_ID\",
            \"filename\": \"/data/send/stress_$i.dat\",
            \"remoteFilename\": \"$VIRTUAL_FILE\"
        }")

    id=$(echo $result | jq -r '.transferId // .id // "error"')
    TRANSFER_IDS[$i]=$id
    echo "  Transfer $i started: $id"
done

echo ""
echo "Waiting for all transfers to complete..."

# Wait for all transfers (max 5 minutes)
timeout=300
all_complete=false
start_wait=$(date +%s)

while [ "$all_complete" = "false" ]; do
    sleep 2
    all_complete=true
    completed=0
    failed=0
    in_progress=0

    for i in $(seq 1 $CONCURRENT); do
        id=${TRANSFER_IDS[$i]}
        if [ "$id" != "error" ] && [ -n "$id" ]; then
            status=$(curl -s "$PW_CLIENT_API/api/v1/transfers/$id" | jq -r '.status // "UNKNOWN"')
            case $status in
                COMPLETED) completed=$((completed + 1)) ;;
                FAILED) failed=$((failed + 1)) ;;
                *) in_progress=$((in_progress + 1)); all_complete=false ;;
            esac
        else
            failed=$((failed + 1))
        fi
    done

    elapsed=$(($(date +%s) - start_wait))
    echo "  Progress: $completed completed, $failed failed, $in_progress in progress (${elapsed}s)"

    if [ $elapsed -gt $timeout ]; then
        echo "  Timeout reached!"
        break
    fi
done

end_all=$(date +%s.%N)
total_time=$(echo "$end_all - $start_all" | bc)

echo ""
echo "=========================================="
echo "  Results"
echo "=========================================="

# Count final statuses
completed=0
failed=0
for i in $(seq 1 $CONCURRENT); do
    id=${TRANSFER_IDS[$i]}
    if [ "$id" != "error" ] && [ -n "$id" ]; then
        status=$(curl -s "$PW_CLIENT_API/api/v1/transfers/$id" | jq -r '.status // "UNKNOWN"')
        if [ "$status" = "COMPLETED" ]; then
            completed=$((completed + 1))
        else
            failed=$((failed + 1))
        fi
    else
        failed=$((failed + 1))
    fi
done

total_bytes=$((FILE_SIZE * completed))
throughput=$(echo "scale=2; $total_bytes / $total_time / 1048576" | bc)

echo "  Total time: ${total_time}s"
echo "  Completed: $completed / $CONCURRENT"
echo "  Failed: $failed / $CONCURRENT"
echo "  Total data transferred: $((total_bytes / 1048576)) MB"
echo "  Aggregate throughput: ${throughput} MB/s"
echo ""

# Calculate success rate
success_rate=$(echo "scale=0; $completed * 100 / $CONCURRENT" | bc)
echo "  Success rate: ${success_rate}%"

# Cleanup
echo ""
echo "Cleaning up test files..."
for i in $(seq 1 $CONCURRENT); do
    rm -f /tmp/pw-client-send/stress_$i.dat
done

echo ""
if [ $completed -eq $CONCURRENT ]; then
    echo "[PASS] All concurrent transfers completed successfully"
    exit 0
elif [ $completed -ge $((CONCURRENT * 80 / 100)) ]; then
    echo "[PASS] ${success_rate}% success rate (acceptable)"
    exit 0
else
    echo "[FAIL] Only ${success_rate}% success rate"
    exit 1
fi
