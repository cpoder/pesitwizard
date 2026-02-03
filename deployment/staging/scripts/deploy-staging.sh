#!/bin/bash
# PeSIT Wizard Staging Deployment Script
#
# Usage:
#   ./deploy-staging.sh [--terraform] [--k8s] [--full]
#
# Options:
#   --terraform  Apply Terraform infrastructure changes only
#   --k8s        Deploy Kubernetes resources only
#   --full       Full deployment (Terraform + K8s)
#   --destroy    Destroy staging environment

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
STAGING_DIR="$PROJECT_ROOT/deployment/staging"
HELM_CHARTS_DIR="$PROJECT_ROOT/pesitwizard-helm-charts"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check required tools
    local required_tools=("terraform" "kubectl" "helm" "aws")
    for tool in "${required_tools[@]}"; do
        if ! command -v "$tool" &> /dev/null; then
            log_error "$tool is required but not installed"
            exit 1
        fi
    done

    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        log_error "AWS credentials not configured"
        exit 1
    fi

    # Check required environment variables
    local required_vars=("POSTGRES_PASSWORD" "REDIS_PASSWORD" "ENCRYPTION_KEY")
    for var in "${required_vars[@]}"; do
        if [ -z "${!var:-}" ]; then
            log_warn "Environment variable $var is not set"
        fi
    done

    log_info "Prerequisites check passed"
}

deploy_terraform() {
    log_info "Deploying Terraform infrastructure..."

    cd "$STAGING_DIR/terraform"

    # Initialize Terraform
    terraform init -upgrade

    # Plan changes
    terraform plan -var-file=staging.tfvars -out=tfplan

    # Apply changes
    read -p "Apply Terraform changes? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        terraform apply tfplan
    else
        log_warn "Terraform apply cancelled"
        return 1
    fi

    # Update kubeconfig
    log_info "Updating kubeconfig..."
    aws eks update-kubeconfig \
        --region "$(terraform output -raw aws_region 2>/dev/null || echo "eu-west-1")" \
        --name "$(terraform output -raw cluster_name)"

    log_info "Terraform deployment completed"
}

deploy_kubernetes() {
    log_info "Deploying Kubernetes resources..."

    # Ensure namespace exists
    kubectl create namespace staging --dry-run=client -o yaml | kubectl apply -f -

    # Apply base resources
    log_info "Applying namespace configuration..."
    kubectl apply -f "$STAGING_DIR/kubernetes/namespace.yaml"

    # Deploy Redis
    log_info "Deploying Redis..."
    kubectl apply -f "$STAGING_DIR/kubernetes/redis.yaml"

    # Wait for Redis to be ready
    kubectl rollout status statefulset/redis -n staging --timeout=5m

    # Deploy monitoring resources
    log_info "Deploying monitoring resources..."
    kubectl apply -f "$STAGING_DIR/kubernetes/monitoring/"

    log_info "Kubernetes base resources deployed"
}

deploy_helm() {
    log_info "Deploying PeSIT Wizard with Helm..."

    # Add any required Helm repos
    helm repo add bitnami https://charts.bitnami.com/bitnami || true
    helm repo update

    # Deploy PostgreSQL (if using Helm subchart)
    log_info "Deploying PostgreSQL..."
    helm upgrade --install postgres bitnami/postgresql \
        --namespace staging \
        --set auth.postgresPassword="${POSTGRES_ADMIN_PASSWORD:-changeme}" \
        --set auth.username=pesitwizard \
        --set auth.password="${POSTGRES_PASSWORD:-changeme}" \
        --set auth.database=pesitwizard \
        --set primary.persistence.size=100Gi \
        --set primary.persistence.storageClass=gp3 \
        --set readReplicas.replicaCount=2 \
        --wait \
        --timeout 10m

    # Deploy PeSIT Wizard Server
    log_info "Deploying PeSIT Wizard Server..."
    helm upgrade --install pesitwizard-staging \
        "$HELM_CHARTS_DIR/pesitwizard-server" \
        -f "$HELM_CHARTS_DIR/values-staging.yaml" \
        --namespace staging \
        --set postgresql.enabled=false \
        --set config.security.masterKey="${ENCRYPTION_KEY:-}" \
        --wait \
        --timeout 10m

    # Wait for all pods to be ready
    log_info "Waiting for all pods to be ready..."
    kubectl wait --for=condition=ready pod \
        -l app=pesitwizard-server \
        -n staging \
        --timeout=5m

    log_info "Helm deployment completed"
}

run_smoke_tests() {
    log_info "Running smoke tests..."

    # Get a pod for testing
    local pod=$(kubectl get pods -n staging \
        -l app=pesitwizard-server \
        -o jsonpath='{.items[0].metadata.name}')

    if [ -z "$pod" ]; then
        log_error "No pesitwizard-server pods found"
        return 1
    fi

    # Port forward for testing
    kubectl port-forward "pod/$pod" 8080:8080 -n staging &
    local pf_pid=$!
    sleep 5

    # Run tests
    local failed=0

    echo "Testing health endpoint..."
    if ! curl -sf http://localhost:8080/actuator/health | jq .; then
        log_error "Health check failed"
        failed=1
    fi

    echo "Testing cluster status..."
    if ! curl -sf http://localhost:8080/api/v1/cluster/status | jq .; then
        log_warn "Cluster status check failed (might be OK for single-node)"
    fi

    # Cleanup
    kill $pf_pid 2>/dev/null || true

    if [ $failed -eq 0 ]; then
        log_info "Smoke tests passed!"
    else
        log_error "Smoke tests failed"
        return 1
    fi
}

destroy_staging() {
    log_warn "This will destroy the staging environment!"
    read -p "Are you sure? (type 'yes' to confirm) " -r
    echo
    if [[ $REPLY != "yes" ]]; then
        log_info "Destroy cancelled"
        return 0
    fi

    log_info "Destroying staging environment..."

    # Delete Helm releases
    helm uninstall pesitwizard-staging -n staging || true
    helm uninstall postgres -n staging || true

    # Delete Kubernetes resources
    kubectl delete -f "$STAGING_DIR/kubernetes/" --ignore-not-found || true
    kubectl delete namespace staging --ignore-not-found || true

    # Destroy Terraform resources
    cd "$STAGING_DIR/terraform"
    terraform destroy -var-file=staging.tfvars -auto-approve

    log_info "Staging environment destroyed"
}

show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --terraform  Apply Terraform infrastructure changes only"
    echo "  --k8s        Deploy Kubernetes resources only"
    echo "  --helm       Deploy Helm charts only"
    echo "  --full       Full deployment (Terraform + K8s + Helm)"
    echo "  --test       Run smoke tests only"
    echo "  --destroy    Destroy staging environment"
    echo "  -h, --help   Show this help message"
}

main() {
    local deploy_terraform=false
    local deploy_k8s=false
    local deploy_helm=false
    local run_tests=false
    local destroy=false

    while [[ $# -gt 0 ]]; do
        case $1 in
            --terraform)
                deploy_terraform=true
                shift
                ;;
            --k8s)
                deploy_k8s=true
                shift
                ;;
            --helm)
                deploy_helm=true
                shift
                ;;
            --full)
                deploy_terraform=true
                deploy_k8s=true
                deploy_helm=true
                run_tests=true
                shift
                ;;
            --test)
                run_tests=true
                shift
                ;;
            --destroy)
                destroy=true
                shift
                ;;
            -h|--help)
                show_usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done

    # Default to full deployment if no options specified
    if ! $deploy_terraform && ! $deploy_k8s && ! $deploy_helm && ! $run_tests && ! $destroy; then
        deploy_terraform=true
        deploy_k8s=true
        deploy_helm=true
        run_tests=true
    fi

    check_prerequisites

    if $destroy; then
        destroy_staging
        exit 0
    fi

    if $deploy_terraform; then
        deploy_terraform
    fi

    if $deploy_k8s; then
        deploy_kubernetes
    fi

    if $deploy_helm; then
        deploy_helm
    fi

    if $run_tests; then
        run_smoke_tests
    fi

    log_info "Deployment completed successfully!"
}

main "$@"
