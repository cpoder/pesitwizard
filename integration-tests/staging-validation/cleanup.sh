#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# PeSIT Wizard - Cleanup Local Staging Environment
#
# Usage:
#   ./cleanup.sh               # Full cleanup: helm uninstall, delete ns, delete cluster
#   ./cleanup.sh --keep-cluster # Only uninstall releases and delete namespace
# =============================================================================

CLUSTER_NAME="pesitwizard-staging"
NAMESPACE="pesitwizard"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

KEEP_CLUSTER=false
for arg in "$@"; do
  case $arg in
    --keep-cluster) KEEP_CLUSTER=true ;;
    --help|-h)
      echo "Usage: $0 [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --keep-cluster  Only uninstall Helm releases and delete namespace"
      echo "  -h, --help      Show this help message"
      exit 0
      ;;
  esac
done

log() { echo -e "${BLUE}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC} $*"; }

# Check if cluster exists
if ! k3d cluster list 2>/dev/null | grep -q "${CLUSTER_NAME}"; then
  log "k3d cluster '${CLUSTER_NAME}' does not exist. Nothing to clean up."
  exit 0
fi

# Ensure kubectl context
kubectl config use-context "k3d-${CLUSTER_NAME}" 2>/dev/null || true

# Uninstall Helm releases
log "Uninstalling Helm releases..."
helm uninstall pesitwizard-server -n "${NAMESPACE}" 2>/dev/null || true
helm uninstall pesitwizard-client -n "${NAMESPACE}" 2>/dev/null || true
success "Helm releases uninstalled"

# Delete k8s resources
log "Deleting Kubernetes resources..."
kubectl delete -f "$(dirname "${BASH_SOURCE[0]}")/k8s/redis-local.yaml" 2>/dev/null || true
kubectl delete -f "$(dirname "${BASH_SOURCE[0]}")/k8s/postgresql-local.yaml" 2>/dev/null || true
success "Kubernetes resources deleted"

# Delete namespace
if kubectl get namespace "${NAMESPACE}" &>/dev/null; then
  log "Deleting namespace '${NAMESPACE}'..."
  kubectl delete namespace "${NAMESPACE}" --timeout=60s 2>/dev/null || true
  success "Namespace deleted"
fi

# Delete k3d cluster
if [ "$KEEP_CLUSTER" = false ]; then
  log "Deleting k3d cluster '${CLUSTER_NAME}'..."
  k3d cluster delete "${CLUSTER_NAME}"
  success "k3d cluster deleted"
else
  log "Keeping k3d cluster '${CLUSTER_NAME}' (--keep-cluster)"
fi

echo ""
success "Cleanup complete"
