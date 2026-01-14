#!/bin/bash
# ===========================================================================
# PeSIT Wizard - Setup Verification Script
# Checks that server, directories, and C:X are properly configured
# ===========================================================================

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

check_ok() {
    echo -e "${GREEN}✓${NC} $1"
}

check_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
}

echo "============================================"
echo "PeSIT Wizard Setup Verification"
echo "============================================"
echo ""

# Check Java
echo "Checking Java..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    check_ok "Java found: $JAVA_VERSION"
else
    check_fail "Java not found"
fi
echo ""

# Check Maven
echo "Checking Maven..."
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version 2>&1 | head -n 1 | awk '{print $3}')
    check_ok "Maven found: $MVN_VERSION"
else
    check_warn "Maven not found (needed for building)"
fi
echo ""

# Check if server JAR exists
echo "Checking PeSIT Wizard Server..."
SERVER_JAR="/home/cpo/pesitwizard/pesitwizard-server/target/pesitwizard-server-1.0.0-SNAPSHOT.jar"
if [ -f "$SERVER_JAR" ]; then
    SERVER_SIZE=$(du -h "$SERVER_JAR" | cut -f1)
    check_ok "Server JAR found: $SERVER_SIZE"
else
    check_warn "Server JAR not found. Run: mvn clean install"
fi
echo ""

# Check if server is running
echo "Checking if server is running..."
if curl -s http://localhost:8080/actuator/health &> /dev/null; then
    check_ok "Server is running on port 8080"

    # Check server status
    SERVER_STATUS=$(curl -s http://localhost:8080/api/servers 2>/dev/null)
    if [ -n "$SERVER_STATUS" ]; then
        echo "  Server instances:"
        echo "$SERVER_STATUS" | grep -o '"serverId":"[^"]*"' | cut -d'"' -f4 | while read sid; do
            echo "    - $sid"
        done
    fi
else
    check_warn "Server not running. Start with: ./scripts/start-server.sh"
fi
echo ""

# Check server directories
echo "Checking server directories..."
RECEIVE_DIR="/tmp/pesit-server-data/receive"
SEND_DIR="/tmp/pesit-server-data/send"

if [ -d "$RECEIVE_DIR" ]; then
    check_ok "Receive directory exists: $RECEIVE_DIR"
    FILE_COUNT=$(find "$RECEIVE_DIR" -type f | wc -l)
    echo "  Files: $FILE_COUNT"
else
    check_warn "Receive directory not found: $RECEIVE_DIR"
fi

if [ -d "$SEND_DIR" ]; then
    check_ok "Send directory exists: $SEND_DIR"
    FILE_COUNT=$(find "$SEND_DIR" -type f | wc -l)
    echo "  Files: $FILE_COUNT"
else
    check_warn "Send directory not found: $SEND_DIR"
fi
echo ""

# Check C:X
echo "Checking IBM Sterling Connect:Express..."
if [ -n "$TOM_DIR" ]; then
    check_ok "TOM_DIR is set: $TOM_DIR"

    if [ -d "$TOM_DIR" ]; then
        check_ok "C:X directory exists"

        if [ -f "$TOM_DIR/itom/p1b8preq" ]; then
            check_ok "p1b8preq command found"
        else
            check_fail "p1b8preq command not found in $TOM_DIR/itom/"
        fi

        # Check if C:X is running
        if pgrep -f "tom.*monitor" &> /dev/null; then
            check_ok "C:X monitor is running"
        else
            check_warn "C:X monitor not running. Start with: $TOM_DIR/config/start_tom.sh"
        fi
    else
        check_fail "C:X directory not found: $TOM_DIR"
    fi
else
    check_warn "TOM_DIR not set. Set with: export TOM_DIR=/home/cpo/cexp"
fi
echo ""

# Check network
echo "Checking network ports..."
if netstat -an 2>/dev/null | grep -q ":8080.*LISTEN"; then
    check_ok "Port 8080 (REST API) is listening"
else
    check_warn "Port 8080 not listening"
fi

if netstat -an 2>/dev/null | grep -q ":5000.*LISTEN"; then
    check_ok "Port 5000 (PeSIT) is listening"
else
    check_warn "Port 5000 not listening (no PeSIT server running)"
fi
echo ""

# Check database
echo "Checking database..."
DB_FILE="/home/cpo/pesitwizard/pesitwizard-server/data/pesitwizard-db.mv.db"
if [ -f "$DB_FILE" ]; then
    DB_SIZE=$(du -h "$DB_FILE" | cut -f1)
    check_ok "Database found: $DB_SIZE"
else
    check_warn "Database not initialized yet"
fi
echo ""

# Check logs
echo "Checking logs..."
LOG_FILE="/home/cpo/pesitwizard/server.log"
if [ -f "$LOG_FILE" ]; then
    LOG_SIZE=$(du -h "$LOG_FILE" | cut -f1)
    LOG_LINES=$(wc -l < "$LOG_FILE")
    check_ok "Server log found: $LOG_SIZE ($LOG_LINES lines)"

    # Check for recent errors
    if grep -q "ERROR" "$LOG_FILE" 2>/dev/null; then
        RECENT_ERRORS=$(grep "ERROR" "$LOG_FILE" | tail -5)
        check_warn "Recent errors found in log:"
        echo "$RECENT_ERRORS" | sed 's/^/  /'
    fi
else
    check_warn "Server log not found"
fi
echo ""

# Summary
echo "============================================"
echo "Summary"
echo "============================================"

if curl -s http://localhost:8080/actuator/health &> /dev/null && \
   [ -n "$TOM_DIR" ] && \
   [ -f "$TOM_DIR/itom/p1b8preq" ]; then
    echo -e "${GREEN}System is ready for testing!${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Configure C:X partner (see scripts/cx-setup-guide.md)"
    echo "  2. Run test: ./scripts/cx-test-send.sh"
elif curl -s http://localhost:8080/actuator/health &> /dev/null; then
    echo -e "${YELLOW}Server is running but C:X needs configuration${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Set TOM_DIR: export TOM_DIR=/home/cpo/cexp"
    echo "  2. Configure C:X partner (see scripts/cx-setup-guide.md)"
else
    echo -e "${YELLOW}System needs setup${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Build: mvn clean install -DskipTests"
    echo "  2. Start server: ./scripts/start-server.sh"
    echo "  3. Configure C:X partner (see scripts/cx-setup-guide.md)"
fi
echo ""
