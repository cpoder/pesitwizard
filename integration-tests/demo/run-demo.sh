#!/bin/bash
# PeSIT Wizard - Demo Video Recorder
#
# This script records a demonstration video of the PeSIT Wizard client UI
# with the full k3d infrastructure (PostgreSQL + Server + Client).
#
# Prerequisites:
# - Docker Desktop with WSL2 backend
# - k3d installed
# - Node.js 18+
#
# Usage:
#   ./run-demo.sh           # Full setup + record video
#   ./run-demo.sh --headed  # Full setup + watch live
#   ./run-demo.sh --skip-infra  # Skip k3d setup (already running)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTEGRATION_DIR="${SCRIPT_DIR}/.."
PROJECT_ROOT="${INTEGRATION_DIR}/.."

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
SERVER_API="${SERVER_API:-http://localhost:30080}"
CLIENT_API="${CLIENT_API:-http://localhost:30081}"
CLIENT_UI_URL="${CLIENT_UI_URL:-http://localhost:3001}"

SKIP_INFRA=false
HEADED=false
UI_PID=""

# Parse arguments
for arg in "$@"; do
    case $arg in
        --skip-infra)
            SKIP_INFRA=true
            ;;
        --headed)
            HEADED=true
            ;;
    esac
done

cleanup() {
    echo ""
    echo -e "${YELLOW}Cleaning up...${NC}"
    if [ -n "$UI_PID" ] && kill -0 "$UI_PID" 2>/dev/null; then
        kill "$UI_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

echo ""
echo "=========================================="
echo -e "${BLUE}   PeSIT Wizard - Demo Video Recorder${NC}"
echo "=========================================="
echo ""

# ========================================
# 1. Check/Setup k3d Infrastructure
# ========================================

if [ "$SKIP_INFRA" = false ]; then
    echo -e "${BLUE}[1/5] Checking k3d infrastructure...${NC}"

    # Check if k3d cluster exists and is running
    if ! k3d cluster list 2>/dev/null | grep -q "pesitwizard"; then
        echo -e "${YELLOW}k3d cluster not found. Setting up...${NC}"
        cd "${INTEGRATION_DIR}/scripts"
        ./setup-k3d.sh
        ./build-images.sh
        ./deploy-k3d.sh
        cd "$SCRIPT_DIR"
    else
        # Check if pods are running
        if ! kubectl get pods -n pesitwizard 2>/dev/null | grep -q "Running"; then
            echo -e "${YELLOW}Pods not running. Deploying stack...${NC}"
            cd "${INTEGRATION_DIR}/scripts"
            ./deploy-k3d.sh
            cd "$SCRIPT_DIR"
        else
            echo -e "${GREEN}k3d infrastructure is running${NC}"
        fi
    fi

    # Wait for services to be ready
    echo "Waiting for services..."
    for i in {1..30}; do
        if curl -sf "${SERVER_API}/actuator/health" >/dev/null 2>&1; then
            echo -e "${GREEN}  Server API ready${NC}"
            break
        fi
        sleep 2
    done

    for i in {1..30}; do
        if curl -sf "${CLIENT_API}/actuator/health" >/dev/null 2>&1; then
            echo -e "${GREEN}  Client API ready${NC}"
            break
        fi
        sleep 2
    done
else
    echo -e "${BLUE}[1/5] Skipping infrastructure setup (--skip-infra)${NC}"
fi

# ========================================
# 2. Setup PeSIT Server Configuration
# ========================================

echo ""
echo -e "${BLUE}[2/5] Configuring PeSIT Server for demo...${NC}"

# Check if server API is accessible
if ! curl -sf "${SERVER_API}/actuator/health" >/dev/null 2>&1; then
    echo -e "${RED}Server API not accessible at ${SERVER_API}${NC}"
    echo "Make sure the k3d cluster is running with pesitwizard-server deployed"
    exit 1
fi

# Create partner DEMO with password demo123 (partner ID max 8 chars)
echo "  Creating partner DEMO..."
curl -sf -X POST "${SERVER_API}/api/v1/config/partners" \
    -H "Content-Type: application/json" \
    -d '{
        "id": "DEMO",
        "description": "Demo Client Partner",
        "password": "demo123",
        "enabled": true,
        "accessType": "BOTH",
        "maxConnections": 10
    }' >/dev/null 2>&1 || echo "    (partner may already exist)"

# Create virtual file DEMOFILE
echo "  Creating virtual file DEMOFILE..."
curl -sf -X POST "${SERVER_API}/api/v1/config/files" \
    -H "Content-Type: application/json" \
    -d '{
        "id": "DEMOFILE",
        "description": "Demo virtual file for UI demo",
        "enabled": true,
        "direction": "BOTH",
        "receiveDirectory": "/data/received",
        "sendDirectory": "/data/send",
        "receiveFilenamePattern": "${filename}_${timestamp}",
        "overwrite": true,
        "recordLength": 1024,
        "recordFormat": 128
    }' >/dev/null 2>&1 || echo "    (virtual file may already exist)"

# Create test directories on server
echo "  Creating directories on server..."
kubectl exec -n pesitwizard pesitwizard-server-api-0 -- mkdir -p /data/send /data/received 2>/dev/null || true
kubectl exec -n pesitwizard pesitwizard-server-api-1 -- mkdir -p /data/send /data/received 2>/dev/null || true

# Create test file in client pod for SEND transfer
echo "  Creating test file in client for SEND transfer..."
kubectl exec -n pesitwizard deployment/pesitwizard-client-api -- sh -c "echo 'PeSIT Wizard Demo Report
================================
Generated: $(date)

This is a demonstration file for PeSIT Wizard.
It will be transferred using the PeSIT protocol.

The PeSIT protocol is used by French banks
for secure file transfers.

Features demonstrated:
- Partner authentication
- Virtual file mapping
- Checkpoint/resume support
- Real-time progress tracking
' > /tmp/demo-report.txt" 2>/dev/null || true

# Get the PeSIT server ID and ensure it's started
echo "  Checking PeSIT server status..."
SERVER_ID=$(curl -sf "${SERVER_API}/api/servers" 2>/dev/null | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2 || echo "")
if [ -n "$SERVER_ID" ]; then
    curl -sf -X POST "${SERVER_API}/api/servers/${SERVER_ID}/start" >/dev/null 2>&1 || true
    echo -e "${GREEN}  PeSIT server started (ID: $SERVER_ID)${NC}"
fi

# Verify configuration
echo "  Verifying configuration..."
PARTNER_OK=$(curl -sf "${SERVER_API}/api/v1/config/partners" 2>/dev/null | grep -c "DEMO" || echo "0")
if [ "$PARTNER_OK" -gt 0 ]; then
    echo -e "${GREEN}  Configuration complete:${NC}"
    echo "    - Partner: DEMO (password: demo123)"
    echo "    - Virtual File: DEMOFILE -> /data/received"
    echo "    - Test file: /data/send/demo-report.txt"
else
    echo -e "${YELLOW}  Warning: Partner DEMO not found${NC}"
fi

# ========================================
# 3. Start Client UI
# ========================================

echo ""
echo -e "${BLUE}[3/5] Starting Client UI...${NC}"

cd "${PROJECT_ROOT}/pesitwizard-client-ui"

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "  Installing UI dependencies..."
    npm install --silent
fi

# Start UI with proxy to k3d client API
echo "  Starting UI dev server (connected to ${CLIENT_API})..."
VITE_API_URL="${CLIENT_API}" npm run dev -- --port 3001 > /tmp/vite-ui.log 2>&1 &
UI_PID=$!

# Wait for UI to be ready
echo "  Waiting for UI..."
for i in {1..30}; do
    if curl -sf "${CLIENT_UI_URL}" >/dev/null 2>&1; then
        echo -e "${GREEN}  Client UI ready at ${CLIENT_UI_URL}${NC}"
        break
    fi
    sleep 1
done

cd "$SCRIPT_DIR"

# ========================================
# 4. Install Playwright
# ========================================

echo ""
echo -e "${BLUE}[4/5] Setting up Playwright...${NC}"

if [ ! -d "node_modules" ]; then
    echo "  Installing dependencies..."
    npm install --silent
fi

if ! npx playwright --version >/dev/null 2>&1; then
    echo "  Installing Playwright browsers..."
    npx playwright install chromium
fi

echo -e "${GREEN}  Playwright ready${NC}"

# ========================================
# 5. Record Demo
# ========================================

echo ""
echo -e "${BLUE}[5/5] Recording demo video...${NC}"
echo ""
echo "Demo scenario:"
echo "  1. Add PeSIT Server (localhost:30500)"
echo "  2. Execute file transfer (DEMOCLIENT -> DEMOFILE)"
echo "  3. Add transfer to favorites"
echo "  4. Create business calendar"
echo "  5. Schedule favorite transfer"
echo ""

mkdir -p recordings

if [ "$HEADED" = true ]; then
    echo "Running demo in headed mode (visible browser)..."
    CLIENT_UI_URL="${CLIENT_UI_URL}" SERVER_API="${SERVER_API}" CLIENT_API="${CLIENT_API}" \
        npx playwright test demo.spec.ts --headed
else
    echo "Recording demo video (headless)..."
    CLIENT_UI_URL="${CLIENT_UI_URL}" SERVER_API="${SERVER_API}" CLIENT_API="${CLIENT_API}" \
        npx playwright test demo.spec.ts
fi

# Find the recorded video
VIDEO=$(find recordings -name "*.webm" -type f -mmin -5 2>/dev/null | head -1)

if [ -n "$VIDEO" ]; then
    echo ""
    echo "=========================================="
    echo -e "${GREEN}Demo video recorded successfully!${NC}"
    echo "=========================================="
    echo ""
    echo "Video location: $VIDEO"
    echo ""

    # Convert to MP4 if ffmpeg is available
    if command -v ffmpeg &> /dev/null; then
        MP4_FILE="${VIDEO%.webm}.mp4"
        echo "Converting to MP4..."
        ffmpeg -y -i "$VIDEO" -c:v libx264 -preset fast -crf 22 "$MP4_FILE" 2>/dev/null
        echo -e "${GREEN}MP4 video: $MP4_FILE${NC}"
    else
        echo "Tip: Install ffmpeg to convert to MP4:"
        echo "  sudo apt install ffmpeg"
    fi
else
    echo ""
    echo -e "${YELLOW}No video file found. Check if the demo completed successfully.${NC}"
fi

echo ""
echo "Infrastructure remains running. To clean up:"
echo "  cd ../scripts && ./cleanup-k3d.sh"
