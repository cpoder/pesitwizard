#!/bin/bash
# Build and run CX integration tests in Docker containers

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
CX_INSTALL_DIR="${CX_INSTALL_DIR:-/home/cpo/pesit/CX}"

echo "=========================================="
echo "  CX Integration Test Build & Run"
echo "=========================================="
echo ""
echo "Script dir: $SCRIPT_DIR"
echo "Project root: $PROJECT_ROOT"
echo "CX install dir: $CX_INSTALL_DIR"
echo ""

# Check if CX installation files exist
if [ ! -f "$CX_INSTALL_DIR/install.sh" ]; then
    echo "ERROR: Connect:Express installation files not found at $CX_INSTALL_DIR"
    echo "Please ensure the CX installation files are available."
    exit 1
fi

# Step 1: Build PW Server JAR
echo "1. Building PeSIT Wizard Server..."
cd "$PROJECT_ROOT"
mvn clean package -DskipTests -pl pesitwizard-server -am -q
echo "   Done."

# Step 2: Copy JAR to Docker context
echo "2. Copying server JAR to Docker context..."
JAR_FILE=$(ls "$PROJECT_ROOT/pesitwizard-server/target/pesitwizard-server-"*-exec.jar 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
    JAR_FILE=$(ls "$PROJECT_ROOT/pesitwizard-server/target/pesitwizard-server-"*.jar 2>/dev/null | head -1)
fi
if [ -z "$JAR_FILE" ]; then
    echo "ERROR: Could not find PW server JAR file"
    exit 1
fi
cp "$JAR_FILE" "$SCRIPT_DIR/pw-server/pesitwizard-server.jar"
echo "   Done."

# Step 3: Copy CX installation files to Docker context
echo "3. Copying Connect:Express installation files..."
mkdir -p "$SCRIPT_DIR/cx/CX"
cp -r "$CX_INSTALL_DIR"/* "$SCRIPT_DIR/cx/CX/"
echo "   Done."

# Step 4: Build Docker images
echo "4. Building Docker images..."
cd "$SCRIPT_DIR"
docker compose build --no-cache
echo "   Done."

# Step 5: Run tests
echo "5. Running integration tests..."
docker compose up --abort-on-container-exit --exit-code-from test-runner

# Capture exit code
EXIT_CODE=$?

# Step 6: Cleanup
echo ""
echo "6. Cleaning up..."
docker compose down -v
rm -rf "$SCRIPT_DIR/cx/CX"
rm -f "$SCRIPT_DIR/pw-server/pesitwizard-server.jar"
echo "   Done."

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo "=========================================="
    echo "  All tests PASSED"
    echo "=========================================="
else
    echo "=========================================="
    echo "  Some tests FAILED"
    echo "=========================================="
fi

exit $EXIT_CODE
