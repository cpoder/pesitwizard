#!/bin/bash
# ===========================================================================
# PeSIT Wizard - C:X Test Script
# Test receiving a file from PeSIT Wizard server to C:X
# ===========================================================================

set -e

# Configuration
PARTNER_NAME="${PARTNER_NAME:-PESITSRV}"
FILE_NAME="${FILE_NAME:-TESTFILE}"
DEST_FILE="${DEST_FILE:-$TOM_DIR/in/received_from_server.txt}"

echo "============================================"
echo "PeSIT Wizard to C:X - File Transfer Test"
echo "============================================"
echo "Partner: $PARTNER_NAME"
echo "File: $FILE_NAME"
echo "Destination: $DEST_FILE"
echo "Direction: R (Reception/Receive)"
echo "============================================"

# Ensure destination directory exists
mkdir -p "$(dirname "$DEST_FILE")"

# Execute transfer request
if [ -n "$DEST_FILE" ]; then
    $TOM_DIR/itom/p1b8preq "/SFN=$FILE_NAME/SPN=$PARTNER_NAME/DIR=R" "/DSN=$DEST_FILE"
else
    $TOM_DIR/itom/p1b8preq "/SFN=$FILE_NAME/SPN=$PARTNER_NAME/DIR=R"
fi

RET=$?
echo ""
echo "Return code: $RET"

if [ "$RET" = "0" ]; then
    echo "✓ Transfer request submitted successfully"
    echo ""
    echo "Received file will be saved to: $DEST_FILE"
    exit 0
else
    echo "✗ Transfer request failed"
    exit 1
fi
