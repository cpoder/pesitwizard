# PeSIT Wizard Operations Manual

This manual provides day-to-day operational procedures for managing PeSIT Wizard in production.

## Table of Contents

- [Daily Operations Checklist](#daily-operations-checklist)
- [Monitoring & Observability](#monitoring--observability)
- [Backup & Recovery](#backup--recovery)
- [Certificate Management](#certificate-management)
- [Scaling Procedures](#scaling-procedures)
- [Maintenance Windows](#maintenance-windows)
- [Partner Management](#partner-management)
- [Troubleshooting Guide](#troubleshooting-guide)

---

## Daily Operations Checklist

### Morning Checks (09:00)

| Check | Command/URL | Expected Result |
|-------|-------------|-----------------|
| Service Health | `curl -sf https://api.pesit.example.com/actuator/health` | `{"status":"UP"}` |
| Cluster Status | Grafana: PeSIT Wizard Overview | All nodes healthy |
| Error Rate (24h) | Grafana: Error Rate panel | < 1% |
| Active Alerts | AlertManager UI | No critical/high alerts |
| Certificate Expiry | Grafana: TLS panel | > 30 days remaining |
| Database Health | Grafana: PostgreSQL dashboard | Replication lag < 1s |
| Transfer Volume | Grafana: Transfer metrics | Within normal range |

### Health Check Script

```bash
#!/bin/bash
# daily-health-check.sh

set -e

echo "=== PeSIT Wizard Daily Health Check ==="
echo "Date: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
echo ""

# API Health
echo "1. API Health Check"
HEALTH=$(curl -sf https://api.pesit.example.com/actuator/health)
STATUS=$(echo $HEALTH | jq -r '.status')
if [ "$STATUS" = "UP" ]; then
    echo "   [OK] API is healthy"
else
    echo "   [FAIL] API status: $STATUS"
    exit 1
fi

# Cluster Members
echo "2. Cluster Health"
MEMBERS=$(curl -sf https://api.pesit.example.com/api/v1/cluster/members | jq -r '.members | length')
if [ "$MEMBERS" -ge 3 ]; then
    echo "   [OK] Cluster has $MEMBERS members"
else
    echo "   [WARN] Cluster has only $MEMBERS members (expected 3+)"
fi

# Pod Status
echo "3. Pod Status"
kubectl get pods -n pesitwizard -o wide | grep -v "Running" | grep -v "NAME" && echo "   [WARN] Some pods not running" || echo "   [OK] All pods running"

# Recent Errors
echo "4. Recent Errors (last hour)"
ERROR_COUNT=$(kubectl logs -l app=pesitwizard-server -n pesitwizard --since=1h | grep -c "ERROR" || true)
if [ "$ERROR_COUNT" -lt 10 ]; then
    echo "   [OK] $ERROR_COUNT errors in last hour"
else
    echo "   [WARN] $ERROR_COUNT errors in last hour"
fi

# Transfer Stats
echo "5. Transfer Statistics (24h)"
curl -sf "https://api.pesit.example.com/api/v1/transfers/stats?period=24h" | jq .

echo ""
echo "=== Health Check Complete ==="
```

### Weekly Tasks

| Day | Task | Procedure |
|-----|------|-----------|
| Monday | Review weekly metrics | Generate weekly report from Grafana |
| Monday | Check backup integrity | Run backup verification |
| Tuesday | Review security alerts | Check Trivy/CodeQL findings |
| Wednesday | Capacity planning | Review resource utilization trends |
| Thursday | Partner connectivity | Verify all partner connections active |
| Friday | Update documentation | Document any changes made during week |

### Monthly Tasks

| Task | Procedure | Owner |
|------|-----------|-------|
| Security patches | Review and apply updates | DevOps |
| Certificate review | Check expiry dates, plan renewals | Security |
| Capacity review | Analyze growth, plan scaling | Platform |
| Disaster recovery test | Test backup restoration | DevOps |
| Access review | Audit user permissions | Security |
| SLO review | Review SLO achievement | Engineering |

---

## Monitoring & Observability

### Grafana Dashboards

| Dashboard | Purpose | URL |
|-----------|---------|-----|
| PeSIT Wizard Overview | High-level service health | /d/pesitwizard-overview |
| SLO Dashboard | SLI/SLO tracking | /d/pesitwizard-slo |
| Transfer Metrics | Transfer performance | /d/pesitwizard-transfers |
| JVM Metrics | Application internals | /d/pesitwizard-jvm |
| PostgreSQL | Database performance | /d/postgres |
| Kubernetes | Cluster resources | /d/kubernetes |

### Key Metrics to Monitor

#### Transfer Metrics
| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `pesitwizard_transfer_total{status="completed"}` | Successful transfers | N/A (informational) |
| `pesitwizard_transfer_total{status="failed"}` | Failed transfers | > 10% failure rate |
| `pesitwizard_transfer_duration_seconds` | Transfer duration | P95 > 300s |
| `pesitwizard_transfers_active` | Active transfers | > 180 (90% capacity) |
| `pesitwizard_bytes_transferred_total` | Data throughput | < 1KB/s with active transfers |

#### Connection Metrics
| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `pesitwizard_connections_active` | Active connections | > 180 |
| `pesitwizard_error_total{type="connection"}` | Connection errors | > 5/sec |
| `pesitwizard_error_total{type="protocol"}` | Protocol errors | > 1/sec |

#### System Metrics
| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `jvm_memory_used_bytes{area="heap"}` | Heap memory | > 85% of max |
| `process_cpu_usage` | CPU usage | > 80% |
| `hikaricp_connections_active` | DB connections | > 90% of pool |

### Log Analysis

#### Log Locations
```bash
# Application logs
kubectl logs -l app=pesitwizard-server -n pesitwizard

# Follow logs
kubectl logs -f pesitwizard-server-0 -n pesitwizard

# Previous container logs (after restart)
kubectl logs pesitwizard-server-0 -n pesitwizard --previous
```

#### Common Log Searches
```bash
# Error logs
kubectl logs -l app=pesitwizard-server -n pesitwizard | grep ERROR

# Transfer failures
kubectl logs -l app=pesitwizard-server -n pesitwizard | grep "transfer failed"

# Connection issues
kubectl logs -l app=pesitwizard-server -n pesitwizard | grep -i "connection"

# Partner-specific logs
kubectl logs -l app=pesitwizard-server -n pesitwizard | grep "PARTNER01"
```

#### Log Aggregation Query Examples (Loki/Elasticsearch)

```
# Loki: Errors by level
{namespace="pesitwizard"} |= "ERROR"

# Loki: Transfer failures
{namespace="pesitwizard"} |~ "transfer.*failed"

# Elasticsearch: Connection errors
{
  "query": {
    "bool": {
      "must": [
        { "match": { "kubernetes.namespace": "pesitwizard" }},
        { "match": { "message": "connection error" }}
      ]
    }
  }
}
```

---

## Backup & Recovery

### Backup Schedule

| Component | Frequency | Retention | Storage |
|-----------|-----------|-----------|---------|
| PostgreSQL (full) | Daily 02:00 UTC | 30 days | S3/GCS |
| PostgreSQL (incremental) | Every 6 hours | 7 days | S3/GCS |
| PostgreSQL (WAL) | Continuous | 7 days | S3/GCS |
| Configuration (Helm) | On change | 90 days | Git |
| Secrets (Vault) | Daily | 30 days | Vault backup |

### Backup Verification Procedure

Run weekly to verify backup integrity:

```bash
#!/bin/bash
# verify-backup.sh

set -e

echo "=== Backup Verification ==="
DATE=$(date +%Y-%m-%d)

# 1. List recent backups
echo "1. Recent backups:"
aws s3 ls s3://pesitwizard-backups/postgres/ --recursive | tail -5

# 2. Download latest backup
echo "2. Downloading latest backup..."
LATEST=$(aws s3 ls s3://pesitwizard-backups/postgres/ --recursive | sort | tail -1 | awk '{print $4}')
aws s3 cp "s3://pesitwizard-backups/postgres/$LATEST" /tmp/backup-test.dump

# 3. Create test database
echo "3. Creating test database..."
kubectl exec -it pesitwizard-db-0 -n pesitwizard -- \
  psql -U postgres -c "CREATE DATABASE backup_test;"

# 4. Restore backup
echo "4. Restoring backup..."
kubectl exec -it pesitwizard-db-0 -n pesitwizard -- \
  pg_restore -U postgres -d backup_test /tmp/backup-test.dump

# 5. Verify data
echo "5. Verifying data..."
kubectl exec -it pesitwizard-db-0 -n pesitwizard -- \
  psql -U postgres -d backup_test -c "SELECT COUNT(*) FROM transfers;"

# 6. Cleanup
echo "6. Cleanup..."
kubectl exec -it pesitwizard-db-0 -n pesitwizard -- \
  psql -U postgres -c "DROP DATABASE backup_test;"
rm /tmp/backup-test.dump

echo "=== Backup Verification Complete ==="
```

### Point-in-Time Recovery

To restore to a specific point in time:

```bash
# 1. Stop application
kubectl scale deployment/pesitwizard-server --replicas=0 -n pesitwizard

# 2. Perform PITR
kubectl exec -it pesitwizard-db-0 -n pesitwizard -- bash -c '
  pg_ctl stop
  rm -rf $PGDATA/*

  # Restore base backup
  pg_restore --target=$PGDATA backup.dump

  # Configure recovery
  cat > $PGDATA/recovery.conf << EOF
  restore_command = "aws s3 cp s3://pesitwizard-backups/wal/%f %p"
  recovery_target_time = "2024-01-15 14:30:00 UTC"
  recovery_target_action = promote
  EOF

  pg_ctl start
'

# 3. Wait for recovery
# Monitor logs for "database system is ready to accept connections"

# 4. Restart application
kubectl scale deployment/pesitwizard-server --replicas=3 -n pesitwizard
```

### Disaster Recovery

Full disaster recovery procedure:

1. **Provision new infrastructure** (Terraform)
   ```bash
   cd deployment/staging/terraform
   terraform apply -var="environment=dr"
   ```

2. **Restore database**
   ```bash
   # Get latest backup
   aws s3 cp s3://pesitwizard-backups/postgres/latest.dump /tmp/

   # Restore to new database
   pg_restore -h new-db-host -U pesitwizard -d pesitwizard /tmp/latest.dump
   ```

3. **Deploy application**
   ```bash
   helm install pesitwizard pesitwizard/pesitwizard-server \
     -f deployment/dr/values.yaml \
     -n pesitwizard
   ```

4. **Update DNS** to point to new infrastructure

5. **Verify functionality**
   ```bash
   ./deployment/scripts/smoke-test.sh
   ```

---

## Certificate Management

### Certificate Inventory

| Certificate | Purpose | Expiry Check | Renewal |
|-------------|---------|--------------|---------|
| PeSIT Server TLS | Protocol encryption | Monthly | 30 days before |
| API HTTPS | REST API encryption | Monthly | 30 days before |
| mTLS Client | Partner authentication | Monthly | 30 days before |
| Database TLS | DB connections | Monthly | 30 days before |

### Checking Certificate Expiry

```bash
# Check PeSIT certificate
echo | openssl s_client -connect pesit.example.com:5001 -servername pesit.example.com 2>/dev/null | \
  openssl x509 -noout -dates

# Check API certificate
echo | openssl s_client -connect api.pesit.example.com:443 -servername api.pesit.example.com 2>/dev/null | \
  openssl x509 -noout -dates

# Check Kubernetes secrets
kubectl get secret pesitwizard-tls -n pesitwizard -o jsonpath='{.data.tls\.crt}' | \
  base64 -d | openssl x509 -noout -dates
```

### Certificate Renewal Procedure

#### For Let's Encrypt (cert-manager)

Certificates renew automatically. Verify:
```bash
kubectl get certificate -n pesitwizard
kubectl describe certificate pesitwizard-tls -n pesitwizard
```

#### For Manual Certificates

1. **Generate CSR**
   ```bash
   openssl req -new -key server.key -out server.csr \
     -subj "/CN=pesit.example.com/O=Example Corp"
   ```

2. **Submit to CA** and receive signed certificate

3. **Update Kubernetes secret**
   ```bash
   kubectl create secret tls pesitwizard-tls \
     --cert=new-server.crt \
     --key=server.key \
     -n pesitwizard \
     --dry-run=client -o yaml | kubectl apply -f -
   ```

4. **Rolling restart** to pick up new certificate
   ```bash
   kubectl rollout restart deployment/pesitwizard-server -n pesitwizard
   ```

5. **Verify**
   ```bash
   echo | openssl s_client -connect pesit.example.com:5001 2>/dev/null | \
     openssl x509 -noout -dates
   ```

### Partner Certificate Management

When partners need to update their client certificates:

1. **Receive new certificate** from partner

2. **Add to truststore**
   ```bash
   keytool -import -alias partner-new \
     -file partner-new.crt \
     -keystore truststore.p12 \
     -storepass $TRUSTSTORE_PASSWORD
   ```

3. **Update secret**
   ```bash
   kubectl create secret generic pesitwizard-truststore \
     --from-file=truststore.p12=truststore.p12 \
     -n pesitwizard \
     --dry-run=client -o yaml | kubectl apply -f -
   ```

4. **Rolling restart**
   ```bash
   kubectl rollout restart deployment/pesitwizard-server -n pesitwizard
   ```

5. **Verify partner connectivity**

6. **Remove old certificate** after grace period
   ```bash
   keytool -delete -alias partner-old \
     -keystore truststore.p12 \
     -storepass $TRUSTSTORE_PASSWORD
   ```

---

## Scaling Procedures

### Horizontal Scaling (Add Pods)

When to scale: Active transfers > 80% capacity, latency increasing

```bash
# Check current scale
kubectl get deployment pesitwizard-server -n pesitwizard

# Scale up
kubectl scale deployment/pesitwizard-server --replicas=5 -n pesitwizard

# Verify pods are ready
kubectl wait --for=condition=ready pod -l app=pesitwizard-server \
  -n pesitwizard --timeout=300s

# Verify cluster membership
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
  curl -s localhost:8080/api/v1/cluster/members | jq .
```

### Vertical Scaling (Increase Resources)

When to scale: Memory pressure, CPU throttling

```bash
# Update resource limits
kubectl set resources deployment/pesitwizard-server \
  --requests=cpu=1500m,memory=3Gi \
  --limits=cpu=3000m,memory=6Gi \
  -n pesitwizard

# Or update via Helm
helm upgrade pesitwizard pesitwizard/pesitwizard-server \
  --set resources.requests.cpu=1500m \
  --set resources.requests.memory=3Gi \
  --set resources.limits.cpu=3000m \
  --set resources.limits.memory=6Gi \
  -n pesitwizard
```

### Database Scaling

#### Connection Pool Increase
```yaml
# Update application.yml or Helm values
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # Increase from default 20
      minimum-idle: 10
```

#### Database Instance Upgrade
```bash
# AWS RDS
aws rds modify-db-instance \
  --db-instance-identifier pesitwizard-db \
  --db-instance-class db.r6g.xlarge \
  --apply-immediately

# Monitor upgrade
aws rds describe-db-instances --db-instance-identifier pesitwizard-db
```

### Auto-scaling Configuration

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: pesitwizard-server
  namespace: pesitwizard
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: pesitwizard-server
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Pods
      pods:
        metric:
          name: pesitwizard_transfers_active
        target:
          type: AverageValue
          averageValue: 50
```

---

## Maintenance Windows

### Scheduling Maintenance

| Type | Frequency | Window | Duration |
|------|-----------|--------|----------|
| Patch updates | Weekly | Sun 02:00-06:00 UTC | 2-4 hours |
| Minor upgrades | Monthly | First Sun 02:00-06:00 UTC | 4 hours |
| Major upgrades | Quarterly | Planned window | 8 hours |
| Emergency patches | As needed | Immediate | Variable |

### Pre-Maintenance Checklist

- [ ] Notify partners 48 hours in advance (non-emergency)
- [ ] Create maintenance communication
- [ ] Verify backup is current
- [ ] Review runback procedure
- [ ] Ensure on-call coverage
- [ ] Test in staging first
- [ ] Prepare rollback artifacts

### Maintenance Procedure

```bash
#!/bin/bash
# maintenance-upgrade.sh

set -e

VERSION=$1
if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version>"
    exit 1
fi

echo "=== Starting Maintenance Window ==="
echo "Target Version: $VERSION"
echo "Start Time: $(date -u)"

# 1. Enable maintenance mode (if supported)
echo "1. Enabling maintenance mode..."
kubectl annotate deployment/pesitwizard-server maintenance=true -n pesitwizard

# 2. Wait for in-flight transfers
echo "2. Waiting for in-flight transfers to complete..."
while true; do
    ACTIVE=$(kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
      curl -s localhost:8080/actuator/metrics/pesitwizard.transfers.active | jq '.measurements[0].value')
    if [ "$ACTIVE" = "0" ]; then
        break
    fi
    echo "   $ACTIVE transfers still active, waiting..."
    sleep 30
done

# 3. Take backup
echo "3. Creating pre-upgrade backup..."
kubectl exec -it pesitwizard-db-0 -n pesitwizard -- \
  pg_dump -U pesitwizard pesitwizard > /tmp/pre-upgrade-backup.dump

# 4. Perform upgrade
echo "4. Performing upgrade to $VERSION..."
helm upgrade pesitwizard pesitwizard/pesitwizard-server \
  --set image.tag=$VERSION \
  -n pesitwizard \
  --wait --timeout 10m

# 5. Verify upgrade
echo "5. Verifying upgrade..."
kubectl wait --for=condition=ready pod -l app=pesitwizard-server \
  -n pesitwizard --timeout=300s

ACTUAL_VERSION=$(kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
  curl -s localhost:8080/actuator/info | jq -r '.build.version')
echo "   Deployed version: $ACTUAL_VERSION"

# 6. Run smoke tests
echo "6. Running smoke tests..."
./deployment/scripts/smoke-test.sh

# 7. Disable maintenance mode
echo "7. Disabling maintenance mode..."
kubectl annotate deployment/pesitwizard-server maintenance- -n pesitwizard

echo "=== Maintenance Complete ==="
echo "End Time: $(date -u)"
```

### Post-Maintenance Verification

```bash
# Health check
curl -sf https://api.pesit.example.com/actuator/health | jq .

# Version verification
curl -sf https://api.pesit.example.com/actuator/info | jq .build

# Cluster status
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
  curl -s localhost:8080/api/v1/cluster/members | jq .

# Test transfer
curl -X POST https://api.pesit.example.com/api/v1/transfers/send \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"partnerId": "TEST0001", "filePath": "/test/hello.txt"}'

# Monitor for 30 minutes
# Check error rates, latency, alerts
```

---

## Partner Management

### Adding a New Partner

1. **Gather partner information**
   - Partner ID (8 characters)
   - Network address (IP/hostname, port)
   - TLS certificate (if mTLS required)
   - Contact information

2. **Create partner configuration**
   ```bash
   curl -X POST https://api.pesit.example.com/api/v1/config/partners \
     -H "Authorization: Bearer $ADMIN_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{
       "partnerId": "NEWPART1",
       "name": "New Partner Corp",
       "host": "pesit.newpartner.com",
       "port": 6502,
       "tlsEnabled": true,
       "description": "New partner for file exchange"
     }'
   ```

3. **Configure firewall** (if needed)
   ```bash
   # Add partner IP to allow list
   kubectl patch networkpolicy pesitwizard-ingress -n pesitwizard --type=json \
     -p '[{"op": "add", "path": "/spec/ingress/0/from/-", "value": {"ipBlock": {"cidr": "203.0.113.0/24"}}}]'
   ```

4. **Add partner certificate** (if mTLS)
   ```bash
   keytool -import -alias NEWPART1 \
     -file newpartner.crt \
     -keystore truststore.p12
   ```

5. **Test connectivity**
   ```bash
   curl -X POST https://api.pesit.example.com/api/v1/config/partners/NEWPART1/test \
     -H "Authorization: Bearer $API_KEY"
   ```

### Removing a Partner

1. **Notify partner** of decommission date

2. **Verify no active transfers**
   ```bash
   curl -sf "https://api.pesit.example.com/api/v1/transfers?partnerId=OLDPART1&status=active"
   ```

3. **Disable partner**
   ```bash
   curl -X PATCH https://api.pesit.example.com/api/v1/config/partners/OLDPART1 \
     -H "Authorization: Bearer $ADMIN_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"enabled": false}'
   ```

4. **Remove from firewall**

5. **Remove certificate** (after grace period)
   ```bash
   keytool -delete -alias OLDPART1 -keystore truststore.p12
   ```

6. **Delete partner** (after retention period)
   ```bash
   curl -X DELETE https://api.pesit.example.com/api/v1/config/partners/OLDPART1 \
     -H "Authorization: Bearer $ADMIN_API_KEY"
   ```

### Partner Connectivity Testing

```bash
#!/bin/bash
# test-partner-connectivity.sh

echo "=== Partner Connectivity Test ==="

PARTNERS=$(curl -sf https://api.pesit.example.com/api/v1/config/partners \
  -H "Authorization: Bearer $API_KEY" | jq -r '.[].partnerId')

for PARTNER in $PARTNERS; do
    echo -n "Testing $PARTNER... "
    RESULT=$(curl -sf -X POST "https://api.pesit.example.com/api/v1/config/partners/$PARTNER/test" \
      -H "Authorization: Bearer $API_KEY" | jq -r '.status')

    if [ "$RESULT" = "success" ]; then
        echo "[OK]"
    else
        echo "[FAIL] - $RESULT"
    fi
done
```

---

## Troubleshooting Guide

### Common Issues

#### Transfer Stuck in Progress

**Symptoms**: Transfer shows "IN_PROGRESS" for extended time

**Diagnosis**:
```bash
# Check transfer status
curl -sf "https://api.pesit.example.com/api/v1/transfers/{transferId}" \
  -H "Authorization: Bearer $API_KEY" | jq .

# Check logs for transfer
kubectl logs -l app=pesitwizard-server -n pesitwizard | grep "{transferId}"
```

**Resolution**:
- Check partner connectivity
- Check for network timeouts
- Cancel and retry if stuck > 1 hour
  ```bash
  curl -X POST "https://api.pesit.example.com/api/v1/transfers/{transferId}/cancel" \
    -H "Authorization: Bearer $API_KEY"
  ```

---

#### High Memory Usage

**Symptoms**: JVM heap usage > 85%, GC pauses increasing

**Diagnosis**:
```bash
# Check memory metrics
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
  curl -s localhost:8080/actuator/metrics/jvm.memory.used | jq .

# Check GC stats
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
  curl -s localhost:8080/actuator/metrics/jvm.gc.pause | jq .
```

**Resolution**:
1. Check for memory leaks (heap dump analysis)
2. Increase heap size if justified
3. Rolling restart to clear memory

---

#### Database Connection Exhaustion

**Symptoms**: "Cannot acquire connection from pool" errors

**Diagnosis**:
```bash
# Check HikariCP metrics
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
  curl -s localhost:8080/actuator/metrics/hikaricp.connections | jq .

# Check database connections
kubectl exec -it pesitwizard-db-0 -n pesitwizard -- \
  psql -U postgres -c "SELECT count(*) FROM pg_stat_activity;"
```

**Resolution**:
1. Check for connection leaks
2. Increase pool size (if database can handle it)
3. Check for long-running queries
4. Rolling restart as temporary fix

---

#### Cluster Not Forming

**Symptoms**: Pods running but not joining cluster

**Diagnosis**:
```bash
# Check each pod's cluster view
for i in 0 1 2; do
    echo "=== Pod $i ==="
    kubectl exec -it pesitwizard-server-$i -n pesitwizard -- \
      curl -s localhost:8080/api/v1/cluster/members
done

# Check network connectivity between pods
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- \
  nc -zv pesitwizard-server-1.pesitwizard-server 7000
```

**Resolution**:
1. Check DNS resolution
2. Check network policies
3. Check cluster configuration
4. Restart cluster (scale to 1, then back to 3)

---

#### TLS Handshake Failures

**Symptoms**: Partners unable to connect, TLS errors in logs

**Diagnosis**:
```bash
# Test TLS from external
openssl s_client -connect pesit.example.com:5001 -servername pesit.example.com

# Check certificate
echo | openssl s_client -connect pesit.example.com:5001 2>/dev/null | \
  openssl x509 -noout -text

# Check logs for TLS errors
kubectl logs -l app=pesitwizard-server -n pesitwizard | grep -i "ssl\|tls\|handshake"
```

**Resolution**:
1. Verify certificate chain is complete
2. Check cipher suite compatibility
3. Verify TLS version support
4. Check certificate expiry

---

### Diagnostic Commands Reference

```bash
# Pod information
kubectl get pods -n pesitwizard -o wide
kubectl describe pod pesitwizard-server-0 -n pesitwizard
kubectl top pods -n pesitwizard

# Logs
kubectl logs pesitwizard-server-0 -n pesitwizard
kubectl logs pesitwizard-server-0 -n pesitwizard --previous
kubectl logs -l app=pesitwizard-server -n pesitwizard --tail=100

# Exec into pod
kubectl exec -it pesitwizard-server-0 -n pesitwizard -- bash

# Actuator endpoints
curl localhost:8080/actuator/health
curl localhost:8080/actuator/info
curl localhost:8080/actuator/metrics
curl localhost:8080/actuator/prometheus
curl localhost:8080/actuator/threaddump

# Network testing
nc -zv hostname port
curl -v https://hostname/path
openssl s_client -connect hostname:port

# Database
psql -h localhost -U pesitwizard -d pesitwizard
SELECT * FROM pg_stat_activity;
SELECT * FROM pg_stat_replication;
```

---

## Appendix

### Environment Variables Reference

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profiles | `production` |
| `SPRING_DATASOURCE_URL` | Database JDBC URL | Required |
| `SPRING_DATASOURCE_USERNAME` | Database username | Required |
| `SPRING_DATASOURCE_PASSWORD` | Database password | Required |
| `PESIT_SERVER_PORT` | PeSIT protocol port | `6502` |
| `PESIT_TLS_ENABLED` | Enable TLS for PeSIT | `true` |
| `PESIT_TLS_KEYSTORE` | Path to keystore | Required if TLS |
| `PESIT_TLS_KEYSTORE_PASSWORD` | Keystore password | Required if TLS |
| `PESITWIZARD_CLUSTER_ENABLED` | Enable clustering | `true` |
| `PESITWIZARD_SECURITY_ENCRYPTION_KEY` | AES encryption key | Required |

### Port Reference

| Port | Protocol | Service |
|------|----------|---------|
| 6502 | TCP | PeSIT protocol |
| 7000 | TCP | Cluster communication |
| 8080 | HTTP | REST API / Actuator |
| 8443 | HTTPS | REST API (TLS) |
| 9090 | HTTP | Prometheus metrics |

### File Locations

| Path | Description |
|------|-------------|
| `/app/pesitwizard-server.jar` | Application JAR |
| `/app/config/application.yml` | Configuration |
| `/etc/pesitwizard/tls/` | TLS certificates |
| `/var/log/pesitwizard/` | Application logs |
| `/data/pesitwizard/` | Data directory |

### Support Contacts

| Role | Contact |
|------|---------|
| On-call | PagerDuty |
| Platform Team | #pesitwizard-platform (Slack) |
| Security | security@example.com |
| Database Admin | #dba (Slack) |
