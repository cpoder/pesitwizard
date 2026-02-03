#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# PeSIT Wizard - Local Staging Validation with k3d
#
# Deploys the full staging-like environment locally using k3d to validate
# Helm chart templates before deploying to AWS EKS.
#
# Usage:
#   ./validate-staging-local.sh              # Full run: build, deploy, test
#   ./validate-staging-local.sh --skip-build # Skip Docker build (reuse images)
#   ./validate-staging-local.sh --test-only  # Only run smoke tests
#   ./validate-staging-local.sh --cleanup    # Tear down everything
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CLUSTER_NAME="pesitwizard-staging"
NAMESPACE="pesitwizard"
HELM_CHARTS_DIR="${PROJECT_ROOT}/pesitwizard-helm-charts"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse arguments
SKIP_BUILD=false
TEST_ONLY=false
CLEANUP_ONLY=false

for arg in "$@"; do
  case $arg in
    --skip-build)  SKIP_BUILD=true ;;
    --test-only)   TEST_ONLY=true ;;
    --cleanup)     CLEANUP_ONLY=true ;;
    --help|-h)
      echo "Usage: $0 [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --skip-build   Skip Docker image builds (reuse existing)"
      echo "  --test-only    Only run smoke tests (cluster must exist)"
      echo "  --cleanup      Tear down everything"
      echo "  -h, --help     Show this help message"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg"
      exit 1
      ;;
  esac
done

log() { echo -e "${BLUE}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC} $*"; }

# ── Phase 0: Cleanup only ──────────────────────────────────────────────────
if [ "$CLEANUP_ONLY" = true ]; then
  log "Running cleanup..."
  "${SCRIPT_DIR}/cleanup.sh"
  exit 0
fi

# ── Phase 0b: Test only ────────────────────────────────────────────────────
if [ "$TEST_ONLY" = true ]; then
  log "Running smoke tests only..."
  "${SCRIPT_DIR}/smoke-tests.sh"
  exit $?
fi

# ── Phase 1: Check prerequisites ───────────────────────────────────────────
log "Checking prerequisites..."

MISSING=()
for cmd in docker k3d kubectl helm; do
  if ! command -v "$cmd" &>/dev/null; then
    MISSING+=("$cmd")
  fi
done

if [ ${#MISSING[@]} -gt 0 ]; then
  error "Missing required tools: ${MISSING[*]}"
  echo "Install them before running this script."
  exit 1
fi

if ! docker info &>/dev/null; then
  error "Docker daemon is not running"
  exit 1
fi

success "All prerequisites met"

# ── Phase 2: Create k3d cluster ────────────────────────────────────────────
if k3d cluster list 2>/dev/null | grep -q "${CLUSTER_NAME}"; then
  log "k3d cluster '${CLUSTER_NAME}' already exists"
else
  log "Creating k3d cluster '${CLUSTER_NAME}' (1 server + 2 agents)..."
  k3d cluster create "${CLUSTER_NAME}" \
    --servers 1 \
    --agents 2 \
    --port "8080:80@loadbalancer" \
    --port "5000:5000@loadbalancer" \
    --wait
  success "k3d cluster created"
fi

# Ensure kubectl context is set
kubectl config use-context "k3d-${CLUSTER_NAME}"
log "Waiting for cluster nodes to be ready..."
kubectl wait --for=condition=Ready nodes --all --timeout=120s
success "All nodes ready"

# ── Phase 3: Build and import Docker images ────────────────────────────────
if [ "$SKIP_BUILD" = false ]; then
  log "Building Docker images..."

  log "Building pesitwizard-server..."
  docker build -t pesitwizard-server:latest "${PROJECT_ROOT}/pesitwizard-server"

  log "Building pesitwizard-client..."
  docker build -t pesitwizard-client:latest "${PROJECT_ROOT}/pesitwizard-client"

  log "Building pesitwizard-client-ui..."
  docker build -t pesitwizard-client-ui:latest "${PROJECT_ROOT}/pesitwizard-client-ui"

  success "Docker images built"
else
  log "Skipping Docker build (--skip-build)"
fi

log "Importing images into k3d cluster..."
k3d image import pesitwizard-server:latest -c "${CLUSTER_NAME}"
k3d image import pesitwizard-client:latest -c "${CLUSTER_NAME}"
k3d image import pesitwizard-client-ui:latest -c "${CLUSTER_NAME}"
success "Images imported into k3d"

# ── Phase 4: Create namespace ──────────────────────────────────────────────
if kubectl get namespace "${NAMESPACE}" &>/dev/null; then
  log "Namespace '${NAMESPACE}' already exists"
else
  log "Creating namespace '${NAMESPACE}'..."
  kubectl create namespace "${NAMESPACE}"
  success "Namespace created"
fi

# ── Phase 5: Deploy PostgreSQL ─────────────────────────────────────────────
log "Deploying PostgreSQL..."
kubectl apply -f "${SCRIPT_DIR}/k8s/postgresql-local.yaml"

log "Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=Available \
  deployment/postgresql -n "${NAMESPACE}" --timeout=120s
success "PostgreSQL is ready"

# ── Phase 6: Deploy Redis ──────────────────────────────────────────────────
log "Deploying Redis..."
kubectl apply -f "${SCRIPT_DIR}/k8s/redis-local.yaml"

log "Waiting for Redis to be ready..."
kubectl wait --for=condition=Available \
  deployment/redis -n "${NAMESPACE}" --timeout=120s
success "Redis is ready"

# ── Phase 7: Deploy PeSIT Wizard Server (3 replicas) ──────────────────────
log "Deploying PeSIT Wizard Server via Helm..."
helm upgrade --install pesitwizard-server \
  "${HELM_CHARTS_DIR}/pesitwizard-server" \
  -f "${SCRIPT_DIR}/values-local.yaml" \
  -n "${NAMESPACE}" \
  --wait \
  --timeout 300s
success "PeSIT Wizard Server deployed"

# ── Phase 8: Deploy PeSIT Wizard Client ────────────────────────────────────
log "Deploying PeSIT Wizard Client via Helm..."
helm upgrade --install pesitwizard-client \
  "${HELM_CHARTS_DIR}/pesitwizard-client" \
  -f "${SCRIPT_DIR}/client-values-local.yaml" \
  -n "${NAMESPACE}" \
  --wait \
  --timeout 300s
success "PeSIT Wizard Client deployed"

# ── Phase 9: Wait for all pods ─────────────────────────────────────────────
log "Waiting for all pods in '${NAMESPACE}' to be ready..."
kubectl wait --for=condition=Ready \
  pods --all -n "${NAMESPACE}" --timeout=300s
success "All pods are ready"

# ── Phase 10: Run smoke tests ──────────────────────────────────────────────
log "Running smoke tests..."
echo ""
"${SCRIPT_DIR}/smoke-tests.sh"
SMOKE_EXIT=$?

# ── Phase 11: Print summary ───────────────────────────────────────────────
echo ""
echo "============================================="
if [ $SMOKE_EXIT -eq 0 ]; then
  echo -e "${GREEN} LOCAL STAGING VALIDATION PASSED ${NC}"
else
  echo -e "${RED} LOCAL STAGING VALIDATION FAILED ${NC}"
fi
echo "============================================="
echo ""
echo "Useful commands:"
echo "  kubectl get pods -n ${NAMESPACE}"
echo "  kubectl logs -n ${NAMESPACE} -l app.kubernetes.io/name=pesitwizard-server --tail=50"
echo "  kubectl port-forward -n ${NAMESPACE} svc/pesitwizard-server 8080:8080"
echo ""
echo "Cleanup:"
echo "  ${SCRIPT_DIR}/cleanup.sh"
echo ""

exit $SMOKE_EXIT
