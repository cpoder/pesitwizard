#!/bin/bash
# Vectis Client Installation Script
# Usage: curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/install-client.sh | bash
# Or with options: curl -fsSL ... | bash -s -- --port-forward --port 9090

set -e

# Default values
PESITWIZARD_NAMESPACE="${PESITWIZARD_NAMESPACE:-pesitwizard}"
GITHUB_REPO="pesitwizard/pesitwizard"
HELM_CHART_PATH="pesitwizard-helm-charts/pesitwizard-client"
HELM_CHART_BRANCH="${PESITWIZARD_VERSION:-main}"
INSTALL_K3S=false
INTERACTIVE=true
STORAGE_PATH=""
INGRESS_HOST="${INGRESS_HOST:-pesitwizard.local}"
INGRESS_PORT=""  # Ingress Controller port (default: 80)
NODE_PORT=""  # If set, use NodePort instead of Ingress

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
            --port|-p)
                NODE_PORT="$2"
                shift 2
                ;;
            --install-k3s)
                INSTALL_K3S=true
                shift
                ;;
            --namespace|-n)
                PESITWIZARD_NAMESPACE="$2"
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
            --storage|-s)
                STORAGE_PATH="$2"
                shift 2
                ;;
            --host)
                INGRESS_HOST="$2"
                shift 2
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
    echo "PeSIT Wizard Client Installer"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -n, --namespace NAME    Kubernetes namespace (default: pesitwizard)"
    echo "  -v, --version VERSION   Version/branch to install (default: main)"
    echo "      --host HOSTNAME     Ingress hostname (default: pesitwizard.local)"
    echo "  -p, --port PORT         Use NodePort instead of Ingress (e.g., 30080)"
    echo "      --install-k3s       Install k3s if no Kubernetes cluster found"
    echo "  -y, --yes               Non-interactive mode, accept all defaults"
    echo "  -h, --help              Show this help message"
    echo ""
    echo "Examples:"
    echo "  # Basic installation (uses Ingress on pesitwizard.local)"
    echo "  curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/install-client.sh | bash"
    echo ""
    echo "  # Install with custom hostname"
    echo "  curl -fsSL ... | bash -s -- --host pesitwizard.mycompany.com"
    echo ""
    echo "  # Install with NodePort on port 30080"
    echo "  curl -fsSL ... | bash -s -- --port 30080"
    echo ""
    echo "  # Install k3s if needed, then install PeSIT Wizard"
    echo "  curl -fsSL ... | bash -s -- --install-k3s"
}

# Check if stdin is a terminal (for interactive prompts)
is_tty() {
    [ -t 0 ]
}

# Prompt user for yes/no (uses /dev/tty for pipe compatibility)
prompt_yn() {
    local prompt="$1"
    local default="${2:-n}"
    
    if [ "$INTERACTIVE" = false ]; then
        [ "$default" = "y" ] && return 0 || return 1
    fi
    
    local yn
    if [ "$default" = "y" ]; then
        echo -n "$prompt [Y/n]: "
        read yn < /dev/tty 2>/dev/null || yn=""
        yn="${yn:-y}"
    else
        echo -n "$prompt [y/N]: "
        read yn < /dev/tty 2>/dev/null || yn=""
        yn="${yn:-n}"
    fi
    
    case "$yn" in
        [Yy]*) return 0 ;;
        *) return 1 ;;
    esac
}

# Prompt for value with default (uses /dev/tty for pipe compatibility)
prompt_value() {
    local prompt="$1"
    local default="$2"
    local varname="$3"
    
    if [ "$INTERACTIVE" = false ]; then
        echo -e "$prompt: ${BLUE}$default${NC} (auto)"
        eval "$varname=\"$default\""
        return
    fi
    
    echo -n "$prompt [$default]: "
    local value
    read value < /dev/tty 2>/dev/null || value=""
    eval "$varname=\"${value:-$default}\""
}

# Show banner
show_banner() {
    echo ""
    echo "╔═══════════════════════════════════════════╗"
    echo "║      PeSIT Wizard Client Installer        ║"
    echo "╚═══════════════════════════════════════════╝"
    echo ""
}

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
    
    kubectl create namespace "$PESITWIZARD_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
    
    echo -e "${GREEN}✓ Namespace '$PESITWIZARD_NAMESPACE' ready${NC}"
}

# Detect and configure Ingress Controller port
configure_ingress_port() {
    # Skip if using NodePort
    if [ -n "$NODE_PORT" ]; then
        return
    fi
    
    echo ""
    echo -e "${YELLOW}Ingress Configuration${NC}"
    
    # Detect Ingress Controller
    INGRESS_TYPE=""
    INGRESS_SVC=""
    INGRESS_NS=""
    
    # Check for Traefik (k3s default)
    if kubectl get svc traefik -n kube-system &>/dev/null; then
        INGRESS_TYPE="traefik"
        INGRESS_SVC="traefik"
        INGRESS_NS="kube-system"
    # Check for Traefik in traefik namespace
    elif kubectl get svc traefik -n traefik &>/dev/null; then
        INGRESS_TYPE="traefik"
        INGRESS_SVC="traefik"
        INGRESS_NS="traefik"
    # Check for nginx-ingress
    elif kubectl get svc ingress-nginx-controller -n ingress-nginx &>/dev/null; then
        INGRESS_TYPE="nginx"
        INGRESS_SVC="ingress-nginx-controller"
        INGRESS_NS="ingress-nginx"
    fi
    
    if [ -z "$INGRESS_TYPE" ]; then
        echo -e "${YELLOW}No Ingress Controller detected.${NC}"
        echo -e "${BLUE}You may need to install one (e.g., Traefik, nginx-ingress)${NC}"
        return
    fi
    
    echo -e "${GREEN}✓ Detected: $INGRESS_TYPE in namespace $INGRESS_NS${NC}"
    
    # Get current port
    CURRENT_PORT=$(kubectl get svc "$INGRESS_SVC" -n "$INGRESS_NS" -o jsonpath='{.spec.ports[?(@.name=="web")].port}' 2>/dev/null)
    if [ -z "$CURRENT_PORT" ]; then
        CURRENT_PORT=$(kubectl get svc "$INGRESS_SVC" -n "$INGRESS_NS" -o jsonpath='{.spec.ports[?(@.port==80)].port}' 2>/dev/null)
    fi
    CURRENT_PORT="${CURRENT_PORT:-80}"
    
    echo -e "${BLUE}Current HTTP port: $CURRENT_PORT${NC}"
    
    if [ "$INTERACTIVE" = true ]; then
        if [ -z "$INGRESS_PORT" ]; then
            prompt_value "Ingress HTTP port" "$CURRENT_PORT" INGRESS_PORT
        fi
        
        if [ "$INGRESS_PORT" != "$CURRENT_PORT" ]; then
            echo -e "${YELLOW}Configuring $INGRESS_TYPE to use port $INGRESS_PORT...${NC}"
            
            if [ "$INGRESS_TYPE" = "traefik" ]; then
                # Patch Traefik service
                kubectl patch svc "$INGRESS_SVC" -n "$INGRESS_NS" --type='json' \
                    -p="[{\"op\": \"replace\", \"path\": \"/spec/ports/0/port\", \"value\": $INGRESS_PORT}]" 2>/dev/null || true
                
                # For NodePort, also update nodePort if applicable
                SVC_TYPE=$(kubectl get svc "$INGRESS_SVC" -n "$INGRESS_NS" -o jsonpath='{.spec.type}')
                if [ "$SVC_TYPE" = "LoadBalancer" ] || [ "$SVC_TYPE" = "NodePort" ]; then
                    # Try to set nodePort as well (may fail if port is out of range)
                    kubectl patch svc "$INGRESS_SVC" -n "$INGRESS_NS" --type='json' \
                        -p="[{\"op\": \"replace\", \"path\": \"/spec/ports/0/nodePort\", \"value\": $INGRESS_PORT}]" 2>/dev/null || true
                fi
            elif [ "$INGRESS_TYPE" = "nginx" ]; then
                kubectl patch svc "$INGRESS_SVC" -n "$INGRESS_NS" --type='json' \
                    -p="[{\"op\": \"replace\", \"path\": \"/spec/ports/0/port\", \"value\": $INGRESS_PORT}]" 2>/dev/null || true
            fi
            
            echo -e "${GREEN}✓ Ingress port configured to $INGRESS_PORT${NC}"
        fi
    else
        INGRESS_PORT="$CURRENT_PORT"
    fi
}

# Generate TLS certificates
generate_certificates() {
    echo ""
    echo -e "${YELLOW}TLS Certificate Configuration${NC}"
    
    # Check if openssl is available
    if ! command -v openssl &> /dev/null; then
        echo -e "${RED}WARNING: openssl not found, skipping certificate generation${NC}"
        echo -e "${BLUE}You can manually configure TLS later via the UI${NC}"
        TLS_ENABLED=false
        return
    fi
    
    TLS_ENABLED=true
    CERT_DIR=$(mktemp -d)
    KEYSTORE_PASSWORD=$(openssl rand -base64 16 | tr -d '=' | head -c 16)
    TRUSTSTORE_PASSWORD=$(openssl rand -base64 16 | tr -d '=' | head -c 16)
    
    echo -e "${BLUE}Generating self-signed client certificate...${NC}"
    
    # Generate private key
    openssl genrsa -out "$CERT_DIR/client-key.pem" 2048 2>/dev/null
    
    # Generate self-signed certificate
    openssl req -new -x509 \
        -key "$CERT_DIR/client-key.pem" \
        -out "$CERT_DIR/client-cert.pem" \
        -days 365 \
        -subj "/CN=pesitwizard-client/O=PeSIT Wizard/C=FR" \
        2>/dev/null
    
    # Create PKCS12 keystore
    openssl pkcs12 -export \
        -in "$CERT_DIR/client-cert.pem" \
        -inkey "$CERT_DIR/client-key.pem" \
        -out "$CERT_DIR/keystore.p12" \
        -name "client" \
        -password "pass:$KEYSTORE_PASSWORD" \
        2>/dev/null
    
    # Create truststore with the same cert (self-signed)
    # In production, this would contain the CA certificate
    openssl pkcs12 -export \
        -in "$CERT_DIR/client-cert.pem" \
        -inkey "$CERT_DIR/client-key.pem" \
        -out "$CERT_DIR/truststore.p12" \
        -name "ca" \
        -password "pass:$TRUSTSTORE_PASSWORD" \
        2>/dev/null
    
    echo -e "${GREEN}✓ Certificates generated${NC}"
    
    # Create Kubernetes secret with certificates
    echo -e "${YELLOW}Creating TLS secret in Kubernetes...${NC}"
    
    kubectl create secret generic pesitwizard-client-tls \
        --namespace "$PESITWIZARD_NAMESPACE" \
        --from-file=keystore.p12="$CERT_DIR/keystore.p12" \
        --from-file=truststore.p12="$CERT_DIR/truststore.p12" \
        --from-literal=keystore-password="$KEYSTORE_PASSWORD" \
        --from-literal=truststore-password="$TRUSTSTORE_PASSWORD" \
        --dry-run=client -o yaml | kubectl apply -f -
    
    echo -e "${GREEN}✓ TLS secret created${NC}"
    
    # Cleanup temp files
    rm -rf "$CERT_DIR"
}

# Configure storage paths
configure_storage() {
    # Default paths
    local default_store="$HOME/pesitwizard-store"
    local default_data="$HOME/pesitwizard-data"
    
    if [ "$INTERACTIVE" = true ]; then
        echo ""
        echo -e "${YELLOW}Storage Configuration${NC}"
        echo -e "${BLUE}Two storage locations are needed:${NC}"
        echo -e "${BLUE}  - Store: Database files (H2)${NC}"
        echo -e "${BLUE}  - Data: Files for transfer${NC}"
        echo ""
        
        if [ -z "$STORE_PATH" ]; then
            prompt_value "Database storage path (pesitwizard-store)" "$default_store" STORE_PATH
        fi
        if [ -z "$DATA_PATH" ]; then
            prompt_value "File storage path (pesitwizard-data)" "$default_data" DATA_PATH
        fi
    else
        STORE_PATH="${STORE_PATH:-$default_store}"
        DATA_PATH="${DATA_PATH:-$default_data}"
    fi
    
    # Expand ~ if present
    STORE_PATH="${STORE_PATH/#\~/$HOME}"
    DATA_PATH="${DATA_PATH/#\~/$HOME}"
    
    # Create directories if they don't exist
    if [ ! -d "$STORE_PATH" ]; then
        echo -e "${YELLOW}Creating directory: $STORE_PATH${NC}"
        mkdir -p "$STORE_PATH"
        chmod 755 "$STORE_PATH"
    fi
    if [ ! -d "$DATA_PATH" ]; then
        echo -e "${YELLOW}Creating directory: $DATA_PATH${NC}"
        mkdir -p "$DATA_PATH"
        chmod 777 "$DATA_PATH"  # Allow user + app read/write
    fi
    
    echo -e "${GREEN}✓ Store path: $STORE_PATH${NC}"
    echo -e "${GREEN}✓ Data path: $DATA_PATH${NC}"
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
    
    # Build helm set arguments
    HELM_ARGS=""
    if [ -n "$STORE_PATH" ]; then
        HELM_ARGS="$HELM_ARGS --set persistence.store.hostPath=$STORE_PATH"
    fi
    if [ -n "$DATA_PATH" ]; then
        HELM_ARGS="$HELM_ARGS --set persistence.data.hostPath=$DATA_PATH"
    fi
    if [ -n "$NODE_PORT" ]; then
        # Use NodePort instead of Ingress
        HELM_ARGS="$HELM_ARGS --set ui.service.nodePort=$NODE_PORT --set ingress.enabled=false"
    elif [ -n "$INGRESS_HOST" ]; then
        HELM_ARGS="$HELM_ARGS --set ingress.hosts[0].host=$INGRESS_HOST"
    fi
    # TLS configuration
    if [ "$TLS_ENABLED" = true ]; then
        HELM_ARGS="$HELM_ARGS --set tls.enabled=true --set tls.existingSecret=pesitwizard-client-tls"
    else
        HELM_ARGS="$HELM_ARGS --set tls.enabled=false"
    fi
    
    # Install or upgrade using local chart
    echo -e "${YELLOW}Installing Helm chart...${NC}"
    helm upgrade --install pesitwizard-client "$CHART_DIR" \
        --namespace "$PESITWIZARD_NAMESPACE" \
        $HELM_ARGS
    
    echo -e "${GREEN}✓ Helm release deployed${NC}"
    echo ""
    echo -e "${YELLOW}Waiting for pods to be ready...${NC}"
    
    # Wait for deployments with progress feedback
    DEPLOYMENTS=$(kubectl get deployments -n "$PESITWIZARD_NAMESPACE" -l app.kubernetes.io/instance=pesitwizard-client -o name 2>/dev/null || true)
    
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
    
    echo -e "${GREEN}✓ PeSIT Wizard Client installed${NC}"
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
    
    # Get node IP
    NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' 2>/dev/null || echo "127.0.0.1")
    
    if [ -n "$NODE_PORT" ]; then
        # NodePort access
        echo -e "${YELLOW}Access the UI:${NC}"
        echo -e "  ${BLUE}http://$NODE_IP:$NODE_PORT${NC}"
    else
        # Ingress access
        local url="http://$INGRESS_HOST"
        if [ -n "$INGRESS_PORT" ] && [ "$INGRESS_PORT" != "80" ]; then
            url="http://$INGRESS_HOST:$INGRESS_PORT"
        fi
        echo -e "${YELLOW}Access the UI:${NC}"
        echo -e "  ${BLUE}$url${NC}"
        echo ""
        
        # Check if hostname resolves
        if ! getent hosts "$INGRESS_HOST" &>/dev/null; then
            echo -e "${YELLOW}Note: Add this entry to your /etc/hosts:${NC}"
            echo -e "  ${BLUE}$NODE_IP  $INGRESS_HOST${NC}"
            echo ""
            
            if [ "$INTERACTIVE" = true ]; then
                if prompt_yn "Add entry to /etc/hosts now? (requires sudo)" "y"; then
                    echo "$NODE_IP  $INGRESS_HOST" | sudo tee -a /etc/hosts >/dev/null
                    echo -e "${GREEN}✓ Added to /etc/hosts${NC}"
                fi
            fi
        fi
    fi
    
    echo ""
    echo "To check status:"
    echo -e "  ${BLUE}kubectl get pods -n $PESITWIZARD_NAMESPACE${NC}"
    echo ""
    echo "To uninstall:"
    echo -e "  ${BLUE}helm uninstall pesitwizard-client -n $PESITWIZARD_NAMESPACE${NC}"
    echo ""
}

# Main
main() {
    parse_args "$@"
    show_banner
    check_prereqs
    setup_namespace
    configure_ingress_port
    generate_certificates
    configure_storage
    deploy_helm
    show_access_info
}

main "$@"
