# PeSIT Wizard Production Deployment Guide

This guide provides comprehensive instructions for deploying PeSIT Wizard to a production environment.

## Table of Contents

- [Pre-Deployment Checklist](#pre-deployment-checklist)
- [Infrastructure Requirements](#infrastructure-requirements)
- [Network Configuration](#network-configuration)
- [TLS Certificate Setup](#tls-certificate-setup)
- [Database Preparation](#database-preparation)
- [Secret Configuration](#secret-configuration)
- [Load Balancer Setup](#load-balancer-setup)
- [Deployment Steps](#deployment-steps)
- [Post-Deployment Validation](#post-deployment-validation)
- [Rollback Procedure](#rollback-procedure)

---

## Pre-Deployment Checklist

Complete all items before proceeding with deployment:

### Security
- [ ] Security scan passed (no critical/high findings)
- [ ] Penetration test completed and findings remediated
- [ ] TLS certificates obtained and validated
- [ ] Secrets configured in Vault or encrypted storage
- [ ] Rate limiting configured and tested
- [ ] Security headers verified

### Infrastructure
- [ ] Kubernetes cluster provisioned (3+ nodes)
- [ ] Database provisioned with replication
- [ ] Redis cluster provisioned (for rate limiting)
- [ ] Network policies configured
- [ ] Storage classes defined
- [ ] Load balancer provisioned

### Configuration
- [ ] Helm values reviewed for production
- [ ] Resource limits set appropriately
- [ ] Environment variables documented
- [ ] Partner configurations prepared
- [ ] Connector credentials encrypted

### Operations
- [ ] Monitoring dashboards deployed
- [ ] Alerting rules configured
- [ ] On-call rotation established
- [ ] Runbooks reviewed by operations team
- [ ] Backup/restore procedure tested
- [ ] Rollback procedure documented and tested

---

## Infrastructure Requirements

### Kubernetes Cluster

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Nodes | 3 | 5+ |
| CPU per node | 4 cores | 8 cores |
| Memory per node | 16 GB | 32 GB |
| Disk per node | 100 GB SSD | 200 GB NVMe |
| Kubernetes version | 1.28+ | 1.29+ |

### PeSIT Wizard Pods

| Component | Replicas | CPU Request | CPU Limit | Memory Request | Memory Limit |
|-----------|----------|-------------|-----------|----------------|--------------|
| pesitwizard-server | 3 | 1000m | 2000m | 2Gi | 4Gi |
| pesitwizard-client | 2 | 500m | 1000m | 1Gi | 2Gi |

### Database (PostgreSQL)

| Tier | vCPU | Memory | Storage | IOPS |
|------|------|--------|---------|------|
| Small (< 100 transfers/day) | 2 | 8 GB | 100 GB | 3000 |
| Medium (100-1000 transfers/day) | 4 | 16 GB | 250 GB | 6000 |
| Large (1000+ transfers/day) | 8 | 32 GB | 500 GB | 12000 |

**Requirements:**
- PostgreSQL 15+
- Replication enabled (1 primary + 2 replicas)
- Point-in-time recovery (PITR) enabled
- Automated backups every 6 hours
- SSL/TLS connections required

### Redis (for Rate Limiting)

| Configuration | Nodes | Memory per Node |
|---------------|-------|-----------------|
| Minimal | 3 | 1 GB |
| Production | 6 (3 primary + 3 replica) | 2 GB |

---

## Network Configuration

### Required Ports

| Port | Protocol | Direction | Purpose |
|------|----------|-----------|---------|
| 6502 | TCP | Inbound | PeSIT protocol |
| 8080 | TCP | Inbound | REST API / Management |
| 8443 | TCP | Inbound | REST API (HTTPS) |
| 5432 | TCP | Internal | PostgreSQL |
| 6379 | TCP | Internal | Redis |
| 9090 | TCP | Internal | Prometheus metrics |
| 7000 | TCP | Internal | Cluster communication |

### Firewall Rules

```bash
# Inbound - Public-facing
iptables -A INPUT -p tcp --dport 6502 -j ACCEPT  # PeSIT
iptables -A INPUT -p tcp --dport 8443 -j ACCEPT  # HTTPS API

# Inbound - Internal network only
iptables -A INPUT -s 10.0.0.0/8 -p tcp --dport 6502 -j ACCEPT  # PeSIT
iptables -A INPUT -s 10.0.0.0/8 -p tcp --dport 8080 -j ACCEPT  # HTTP API
iptables -A INPUT -s 10.0.0.0/8 -p tcp --dport 9090 -j ACCEPT  # Prometheus

# Drop non-TLS from public
iptables -A INPUT -p tcp --dport 6502 -j DROP
iptables -A INPUT -p tcp --dport 8080 -j DROP
```

### Kubernetes Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: pesitwizard-ingress
  namespace: pesitwizard
spec:
  podSelector:
    matchLabels:
      app: pesitwizard-server
  policyTypes:
    - Ingress
  ingress:
    # Allow PeSIT from partners
    - from:
        - ipBlock:
            cidr: 0.0.0.0/0
      ports:
        - protocol: TCP
          port: 6502
    # Allow API from load balancer
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - protocol: TCP
          port: 8080
    # Allow metrics from monitoring
    - from:
        - namespaceSelector:
            matchLabels:
              name: monitoring
      ports:
        - protocol: TCP
          port: 9090
```

### DNS Configuration

Configure DNS records for:

| Record | Type | Value | Purpose |
|--------|------|-------|---------|
| pesit.example.com | A | Load balancer IP | PeSIT protocol endpoint |
| api.pesit.example.com | A | Load balancer IP | REST API endpoint |
| *.pesit.example.com | CNAME | pesit.example.com | Wildcard for partners |

---

## TLS Certificate Setup

### Certificate Requirements

- **Algorithm**: RSA 2048+ or ECDSA P-256+
- **Validity**: 1 year maximum
- **SAN**: Include all hostnames
- **Chain**: Complete certificate chain required

### Obtaining Certificates

#### Option 1: Let's Encrypt (cert-manager)

```yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: pesitwizard-tls
  namespace: pesitwizard
spec:
  secretName: pesitwizard-tls-secret
  issuerRef:
    name: letsencrypt-prod
    kind: ClusterIssuer
  commonName: pesit.example.com
  dnsNames:
    - pesit.example.com
    - api.pesit.example.com
```

#### Option 2: Internal CA

```bash
# Generate CA (if not exists)
openssl genrsa -out ca.key 4096
openssl req -new -x509 -days 3650 -key ca.key -out ca.crt \
  -subj "/CN=PeSIT Wizard CA/O=Example Corp"

# Generate server certificate
openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr \
  -subj "/CN=pesit.example.com/O=Example Corp"

# Sign certificate
openssl x509 -req -days 365 -in server.csr -CA ca.crt -CAkey ca.key \
  -CAcreateserial -out server.crt \
  -extfile <(printf "subjectAltName=DNS:pesit.example.com,DNS:api.pesit.example.com")

# Create PKCS12 keystore
openssl pkcs12 -export -in server.crt -inkey server.key \
  -out server.p12 -name pesitwizard \
  -password pass:changeit
```

### Installing Certificates

```bash
# Create Kubernetes secret
kubectl create secret tls pesitwizard-tls \
  --cert=server.crt \
  --key=server.key \
  -n pesitwizard

# Create keystore secret for PeSIT protocol
kubectl create secret generic pesitwizard-keystore \
  --from-file=keystore.p12=server.p12 \
  --from-literal=keystore-password=changeit \
  -n pesitwizard
```

### Mutual TLS (mTLS) Setup

For partners requiring client certificate authentication:

```yaml
# Partner trust store
apiVersion: v1
kind: Secret
metadata:
  name: partner-truststore
  namespace: pesitwizard
type: Opaque
data:
  truststore.p12: <base64-encoded-truststore>
  truststore-password: <base64-encoded-password>
```

Configuration in `values.yaml`:

```yaml
pesit:
  tls:
    enabled: true
    keyStore: /etc/pesitwizard/tls/keystore.p12
    keyStorePassword: ${KEYSTORE_PASSWORD}
    trustStore: /etc/pesitwizard/tls/truststore.p12
    trustStorePassword: ${TRUSTSTORE_PASSWORD}
    clientAuth: REQUIRE  # or WANT for optional
```

---

## Database Preparation

### Create Database and User

```sql
-- Create database
CREATE DATABASE pesitwizard
  ENCODING 'UTF8'
  LC_COLLATE 'en_US.UTF-8'
  LC_CTYPE 'en_US.UTF-8';

-- Create application user
CREATE USER pesitwizard WITH PASSWORD '<strong-password>';
GRANT ALL PRIVILEGES ON DATABASE pesitwizard TO pesitwizard;

-- Create read-only user for monitoring
CREATE USER pesitwizard_readonly WITH PASSWORD '<readonly-password>';
GRANT CONNECT ON DATABASE pesitwizard TO pesitwizard_readonly;
GRANT USAGE ON SCHEMA public TO pesitwizard_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO pesitwizard_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO pesitwizard_readonly;
```

### Configure Connection Pooling

Recommended PgBouncer configuration:

```ini
[databases]
pesitwizard = host=postgres-primary port=5432 dbname=pesitwizard

[pgbouncer]
listen_addr = 0.0.0.0
listen_port = 6432
auth_type = scram-sha-256
pool_mode = transaction
max_client_conn = 1000
default_pool_size = 50
min_pool_size = 10
reserve_pool_size = 10
```

### Verify Schema Migration

```bash
# Check Flyway migration status
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
  java -jar /app/pesitwizard-server.jar flyway info
```

---

## Secret Configuration

### Required Secrets

| Secret | Purpose | Storage |
|--------|---------|---------|
| `db-password` | PostgreSQL password | Vault/K8s Secret |
| `redis-password` | Redis password | Vault/K8s Secret |
| `keystore-password` | TLS keystore password | Vault/K8s Secret |
| `encryption-key` | AES key for secrets | Vault/K8s Secret |
| `api-key-salt` | Salt for API key hashing | Vault/K8s Secret |

### Using HashiCorp Vault

```bash
# Enable KV secrets engine
vault secrets enable -path=pesitwizard kv-v2

# Store secrets
vault kv put pesitwizard/database \
  username=pesitwizard \
  password=<strong-password>

vault kv put pesitwizard/tls \
  keystore-password=<keystore-password>

vault kv put pesitwizard/encryption \
  key=<32-byte-base64-encoded-key>
```

Kubernetes integration with Vault Agent:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: pesitwizard
  namespace: pesitwizard
  annotations:
    vault.hashicorp.com/agent-inject: "true"
    vault.hashicorp.com/role: "pesitwizard"
    vault.hashicorp.com/agent-inject-secret-db: "pesitwizard/database"
    vault.hashicorp.com/agent-inject-template-db: |
      {{- with secret "pesitwizard/database" -}}
      export DB_USERNAME={{ .Data.data.username }}
      export DB_PASSWORD={{ .Data.data.password }}
      {{- end -}}
```

### Using Kubernetes Secrets

```bash
# Create secrets
kubectl create secret generic pesitwizard-db \
  --from-literal=username=pesitwizard \
  --from-literal=password=<strong-password> \
  -n pesitwizard

kubectl create secret generic pesitwizard-encryption \
  --from-literal=key=<32-byte-base64-encoded-key> \
  -n pesitwizard
```

### Environment Variable Mapping

```yaml
env:
  - name: SPRING_DATASOURCE_USERNAME
    valueFrom:
      secretKeyRef:
        name: pesitwizard-db
        key: username
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: pesitwizard-db
        key: password
  - name: PESITWIZARD_SECURITY_ENCRYPTION_KEY
    valueFrom:
      secretKeyRef:
        name: pesitwizard-encryption
        key: key
```

---

## Load Balancer Setup

### Layer 4 (TCP) Load Balancer for PeSIT Protocol

PeSIT protocol requires TCP passthrough (not HTTP):

```yaml
apiVersion: v1
kind: Service
metadata:
  name: pesitwizard-pesit-lb
  namespace: pesitwizard
  annotations:
    # AWS NLB
    service.beta.kubernetes.io/aws-load-balancer-type: nlb
    service.beta.kubernetes.io/aws-load-balancer-cross-zone-load-balancing-enabled: "true"
spec:
  type: LoadBalancer
  selector:
    app: pesitwizard-server
  ports:
    - name: pesit
      port: 6502
      targetPort: 6502
      protocol: TCP
```

### Layer 7 (HTTP) Load Balancer for REST API

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: pesitwizard-api
  namespace: pesitwizard
  annotations:
    kubernetes.io/ingress.class: nginx
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-body-size: "100m"
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
    - hosts:
        - api.pesit.example.com
      secretName: pesitwizard-api-tls
  rules:
    - host: api.pesit.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: pesitwizard-server
                port:
                  number: 8080
```

### Health Check Configuration

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3
```

---

## Deployment Steps

### Step 1: Prepare Namespace

```bash
# Create namespace
kubectl create namespace pesitwizard

# Apply resource quotas
kubectl apply -f deployment/kubernetes/namespace.yaml
```

### Step 2: Deploy Secrets

```bash
# Deploy secrets (Vault or K8s secrets)
kubectl apply -f deployment/secrets/

# Verify secrets
kubectl get secrets -n pesitwizard
```

### Step 3: Deploy Database

```bash
# If using Helm chart for PostgreSQL
helm install pesitwizard-db bitnami/postgresql \
  -f deployment/database/values.yaml \
  -n pesitwizard

# Wait for database to be ready
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=postgresql \
  -n pesitwizard --timeout=300s
```

### Step 4: Deploy Redis

```bash
# Deploy Redis cluster
helm install pesitwizard-redis bitnami/redis \
  -f deployment/redis/values.yaml \
  -n pesitwizard

# Wait for Redis to be ready
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=redis \
  -n pesitwizard --timeout=300s
```

### Step 5: Deploy PeSIT Wizard

```bash
# Add Helm repository (if using remote repo)
helm repo add pesitwizard https://charts.pesitwizard.io

# Deploy with production values
helm install pesitwizard pesitwizard/pesitwizard-server \
  -f deployment/production/values.yaml \
  -n pesitwizard \
  --set image.tag=1.0.0 \
  --wait --timeout 10m

# Verify deployment
kubectl get pods -n pesitwizard
kubectl get svc -n pesitwizard
```

### Step 6: Verify Cluster Formation

```bash
# Check cluster status
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
  curl -s http://localhost:8080/actuator/health | jq .

# Check cluster members
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
  curl -s http://localhost:8080/api/v1/cluster/members | jq .
```

### Step 7: Run Schema Migrations

```bash
# Migrations run automatically on startup, verify:
kubectl logs pesitwizard-server-0 -n pesitwizard | grep -i flyway
```

### Step 8: Deploy Monitoring

```bash
# Deploy Prometheus/Grafana stack
helm install monitoring prometheus-community/kube-prometheus-stack \
  -f deployment/observability/values.yaml \
  -n monitoring

# Import dashboards
kubectl apply -f deployment/observability/dashboards/
```

---

## Post-Deployment Validation

### Health Checks

```bash
# API health
curl -sf https://api.pesit.example.com/actuator/health | jq .

# PeSIT port connectivity
openssl s_client -connect pesit.example.com:5001 -servername pesit.example.com

# Cluster health
curl -sf https://api.pesit.example.com/api/v1/cluster/health | jq .
```

### Functional Tests

```bash
# Test file transfer (requires partner or test server)
curl -X POST https://api.pesit.example.com/api/v1/transfers/send \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "partnerId": "TEST0001",
    "filePath": "/test/hello.txt",
    "destinationPath": "/incoming/hello.txt"
  }'
```

### Performance Validation

```bash
# Run load test
cd deployment/loadtest
./run-baseline-test.sh --target https://api.pesit.example.com

# Expected results:
# - P95 latency < 30s for transfers
# - Error rate < 1%
# - Throughput meets baseline
```

### Security Validation

```bash
# TLS configuration
nmap --script ssl-enum-ciphers -p 5001 pesit.example.com

# Security headers
curl -I https://api.pesit.example.com/actuator/health

# Expected headers:
# X-Content-Type-Options: nosniff
# X-Frame-Options: DENY
# Strict-Transport-Security: max-age=31536000; includeSubDomains
# Content-Security-Policy: default-src 'self'
```

### Monitoring Validation

```bash
# Check metrics are being scraped
curl -sf http://prometheus:9090/api/v1/targets | jq '.data.activeTargets[] | select(.labels.job=="pesitwizard")'

# Verify dashboards
# Access Grafana at https://grafana.example.com
# Check PeSIT Wizard SLO dashboard
```

---

## Rollback Procedure

### Automatic Rollback (Helm)

Helm automatically maintains release history:

```bash
# View release history
helm history pesitwizard -n pesitwizard

# Rollback to previous release
helm rollback pesitwizard -n pesitwizard

# Rollback to specific revision
helm rollback pesitwizard 2 -n pesitwizard
```

### Manual Rollback Steps

If Helm rollback fails:

#### Step 1: Stop Traffic

```bash
# Scale down to stop new connections
kubectl scale deployment pesitwizard-server --replicas=0 -n pesitwizard

# Wait for pods to terminate
kubectl wait --for=delete pod -l app=pesitwizard-server \
  -n pesitwizard --timeout=120s
```

#### Step 2: Restore Previous Version

```bash
# Deploy previous image version
kubectl set image deployment/pesitwizard-server \
  pesitwizard-server=pesitwizard/pesitwizard-server:1.0.0-previous \
  -n pesitwizard
```

#### Step 3: Database Rollback (if needed)

**WARNING**: Only perform if database schema changes caused issues.

```bash
# Connect to database
kubectl exec -it pesitwizard-db-0 -n pesitwizard -- psql -U pesitwizard

# Run Flyway undo (if available)
java -jar flyway.jar -url=jdbc:postgresql://localhost:5432/pesitwizard \
  -user=pesitwizard -password=$DB_PASSWORD undo

# OR restore from backup
pg_restore -h localhost -U pesitwizard -d pesitwizard backup.dump
```

#### Step 4: Scale Back Up

```bash
# Scale back to normal
kubectl scale deployment pesitwizard-server --replicas=3 -n pesitwizard

# Verify pods are healthy
kubectl wait --for=condition=ready pod -l app=pesitwizard-server \
  -n pesitwizard --timeout=300s
```

#### Step 5: Verify Rollback

```bash
# Check application version
curl -sf https://api.pesit.example.com/actuator/info | jq .

# Run health checks
curl -sf https://api.pesit.example.com/actuator/health | jq .

# Run smoke tests
./deployment/scripts/smoke-test.sh
```

### Emergency Contacts

If rollback fails or critical issues occur:

| Role | Contact | Escalation |
|------|---------|------------|
| On-call Engineer | PagerDuty | Immediate |
| Platform Lead | +1-XXX-XXX-XXXX | 15 min |
| Engineering Manager | +1-XXX-XXX-XXXX | 30 min |

---

## Appendix

### Production Configuration Reference

See `deployment/production/values.yaml` for complete configuration options.

### Common Issues

| Issue | Cause | Resolution |
|-------|-------|------------|
| Pods stuck in Pending | Insufficient resources | Scale cluster or reduce requests |
| Database connection errors | Wrong credentials or network | Check secrets and network policies |
| TLS handshake failures | Certificate mismatch | Verify certificate SAN and chain |
| Cluster split-brain | Network partition | Check network policies, restart pods |

### Additional Resources

- [Troubleshooting Guide](./TROUBLESHOOTING.md)
- [High Availability Guide](./HIGH_AVAILABILITY.md)
- [Performance Tuning Guide](./PERFORMANCE_TUNING.md)
- [Security Audit Checklist](./SECURITY_AUDIT_CHECKLIST.md)
