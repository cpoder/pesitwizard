#!/bin/bash
# PeSIT Wizard - Deployment Script
# Deploys the full stack to k3d for integration testing

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
cd "$PROJECT_DIR"

echo "=========================================="
echo "Deploying PeSIT Wizard Stack"
echo "=========================================="

# Build images locally (or pull from registry)
echo "[1/6] Building/Pulling Docker images..."

# If images are not available, build them
if ! docker images | grep -q pesitwizard-server; then
    echo "Building server image..."
    cd pesitwizard-server
    mvn package -DskipTests -q
    docker build -t ghcr.io/pesitwizard/pesitwizard-server:latest .
    cd ..
fi

if ! docker images | grep -q pesitwizard-client; then
    echo "Building client image..."
    cd pesitwizard-client
    mvn package -DskipTests -q
    docker build -t ghcr.io/pesitwizard/pesitwizard-client:latest .
    cd ..
fi

# Import images to k3d
echo "[2/6] Importing images to k3d..."
k3d image import ghcr.io/pesitwizard/pesitwizard-server:latest -c pesitwizard || true
k3d image import ghcr.io/pesitwizard/pesitwizard-client:latest -c pesitwizard || true

# Deploy PostgreSQL
echo "[3/6] Deploying PostgreSQL..."
kubectl apply -f "$PROJECT_DIR/integration-tests/k8s/postgresql.yaml" -n pesitwizard
kubectl wait --for=condition=Ready pod -l app=postgresql -n pesitwizard --timeout=120s

# Deploy PeSIT Server
echo "[4/6] Deploying PeSIT Server..."
helm upgrade --install pesitwizard-server \
    "$PROJECT_DIR/pesitwizard-helm-charts/pesitwizard-server" \
    -n pesitwizard \
    -f "$PROJECT_DIR/integration-tests/helm-values/server-values.yaml" \
    --wait --timeout 5m

# Deploy PeSIT Client
echo "[5/6] Deploying PeSIT Client..."
helm upgrade --install pesitwizard-client \
    "$PROJECT_DIR/pesitwizard-helm-charts/pesitwizard-client" \
    -n pesitwizard \
    -f "$PROJECT_DIR/integration-tests/helm-values/client-values.yaml" \
    --wait --timeout 5m

# Verify deployments
echo "[6/6] Verifying deployments..."
kubectl get pods -n pesitwizard
kubectl get svc -n pesitwizard

echo ""
echo "=========================================="
echo "Deployment complete!"
echo ""
echo "Services available:"
echo "  Server API: http://localhost:30080"
echo "  Client API: http://localhost:30081"
echo "  PeSIT Port: localhost:30500"
echo "=========================================="
