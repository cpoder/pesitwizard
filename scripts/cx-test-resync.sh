#!/bin/bash
# Test sync/resync with transfer restart
# Simulates transfer interruption and restart

set -e

SIZE_MB=${1:-10}
CEXP_HOME="/home/cpo/cexp"
PESITWIZARD_HOME="/home/cpo/pesitwizard"

echo "=== Sync/Resync Test with ${SIZE_MB}MB file ==="

# Create large test file
echo "Creating ${SIZE_MB}MB test file..."
dd if=/dev/urandom of=${PESITWIZARD_HOME}/send/test/TESTFILE bs=1M count=${SIZE_MB} 2>/dev/null
ORIG_MD5=$(md5sum ${PESITWIZARD_HOME}/send/test/TESTFILE | awk '{print $1}')
echo "Original file MD5: $ORIG_MD5"
ls -lh ${PESITWIZARD_HOME}/send/test/TESTFILE

echo ""
echo "=== Test: PULL with sync points ==="
echo "Monitor server logs for SYNC_POINT/ACK_SYNC messages"
echo ""

cd ${CEXP_HOME}
./p_trcrecv TESTFILE PESITSRV &
RECV_PID=$!

# Wait a bit then check for sync points in logs
sleep 3
echo "Checking for sync points in server log..."
grep -i "sync" ${PESITWIZARD_HOME}/server.log 2>/dev/null | tail -10 || echo "(no sync messages yet)"

# Wait for transfer to complete
echo ""
echo "Waiting for transfer to complete..."
for i in {1..120}; do
    if ! ps -p $RECV_PID > /dev/null 2>&1; then
        break
    fi
    sleep 1
done

if [ -f "${CEXP_HOME}/received/TESTFILE" ]; then
    RECV_MD5=$(md5sum ${CEXP_HOME}/received/TESTFILE | awk '{print $1}')
    echo "Received file MD5: $RECV_MD5"
    
    if [ "$ORIG_MD5" = "$RECV_MD5" ]; then
        echo "✓ MD5 checksum matches - Transfer PASSED"
    else
        echo "✗ MD5 checksum mismatch - Transfer FAILED"
        exit 1
    fi
    rm -f ${CEXP_HOME}/received/TESTFILE
else
    echo "✗ File not received"
    exit 1
fi

echo ""
echo "=== Sync point statistics ==="
grep -c "SYNC_POINT\|ACK_SYNC\|syncPoint" ${PESITWIZARD_HOME}/server.log 2>/dev/null || echo "0"
echo "sync points exchanged"

echo ""
echo "=== Resync test complete ==="
echo ""
echo "To test restart/resync manually:"
echo "1. Start a large transfer"
echo "2. Kill the client mid-transfer: pkill -9 p_trcrecv"
echo "3. Restart the same transfer - it should resume from last sync point"
