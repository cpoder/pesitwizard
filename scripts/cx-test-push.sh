#!/bin/bash
# Test PUSH: C:X sends to PesitWizard

set -e

SIZE_MB=${1:-5}
CEXP_HOME="/home/cpo/cexp"
TOM_DIR="${CEXP_HOME}"
PESITWIZARD_HOME="/home/cpo/pesitwizard"

# Source C:X environment
. ${CEXP_HOME}/profile

echo "=== PUSH Test: C:X sends ${SIZE_MB}MB file to PesitWizard ==="

# Create file to send from C:X
mkdir -p ${CEXP_HOME}/out
dd if=/dev/urandom of=${CEXP_HOME}/out/testfile.txt bs=1M count=${SIZE_MB} 2>/dev/null
ls -lh ${CEXP_HOME}/out/testfile.txt
ORIG_MD5=$(md5sum ${CEXP_HOME}/out/testfile.txt | awk '{print $1}')
echo "Original MD5: $ORIG_MD5"

echo ""
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
    
    if [ "$ORIG_MD5" = "$RECV_MD5" ]; then
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
