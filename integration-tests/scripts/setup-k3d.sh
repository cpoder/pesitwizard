#!/bin/bash
# PeSIT Wizard - k3d Setup for WSL2/Docker environments
# Alternative to Vagrant for environments where nested virtualization doesn't work

set -e

echo "=========================================="
echo "PeSIT Wizard - k3d Setup"
echo "=========================================="

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is required but not installed."
    echo "Install Docker Desktop for Windows with WSL2 backend."
    exit 1
fi

# Check if Docker is running
if ! docker info &> /dev/null; then
    echo "ERROR: Docker daemon is not running."
    echo "Start Docker Desktop and ensure WSL2 integration is enabled."
    exit 1
fi

echo "[1/5] Installing k3d..."
if ! command -v k3d &> /dev/null; then
    curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
fi
echo "k3d version: $(k3d version | head -1)"

echo "[2/5] Installing kubectl..."
if ! command -v kubectl &> /dev/null; then
    curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
    chmod +x kubectl
    sudo mv kubectl /usr/local/bin/
fi
echo "kubectl version: $(kubectl version --client --short 2>/dev/null || kubectl version --client)"

echo "[3/5] Installing Helm..."
if ! command -v helm &> /dev/null; then
    curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
fi
echo "Helm version: $(helm version --short)"

echo "[4/5] Creating k3d cluster..."
# Delete existing cluster if present
k3d cluster delete pesitwizard 2>/dev/null || true

# Create cluster with port mappings
k3d cluster create pesitwizard \
    --servers 1 \
    --agents 1 \
    --port "30080:30080@server:0" \
    --port "30081:30081@server:0" \
    --port "30500:30500@server:0" \
    --port "30501:30501@server:0" \
    --wait

echo "[5/5] Verifying cluster..."
kubectl cluster-info
kubectl get nodes

# Create namespace
kubectl create namespace pesitwizard --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "=========================================="
echo "k3d cluster 'pesitwizard' is ready!"
echo ""
echo "Next steps:"
echo "  1. Build images: ./build-images.sh"
echo "  2. Deploy stack: ./deploy-k3d.sh"
echo "  3. Run tests:    ./tests/run-all-tests.sh"
echo "=========================================="
