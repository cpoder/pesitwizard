#!/bin/bash
# PeSIT Wizard - Build and import Docker images to k3d

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/../.."

echo "=========================================="
echo "Building Docker Images"
echo "=========================================="

cd "${PROJECT_ROOT}"

# Build Java artifacts
echo "[1/4] Building Java artifacts..."
mvn package -DskipTests -q -pl pesitwizard-server,pesitwizard-client -am

# Build server image
echo "[2/4] Building server image..."
docker build -t ghcr.io/pesitwizard/pesitwizard-server:latest pesitwizard-server/

# Build client image
echo "[3/4] Building client image..."
docker build -t ghcr.io/pesitwizard/pesitwizard-client:latest pesitwizard-client/

# Import images to k3d
echo "[4/4] Importing images to k3d cluster..."
k3d image import ghcr.io/pesitwizard/pesitwizard-server:latest -c pesitwizard
k3d image import ghcr.io/pesitwizard/pesitwizard-client:latest -c pesitwizard

echo ""
echo "=========================================="
echo "Images built and imported successfully!"
echo "=========================================="
