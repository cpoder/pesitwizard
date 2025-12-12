#!/bin/bash
#
# End-to-End Test Script for PeSIT Server on Kubernetes
#
# This script:
# 1. Creates a local Kubernetes cluster (kind)
# 2. Builds and loads the Docker image
# 3. Deploys the PeSIT server with PostgreSQL via Helm
# 4. Waits for all pods to be ready
# 5. Performs a PeSIT file transfer test
# 6. Cleans up everything
#
# Prerequisites:
# - Docker
# - kind (Kubernetes in Docker)
# - kubectl
# - helm
# - Maven (for building)
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CLUSTER_NAME="pesit-e2e-test"
NAMESPACE="pesit-test"
RELEASE_NAME="pesit"
IMAGE_NAME="pesit/pesit-server"
IMAGE_TAG="e2e-test"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
HELM_CHART_DIR="$PROJECT_DIR/../helm-charts/pesit-server"

# Test configuration
PESIT_PORT=5000
TEST_TIMEOUT=300  # 5 minutes

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  $1${NC}"
    echo -e "${GREEN}========================================${NC}"
}

cleanup() {
    log_step "CLEANUP"
    
    # Delete Helm release
    if helm list -n "$NAMESPACE" 2>/dev/null | grep -q "$RELEASE_NAME"; then
        log_info "Deleting Helm release..."
        helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" --wait 2>/dev/null || true
    fi
    
    # Delete namespace
    if kubectl get namespace "$NAMESPACE" &>/dev/null; then
        log_info "Deleting namespace..."
        kubectl delete namespace "$NAMESPACE" --wait=false 2>/dev/null || true
    fi
    
    # Delete kind cluster
    if kind get clusters 2>/dev/null | grep -q "$CLUSTER_NAME"; then
        log_info "Deleting kind cluster..."
        kind delete cluster --name "$CLUSTER_NAME"
    fi
    
    log_success "Cleanup complete"
}

check_prerequisites() {
    log_step "CHECKING PREREQUISITES"
    
    local missing=()
    
    command -v docker &>/dev/null || missing+=("docker")
    command -v kind &>/dev/null || missing+=("kind")
    command -v kubectl &>/dev/null || missing+=("kubectl")
    command -v helm &>/dev/null || missing+=("helm")
    command -v mvn &>/dev/null || missing+=("mvn")
    
    if [ ${#missing[@]} -ne 0 ]; then
        log_error "Missing prerequisites: ${missing[*]}"
        echo ""
        echo "Install missing tools:"
        echo "  - docker: https://docs.docker.com/get-docker/"
        echo "  - kind: https://kind.sigs.k8s.io/docs/user/quick-start/#installation"
        echo "  - kubectl: https://kubernetes.io/docs/tasks/tools/"
        echo "  - helm: https://helm.sh/docs/intro/install/"
        echo "  - mvn: https://maven.apache.org/install.html"
        exit 1
    fi
    
    # Check Docker is running
    if ! docker info &>/dev/null; then
        log_error "Docker is not running. Please start Docker."
        exit 1
    fi
    
    log_success "All prerequisites met"
}

create_cluster() {
    log_step "CREATING KUBERNETES CLUSTER"
    
    # Check if cluster already exists
    if kind get clusters 2>/dev/null | grep -q "$CLUSTER_NAME"; then
        log_warn "Cluster '$CLUSTER_NAME' already exists, deleting..."
        kind delete cluster --name "$CLUSTER_NAME"
    fi
    
    # Create kind cluster with port mapping for PeSIT
    log_info "Creating kind cluster '$CLUSTER_NAME'..."
    cat <<EOF | kind create cluster --name "$CLUSTER_NAME" --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  extraPortMappings:
  - containerPort: 30500
    hostPort: 5000
    protocol: TCP
  - containerPort: 30080
    hostPort: 8080
    protocol: TCP
- role: worker
- role: worker
EOF
    
    # Wait for cluster to be ready
    log_info "Waiting for cluster to be ready..."
    kubectl wait --for=condition=Ready nodes --all --timeout=120s
    
    log_success "Cluster created successfully"
    kubectl get nodes
}

build_and_load_image() {
    log_step "BUILDING AND LOADING DOCKER IMAGE"
    
    cd "$PROJECT_DIR"
    
    # Build the Maven project
    log_info "Building Maven project..."
    mvn clean package -DskipTests -q
    
    # Build Docker image
    log_info "Building Docker image..."
    docker build -t "$IMAGE_NAME:$IMAGE_TAG" .
    
    # Load image into kind cluster
    log_info "Loading image into kind cluster..."
    kind load docker-image "$IMAGE_NAME:$IMAGE_TAG" --name "$CLUSTER_NAME"
    
    log_success "Image built and loaded"
}

deploy_pesit_server() {
    log_step "DEPLOYING PESIT SERVER"
    
    # Create namespace
    log_info "Creating namespace '$NAMESPACE'..."
    kubectl create namespace "$NAMESPACE" || true
    
    # Update Helm dependencies
    log_info "Updating Helm dependencies..."
    cd "$HELM_CHART_DIR"
    helm dependency update
    
    # Deploy with Helm
    log_info "Deploying PeSIT server..."
    helm install "$RELEASE_NAME" "$HELM_CHART_DIR" \
        --namespace "$NAMESPACE" \
        --set image.repository="$IMAGE_NAME" \
        --set image.tag="$IMAGE_TAG" \
        --set image.pullPolicy=Never \
        --set replicaCount=2 \
        --set pesitService.type=NodePort \
        --set pesitService.nodePort=30500 \
        --set service.type=NodePort \
        --set service.nodePort=30080 \
        --set pesit.strictPartnerCheck=false \
        --set pesit.strictFileCheck=false \
        --set postgresql.auth.password=pesit123 \
        --wait \
        --timeout 5m
    
    log_success "Helm release deployed"
}

wait_for_pods() {
    log_step "WAITING FOR PODS TO BE READY"
    
    log_info "Waiting for PostgreSQL..."
    kubectl wait --for=condition=Ready pod \
        -l app.kubernetes.io/name=postgresql \
        -n "$NAMESPACE" \
        --timeout=180s
    
    log_info "Waiting for PeSIT server pods..."
    kubectl wait --for=condition=Ready pod \
        -l app.kubernetes.io/name=pesit-server \
        -n "$NAMESPACE" \
        --timeout=180s
    
    # Show pod status
    log_info "Pod status:"
    kubectl get pods -n "$NAMESPACE"
    
    # Show services
    log_info "Services:"
    kubectl get svc -n "$NAMESPACE"
    
    log_success "All pods are ready"
}

check_cluster_status() {
    log_step "CHECKING CLUSTER STATUS"
    
    # Port-forward to check cluster status
    log_info "Checking cluster status via REST API..."
    
    # Get the first pod name
    POD_NAME=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=pesit-server -o jsonpath='{.items[0].metadata.name}')
    
    # Port-forward in background
    kubectl port-forward -n "$NAMESPACE" "pod/$POD_NAME" 18080:8080 &
    PF_PID=$!
    sleep 3
    
    # Check cluster status
    CLUSTER_STATUS=$(curl -s http://localhost:18080/api/cluster/status)
    echo "$CLUSTER_STATUS" | python3 -m json.tool 2>/dev/null || echo "$CLUSTER_STATUS"
    
    # Kill port-forward
    kill $PF_PID 2>/dev/null || true
    
    log_success "Cluster is operational"
}

create_test_server() {
    log_step "CREATING TEST SERVER INSTANCE"
    
    # Get the first pod name
    POD_NAME=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=pesit-server -o jsonpath='{.items[0].metadata.name}')
    
    # Port-forward in background
    kubectl port-forward -n "$NAMESPACE" "pod/$POD_NAME" 18080:8080 &
    PF_PID=$!
    sleep 3
    
    # Create a PeSIT server instance
    log_info "Creating PeSIT server instance..."
    RESPONSE=$(curl -s -X POST http://localhost:18080/api/servers \
        -H "Content-Type: application/json" \
        -d '{
            "serverId": "E2E_TEST_SERVER",
            "port": 5000,
            "maxConnections": 50,
            "receiveDirectory": "/data/received",
            "sendDirectory": "/data/send",
            "strictPartnerCheck": false,
            "strictFileCheck": false
        }')
    echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    
    # Start the server
    log_info "Starting PeSIT server..."
    RESPONSE=$(curl -s -X POST http://localhost:18080/api/servers/E2E_TEST_SERVER/start)
    echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    
    # Check status
    log_info "Server status:"
    RESPONSE=$(curl -s http://localhost:18080/api/servers/E2E_TEST_SERVER/status)
    echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    
    # Kill port-forward
    kill $PF_PID 2>/dev/null || true
    
    log_success "Test server created and started"
}

run_file_transfer_test() {
    log_step "RUNNING FILE TRANSFER TEST"
    
    cd "$PROJECT_DIR"
    
    # Get the node IP (for kind, it's localhost)
    NODE_IP="localhost"
    
    # Port-forward PeSIT port
    log_info "Setting up port-forward for PeSIT protocol..."
    POD_NAME=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=pesit-server -o jsonpath='{.items[0].metadata.name}')
    kubectl port-forward -n "$NAMESPACE" "pod/$POD_NAME" 15000:5000 &
    PF_PID=$!
    sleep 3
    
    # Run the integration test from vectis-protocol
    log_info "Running PeSIT file transfer test..."
    cd "$PROJECT_DIR/../vectis-protocol"
    
    if mvn test -Dtest=CompleteFileTransferTest \
        -Dpesit.integration.enabled=true \
        -Dpesit.test.host=localhost \
        -Dpesit.test.port=15000 \
        -Dpesit.test.server=E2E_TEST_SERVER \
        -q; then
        log_success "File transfer test PASSED!"
        TEST_RESULT=0
    else
        log_error "File transfer test FAILED!"
        TEST_RESULT=1
    fi
    
    # Kill port-forward
    kill $PF_PID 2>/dev/null || true
    
    return $TEST_RESULT
}

show_logs() {
    log_step "SHOWING LOGS (last 50 lines per pod)"
    
    for POD in $(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=pesit-server -o jsonpath='{.items[*].metadata.name}'); do
        echo ""
        log_info "Logs from $POD:"
        kubectl logs -n "$NAMESPACE" "$POD" --tail=50 || true
    done
}

# Main execution
main() {
    echo ""
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║     PeSIT Server End-to-End Test on Kubernetes                ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo ""
    
    # Set trap for cleanup on exit
    trap cleanup EXIT
    
    START_TIME=$(date +%s)
    
    # Run test steps
    check_prerequisites
    create_cluster
    build_and_load_image
    deploy_pesit_server
    wait_for_pods
    check_cluster_status
    create_test_server
    
    # Run the actual file transfer test
    if run_file_transfer_test; then
        TEST_PASSED=true
    else
        TEST_PASSED=false
        show_logs
    fi
    
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    
    echo ""
    echo "╔═══════════════════════════════════════════════════════════════╗"
    if [ "$TEST_PASSED" = true ]; then
        echo "║     ✅ E2E TEST PASSED                                        ║"
    else
        echo "║     ❌ E2E TEST FAILED                                        ║"
    fi
    echo "║     Duration: ${DURATION}s                                           ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo ""
    
    if [ "$TEST_PASSED" = true ]; then
        exit 0
    else
        exit 1
    fi
}

# Parse arguments
case "${1:-}" in
    --cleanup-only)
        cleanup
        exit 0
        ;;
    --skip-cleanup)
        trap - EXIT
        main
        ;;
    --help)
        echo "Usage: $0 [OPTIONS]"
        echo ""
        echo "Options:"
        echo "  --cleanup-only   Only run cleanup (delete cluster)"
        echo "  --skip-cleanup   Run test but don't cleanup at the end"
        echo "  --help           Show this help message"
        exit 0
        ;;
    *)
        main
        ;;
esac
