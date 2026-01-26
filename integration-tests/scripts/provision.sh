#!/bin/bash
# PeSIT Wizard - VM Provisioning Script
# Installs k3d and all required dependencies

set -e

echo "=========================================="
echo "PeSIT Wizard Integration Test Environment"
echo "=========================================="

# Update system
echo "[1/7] Updating system packages..."
apt-get update -qq
apt-get upgrade -y -qq

# Install dependencies
echo "[2/7] Installing dependencies..."
apt-get install -y -qq \
    curl \
    wget \
    git \
    jq \
    httpie \
    postgresql-client \
    openjdk-21-jdk-headless \
    maven \
    docker.io \
    apt-transport-https \
    ca-certificates \
    gnupg

# Configure Docker
echo "[3/7] Configuring Docker..."
systemctl enable docker
systemctl start docker
usermod -aG docker vagrant || true

# Install k3d
echo "[4/7] Installing k3d..."
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash

# Create k3d cluster
echo "[5/7] Creating k3d cluster..."
k3d cluster create pesitwizard \
    --agents 1 \
    --port "30080:30080@server:0" \
    --port "30081:30081@server:0" \
    --port "30500:30500@server:0" \
    --wait

# Setup kubeconfig
echo "[6/7] Setting up kubeconfig..."
mkdir -p /home/vagrant/.kube
k3d kubeconfig get pesitwizard > /home/vagrant/.kube/config
chown -R vagrant:vagrant /home/vagrant/.kube
chmod 600 /home/vagrant/.kube/config

# Install Helm
echo "[7/7] Installing Helm..."
curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Add bash completion
kubectl completion bash > /etc/bash_completion.d/kubectl
helm completion bash > /etc/bash_completion.d/helm

# Create namespaces
kubectl create namespace pesitwizard --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "=========================================="
echo "Provisioning complete!"
echo "k3d version: $(k3d --version)"
echo "Helm version: $(helm version --short)"
echo "=========================================="
