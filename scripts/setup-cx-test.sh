#!/bin/bash
# Setup script for C:X integration testing
# Creates server, partner and virtual file via API

API_BASE="http://localhost:8080/api"

echo "Waiting for server to be ready..."
for i in {1..30}; do
    if curl -s "${API_BASE}/health" > /dev/null 2>&1; then
        echo "Server is ready!"
        break
    fi
    sleep 1
done

echo ""
echo "=== Creating PeSIT Server PESITSRV ==="
curl -s -X POST "${API_BASE}/servers" \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": "PESITSRV",
    "port": 6502,
    "bindAddress": "0.0.0.0",
    "protocolVersion": 2,
    "maxConnections": 100,
    "connectionTimeout": 30000,
    "readTimeout": 60000,
    "receiveDirectory": "./received",
    "sendDirectory": "./send",
    "maxEntitySize": 4096,
    "syncPointsEnabled": true,
    "syncIntervalKb": 32,
    "resyncEnabled": true,
    "strictPartnerCheck": false,
    "strictFileCheck": false,
    "autoStart": true
  }' | jq .

echo ""
echo "=== Starting PeSIT Server PESITSRV ==="
curl -s -X POST "${API_BASE}/servers/PESITSRV/start" | jq .

echo ""
echo "=== Creating/Updating Partner CXCLIENT ==="
curl -s -X PUT "${API_BASE}/v1/config/partners/CXCLIENT" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "CXCLIENT",
    "description": "IBM Connect:Express client for testing",
    "enabled": true,
    "password": "test123",
    "accessType": "BOTH",
    "maxConnections": 5,
    "allowedFiles": "*"
  }' | jq .

echo ""
echo "=== Creating/Updating Virtual File TESTFILE ==="
curl -s -X PUT "${API_BASE}/v1/config/files/TESTFILE" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "TESTFILE",
    "description": "Test file for C:X integration",
    "enabled": true,
    "direction": "BOTH",
    "receiveDirectory": "./received/test",
    "sendDirectory": "./send/test",
    "receiveFilenamePattern": "${virtualfile}_${timestamp}",
    "overwrite": true,
    "maxFileSize": 0,
    "fileType": 0,
    "recordLength": 1024,
    "recordFormat": 128
  }' | jq .

echo ""
echo "=== Verifying configuration ==="
echo "Server PESITSRV:"
curl -s "${API_BASE}/servers/PESITSRV" | jq .
echo ""
echo "Partner CXCLIENT:"
curl -s "${API_BASE}/v1/config/partners/CXCLIENT" | jq .
echo ""
echo "Virtual File TESTFILE:"
curl -s "${API_BASE}/v1/config/files/TESTFILE" | jq .

echo ""
echo "=== Setup complete ==="
