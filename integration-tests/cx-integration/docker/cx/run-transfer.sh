#!/bin/bash
# Run a PeSIT transfer from CX to PW
# This script is called from within the CX container

. $TOM_DIR/profile

FILE_TO_SEND="$1"
LOGICAL_FILE="${2:-PWSEND}"

if [ -z "$FILE_TO_SEND" ]; then
    echo "Usage: $0 <file_to_send> [logical_file]"
    exit 1
fi

if [ ! -f "$FILE_TO_SEND" ]; then
    echo "ERROR: File not found: $FILE_TO_SEND"
    exit 1
fi

echo "Submitting transfer request..."
echo "  Physical file: $FILE_TO_SEND"
echo "  Logical file: $LOGICAL_FILE"
echo "  Partner: PWSERVER"

# Submit transfer request using p1b8preq
# Arg 1: /SFN=<logical_file>/SPN=<partner>/DIR=T
# Arg 2: /DSN=<physical_file>
# SFN: Symbolic File Name (logical file name, 8 chars max)
# SPN: Symbolic Partner Name
# DIR: Direction (T=transmit)
# DSN: Data Set Name (physical file path)
$TOM_DIR/itom/p1b8preq "/SFN=$LOGICAL_FILE/SPN=PWSERVER/DIR=T" "/DSN=$FILE_TO_SEND"

RC=$?
if [ $RC -ne 0 ]; then
    echo "ERROR: Transfer request failed with code $RC"
    case $RC in
        1) echo "  - Invalid number of parameters" ;;
        2) echo "  - Invalid argument" ;;
        3) echo "  - Request submitted but transfer refused by monitor" ;;
        4) echo "  - Queue creation problem" ;;
    esac
    exit $RC
fi

echo "Transfer request submitted successfully. Waiting for completion..."

# Wait for transfer to complete (poll for 60 seconds)
echo "Waiting for transfer to complete..."
sleep 60  # Give transfer time to complete
echo "Transfer wait time completed."
exit 0
