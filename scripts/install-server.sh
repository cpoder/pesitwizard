#!/bin/bash
# PeSIT Wizard Server Installation Script (Standalone)
# Usage: curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/install-server.sh | bash

set -e

PESITWIZARD_NAMESPACE="${PESITWIZARD_NAMESPACE:-pesitwizard}"
PESITWIZARD_VERSION="${PESITWIZARD_VERSION:-0.1.0}"
OCI_CHART="oci://ghcr.io/pesitwizard/charts/pesitwizard-server"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo "╔═══════════════════════════════════════════════════╗"
echo "║      PeSIT Wizard Server Installer                ║"
echo "║           (Standalone Mode)                       ║"
echo "╚═══════════════════════════════════════════════════╝"
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

    kubectl create namespace "$PESITWIZARD_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

    echo -e "${GREEN}✓ Namespace '$PESITWIZARD_NAMESPACE' ready${NC}"
}

# Install via Helm from OCI registry
install_helm() {
    echo ""
    echo -e "${YELLOW}Installing PeSIT Wizard Server...${NC}"

    echo -e "${YELLOW}Installing Helm chart from ${OCI_CHART}...${NC}"
    helm upgrade --install pesitwizard-server "$OCI_CHART" \
        --version "$PESITWIZARD_VERSION" \
        --namespace "$PESITWIZARD_NAMESPACE"

    echo -e "${GREEN}✓ Helm release deployed${NC}"
    echo ""
    echo -e "${YELLOW}Waiting for pods to be ready...${NC}"

    # Wait for deployments with progress feedback
    DEPLOYMENTS=$(kubectl get deployments -n "$PESITWIZARD_NAMESPACE" -l app.kubernetes.io/instance=pesitwizard-server -o name 2>/dev/null || true)

    if [ -n "$DEPLOYMENTS" ]; then
        for deploy in $DEPLOYMENTS; do
            echo -n "  Waiting for $deploy... "
            if kubectl rollout status "$deploy" -n "$PESITWIZARD_NAMESPACE" --timeout=5m 2>/dev/null; then
                echo -e "${GREEN}ready${NC}"
            else
                echo -e "${YELLOW}timeout (check manually)${NC}"
            fi
        done
    fi

    echo -e "${GREEN}✓ PeSIT Wizard Server installed${NC}"
}

# Show access info
show_access_info() {
    echo ""
    echo "╔═══════════════════════════════════════════════════╗"
    echo "║     Installation Complete!                        ║"
    echo "╚═══════════════════════════════════════════════════╝"
    echo ""
    echo -e "${GREEN}PeSIT Wizard Server is now installed.${NC}"
    echo ""
    echo "PeSIT port: 5000"
    echo "HTTP API port: 8080"
    echo ""
    echo "To expose the PeSIT port:"
    echo "  kubectl port-forward svc/pesitwizard-server 5000:5000 -n $PESITWIZARD_NAMESPACE"
    echo ""
    echo "To check status:"
    echo "  kubectl get pods -n $PESITWIZARD_NAMESPACE"
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
