#!/bin/bash
# PeSIT Wizard Performance Benchmark
# Tests throughput and latency for various file sizes

set -e

PW_CLIENT_API="${PW_CLIENT_API:-http://pw-client:9081}"
SERVER_NAME="${SERVER_NAME:-cx-server}"
PARTNER_ID="${PARTNER_ID:-PWSRV01}"
VIRTUAL_FILE="${VIRTUAL_FILE:-PWRECV}"
ITERATIONS="${ITERATIONS:-3}"
RESULTS_FILE="/tmp/benchmark-results.csv"

echo "=========================================="
echo "  PeSIT Wizard Performance Benchmark"
echo "=========================================="
echo ""
echo "Configuration:"
echo "  API: $PW_CLIENT_API"
echo "  Server: $SERVER_NAME"
echo "  Partner: $PARTNER_ID"
echo "  Iterations: $ITERATIONS"
echo ""

# Install required packages
apt-get update -qq && apt-get install -y -qq curl jq bc > /dev/null 2>&1

# Wait for services
echo "Waiting for services..."
sleep 10

# Initialize results file
echo "size_bytes,iteration,duration_sec,throughput_mbps,status" > $RESULTS_FILE

# Test sizes: 100KB, 1MB, 5MB, 10MB, 50MB
SIZES="102400 1048576 5242880 10485760 52428800"
SIZE_NAMES="100KB 1MB 5MB 10MB 50MB"

benchmark_transfer() {
    local size=$1
    local name=$2
    local iteration=$3

    # Create test file
    dd if=/dev/urandom of=/tmp/bench_${name}.dat bs=$size count=1 2>/dev/null

    # Get start time (nanoseconds)
    local start=$(date +%s.%N)

    # Start transfer
    local result=$(curl -s -X POST -H "Content-Type: application/json" \
        "$PW_CLIENT_API/api/v1/transfers/send" \
        -d "{
            \"server\": \"$SERVER_NAME\",
            \"partnerId\": \"$PARTNER_ID\",
            \"filename\": \"/data/send/bench_${name}.dat\",
            \"remoteFilename\": \"$VIRTUAL_FILE\",
            \"syncPointsEnabled\": false
        }")

    local transfer_id=$(echo $result | jq -r '.transferId // .id // "error"')

    if [ "$transfer_id" = "error" ] || [ "$transfer_id" = "null" ]; then
        echo "error,0,0,FAILED" >> $RESULTS_FILE
        return 1
    fi

    # Wait for completion (max 5 minutes)
    local timeout=300
    local elapsed=0
    local status="PENDING"

    while [ "$status" != "COMPLETED" ] && [ "$status" != "FAILED" ] && [ $elapsed -lt $timeout ]; do
        sleep 1
        elapsed=$((elapsed + 1))
        status=$(curl -s "$PW_CLIENT_API/api/v1/transfers/$transfer_id" | jq -r '.status // "PENDING"')
    done

    local end=$(date +%s.%N)
    local duration=$(echo "$end - $start" | bc)

    if [ "$status" = "COMPLETED" ]; then
        # Calculate throughput in MB/s
        local throughput=$(echo "scale=2; $size / $duration / 1048576" | bc)
        echo "$size,$iteration,$duration,$throughput,$status" >> $RESULTS_FILE
        echo "      Run $iteration: ${duration}s (${throughput} MB/s)"
        return 0
    else
        echo "$size,$iteration,$duration,0,$status" >> $RESULTS_FILE
        echo "      Run $iteration: FAILED ($status)"
        return 1
    fi
}

# Copy test files to client send directory
prepare_files() {
    local size=$1
    local name=$2
    dd if=/dev/urandom of=/tmp/pw-client-send/bench_${name}.dat bs=$size count=1 2>/dev/null
}

echo ""
echo "Starting benchmarks..."
echo ""

# Run benchmarks for each size
idx=1
for size in $SIZES; do
    name=$(echo $SIZE_NAMES | cut -d' ' -f$idx)
    echo "--- Benchmarking $name ($size bytes) ---"

    # Prepare test file
    prepare_files $size $name

    total_time=0
    total_throughput=0
    success_count=0

    for i in $(seq 1 $ITERATIONS); do
        if benchmark_transfer $size $name $i; then
            # Extract last result
            last_line=$(tail -1 $RESULTS_FILE)
            duration=$(echo $last_line | cut -d',' -f3)
            throughput=$(echo $last_line | cut -d',' -f4)
            total_time=$(echo "$total_time + $duration" | bc)
            total_throughput=$(echo "$total_throughput + $throughput" | bc)
            success_count=$((success_count + 1))
        fi
        sleep 2
    done

    if [ $success_count -gt 0 ]; then
        avg_time=$(echo "scale=2; $total_time / $success_count" | bc)
        avg_throughput=$(echo "scale=2; $total_throughput / $success_count" | bc)
        echo "   Average: ${avg_time}s (${avg_throughput} MB/s) - $success_count/$ITERATIONS successful"
    else
        echo "   All iterations failed"
    fi

    # Clean up
    rm -f /tmp/pw-client-send/bench_${name}.dat

    echo ""
    idx=$((idx + 1))
done

echo "=========================================="
echo "  Benchmark Complete"
echo "=========================================="
echo ""
echo "Results saved to: $RESULTS_FILE"
echo ""

# Print summary table
echo "Summary:"
echo "--------"
printf "%-10s %-12s %-15s %-10s\n" "Size" "Avg Time" "Avg Throughput" "Success"
echo "---------------------------------------------------"

for size in $SIZES; do
    name=$(echo $SIZE_NAMES | cut -d' ' -f$(echo $SIZES | tr ' ' '\n' | grep -n "^$size$" | cut -d: -f1))
    completed=$(grep "^$size,.*,COMPLETED" $RESULTS_FILE | wc -l)
    if [ $completed -gt 0 ]; then
        avg_time=$(grep "^$size,.*,COMPLETED" $RESULTS_FILE | awk -F',' '{sum+=$3; count++} END {printf "%.2f", sum/count}')
        avg_throughput=$(grep "^$size,.*,COMPLETED" $RESULTS_FILE | awk -F',' '{sum+=$4; count++} END {printf "%.2f", sum/count}')
        printf "%-10s %-12s %-15s %-10s\n" "$name" "${avg_time}s" "${avg_throughput} MB/s" "$completed/$ITERATIONS"
    else
        printf "%-10s %-12s %-15s %-10s\n" "$name" "N/A" "N/A" "0/$ITERATIONS"
    fi
done

echo ""
echo "Note: Throughput includes protocol overhead and sync points."
echo "      Network conditions and server load affect results."
