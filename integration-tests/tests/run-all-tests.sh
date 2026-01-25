#!/bin/bash
# PeSIT Wizard - Integration Test Runner
# Runs all API integration tests and generates report

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_DIR="${SCRIPT_DIR}/../reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="${REPORT_DIR}/integration-test-${TIMESTAMP}.md"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

# Server endpoints
SERVER_API="${SERVER_API:-http://localhost:30080}"
CLIENT_API="${CLIENT_API:-http://localhost:30081}"
PESIT_HOST="${PESIT_HOST:-localhost}"
PESIT_PORT="${PESIT_PORT:-30500}"

mkdir -p "${REPORT_DIR}"

# Initialize report
cat > "${REPORT_FILE}" << EOF
# PeSIT Wizard Integration Test Report

**Date:** $(date)
**Environment:** k3s Integration Test

## Test Configuration
- Server API: ${SERVER_API}
- Client API: ${CLIENT_API}
- PeSIT Host: ${PESIT_HOST}:${PESIT_PORT}

---

## Test Results

EOF

log_test() {
    local status=$1
    local test_name=$2
    local details=$3
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    case $status in
        "PASS")
            PASSED_TESTS=$((PASSED_TESTS + 1))
            echo -e "${GREEN}✓${NC} ${test_name}"
            echo "- ✅ **PASS** - ${test_name}" >> "${REPORT_FILE}"
            ;;
        "FAIL")
            FAILED_TESTS=$((FAILED_TESTS + 1))
            echo -e "${RED}✗${NC} ${test_name}: ${details}"
            echo "- ❌ **FAIL** - ${test_name}" >> "${REPORT_FILE}"
            echo "  - Error: ${details}" >> "${REPORT_FILE}"
            ;;
        "SKIP")
            SKIPPED_TESTS=$((SKIPPED_TESTS + 1))
            echo -e "${YELLOW}○${NC} ${test_name} (skipped: ${details})"
            echo "- ⏭️ **SKIP** - ${test_name} (${details})" >> "${REPORT_FILE}"
            ;;
    esac
}

wait_for_service() {
    local url=$1
    local max_attempts=${2:-30}
    local attempt=0
    
    echo "Waiting for service at ${url}..."
    while [ $attempt -lt $max_attempts ]; do
        if curl -sf "${url}/actuator/health" > /dev/null 2>&1; then
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 2
    done
    return 1
}

echo "=========================================="
echo "PeSIT Wizard Integration Tests"
echo "=========================================="
echo ""

# Wait for services
echo "Checking service availability..."
if ! wait_for_service "${SERVER_API}"; then
    echo -e "${RED}Server not available at ${SERVER_API}${NC}"
    exit 1
fi

if ! wait_for_service "${CLIENT_API}"; then
    echo -e "${RED}Client not available at ${CLIENT_API}${NC}"
    exit 1
fi

echo ""
echo "=========================================="
echo "Running Tests"
echo "=========================================="
echo ""

# Run test suites
echo "### Server API Tests" >> "${REPORT_FILE}"
source "${SCRIPT_DIR}/test-server-api.sh"

echo "" >> "${REPORT_FILE}"
echo "### Client API Tests" >> "${REPORT_FILE}"
source "${SCRIPT_DIR}/test-client-api.sh"

echo "" >> "${REPORT_FILE}"
echo "### Transfer Tests" >> "${REPORT_FILE}"
source "${SCRIPT_DIR}/test-transfers.sh"

echo "" >> "${REPORT_FILE}"
echo "### Certificate Tests" >> "${REPORT_FILE}"
source "${SCRIPT_DIR}/test-certificates.sh"

echo "" >> "${REPORT_FILE}"
echo "### Partner Management Tests" >> "${REPORT_FILE}"
source "${SCRIPT_DIR}/test-partners.sh"

echo "" >> "${REPORT_FILE}"
echo "### Audit & Monitoring Tests" >> "${REPORT_FILE}"
source "${SCRIPT_DIR}/test-audit.sh"

# Summary
echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo ""
echo -e "Total:   ${TOTAL_TESTS}"
echo -e "Passed:  ${GREEN}${PASSED_TESTS}${NC}"
echo -e "Failed:  ${RED}${FAILED_TESTS}${NC}"
echo -e "Skipped: ${YELLOW}${SKIPPED_TESTS}${NC}"

# Add summary to report
cat >> "${REPORT_FILE}" << EOF

---

## Summary

| Metric | Count |
|--------|-------|
| Total Tests | ${TOTAL_TESTS} |
| Passed | ${PASSED_TESTS} |
| Failed | ${FAILED_TESTS} |
| Skipped | ${SKIPPED_TESTS} |
| **Success Rate** | **$(echo "scale=1; ${PASSED_TESTS}*100/${TOTAL_TESTS}" | bc)%** |

EOF

echo ""
echo "Report saved to: ${REPORT_FILE}"

# Exit with error if any tests failed
if [ $FAILED_TESTS -gt 0 ]; then
    exit 1
fi
