#!/bin/bash
#
# Test Connect:Express Client sending files to PeSIT Wizard Server
#
# This script uses Connect:Express p1b8preq command to initiate file transfers
# to a PeSIT Wizard server.
#
# Prerequisites:
#   - Connect:Express must be running and configured (run setup-cx-for-pesitwizard.sh)
#   - PeSIT Wizard server must be running
#   - Partner PWSERVER must exist in CX

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CX_DIR="${TOM_DIR:-/home/cpo/cx}"

# Source CX profile for environment
if [ -f "$CX_DIR/profile" ]; then
    source "$CX_DIR/profile"
fi

# PeSIT Wizard server configuration (must match CX partner PWSERVER)
PW_SERVER_HOST="${PW_SERVER_HOST:-localhost}"
PW_SERVER_PORT="${PW_SERVER_PORT:-5001}"

# CX configuration
CX_PARTNER="${CX_PARTNER:-PWSERVER}"
CX_FILE_NAME="${CX_FILE_NAME:-PWSEND}"

# Test data directories
TEST_DATA_DIR="${TEST_DATA_DIR:-/tmp/pesitwizard-tests}"
CX_SEND_DIR="${CX_SEND_DIR:-$TEST_DATA_DIR/cx-send}"
PW_RECV_DIR="${PW_RECV_DIR:-$TEST_DATA_DIR/pw-received}"

# p1b8preq command
P1B8PREQ="${P1B8PREQ:-$CX_DIR/itom/p1b8preq}"

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

    # Check if p1b8preq exists
    if [ ! -x "$P1B8PREQ" ]; then
        log_error "p1b8preq not found at $P1B8PREQ"
        log_error "Set TOM_DIR or P1B8PREQ environment variable"
        exit 1
    fi
    log_info "Found p1b8preq at $P1B8PREQ"

    # Check if PW server is running (try to connect to its port)
    if ! nc -z "$PW_SERVER_HOST" "$PW_SERVER_PORT" 2>/dev/null; then
        log_warn "Cannot connect to PeSIT Wizard server at $PW_SERVER_HOST:$PW_SERVER_PORT"
        log_warn "Make sure the server is running: cd pesitwizard-server && mvn spring-boot:run"
    else
        log_info "PeSIT Wizard server is reachable at $PW_SERVER_HOST:$PW_SERVER_PORT"
    fi

    # Check if CX is running
    if ! pgrep -f "tom_mon" > /dev/null 2>&1; then
        log_warn "Connect:Express does not appear to be running"
        log_warn "Start it with: $CX_DIR/gtrf/tom_mon"
    fi

    # Check directories
    mkdir -p "$CX_SEND_DIR"
    mkdir -p "$PW_RECV_DIR"
}

# Create a test file in CX send directory
create_test_file() {
    local name="$1"
    local size_kb="${2:-1}"

    local filepath="$CX_SEND_DIR/$name"

    if [ "$size_kb" -gt 0 ]; then
        dd if=/dev/urandom of="$filepath" bs=1024 count="$size_kb" 2>/dev/null
    else
        echo "Small test file created at $(date)" > "$filepath"
    fi

    echo "$filepath"
}

# Send file using p1b8preq
# p1b8preq syntax:
#   p1b8preq /SFN=<symbolic_file>/SPN=<partner>/DIR=T|R [/DSN=<physical_file>] [options]
#
# Common options:
#   /SFN=<file>     - Symbolic file name (virtual file in CX)
#   /SPN=<partner>  - Symbolic partner name
#   /DIR=T|R        - Direction: T=Transmit (send), R=Receive
#   /DSN=<path>     - Physical file path (overrides virtual file definition)
#   /TYP=B|T        - File type: B=Binary, T=Text
#   /ORG=S|D        - File organization: S=Sequential, D=Direct
#   /REC=<length>   - Record length (for fixed records)
#   /API=           - Application ID (user-defined)
send_file_via_cx() {
    local local_path="$1"
    local symbolic_file="${2:-$CX_FILE_NAME}"

    log_info "Sending via CX: $local_path -> $symbolic_file to partner $CX_PARTNER"

    # Build p1b8preq command
    # Using DIR=T for transmit (CX sends to PW server)
    local cmd="$P1B8PREQ /SFN=$symbolic_file/SPN=$CX_PARTNER/DIR=T/DSN=$local_path"

    log_info "Command: $cmd"

    # Execute and capture output
    local output
    local exit_code=0
    output=$($cmd 2>&1) || exit_code=$?

    echo "$output"

    if [ $exit_code -ne 0 ]; then
        log_error "p1b8preq failed with exit code: $exit_code"
        return 1
    fi

    # Check for success in output
    # p1b8preq returns various status codes in the output
    if echo "$output" | grep -qi "error\|fail\|reject"; then
        log_error "Transfer appears to have failed"
        return 1
    fi

    # Extract request number if available
    local reqn
    reqn=$(echo "$output" | grep -o "REQN=[^ ]*" | head -1)
    if [ -n "$reqn" ]; then
        log_info "Request submitted: $reqn"
    fi

    return 0
}

# Wait for transfer to complete
# This is tricky since p1b8preq submits async requests
# We need to poll the request status or watch for the received file
wait_for_transfer() {
    local request_id="$1"
    local timeout="${2:-60}"

    log_info "Waiting for transfer to complete (timeout: ${timeout}s)..."

    # For now, just wait and check if file appeared
    # In production, would use p1b8pret to check request status
    local attempts=0
    while [ $attempts -lt $timeout ]; do
        sleep 1
        attempts=$((attempts + 1))

        # Check if new file appeared in PW receive directory
        local recent_files
        recent_files=$(find "$PW_RECV_DIR" -type f -mmin -1 2>/dev/null | wc -l)
        if [ "$recent_files" -gt 0 ]; then
            log_info "File(s) received!"
            return 0
        fi

        if [ $((attempts % 10)) -eq 0 ]; then
            log_info "Still waiting... ($attempts/${timeout}s)"
        fi
    done

    log_warn "Timed out waiting for transfer"
    return 1
}

# Alternative: Check request status using p1b8pret
check_request_status() {
    local reqn="$1"

    if [ -z "$reqn" ]; then
        return 1
    fi

    local p1b8pret="$CX_DIR/itom/p1b8pret"
    if [ ! -x "$p1b8pret" ]; then
        log_warn "p1b8pret not available for status check"
        return 1
    fi

    log_info "Checking request status: $reqn"
    "$p1b8pret" "/RQN=$reqn"
}

# Verify received file
verify_file() {
    local original="$1"

    local filename
    filename=$(basename "$original")

    # Find recent files in PW receive directory
    local received
    received=$(find "$PW_RECV_DIR" -type f -mmin -5 2>/dev/null | head -1)

    if [ -z "$received" ]; then
        log_error "No received file found in $PW_RECV_DIR"
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
    test_file=$(create_test_file "cx-test-${test_name}.dat" "$file_size_kb")

    # Clean up old received files
    rm -f "$PW_RECV_DIR"/* 2>/dev/null || true

    # Send file via CX
    if send_file_via_cx "$test_file"; then
        # Wait for transfer and verify
        sleep 5  # Give transfer time to complete

        if verify_file "$test_file"; then
            echo -e "${GREEN}PASS${NC}: $test_name"
            return 0
        else
            echo -e "${RED}FAIL${NC}: $test_name (verification failed)"
            return 1
        fi
    else
        echo -e "${RED}FAIL${NC}: $test_name (send failed)"
        return 1
    fi
}

# Main test suite
run_tests() {
    local passed=0
    local failed=0

    echo ""
    echo "=========================================="
    echo "  CX Client -> PW Server Test Suite"
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

    # Test 4: 100KB file
    if run_test "100kb-file" 100; then
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
    echo "  --status REQN            Check status of a request"
    echo "  --help                   Show this help"
    echo ""
    echo "Environment variables:"
    echo "  TOM_DIR        Connect:Express installation directory"
    echo "  CX_PARTNER     CX partner name for PW server (default: PWSERVER)"
    echo "  CX_FILE_NAME   CX symbolic file name (default: PWSEND)"
    echo "  PW_RECV_DIR    Directory where PW server saves files"
    echo ""
    echo "p1b8preq syntax:"
    echo "  p1b8preq /SFN=<file>/SPN=<partner>/DIR=T|R [/DSN=<path>]"
    echo ""
    echo "  /SFN  - Symbolic file name (virtual file in CX)"
    echo "  /SPN  - Symbolic partner name"
    echo "  /DIR  - Direction: T=Transmit (send), R=Receive"
    echo "  /DSN  - Physical file path (optional override)"
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
        --status)
            if [ -z "$2" ]; then
                log_error "Missing REQN argument"
                exit 1
            fi
            check_request_status "$2"
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
