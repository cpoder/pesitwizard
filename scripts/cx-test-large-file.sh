#!/bin/bash
# Test with large files (5 MB)
# Tests both PUSH (C:X -> PesitWizard) and PULL (PesitWizard -> C:X)

set -e

SIZE_MB=${1:-5}
CEXP_HOME="/home/cpo/cexp"
TOM_DIR="${CEXP_HOME}"
PESITWIZARD_HOME="/home/cpo/pesitwizard"

# Source C:X environment
. ${CEXP_HOME}/profile

echo "=== Creating ${SIZE_MB}MB test file ==="
mkdir -p ${PESITWIZARD_HOME}/send/test
dd if=/dev/urandom of=${PESITWIZARD_HOME}/send/test/TESTFILE bs=1M count=${SIZE_MB} 2>/dev/null
ls -lh ${PESITWIZARD_HOME}/send/test/TESTFILE
ORIG_MD5=$(md5sum ${PESITWIZARD_HOME}/send/test/TESTFILE | awk '{print $1}')
echo "Original MD5: $ORIG_MD5"

echo ""
echo "=== Test 1: PULL - C:X receives from PesitWizard ==="
echo "Submitting C:X receive request..."

# C:X transfer request: DIR=R (receive), SPN=partner, SFN=symbolic file name
req=$(${TOM_DIR}/itom/p1b8preq /SFN=TESTFILE/DIR=R/SPN=PESITSRV/NTF=0 2>&1)
if [ $? -ne 0 ]; then
    echo "Request failed: $req"
    exit 1
fi
echo "Request $req submitted"

# Wait for transfer to complete
echo "Waiting for transfer..."
sleep 5
for i in {1..60}; do
    status=$(${TOM_DIR}/itom/p1b8pren /REQ=$req 2>&1 | grep -i "state" || true)
    if echo "$status" | grep -qi "ended\|done\|complet"; then
        echo "Transfer completed"
        break
    fi
    sleep 2
done

# Check received file - C:X virtual file TESTFILE is mapped to /home/cpo/cexp/out/testfile.txt
RECV_FILE="${CEXP_HOME}/out/testfile.txt"
if [ -f "$RECV_FILE" ]; then
    echo "File received: $RECV_FILE"
    ls -lh "$RECV_FILE"
    RECV_MD5=$(md5sum "$RECV_FILE" | awk '{print $1}')
    echo "Received MD5: $RECV_MD5"
    
    if [ "$ORIG_MD5" = "$RECV_MD5" ]; then
        echo "✓ MD5 checksum matches - PULL test PASSED"
    else
        echo "✗ MD5 checksum mismatch - PULL test FAILED"
        exit 1
    fi
else
    echo "✗ File not received - PULL test FAILED"
    echo "Looking in ${CEXP_HOME}/out/"
    ls -la ${CEXP_HOME}/out/ 2>/dev/null || true
    exit 1
fi

echo ""
echo "=== Test 2: PUSH - C:X sends to PesitWizard ==="
# Create file to send from C:X
mkdir -p ${CEXP_HOME}/out
dd if=/dev/urandom of=${CEXP_HOME}/out/testfile.txt bs=1M count=${SIZE_MB} 2>/dev/null
PUSH_ORIG_MD5=$(md5sum ${CEXP_HOME}/out/testfile.txt | awk '{print $1}')
echo "Original MD5: $PUSH_ORIG_MD5"

echo "Submitting C:X send request..."
# DIR=T (transmit/send)
req=$(${TOM_DIR}/itom/p1b8preq /SFN=TESTFILE/DIR=T/SPN=PESITSRV/NTF=0 /DSN=${CEXP_HOME}/out/testfile.txt 2>&1)
if [ $? -ne 0 ]; then
    echo "Request failed: $req"
    exit 1
fi
echo "Request $req submitted"

# Wait for transfer to complete
echo "Waiting for transfer..."
sleep 5
for i in {1..60}; do
    status=$(${TOM_DIR}/itom/p1b8pren /REQ=$req 2>&1 | grep -i "state" || true)
    if echo "$status" | grep -qi "ended\|done\|complet"; then
        echo "Transfer completed"
        break
    fi
    sleep 2
done

# Find received file in PesitWizard
RECV_FILE=$(ls -t ${PESITWIZARD_HOME}/received/test/TESTFILE_* 2>/dev/null | head -1)
if [ -n "$RECV_FILE" ] && [ -f "$RECV_FILE" ]; then
    echo "File received: $RECV_FILE"
    ls -lh "$RECV_FILE"
    RECV_MD5=$(md5sum "$RECV_FILE" | awk '{print $1}')
    echo "Received MD5: $RECV_MD5"
    
    if [ "$PUSH_ORIG_MD5" = "$RECV_MD5" ]; then
        echo "✓ MD5 checksum matches - PUSH test PASSED"
    else
        echo "✗ MD5 checksum mismatch - PUSH test FAILED"
        exit 1
    fi
else
    echo "✗ File not received - PUSH test FAILED"
    echo "Looking in ${PESITWIZARD_HOME}/received/test/"
    ls -la ${PESITWIZARD_HOME}/received/test/ 2>/dev/null || true
    exit 1
fi

echo ""
echo "=== All large file tests PASSED ==="
