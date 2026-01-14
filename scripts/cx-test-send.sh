#!/bin/bash
# ===========================================================================
# PeSIT Wizard - C:X Test Script
# Test sending a file from C:X to PeSIT Wizard server
# ===========================================================================

set -e

# Configuration
PARTNER_NAME="${PARTNER_NAME:-PESITSRV}"
FILE_NAME="${FILE_NAME:-TESTFILE}"
SOURCE_FILE="${SOURCE_FILE:-$TOM_DIR/out/test_to_server.txt}"

# Create test file if it doesn't exist
if [ ! -f "$SOURCE_FILE" ]; then
    echo "Creating test file at: $SOURCE_FILE"
    mkdir -p "$(dirname "$SOURCE_FILE")"
    echo "Test data from C:X client - $(date)" > "$SOURCE_FILE"
    echo "This is a test file transfer using PeSIT protocol" >> "$SOURCE_FILE"
fi

echo "============================================"
echo "C:X to PeSIT Wizard - File Transfer Test"
echo "============================================"
echo "Partner: $PARTNER_NAME"
echo "File: $FILE_NAME"
echo "Source: $SOURCE_FILE"
echo "Direction: T (Transmission/Send)"
echo "============================================"

# Execute transfer request
if [ -n "$SOURCE_FILE" ]; then
    $TOM_DIR/itom/p1b8preq "/SFN=$FILE_NAME/SPN=$PARTNER_NAME/DIR=T" "/DSN=$SOURCE_FILE"
else
    $TOM_DIR/itom/p1b8preq "/SFN=$FILE_NAME/SPN=$PARTNER_NAME/DIR=T"
fi

RET=$?
echo ""
echo "Return code: $RET"

if [ "$RET" = "0" ]; then
    echo "✓ Transfer request submitted successfully"
    echo ""
    echo "Check server logs at: /home/cpo/pesitwizard/server.log"
    echo "Check received files at: /tmp/pesit-server-data/receive/"
    exit 0
else
    echo "✗ Transfer request failed"
    exit 1
fi
