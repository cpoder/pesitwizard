#!/bin/bash
# Vectis Uninstall Script
# Usage: curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/uninstall.sh | bash

set -e

VECTIS_NAMESPACE="${VECTIS_NAMESPACE:-pesitwizard}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo "╔═══════════════════════════════════════════╗"
echo "║         Vectis Uninstaller                ║"
echo "╚═══════════════════════════════════════════╝"
echo ""

# Check prerequisites
check_prereqs() {
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
}

# Confirm uninstall
confirm_uninstall() {
    echo -e "${YELLOW}WARNING: This will uninstall Vectis components from namespace '$VECTIS_NAMESPACE'${NC}"
    echo ""
    
    if [ "$FORCE_UNINSTALL" = "true" ]; then
        echo "Force uninstall enabled, skipping confirmation."
        return
    fi
    
    echo -n "Are you sure you want to continue? [y/N] "
    read -r response
    
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        echo "Uninstall cancelled."
        exit 0
    fi
}

# Uninstall Helm releases
uninstall_helm() {
    echo ""
    echo -e "${YELLOW}Uninstalling Helm releases...${NC}"
    
    # Uninstall pesitwizard-client if exists
    if helm list -n "$VECTIS_NAMESPACE" | grep -q pesitwizard-client; then
        helm uninstall pesitwizard-client --namespace "$VECTIS_NAMESPACE" --wait
        echo -e "${GREEN}✓ pesitwizard-client uninstalled${NC}"
    fi
    
    # Uninstall pesitwizard-server if exists
    if helm list -n "$VECTIS_NAMESPACE" | grep -q pesitwizard-server; then
        helm uninstall pesitwizard-server --namespace "$VECTIS_NAMESPACE" --wait
        echo -e "${GREEN}✓ pesitwizard-server uninstalled${NC}"
    fi
}

# Optionally delete namespace
cleanup_namespace() {
    echo ""
    
    if [ "$DELETE_NAMESPACE" = "true" ]; then
        echo -e "${YELLOW}Deleting namespace '$VECTIS_NAMESPACE'...${NC}"
        kubectl delete namespace "$VECTIS_NAMESPACE" --wait=false
        echo -e "${GREEN}✓ Namespace deletion initiated${NC}"
    else
        echo -e "${YELLOW}Namespace '$VECTIS_NAMESPACE' preserved.${NC}"
        echo "To delete it manually: kubectl delete namespace $VECTIS_NAMESPACE"
    fi
}

# Show completion message
show_completion() {
    echo ""
    echo "╔═══════════════════════════════════════════╗"
    echo "║       Uninstall Complete!                 ║"
    echo "╚═══════════════════════════════════════════╝"
    echo ""
    echo -e "${GREEN}Vectis has been uninstalled.${NC}"
    echo ""
    if [ "$DELETE_NAMESPACE" != "true" ]; then
        echo "Note: PersistentVolumeClaims may still exist."
        echo "To fully clean up: kubectl delete pvc --all -n $VECTIS_NAMESPACE"
        echo ""
    fi
}

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --force|-f)
                FORCE_UNINSTALL=true
                shift
                ;;
            --delete-namespace)
                DELETE_NAMESPACE=true
                shift
                ;;
            --namespace|-n)
                VECTIS_NAMESPACE="$2"
                shift 2
                ;;
            --help|-h)
                echo "Usage: $0 [OPTIONS]"
                echo ""
                echo "Options:"
                echo "  -f, --force            Skip confirmation prompt"
                echo "  -n, --namespace NAME   Specify namespace (default: pesitwizard)"
                echo "  --delete-namespace     Also delete the namespace"
                echo "  -h, --help             Show this help message"
                exit 0
                ;;
            *)
                echo "Unknown option: $1"
                exit 1
                ;;
        esac
    done
}

# Main
main() {
    parse_args "$@"
    check_prereqs
    confirm_uninstall
    uninstall_helm
    cleanup_namespace
    show_completion
}

main "$@"
