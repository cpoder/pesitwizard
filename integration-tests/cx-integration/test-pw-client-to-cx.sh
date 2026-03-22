#!/bin/bash
#
# Test PeSIT Wizard Client sending files to Connect:Express Server
#
# This script uses the PeSIT Wizard client REST API to initiate a file transfer
# to a Connect:Express server.
#
# Prerequisites:
#   - Connect:Express must be running and configured (run setup-cx-for-pesitwizard.sh)
#   - PeSIT Wizard client must be running

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# PeSIT Wizard client REST API
PW_CLIENT_API="${PW_CLIENT_API:-http://localhost:9081}"

# Connect:Express server configuration
CX_HOST="${CX_HOST:-localhost}"
CX_PORT="${CX_PORT:-5000}"
CX_DPCSID="${CX_DPCSID:-CETOM1}"
CX_PASSWORD="${CX_PASSWORD:-}"

# Test data
TEST_DATA_DIR="${TEST_DATA_DIR:-/tmp/pesitwizard-tests}"
PW_SEND_DIR="${PW_SEND_DIR:-$TEST_DATA_DIR/pw-send}"
CX_RECV_DIR="${CX_RECV_DIR:-$TEST_DATA_DIR/cx-received}"

# Symbolic file name in CX (must match virtual file definition)
CX_FILE_NAME="${CX_FILE_NAME:-PWRECV}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_test() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check if PW client is running
    if ! curl -s "$PW_CLIENT_API/actuator/health" > /dev/null 2>&1; then
        log_error "PeSIT Wizard client not responding at $PW_CLIENT_API"
        log_error "Start it with: cd pesitwizard-client && mvn spring-boot:run"
        exit 1
    fi
    log_info "PeSIT Wizard client is running"

    # Check if test directories exist
    mkdir -p "$PW_SEND_DIR"
    mkdir -p "$CX_RECV_DIR"
}

# Create a test file
create_test_file() {
    local name="$1"
    local size_kb="${2:-1}"

    local filepath="$PW_SEND_DIR/$name"

    if [ "$size_kb" -gt 0 ]; then
        dd if=/dev/urandom of="$filepath" bs=1024 count="$size_kb" 2>/dev/null
    else
        echo "Small test file created at $(date)" > "$filepath"
    fi

    echo "$filepath"
}

# Send a file using PW client REST API
send_file() {
    local local_path="$1"
    local remote_file="$2"

    log_info "Sending file: $local_path -> $remote_file on $CX_HOST:$CX_PORT"

    # Build the request JSON
    local request_json
    request_json=$(cat <<EOF
{
  "filename": "$local_path",
  "remoteHost": "$CX_HOST",
  "remotePort": $CX_PORT,
  "remoteDpcsid": "$CX_DPCSID",
  "remotePassword": "$CX_PASSWORD",
  "remoteFileName": "$remote_file",
  "mode": "BINARY"
}
EOF
)

    # Initiate the transfer
    local response
    response=$(curl -s -X POST "$PW_CLIENT_API/api/transfers/send" \
        -H "Content-Type: application/json" \
        -d "$request_json")

    # Extract transfer ID
    local transfer_id
    transfer_id=$(echo "$response" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

    if [ -z "$transfer_id" ]; then
        log_error "Failed to start transfer"
        echo "$response"
        return 1
    fi

    log_info "Transfer started with ID: $transfer_id"

    # Poll for completion
    local status="PENDING"
    local attempts=0
    local max_attempts=60

    while [ "$status" != "COMPLETED" ] && [ "$status" != "FAILED" ] && [ "$attempts" -lt "$max_attempts" ]; do
        sleep 1
        attempts=$((attempts + 1))

        local status_response
        status_response=$(curl -s "$PW_CLIENT_API/api/transfers/$transfer_id")
        status=$(echo "$status_response" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)

        if [ $((attempts % 5)) -eq 0 ]; then
            log_info "Transfer status: $status (attempt $attempts/$max_attempts)"
        fi
    done

    if [ "$status" = "COMPLETED" ]; then
        log_info "Transfer completed successfully!"
        return 0
    elif [ "$status" = "FAILED" ]; then
        log_error "Transfer failed!"
        curl -s "$PW_CLIENT_API/api/transfers/$transfer_id" | head -20
        return 1
    else
        log_error "Transfer timed out after $max_attempts seconds"
        return 1
    fi
}

# Verify received file
verify_file() {
    local original="$1"
    local received_dir="$2"

    local filename
    filename=$(basename "$original")

    # CX uses &REQN substitution, so we need to find the received file
    local received
    received=$(find "$received_dir" -type f -newer "$original" -name "*" 2>/dev/null | head -1)

    if [ -z "$received" ]; then
        # Try finding any recent file
        received=$(find "$received_dir" -type f -mmin -5 2>/dev/null | head -1)
    fi

    if [ -z "$received" ]; then
        log_error "Could not find received file in $received_dir"
        return 1
    fi

    log_info "Comparing: $original vs $received"

    local orig_size
    local recv_size
    orig_size=$(stat -c%s "$original" 2>/dev/null || stat -f%z "$original")
    recv_size=$(stat -c%s "$received" 2>/dev/null || stat -f%z "$received")

    if [ "$orig_size" != "$recv_size" ]; then
        log_error "Size mismatch: original=$orig_size bytes, received=$recv_size bytes"
        return 1
    fi

    if cmp -s "$original" "$received"; then
        log_info "Files match perfectly!"
        return 0
    else
        log_error "Files differ!"
        return 1
    fi
}

# Run a single test
run_test() {
    local test_name="$1"
    local file_size_kb="$2"

    log_test "Running test: $test_name (${file_size_kb}KB)"
    echo "----------------------------------------"

    # Create test file
    local test_file
    test_file=$(create_test_file "test-${test_name}.dat" "$file_size_kb")

    # Clean up old received files
    rm -f "$CX_RECV_DIR"/* 2>/dev/null || true

    # Send file
    if send_file "$test_file" "$CX_FILE_NAME"; then
        # Verify file (give CX time to write)
        sleep 2
        if verify_file "$test_file" "$CX_RECV_DIR"; then
            echo -e "${GREEN}PASS${NC}: $test_name"
            return 0
        else
            echo -e "${RED}FAIL${NC}: $test_name (verification failed)"
            return 1
        fi
    else
        echo -e "${RED}FAIL${NC}: $test_name (transfer failed)"
        return 1
    fi
}

# Main test suite
run_tests() {
    local passed=0
    local failed=0

    echo ""
    echo "=========================================="
    echo "  PW Client -> CX Server Test Suite"
    echo "=========================================="
    echo ""

    # Test 1: Small file (< 1KB)
    if run_test "small-file" 0; then
        passed=$((passed + 1))
    else
        failed=$((failed + 1))
    fi
    echo ""

    # Test 2: 1KB file
    if run_test "1kb-file" 1; then
        passed=$((passed + 1))
    else
        failed=$((failed + 1))
    fi
    echo ""

    # Test 3: 10KB file
    if run_test "10kb-file" 10; then
        passed=$((passed + 1))
    else
        failed=$((failed + 1))
    fi
    echo ""

    # Test 4: 100KB file (tests chunking)
    if run_test "100kb-file" 100; then
        passed=$((passed + 1))
    else
        failed=$((failed + 1))
    fi
    echo ""

    # Test 5: 1MB file (tests sync points)
    if run_test "1mb-file" 1024; then
        passed=$((passed + 1))
    else
        failed=$((failed + 1))
    fi
    echo ""

    # Summary
    echo "=========================================="
    echo "  Test Results"
    echo "=========================================="
    echo -e "  Passed: ${GREEN}$passed${NC}"
    echo -e "  Failed: ${RED}$failed${NC}"
    echo "=========================================="

    if [ "$failed" -gt 0 ]; then
        return 1
    fi
    return 0
}

# Usage
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --single FILE SIZE_KB    Run single transfer test"
    echo "  --suite                  Run full test suite (default)"
    echo "  --help                   Show this help"
    echo ""
    echo "Environment variables:"
    echo "  PW_CLIENT_API   PeSIT Wizard client REST API URL (default: http://localhost:9081)"
    echo "  CX_HOST         Connect:Express host (default: localhost)"
    echo "  CX_PORT         Connect:Express port (default: 5000)"
    echo "  CX_DPCSID       Connect:Express server DPCSID (default: CETOM1)"
    echo "  CX_FILE_NAME    CX virtual file name (default: PWRECV)"
}

# Main
main() {
    case "${1:-}" in
        --single)
            check_prerequisites
            if [ -z "$2" ] || [ -z "$3" ]; then
                log_error "Missing FILE or SIZE_KB argument"
                usage
                exit 1
            fi
            run_test "single" "$3"
            ;;
        --help)
            usage
            ;;
        --suite|*)
            check_prerequisites
            run_tests
            ;;
    esac
}

main "$@"
