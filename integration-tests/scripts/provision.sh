#!/bin/bash
# PeSIT Wizard - VM Provisioning Script
# Installs k3s and all required dependencies

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
usermod -aG docker vagrant

# Install k3s
echo "[4/7] Installing k3s..."
curl -sfL https://get.k3s.io | sh -s - \
    --write-kubeconfig-mode 644 \
    --disable traefik \
    --node-name pesitwizard-node

# Wait for k3s to be ready
echo "[5/7] Waiting for k3s to be ready..."
sleep 10
kubectl wait --for=condition=Ready node/pesitwizard-node --timeout=120s

# Setup kubeconfig for vagrant user
echo "[6/7] Setting up kubeconfig..."
mkdir -p /home/vagrant/.kube
cp /etc/rancher/k3s/k3s.yaml /home/vagrant/.kube/config
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
echo "k3s version: $(k3s --version | head -1)"
echo "Helm version: $(helm version --short)"
echo "=========================================="
