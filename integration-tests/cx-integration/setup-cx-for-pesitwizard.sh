#!/bin/bash
#
# Configure Connect:Express for PeSIT Wizard integration tests
#
# This script creates the necessary partners and virtual files in CX
# to allow bidirectional testing between PeSIT Wizard and Connect:Express.
#
# Prerequisites:
#   - Connect:Express must be running (use $start_tom)
#   - TOM_DIR environment variable must be set
#   - cx-setup-partner and cx-setup-file tools must be compiled

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CX_DIR="${TOM_DIR:-/home/cpo/cx}"

# Default configuration
PW_SERVER_HOST="${PW_SERVER_HOST:-localhost}"
PW_SERVER_PORT="${PW_SERVER_PORT:-05001}"
PW_SERVER_DPCSID="${PW_SERVER_DPCSID:-PWSRV01}"
PW_SERVER_PASSWD="${PW_SERVER_PASSWD:-}"

CX_SERVER_HOST="${CX_SERVER_HOST:-localhost}"
CX_SERVER_PORT="${CX_SERVER_PORT:-05000}"
CX_SERVER_DPCSID="${CX_SERVER_DPCSID:-CETOM1}"

# Test data directories
TEST_DATA_DIR="${TEST_DATA_DIR:-/tmp/pesitwizard-tests}"
CX_RECV_DIR="${TEST_DATA_DIR}/cx-received"
CX_SEND_DIR="${TEST_DATA_DIR}/cx-send"
PW_RECV_DIR="${TEST_DATA_DIR}/pw-received"
PW_SEND_DIR="${TEST_DATA_DIR}/pw-send"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    if [ ! -d "$CX_DIR" ]; then
        log_error "CX_DIR not found: $CX_DIR"
        log_error "Set TOM_DIR environment variable to CX installation path"
        exit 1
    fi

    # Source CX profile
    if [ -f "$CX_DIR/profile" ]; then
        source "$CX_DIR/profile"
        log_info "Sourced CX profile from $CX_DIR/profile"
    fi

    # Check if tools are compiled
    if [ ! -x "$SCRIPT_DIR/cx-setup-partner" ] || [ ! -x "$SCRIPT_DIR/cx-setup-file" ]; then
        log_warn "Tools not compiled. Building..."
        (cd "$SCRIPT_DIR" && make TOM_DIR="$CX_DIR")
    fi

    # Check if CX is running
    if ! pgrep -f "tom_mon" > /dev/null 2>&1; then
        log_warn "Connect:Express does not appear to be running"
        log_warn "Start it with: $CX_DIR/gtrf/tom_mon"
    fi
}

# Create test directories
create_directories() {
    log_info "Creating test directories..."

    mkdir -p "$CX_RECV_DIR"
    mkdir -p "$CX_SEND_DIR"
    mkdir -p "$PW_RECV_DIR"
    mkdir -p "$PW_SEND_DIR"

    # Set permissions
    chmod 777 "$CX_RECV_DIR" "$CX_SEND_DIR" "$PW_RECV_DIR" "$PW_SEND_DIR"

    log_info "Created directories under $TEST_DATA_DIR"
}

# Create partner for PeSIT Wizard server
create_pw_server_partner() {
    log_info "Creating partner for PeSIT Wizard server..."

    "$SCRIPT_DIR/cx-setup-partner" \
        "PWSERVER" \
        "$PW_SERVER_HOST" \
        "$PW_SERVER_PORT" \
        "$PW_SERVER_DPCSID" \
        "$PW_SERVER_PASSWD" || {
        log_warn "Partner PWSERVER may already exist (code 0017 = duplicate)"
    }
}

# Create virtual files for testing
create_virtual_files() {
    log_info "Creating virtual files..."

    # File for CX to receive from PW client (PW sends -> CX receives)
    # Direction: R (receive), open to all partners
    "$SCRIPT_DIR/cx-setup-file" \
        "PWRECV" \
        "R" \
        '$$ALL$$' \
        '$$ALL$$' \
        "$CX_RECV_DIR/&REQN" \
        "04096" \
        "BV" || {
        log_warn "File PWRECV may already exist"
    }

    # File for CX to send to PW server (CX sends -> PW receives)
    # Direction: T (transmit), to PWSERVER partner
    "$SCRIPT_DIR/cx-setup-file" \
        "PWSEND" \
        "T" \
        "PWSERVER" \
        '$$ALL$$' \
        "$CX_SEND_DIR/testfile.dat" \
        "04096" \
        "BV" || {
        log_warn "File PWSEND may already exist"
    }

    # Bidirectional file for general testing
    "$SCRIPT_DIR/cx-setup-file" \
        "PWTEST" \
        "B" \
        '$$ALL$$' \
        '$$ALL$$' \
        "$TEST_DATA_DIR/&REQN" \
        "04096" \
        "BV" || {
        log_warn "File PWTEST may already exist"
    }
}

# Create test data file
create_test_data() {
    log_info "Creating test data files..."

    # Small text file
    echo "This is a test file for PeSIT Wizard integration tests." > "$CX_SEND_DIR/testfile.dat"
    echo "Created at: $(date)" >> "$CX_SEND_DIR/testfile.dat"
    echo "Line 3 of test data." >> "$CX_SEND_DIR/testfile.dat"

    # Larger binary file (1KB with random data)
    dd if=/dev/urandom of="$CX_SEND_DIR/binary-1k.dat" bs=1024 count=1 2>/dev/null

    # Medium file (100KB)
    dd if=/dev/urandom of="$CX_SEND_DIR/binary-100k.dat" bs=1024 count=100 2>/dev/null

    log_info "Test data created in $CX_SEND_DIR"
}

# Print configuration summary
print_summary() {
    echo ""
    echo "=========================================="
    echo "  Connect:Express Integration Setup"
    echo "=========================================="
    echo ""
    echo "CX Installation:     $CX_DIR"
    echo "Test Data Directory: $TEST_DATA_DIR"
    echo ""
    echo "PeSIT Wizard Server Partner:"
    echo "  Name:     PWSERVER"
    echo "  Host:     $PW_SERVER_HOST"
    echo "  Port:     $PW_SERVER_PORT"
    echo "  DPCSID:   $PW_SERVER_DPCSID"
    echo ""
    echo "Virtual Files:"
    echo "  PWRECV  - CX receives from PW client -> $CX_RECV_DIR"
    echo "  PWSEND  - CX sends to PW server      -> $CX_SEND_DIR"
    echo "  PWTEST  - Bidirectional testing      -> $TEST_DATA_DIR"
    echo ""
    echo "Test Scripts:"
    echo "  $SCRIPT_DIR/test-pw-client-to-cx.sh"
    echo "  $SCRIPT_DIR/test-cx-client-to-pw.sh"
    echo ""
    echo "=========================================="
}

# Main
main() {
    echo "Setting up Connect:Express for PeSIT Wizard integration tests"
    echo ""

    check_prerequisites
    create_directories
    create_pw_server_partner
    create_virtual_files
    create_test_data
    print_summary

    log_info "Setup complete!"
}

main "$@"
