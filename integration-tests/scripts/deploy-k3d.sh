#!/bin/bash
# PeSIT Wizard - Deploy stack to k3d cluster

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/../.."

echo "=========================================="
echo "Deploying PeSIT Wizard Stack to k3d"
echo "=========================================="

# Verify cluster is running
if ! kubectl cluster-info &> /dev/null; then
    echo "ERROR: k3d cluster not accessible. Run setup-k3d.sh first."
    exit 1
fi

# Deploy PostgreSQL
echo "[1/3] Deploying PostgreSQL..."
kubectl apply -f "${SCRIPT_DIR}/../k8s/postgresql.yaml" -n pesitwizard
echo "Waiting for PostgreSQL..."
kubectl wait --for=condition=Ready pod -l app=postgresql -n pesitwizard --timeout=120s

# Deploy PeSIT Server
echo "[2/3] Deploying PeSIT Server..."
helm upgrade --install pesitwizard-server \
    "${PROJECT_ROOT}/pesitwizard-helm-charts/pesitwizard-server" \
    -n pesitwizard \
    -f "${SCRIPT_DIR}/../helm-values/server-values.yaml" \
    --wait --timeout 5m

# Deploy PeSIT Client
echo "[3/3] Deploying PeSIT Client..."
helm upgrade --install pesitwizard-client \
    "${PROJECT_ROOT}/pesitwizard-helm-charts/pesitwizard-client" \
    -n pesitwizard \
    -f "${SCRIPT_DIR}/../helm-values/client-values.yaml" \
    --wait --timeout 5m

# Show status
echo ""
echo "=========================================="
echo "Deployment Status"
echo "=========================================="
kubectl get pods -n pesitwizard
echo ""
kubectl get svc -n pesitwizard

echo ""
echo "=========================================="
echo "Stack deployed! Services available at:"
echo "  Server API: http://localhost:30080"
echo "  Client API: http://localhost:30081"
echo "  PeSIT TCP:  localhost:30500"
echo ""
echo "Run tests: ./tests/run-all-tests.sh"
echo "=========================================="
