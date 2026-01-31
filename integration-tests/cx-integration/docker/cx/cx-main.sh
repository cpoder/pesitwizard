#!/bin/bash
# Connect:Express main startup script (runs as cxuser)

set -e

# Source CX profile
. $TOM_DIR/profile

echo "Configuring Connect:Express for PeSIT Wizard integration tests..."

# Run partner/file setup (tom_prm can be run with monitor stopped)
/setup-partners.sh

# Start the CX monitor
echo "Starting Connect:Express monitor..."
$TOM_DIR/config/start_tom.sh

# Wait for monitor to start (with retries)
echo "Waiting for Connect:Express monitor to start..."
for i in $(seq 1 30); do
    if pgrep -f "tom_mon" > /dev/null; then
        echo "Connect:Express monitor started successfully"
        break
    fi
    sleep 1
done

# Final check
if ! pgrep -f "tom_mon" > /dev/null; then
    echo "ERROR: Connect:Express monitor failed to start"
    cat $TOM_DIR/config/LOG 2>/dev/null || true
    exit 1
fi

# Run automatic transfer tests if enabled
if [ "${RUN_TRANSFER_TESTS:-false}" = "true" ]; then
    echo "Running automatic transfer tests..."

    # First, configure the PW server via its REST API
    echo "Configuring PW server via REST API..."
    API_KEY="integration-test-key"
    PW_API="http://pw-server:8080"

    # Wait for PW API to be ready
    echo "Waiting for PW API to be ready..."
    for i in $(seq 1 30); do
        if curl -s "$PW_API/actuator/health" | grep -q "UP"; then
            echo "PW API is ready."
            break
        fi
        sleep 1
    done

    # Create partners
    echo "Creating partner PWSRV01..."
    curl -s -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
        "$PW_API/api/v1/config/partners" \
        -d '{"id":"PWSRV01","description":"CX client","password":"","enabled":true,"accessType":"BOTH","maxConnections":10}' || true

    echo "Creating partner CETOM1..."
    curl -s -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
        "$PW_API/api/v1/config/partners" \
        -d '{"id":"CETOM1","description":"CX server","password":"","enabled":true,"accessType":"BOTH","maxConnections":10}' || true

    # Create virtual files
    # Virtual file for receiving from CX (PW as server)
    echo "Creating virtual file PWSEND (receive from CX)..."
    curl -s -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
        "$PW_API/api/v1/config/files" \
        -d '{"id":"PWSEND","description":"Receive from CX","enabled":true,"direction":"RECEIVE","receiveDirectory":"/data/received","receiveFilenamePattern":"from_cx_${transferId}","overwrite":false,"recordLength":4096,"recordFormat":0}' || true

    # Virtual file for sending to CX (PW as client)
    echo "Creating virtual file PWRECV (send to CX)..."
    curl -s -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
        "$PW_API/api/v1/config/files" \
        -d '{"id":"PWRECV","description":"Send to CX","enabled":true,"direction":"SEND","sendDirectory":"/data/send","recordLength":4096,"recordFormat":0}' || true

    # Create remote partner for CX (so PW can connect to CX as client)
    echo "Creating remote partner CXSERVER..."
    curl -s -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
        "$PW_API/api/v1/config/remote-partners" \
        -d '{"id":"CXSERVER","description":"Connect:Express server","host":"cx-server","port":5000,"localPartnerId":"PWSRV01","remotePartnerId":"CETOM1","enabled":true,"maxConnections":5}' || true

    # Create and start PW server instance
    echo "Creating PW server instance..."
    curl -s -X POST -H "X-API-Key: $API_KEY" -H "Content-Type: application/json" \
        "$PW_API/api/servers" \
        -d '{"serverId":"PWSERVER","port":5001,"receiveDirectory":"/data/received","sendDirectory":"/data/send","maxEntitySize":32768,"syncPointsEnabled":true,"syncIntervalKb":256,"autoStart":true}' || true

    sleep 3

    # Start the server if not auto-started
    echo "Starting PW server..."
    curl -s -X POST -H "X-API-Key: $API_KEY" "$PW_API/api/servers/PWSERVER/start" || true

    # Wait for PW server PeSIT port to be ready
    echo "Waiting for PW PeSIT server to be ready..."
    for i in $(seq 1 30); do
        if nc -z pw-server 5001 2>/dev/null; then
            echo "PW PeSIT server is ready."
            break
        fi
        sleep 1
    done

    # Create and send small test file
    echo "Test 1: Small file transfer..."
    echo "Hello from Connect:Express at $(date)" > /tmp/cx-send/small-test.txt
    /run-transfer.sh /tmp/cx-send/small-test.txt PWSEND || echo "Small file transfer failed"

    # Create and send 5MB test file
    echo "Test 2: CX->PW Medium file transfer (5MB)..."
    dd if=/dev/urandom of=/tmp/cx-send/medium-test.dat bs=1M count=5 2>/dev/null
    md5sum /tmp/cx-send/medium-test.dat > /tmp/cx-send/medium-test.md5
    /run-transfer.sh /tmp/cx-send/medium-test.dat PWSEND || echo "Medium file transfer failed"

    echo ""
    echo "=== CX -> PW Server Tests Completed ==="
    echo "(PW Client -> CX tests will run from the pw-client container)"
fi

# Keep container running and tail the log
echo "Connect:Express is running. Tailing log..."
exec tail -f $TOM_DIR/config/LOG 2>/dev/null || sleep infinity
