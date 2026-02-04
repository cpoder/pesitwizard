# Operations Runbook

Maintenance and operations procedures for PeSIT Wizard.

## Daily Operations

### Health Check

```bash
#!/bin/bash
# daily-health-check.sh

echo "=== PeSIT Wizard Health Check ==="
echo "Date: $(date)"
echo ""

# 1. API Health
echo "1. API Health..."
curl -sf http://localhost:8080/actuator/health | jq -r '.status' || echo "FAIL"
curl -sf http://localhost:8080/actuator/health | jq -r '.status' || echo "FAIL"

# 2. Disk space
echo ""
echo "2. Disk Space..."
df -h /data | tail -1 | awk '{print "Used: "$5" Free: "$4}'

# 3. Transfers last 24h
echo ""
echo "3. Transfers (last 24h)..."
curl -sf "http://localhost:8080/api/v1/transfers?from=$(date -d '24 hours ago' +%Y-%m-%d)" | \
  jq '{total: .totalElements, completed: [.content[] | select(.status=="COMPLETED")] | length, failed: [.content[] | select(.status=="FAILED")] | length}'

# 4. Certificate expiry
echo ""
echo "4. Certificate Expiry..."
curl -sf http://localhost:8080/api/v1/certificates | jq -r '.[] | "\(.alias): \(.expiresAt)"'
```

### Transfer Monitoring

```bash
# In-progress transfers
curl -s http://localhost:8080/api/v1/transfers?status=IN_PROGRESS | jq

# Failed transfers (last 24h)
curl -s "http://localhost:8080/api/v1/transfers?status=FAILED&from=$(date -d '24 hours ago' +%Y-%m-%d)" | jq

# Statistics
curl -s http://localhost:8080/api/v1/transfers/stats | jq
```

---

## Backup and Restore

### Backup

#### Database (H2 embedded)

```bash
#!/bin/bash
# backup-database.sh

BACKUP_DIR=/backup/pesitwizard
DATE=$(date +%Y%m%d_%H%M%S)

# Gracefully stop the service (optional but recommended)
# systemctl stop pesitwizard-client

# Back up the H2 database
mkdir -p $BACKUP_DIR
cp -r /app/db/* $BACKUP_DIR/db_$DATE/

# Back up the configuration
cp /app/application.yml $BACKUP_DIR/config_$DATE.yml

echo "Backup completed: $BACKUP_DIR/*_$DATE"

# Rotation: keep 30 days
find $BACKUP_DIR -type d -mtime +30 -exec rm -rf {} \;
```

#### PostgreSQL Database

```bash
#!/bin/bash
# backup-postgresql.sh

BACKUP_DIR=/backup/pesitwizard
DATE=$(date +%Y%m%d_%H%M%S)

# Dump PostgreSQL
pg_dump -h localhost -U pesitwizard -d pesitwizard > $BACKUP_DIR/pesitwizard_$DATE.sql

# Compression
gzip $BACKUP_DIR/pesitwizard_$DATE.sql

echo "Backup completed: $BACKUP_DIR/pesitwizard_$DATE.sql.gz"

# Rotation
find $BACKUP_DIR -name "*.sql.gz" -mtime +30 -delete
```

#### Certificates

```bash
#!/bin/bash
# backup-certificates.sh

BACKUP_DIR=/backup/pesitwizard/certs
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# Export all certificates via API
curl -s -u admin:admin http://localhost:8080/api/v1/certificates/export > $BACKUP_DIR/certs_$DATE.json

# Back up keystores
cp /app/keystores/* $BACKUP_DIR/keystores_$DATE/ 2>/dev/null || true

echo "Certificates backup completed"
```

### Restore

#### Restore H2 Database

```bash
#!/bin/bash
# restore-database.sh

BACKUP_PATH=$1

if [ -z "$BACKUP_PATH" ]; then
  echo "Usage: $0 /backup/pesitwizard/db_YYYYMMDD_HHMMSS"
  exit 1
fi

# Stop the service
systemctl stop pesitwizard-client

# Restore
rm -rf /app/db/*
cp -r $BACKUP_PATH/* /app/db/

# Restart
systemctl start pesitwizard-client

echo "Database restored from $BACKUP_PATH"
```

#### Restore PostgreSQL

```bash
#!/bin/bash
# restore-postgresql.sh

BACKUP_FILE=$1

if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: $0 /backup/pesitwizard/pesitwizard_YYYYMMDD.sql.gz"
  exit 1
fi

# Terminate connections
psql -h localhost -U postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='pesitwizard';"

# Restore
gunzip -c $BACKUP_FILE | psql -h localhost -U pesitwizard -d pesitwizard

echo "Database restored from $BACKUP_FILE"
```

---

## Scheduled Maintenance

### Purge Old Transfers

```bash
#!/bin/bash
# purge-old-transfers.sh

RETENTION_DAYS=${1:-90}

echo "Purging transfers older than $RETENTION_DAYS days..."

# Via API (if endpoint is available)
curl -X DELETE "http://localhost:8080/api/v1/transfers/purge?olderThanDays=$RETENTION_DAYS"

# Or directly in the database (H2)
# java -cp h2.jar org.h2.tools.Shell -url jdbc:h2:/app/db/pesitwizard -sql \
#   "DELETE FROM transfer_history WHERE started_at < DATEADD('DAY', -$RETENTION_DAYS, CURRENT_TIMESTAMP)"
```

### Purge Received Files

```bash
#!/bin/bash
# purge-received-files.sh

RETENTION_DAYS=${1:-30}
RECEIVED_DIR=/data/received

echo "Purging files older than $RETENTION_DAYS days in $RECEIVED_DIR..."

find $RECEIVED_DIR -type f -mtime +$RETENTION_DAYS -delete
find $RECEIVED_DIR -type d -empty -delete

echo "Purge completed"
```

### Log Rotation

```yaml
# logback-spring.xml
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
  <file>/var/log/pesitwizard/pesitwizard.log</file>
  <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
    <fileNamePattern>/var/log/pesitwizard/pesitwizard.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
    <maxHistory>90</maxHistory>
    <totalSizeCap>10GB</totalSizeCap>
  </rollingPolicy>
</appender>
```

### Certificate Renewal

```bash
#!/bin/bash
# renew-certificates.sh

# Check certificates expiring within 30 days
EXPIRING=$(curl -s -u admin:admin http://localhost:8080/api/v1/certificates | \
  jq -r '.[] | select(.daysUntilExpiry < 30) | .alias')

for cert in $EXPIRING; do
  echo "Renewing certificate: $cert"
  curl -X POST "http://localhost:8080/api/v1/certificates/$cert/renew" \
    -u admin:admin
done
```

---

## Upgrades

### Docker Upgrade

```bash
#!/bin/bash
# upgrade-docker.sh

NEW_VERSION=$1

if [ -z "$NEW_VERSION" ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

echo "Upgrading to version $NEW_VERSION..."

# 1. Backup
./backup-database.sh
./backup-certificates.sh

# 2. Pull new images
docker pull pesitwizard/server:$NEW_VERSION
docker pull pesitwizard/client:$NEW_VERSION

# 3. Update docker-compose.yml
sed -i "s/pesitwizard\/server:.*/pesitwizard\/server:$NEW_VERSION/" docker-compose.yml
sed -i "s/pesitwizard\/client:.*/pesitwizard\/client:$NEW_VERSION/" docker-compose.yml

# 4. Restart with new version
docker compose down
docker compose up -d

# 5. Verify health
sleep 30
./daily-health-check.sh

echo "Upgrade completed"
```

### Kubernetes Upgrade

```bash
#!/bin/bash
# upgrade-kubernetes.sh

NEW_VERSION=$1
NAMESPACE=${2:-pesitwizard}

if [ -z "$NEW_VERSION" ]; then
  echo "Usage: $0 <version> [namespace]"
  exit 1
fi

echo "Upgrading to version $NEW_VERSION in namespace $NAMESPACE..."

# Rolling update
kubectl set image deployment/pesitwizard-server \
  pesitwizard-server=pesitwizard/server:$NEW_VERSION \
  -n $NAMESPACE

kubectl set image deployment/pesitwizard-client \
  pesitwizard-client=pesitwizard/client:$NEW_VERSION \
  -n $NAMESPACE

# Wait for rollout
kubectl rollout status deployment/pesitwizard-server -n $NAMESPACE
kubectl rollout status deployment/pesitwizard-client -n $NAMESPACE

echo "Upgrade completed"
```

### Rollback

```bash
#!/bin/bash
# rollback.sh

# Docker
docker compose down
git checkout HEAD~1 docker-compose.yml
docker compose up -d

# Kubernetes
kubectl rollout undo deployment/pesitwizard-server -n pesitwizard
kubectl rollout undo deployment/pesitwizard-client -n pesitwizard
```

---

## Incident Management

### Incident: Stuck Transfers

**Symptoms**: Queues building up, transfers stuck in IN_PROGRESS for a long time

**Actions**:
```bash
# 1. Identify stuck transfers
curl -s "http://localhost:8080/api/v1/transfers?status=IN_PROGRESS" | \
  jq '.content[] | select(.startedAt < (now - 3600 | todate)) | {id, startedAt, bytesTransferred}'

# 2. Cancel stuck transfers
for id in $(curl -s ... | jq -r '.[].id'); do
  curl -X POST "http://localhost:8080/api/v1/transfers/$id/cancel"
done

# 3. Check resources
docker stats --no-stream

# 4. Restart if necessary
docker compose restart pw-client
```

### Incident: Critical Disk Space

**Symptoms**: "No space left on device" errors

**Actions**:
```bash
# 1. Identify usage
df -h
du -sh /data/* | sort -rh | head -10

# 2. Emergency purge
find /data/received -type f -mtime +7 -delete
find /var/log/pesitwizard -name "*.log.*" -mtime +7 -delete

# 3. Verify
df -h /data
```

### Incident: Expired Certificate

**Symptoms**: TLS errors, refused connections

**Actions**:
```bash
# 1. Identify the expired certificate
curl -s -u admin:admin http://localhost:8080/api/v1/certificates | \
  jq '.[] | select(.daysUntilExpiry <= 0)'

# 2. Renew
curl -X POST "http://localhost:8080/api/v1/certificates/CERT_ALIAS/renew" \
  -u admin:admin

# 3. Distribute to partners if necessary
# 4. Restart connections
```

### Incident: Corrupted Database

**Symptoms**: SQL errors, application fails to start

**Actions**:
```bash
# 1. Stop the service
systemctl stop pesitwizard-client

# 2. Attempt H2 repair
java -cp h2.jar org.h2.tools.Recover -dir /app/db -db pesitwizard

# 3. If repair fails, restore from backup
./restore-database.sh /backup/pesitwizard/db_LATEST

# 4. Restart
systemctl start pesitwizard-client
```

---

## Monitoring and Alerts

### Key Metrics to Monitor

| Metric | Warning Threshold | Critical Threshold |
|--------|-------------------|--------------------|
| Transfer error rate | > 5% | > 20% |
| Average transfer time | > 60s | > 300s |
| Transfer queue | > 50 | > 200 |
| Disk space | < 20% | < 10% |
| JVM memory | > 80% | > 95% |
| Certificate expiration | < 30 days | < 7 days |

### Prometheus Configuration

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'pesitwizard-server'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['pesitwizard-server:8080']

  - job_name: 'pesitwizard-client'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['pesitwizard-client:8080']
```

### AlertManager Alerts

```yaml
# alertmanager-rules.yml
groups:
  - name: pesitwizard
    rules:
      - alert: HighTransferErrorRate
        expr: rate(pesit_transfers_failed_total[5m]) / rate(pesit_transfers_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High transfer error rate"

      - alert: CertificateExpiringSoon
        expr: pesitwizard_certificate_days_until_expiry < 30
        for: 1h
        labels:
          severity: warning
        annotations:
          summary: "Certificate expiring in {{ $value }} days"

      - alert: DiskSpaceLow
        expr: node_filesystem_avail_bytes{mountpoint="/data"} / node_filesystem_size_bytes < 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Disk space critical: {{ $value | humanizePercentage }}"
```

---

## Contacts and Escalation

| Level | Timeframe | Contact |
|-------|-----------|---------|
| L1 - Operations | 15 min | ops@example.com |
| L2 - Technical Support | 1h | support@example.com |
| L3 - Development | 4h | dev@example.com |

### Escalation Procedure

1. **L1**: Check logs, restart if necessary
2. **L2**: In-depth diagnosis, backup restoration
3. **L3**: Code analysis, emergency fix
