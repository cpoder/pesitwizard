#!/bin/bash
#
# PeSIT Wizard Installation Test
# Validates that all components can be built and installed correctly
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "[INFO] $1"; }
log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; return 1; }

ERRORS=0

echo "============================================"
echo "  Installation Tests"
echo "============================================"

# ============================================
# Test 1: Maven Build
# ============================================
log_info "Test 1: Building Java modules with Maven..."

cd "$PROJECT_ROOT"
if mvn clean install -DskipTests -q 2>/dev/null; then
    log_pass "Maven build successful"
else
    log_fail "Maven build failed"
    ((ERRORS++))
fi

# ============================================
# Test 2: Verify JAR artifacts
# ============================================
log_info "Test 2: Verifying JAR artifacts..."

MODULES=(
    "pesitwizard-pesit"
    "pesitwizard-server"
    "pesitwizard-client"
    "pesitwizard-connector-api"
    "pesitwizard-connector-local"
)

for module in "${MODULES[@]}"; do
    jar=$(ls "$PROJECT_ROOT/$module/target/${module}"-*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1)
    if [ -n "$jar" ] && [ -f "$jar" ]; then
        log_pass "Found: $jar"
    else
        log_fail "Missing: $module JAR"
        ((ERRORS++))
    fi
done

# ============================================
# Test 3: Frontend Build
# ============================================
log_info "Test 3: Building frontend with npm..."

cd "$PROJECT_ROOT/pesitwizard-client-ui"
if npm ci --silent 2>/dev/null && npm run build 2>/dev/null; then
    log_pass "Frontend build successful"
else
    log_fail "Frontend build failed"
    ((ERRORS++))
fi

# ============================================
# Test 4: Verify frontend dist
# ============================================
log_info "Test 4: Verifying frontend dist..."

if [ -d "$PROJECT_ROOT/pesitwizard-client-ui/dist" ] && \
   [ -f "$PROJECT_ROOT/pesitwizard-client-ui/dist/index.html" ]; then
    log_pass "Frontend dist directory exists"
else
    log_fail "Frontend dist missing or incomplete"
    ((ERRORS++))
fi

# ============================================
# Test 5: Docker Build (if Docker available)
# ============================================
if command -v docker &> /dev/null; then
    log_info "Test 5: Building Docker images..."
    
    cd "$PROJECT_ROOT/pesitwizard-server"
    if docker build -t pesitwizard-server:test -f Dockerfile . 2>/dev/null; then
        log_pass "Server Docker image built"
        docker rmi pesitwizard-server:test 2>/dev/null || true
    else
        log_fail "Server Docker build failed"
        ((ERRORS++))
    fi
else
    echo -e "${YELLOW}[SKIP]${NC} Docker not available, skipping Docker tests"
fi

# ============================================
# Test 6: Configuration Validation
# ============================================
log_info "Test 6: Validating configuration files..."

CONFIG_FILES=(
    "pesitwizard-server/src/main/resources/application.yml"
    "pesitwizard-server/src/main/resources/application-test.yml"
)

for cfg in "${CONFIG_FILES[@]}"; do
    if [ -f "$PROJECT_ROOT/$cfg" ]; then
        log_pass "Found: $cfg"
    else
        log_fail "Missing: $cfg"
        ((ERRORS++))
    fi
done

# ============================================
# Test 7: Helm Charts Validation
# ============================================
if command -v helm &> /dev/null; then
    log_info "Test 7: Validating Helm charts..."
    
    cd "$PROJECT_ROOT/pesitwizard-helm-charts"
    if helm lint pesitwizard-server 2>/dev/null; then
        log_pass "Helm chart validation passed"
    else
        log_fail "Helm chart validation failed"
        ((ERRORS++))
    fi
else
    echo -e "${YELLOW}[SKIP]${NC} Helm not available, skipping Helm tests"
fi

# ============================================
# Summary
# ============================================
echo ""
echo "============================================"
if [ $ERRORS -eq 0 ]; then
    echo -e "  ${GREEN}Installation Tests: ALL PASSED${NC}"
    exit 0
else
    echo -e "  ${RED}Installation Tests: $ERRORS FAILED${NC}"
    exit 1
fi
