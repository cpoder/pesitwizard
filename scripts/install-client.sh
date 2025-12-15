#!/bin/bash
# Vectis Client Installation Script
# Usage: curl -fsSL https://raw.githubusercontent.com/cpoder/vectis/main/scripts/install-client.sh | bash
# Or with options: curl -fsSL ... | bash -s -- --port-forward --port 9090

set -e

# Default values
VECTIS_NAMESPACE="${VECTIS_NAMESPACE:-vectis}"
GITHUB_REPO="cpoder/vectis"
HELM_CHART_PATH="vectis-helm-charts/vectis-client"
HELM_CHART_BRANCH="${VECTIS_VERSION:-main}"
PORT_FORWARD=false
FORWARD_PORT=8080
INSTALL_K3S=false
INTERACTIVE=true

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --port-forward|-f)
                PORT_FORWARD=true
                shift
                ;;
            --port|-p)
                FORWARD_PORT="$2"
                shift 2
                ;;
            --install-k3s)
                INSTALL_K3S=true
                shift
                ;;
            --namespace|-n)
                VECTIS_NAMESPACE="$2"
                shift 2
                ;;
            --version|-v)
                HELM_CHART_BRANCH="$2"
                shift 2
                ;;
            --yes|-y)
                INTERACTIVE=false
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                echo -e "${RED}Unknown option: $1${NC}"
                show_help
                exit 1
                ;;
        esac
    done
}

show_help() {
    echo "Vectis Client Installer"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -f, --port-forward      Start port forwarding after installation"
    echo "  -p, --port PORT         Port for port forwarding (default: 8080)"
    echo "  -n, --namespace NAME    Kubernetes namespace (default: vectis)"
    echo "  -v, --version VERSION   Version/branch to install (default: main)"
    echo "      --install-k3s       Install k3s if no Kubernetes cluster found"
    echo "  -y, --yes               Non-interactive mode, accept all defaults"
    echo "  -h, --help              Show this help message"
    echo ""
    echo "Examples:"
    echo "  # Basic installation"
    echo "  curl -fsSL https://raw.githubusercontent.com/cpoder/vectis/main/scripts/install-client.sh | bash"
    echo ""
    echo "  # Install with port forwarding on port 9090"
    echo "  curl -fsSL ... | bash -s -- --port-forward --port 9090"
    echo ""
    echo "  # Install k3s if needed, then install Vectis"
    echo "  curl -fsSL ... | bash -s -- --install-k3s --port-forward"
}

# Prompt user for yes/no
prompt_yn() {
    local prompt="$1"
    local default="${2:-n}"
    
    if [ "$INTERACTIVE" = false ]; then
        [ "$default" = "y" ] && return 0 || return 1
    fi
    
    local yn
    if [ "$default" = "y" ]; then
        read -p "$prompt [Y/n]: " yn
        yn="${yn:-y}"
    else
        read -p "$prompt [y/N]: " yn
        yn="${yn:-n}"
    fi
    
    case "$yn" in
        [Yy]*) return 0 ;;
        *) return 1 ;;
    esac
}

# Prompt for value with default
prompt_value() {
    local prompt="$1"
    local default="$2"
    local varname="$3"
    
    if [ "$INTERACTIVE" = false ]; then
        eval "$varname=\"$default\""
        return
    fi
    
    local value
    read -p "$prompt [$default]: " value
    eval "$varname=\"${value:-$default}\""
}

echo ""
echo "╔═══════════════════════════════════════════╗"
echo "║        Vectis Client Installer            ║"
echo "╚═══════════════════════════════════════════╝"
echo ""

# Install k3s
install_k3s() {
    echo -e "${YELLOW}Installing k3s...${NC}"
    echo -e "${BLUE}This will install a lightweight Kubernetes distribution.${NC}"
    
    if ! curl -sfL https://get.k3s.io | sh -s - --write-kubeconfig-mode 644; then
        echo -e "${RED}ERROR: Failed to install k3s${NC}"
        exit 1
    fi
    
    # Wait for k3s to be ready
    echo -e "${YELLOW}Waiting for k3s to be ready...${NC}"
    sleep 10
    
    # Setup kubeconfig
    export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
    if [ ! -f "$KUBECONFIG" ]; then
        # Try user-specific location
        mkdir -p ~/.kube
        sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
        sudo chown $(id -u):$(id -g) ~/.kube/config
        export KUBECONFIG=~/.kube/config
    fi
    
    # Wait for node to be ready
    local retries=30
    while [ $retries -gt 0 ]; do
        if kubectl get nodes 2>/dev/null | grep -q " Ready"; then
            break
        fi
        echo -n "."
        sleep 2
        retries=$((retries - 1))
    done
    echo ""
    
    if ! kubectl get nodes 2>/dev/null | grep -q " Ready"; then
        echo -e "${RED}ERROR: k3s node not ready after timeout${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ k3s installed and ready${NC}"
}

# Install helm if missing
install_helm() {
    echo -e "${YELLOW}Installing Helm...${NC}"
    
    if ! curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash; then
        echo -e "${RED}ERROR: Failed to install Helm${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Helm installed${NC}"
}

# Check prerequisites
check_prereqs() {
    echo -e "${YELLOW}Checking prerequisites...${NC}"
    
    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        echo -e "${YELLOW}kubectl is not installed${NC}"
        if [ "$INSTALL_K3S" = true ] || prompt_yn "Install k3s (includes kubectl)?"; then
            INSTALL_K3S=true
        else
            echo -e "${RED}ERROR: kubectl is required${NC}"
            exit 1
        fi
    fi
    
    # Check Kubernetes cluster
    if ! kubectl cluster-info &> /dev/null 2>&1; then
        echo -e "${YELLOW}No Kubernetes cluster found${NC}"
        if [ "$INSTALL_K3S" = true ] || prompt_yn "Install k3s (lightweight Kubernetes)?"; then
            install_k3s
        else
            echo -e "${RED}ERROR: Cannot connect to Kubernetes cluster${NC}"
            echo -e "${BLUE}Tip: Use --install-k3s to automatically install k3s${NC}"
            exit 1
        fi
    fi
    
    # Check helm
    if ! command -v helm &> /dev/null; then
        echo -e "${YELLOW}Helm is not installed${NC}"
        if prompt_yn "Install Helm?" "y"; then
            install_helm
        else
            echo -e "${RED}ERROR: Helm is required${NC}"
            exit 1
        fi
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

# Download and deploy via Helm from GitHub
deploy_helm() {
    echo ""
    echo -e "${YELLOW}Installing Vectis Client...${NC}"
    
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
    helm upgrade --install vectis-client "$CHART_DIR" \
        --namespace "$VECTIS_NAMESPACE"
    
    echo -e "${GREEN}✓ Helm release deployed${NC}"
    echo ""
    echo -e "${YELLOW}Waiting for pods to be ready...${NC}"
    
    # Wait for deployments with progress feedback
    DEPLOYMENTS=$(kubectl get deployments -n "$VECTIS_NAMESPACE" -l app.kubernetes.io/instance=vectis-client -o name 2>/dev/null || true)
    
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
    
    echo -e "${GREEN}✓ Vectis Client installed${NC}"
}

# Start port forwarding
start_port_forward() {
    echo ""
    echo -e "${YELLOW}Starting port forwarding on port $FORWARD_PORT...${NC}"
    
    # Check if port is in use
    if command -v lsof &> /dev/null && lsof -i ":$FORWARD_PORT" &> /dev/null; then
        echo -e "${YELLOW}Port $FORWARD_PORT is already in use${NC}"
        if [ "$INTERACTIVE" = true ]; then
            prompt_value "Enter a different port" "$((FORWARD_PORT + 1))" FORWARD_PORT
        else
            FORWARD_PORT=$((FORWARD_PORT + 1))
            echo -e "${BLUE}Using port $FORWARD_PORT instead${NC}"
        fi
    fi
    
    echo -e "${GREEN}Starting port forwarding in background...${NC}"
    echo -e "${BLUE}UI will be available at: http://localhost:$FORWARD_PORT${NC}"
    echo ""
    
    # Start port-forward in background
    kubectl port-forward svc/vectis-client-ui $FORWARD_PORT:80 -n "$VECTIS_NAMESPACE" &
    PF_PID=$!
    
    # Give it a moment to start
    sleep 2
    
    if kill -0 $PF_PID 2>/dev/null; then
        echo -e "${GREEN}✓ Port forwarding started (PID: $PF_PID)${NC}"
        echo ""
        echo -e "${BLUE}Opening browser...${NC}"
        
        # Try to open browser
        if command -v xdg-open &> /dev/null; then
            xdg-open "http://localhost:$FORWARD_PORT" 2>/dev/null &
        elif command -v open &> /dev/null; then
            open "http://localhost:$FORWARD_PORT" 2>/dev/null &
        fi
        
        echo ""
        echo -e "${YELLOW}Press Ctrl+C to stop port forwarding${NC}"
        wait $PF_PID
    else
        echo -e "${RED}Port forwarding failed to start${NC}"
        return 1
    fi
}

# Show access info
show_access_info() {
    echo ""
    echo "╔═══════════════════════════════════════════╗"
    echo "║     Installation Complete!                ║"
    echo "╚═══════════════════════════════════════════╝"
    echo ""
    echo -e "${GREEN}Vectis Client is now installed.${NC}"
    echo ""
    
    if [ "$PORT_FORWARD" = true ]; then
        return
    fi
    
    echo "To access the UI:"
    echo -e "  ${BLUE}kubectl port-forward svc/vectis-client-ui 8080:80 -n $VECTIS_NAMESPACE${NC}"
    echo "  Then open: http://localhost:8080"
    echo ""
    echo "To check status:"
    echo -e "  ${BLUE}kubectl get pods -n $VECTIS_NAMESPACE${NC}"
    echo ""
    echo "To uninstall:"
    echo -e "  ${BLUE}helm uninstall vectis-client -n $VECTIS_NAMESPACE${NC}"
    echo ""
    
    # Ask about port forwarding if interactive
    if [ "$INTERACTIVE" = true ]; then
        if prompt_yn "Start port forwarding now?" "y"; then
            prompt_value "Port to use" "8080" FORWARD_PORT
            PORT_FORWARD=true
        fi
    fi
}

# Main
main() {
    parse_args "$@"
    check_prereqs
    setup_namespace
    deploy_helm
    show_access_info
    
    if [ "$PORT_FORWARD" = true ]; then
        start_port_forward
    fi
}

main "$@"
