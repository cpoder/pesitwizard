#!/bin/bash
# Vectis Server Installation Script (Standalone)
# Usage: curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/install-server.sh | bash

set -e

VECTIS_NAMESPACE="${VECTIS_NAMESPACE:-pesitwizard}"
GITHUB_REPO="pesitwizard/pesitwizard"
HELM_CHART_PATH="pesitwizard-helm-charts/pesitwizard-server"
HELM_CHART_BRANCH="${VECTIS_VERSION:-main}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo "╔═══════════════════════════════════════════╗"
echo "║        Vectis Server Installer            ║"
echo "║           (Standalone Mode)               ║"
echo "╚═══════════════════════════════════════════╝"
echo ""

# Check prerequisites
check_prereqs() {
    echo -e "${YELLOW}Checking prerequisites...${NC}"
    
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}ERROR: kubectl is not installed${NC}"
        exit 1
    fi
    
    if ! command -v helm &> /dev/null; then
        echo -e "${RED}ERROR: helm is not installed${NC}"
        exit 1
    fi
    
    if ! kubectl cluster-info &> /dev/null; then
        echo -e "${RED}ERROR: Cannot connect to Kubernetes cluster${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ All prerequisites met${NC}"
}

# Create namespace
setup_namespace() {
    echo ""
    echo -e "${YELLOW}Setting up namespace...${NC}"
    
    kubectl create namespace "$VECTIS_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
    
    echo -e "${GREEN}✓ Namespace '$VECTIS_NAMESPACE' ready${NC}"
}

# Download and install via Helm from GitHub
install_helm() {
    echo ""
    echo -e "${YELLOW}Installing Vectis Server...${NC}"
    
    # Create temp directory for chart
    CHART_TMP=$(mktemp -d)
    trap "rm -rf $CHART_TMP" EXIT
    
    # Download chart from GitHub
    echo -e "${YELLOW}Downloading Helm chart from GitHub...${NC}"
    CHART_URL="https://github.com/${GITHUB_REPO}/archive/refs/heads/${HELM_CHART_BRANCH}.tar.gz"
    
    if ! curl -fsSL "$CHART_URL" | tar -xz -C "$CHART_TMP" --strip-components=1; then
        echo -e "${RED}ERROR: Failed to download Helm chart from GitHub${NC}"
        echo "URL: $CHART_URL"
        exit 1
    fi
    
    CHART_DIR="$CHART_TMP/$HELM_CHART_PATH"
    
    if [ ! -d "$CHART_DIR" ]; then
        echo -e "${RED}ERROR: Chart directory not found: $HELM_CHART_PATH${NC}"
        exit 1
    fi
    
    # Install or upgrade using local chart
    echo -e "${YELLOW}Installing Helm chart...${NC}"
    helm upgrade --install pesitwizard-server "$CHART_DIR" \
        --namespace "$VECTIS_NAMESPACE"
    
    echo -e "${GREEN}✓ Helm release deployed${NC}"
    echo ""
    echo -e "${YELLOW}Waiting for pods to be ready...${NC}"
    
    # Wait for deployments with progress feedback
    DEPLOYMENTS=$(kubectl get deployments -n "$VECTIS_NAMESPACE" -l app.kubernetes.io/instance=pesitwizard-server -o name 2>/dev/null || true)
    
    if [ -n "$DEPLOYMENTS" ]; then
        for deploy in $DEPLOYMENTS; do
            echo -n "  Waiting for $deploy... "
            if kubectl rollout status "$deploy" -n "$VECTIS_NAMESPACE" --timeout=5m 2>/dev/null; then
                echo -e "${GREEN}ready${NC}"
            else
                echo -e "${YELLOW}timeout (check manually)${NC}"
            fi
        done
    fi
    
    echo -e "${GREEN}✓ Vectis Server installed${NC}"
}

# Show access info
show_access_info() {
    echo ""
    echo "╔═══════════════════════════════════════════╗"
    echo "║     Installation Complete!                ║"
    echo "╚═══════════════════════════════════════════╝"
    echo ""
    echo -e "${GREEN}Vectis Server is now installed.${NC}"
    echo ""
    echo "PeSIT port: 5000"
    echo "HTTP API port: 8080"
    echo ""
    echo "To expose the PeSIT port:"
    echo "  kubectl port-forward svc/pesitwizard-server 5000:5000 -n $VECTIS_NAMESPACE"
    echo ""
    echo "To check status:"
    echo "  kubectl get pods -n $VECTIS_NAMESPACE"
    echo ""
}

# Main
main() {
    check_prereqs
    setup_namespace
    install_helm
    show_access_info
}

main "$@"
