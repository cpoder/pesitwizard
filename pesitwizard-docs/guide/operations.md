# Runbook Opérationnel

Procédures de maintenance et d'exploitation pour PeSIT Wizard.

## Opérations quotidiennes

### Vérification santé

```bash
#!/bin/bash
# daily-health-check.sh

echo "=== PeSIT Wizard Health Check ==="
echo "Date: $(date)"
echo ""

# 1. API Health
echo "1. API Health..."
curl -sf http://localhost:8080/actuator/health | jq -r '.status' || echo "FAIL"
curl -sf http://localhost:9081/actuator/health | jq -r '.status' || echo "FAIL"

# 2. Disk space
echo ""
echo "2. Disk Space..."
df -h /data | tail -1 | awk '{print "Used: "$5" Free: "$4}'

# 3. Transfers last 24h
echo ""
echo "3. Transfers (last 24h)..."
curl -sf "http://localhost:9081/api/v1/transfers?from=$(date -d '24 hours ago' +%Y-%m-%d)" | \
  jq '{total: .totalElements, completed: [.content[] | select(.status=="COMPLETED")] | length, failed: [.content[] | select(.status=="FAILED")] | length}'

# 4. Certificate expiry
echo ""
echo "4. Certificate Expiry..."
curl -sf http://localhost:8080/api/v1/certificates | jq -r '.[] | "\(.alias): \(.expiresAt)"'
```

### Surveillance des transferts

```bash
# Transferts en cours
curl -s http://localhost:9081/api/v1/transfers?status=IN_PROGRESS | jq

# Transferts échoués (dernières 24h)
curl -s "http://localhost:9081/api/v1/transfers?status=FAILED&from=$(date -d '24 hours ago' +%Y-%m-%d)" | jq

# Statistiques
curl -s http://localhost:9081/api/v1/transfers/stats | jq
```

---

## Sauvegarde et restauration

### Sauvegarde

#### Base de données (H2 embedded)

```bash
#!/bin/bash
# backup-database.sh

BACKUP_DIR=/backup/pesitwizard
DATE=$(date +%Y%m%d_%H%M%S)

# Arrêter proprement le service (optionnel mais recommandé)
# systemctl stop pesitwizard-client

# Sauvegarder la base H2
mkdir -p $BACKUP_DIR
cp -r /app/db/* $BACKUP_DIR/db_$DATE/

# Sauvegarder la configuration
cp /app/application.yml $BACKUP_DIR/config_$DATE.yml

echo "Backup completed: $BACKUP_DIR/*_$DATE"

# Rotation: garder 30 jours
find $BACKUP_DIR -type d -mtime +30 -exec rm -rf {} \;
```

#### Base de données PostgreSQL

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

#### Certificats

```bash
#!/bin/bash
# backup-certificates.sh

BACKUP_DIR=/backup/pesitwizard/certs
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# Exporter tous les certificats via API
curl -s -u admin:admin http://localhost:8080/api/v1/certificates/export > $BACKUP_DIR/certs_$DATE.json

# Sauvegarder les keystores
cp /app/keystores/* $BACKUP_DIR/keystores_$DATE/ 2>/dev/null || true

echo "Certificates backup completed"
```

### Restauration

#### Restaurer la base H2

```bash
#!/bin/bash
# restore-database.sh

BACKUP_PATH=$1

if [ -z "$BACKUP_PATH" ]; then
  echo "Usage: $0 /backup/pesitwizard/db_YYYYMMDD_HHMMSS"
  exit 1
fi

# Arrêter le service
systemctl stop pesitwizard-client

# Restaurer
rm -rf /app/db/*
cp -r $BACKUP_PATH/* /app/db/

# Redémarrer
systemctl start pesitwizard-client

echo "Database restored from $BACKUP_PATH"
```

#### Restaurer PostgreSQL

```bash
#!/bin/bash
# restore-postgresql.sh

BACKUP_FILE=$1

if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: $0 /backup/pesitwizard/pesitwizard_YYYYMMDD.sql.gz"
  exit 1
fi

# Arrêter les connexions
psql -h localhost -U postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='pesitwizard';"

# Restaurer
gunzip -c $BACKUP_FILE | psql -h localhost -U pesitwizard -d pesitwizard

echo "Database restored from $BACKUP_FILE"
```

---

## Maintenance planifiée

### Purge des anciens transferts

```bash
#!/bin/bash
# purge-old-transfers.sh

RETENTION_DAYS=${1:-90}

echo "Purging transfers older than $RETENTION_DAYS days..."

# Via API (si endpoint disponible)
curl -X DELETE "http://localhost:9081/api/v1/transfers/purge?olderThanDays=$RETENTION_DAYS"

# Ou directement en base (H2)
# java -cp h2.jar org.h2.tools.Shell -url jdbc:h2:/app/db/pesitwizard -sql \
#   "DELETE FROM transfer_history WHERE started_at < DATEADD('DAY', -$RETENTION_DAYS, CURRENT_TIMESTAMP)"
```

### Purge des fichiers reçus

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

### Rotation des logs

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

### Renouvellement des certificats

```bash
#!/bin/bash
# renew-certificates.sh

# Vérifier les certificats expirant dans 30 jours
EXPIRING=$(curl -s -u admin:admin http://localhost:8080/api/v1/certificates | \
  jq -r '.[] | select(.daysUntilExpiry < 30) | .alias')

for cert in $EXPIRING; do
  echo "Renewing certificate: $cert"
  curl -X POST "http://localhost:8080/api/v1/certificates/$cert/renew" \
    -u admin:admin
done
```

---

## Mise à jour

### Mise à jour Docker

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

### Mise à jour Kubernetes

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

## Gestion des incidents

### Incident: Transferts bloqués

**Symptômes**: Files d'attente qui s'accumulent, transferts IN_PROGRESS depuis longtemps

**Actions**:
```bash
# 1. Identifier les transferts bloqués
curl -s "http://localhost:9081/api/v1/transfers?status=IN_PROGRESS" | \
  jq '.content[] | select(.startedAt < (now - 3600 | todate)) | {id, startedAt, bytesTransferred}'

# 2. Annuler les transferts bloqués
for id in $(curl -s ... | jq -r '.[].id'); do
  curl -X POST "http://localhost:9081/api/v1/transfers/$id/cancel"
done

# 3. Vérifier les ressources
docker stats --no-stream

# 4. Redémarrer si nécessaire
docker compose restart pw-client
```

### Incident: Espace disque critique

**Symptômes**: Erreurs "No space left on device"

**Actions**:
```bash
# 1. Identifier l'utilisation
df -h
du -sh /data/* | sort -rh | head -10

# 2. Purge d'urgence
find /data/received -type f -mtime +7 -delete
find /var/log/pesitwizard -name "*.log.*" -mtime +7 -delete

# 3. Vérifier
df -h /data
```

### Incident: Certificat expiré

**Symptômes**: Erreurs TLS, connexions refusées

**Actions**:
```bash
# 1. Identifier le certificat expiré
curl -s -u admin:admin http://localhost:8080/api/v1/certificates | \
  jq '.[] | select(.daysUntilExpiry <= 0)'

# 2. Renouveler
curl -X POST "http://localhost:8080/api/v1/certificates/CERT_ALIAS/renew" \
  -u admin:admin

# 3. Distribuer aux partenaires si nécessaire
# 4. Redémarrer les connexions
```

### Incident: Base de données corrompue

**Symptômes**: Erreurs SQL, application ne démarre pas

**Actions**:
```bash
# 1. Arrêter le service
systemctl stop pesitwizard-client

# 2. Tenter une réparation H2
java -cp h2.jar org.h2.tools.Recover -dir /app/db -db pesitwizard

# 3. Si échec, restaurer depuis backup
./restore-database.sh /backup/pesitwizard/db_LATEST

# 4. Redémarrer
systemctl start pesitwizard-client
```

---

## Monitoring et alertes

### Métriques clés à surveiller

| Métrique | Seuil Warning | Seuil Critical |
|----------|---------------|----------------|
| Taux d'erreur transferts | > 5% | > 20% |
| Temps de transfert moyen | > 60s | > 300s |
| Queue de transferts | > 50 | > 200 |
| Espace disque | < 20% | < 10% |
| Mémoire JVM | > 80% | > 95% |
| Certificats expiration | < 30 jours | < 7 jours |

### Configuration Prometheus

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
      - targets: ['pesitwizard-client:9081']
```

### Alertes AlertManager

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

## Contacts et escalade

| Niveau | Délai | Contact |
|--------|-------|---------|
| L1 - Opérations | 15 min | ops@example.com |
| L2 - Support technique | 1h | support@example.com |
| L3 - Développement | 4h | dev@example.com |

### Procédure d'escalade

1. **L1**: Vérifier les logs, redémarrer si nécessaire
2. **L2**: Diagnostic approfondi, restauration backup
3. **L3**: Analyse code, correctif d'urgence
