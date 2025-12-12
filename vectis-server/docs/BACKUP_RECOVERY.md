# Backup and Recovery Guide

This document describes the backup and recovery procedures for the PeSIT Server.

## Overview

The PeSIT Server supports:
- **Automatic scheduled backups** - Configurable daily backups
- **Manual backups** - On-demand backup via REST API
- **Backup retention** - Automatic cleanup of old backups
- **Database restore** - Restore from backup (H2 database)

## Configuration

Add the following to `application.yml`:

```yaml
pesit.backup:
  # Directory for storing backups
  directory: ./backups
  
  # Retention period in days
  retention-days: 30
  
  # Maximum number of backups to keep
  max-backups: 10
  
  # Backup schedule (cron expression)
  # Default: 1 AM daily
  schedule: "0 0 1 * * ?"
```

## REST API

All backup endpoints require `ADMIN` role.

### Create Backup

```bash
POST /api/v1/backup?description=Manual%20backup

Response:
{
  "success": true,
  "backupName": "pesit_backup_20241203_143022",
  "backupPath": "./backups/pesit_backup_20241203_143022.zip",
  "backupType": "H2_DATABASE",
  "sizeBytes": 1048576,
  "timestamp": "2024-12-03T14:30:22Z",
  "description": "Manual backup"
}
```

### List Backups

```bash
GET /api/v1/backup

Response:
[
  {
    "name": "pesit_backup_20241203_143022.zip",
    "path": "./backups/pesit_backup_20241203_143022.zip",
    "type": "H2_DATABASE",
    "sizeBytes": 1048576,
    "createdAt": "2024-12-03T14:30:22Z"
  }
]
```

### Restore Backup

```bash
POST /api/v1/backup/restore/pesit_backup_20241203_143022.zip

Response:
{
  "success": true,
  "backupName": "pesit_backup_20241203_143022.zip",
  "timestamp": "2024-12-03T15:00:00Z",
  "message": "Database restored from backup. Restart required."
}
```

### Delete Backup

```bash
DELETE /api/v1/backup/pesit_backup_20241203_143022.zip

Response: 204 No Content
```

### Cleanup Old Backups

```bash
POST /api/v1/backup/cleanup

Response: 3  # Number of backups deleted
```

## Database-Specific Procedures

### H2 Database (Development/Testing)

The backup service automatically handles H2 database backups by copying the database file.

**Backup:**
```bash
# Automatic via API
curl -X POST http://localhost:8080/api/v1/backup \
  -H "Authorization: Bearer $TOKEN"
```

**Restore:**
```bash
# 1. Stop the server
# 2. Restore via API
curl -X POST http://localhost:8080/api/v1/backup/restore/pesit_backup_xxx.zip \
  -H "Authorization: Bearer $TOKEN"
# 3. Restart the server
```

### PostgreSQL (Production)

For PostgreSQL, use `pg_dump` and `pg_restore`:

**Backup:**
```bash
# Full database backup
pg_dump -h localhost -U pesit -d pesit -F c -f pesit_backup.dump

# Schema only
pg_dump -h localhost -U pesit -d pesit -s -f pesit_schema.sql

# Data only
pg_dump -h localhost -U pesit -d pesit -a -f pesit_data.sql
```

**Restore:**
```bash
# 1. Stop the PeSIT server

# 2. Drop and recreate database (if needed)
dropdb -h localhost -U postgres pesit
createdb -h localhost -U postgres -O pesit pesit

# 3. Restore from backup
pg_restore -h localhost -U pesit -d pesit pesit_backup.dump

# 4. Start the PeSIT server
```

### MySQL/MariaDB

**Backup:**
```bash
# Full database backup
mysqldump -h localhost -u pesit -p pesit > pesit_backup.sql

# With routines and triggers
mysqldump -h localhost -u pesit -p --routines --triggers pesit > pesit_backup.sql
```

**Restore:**
```bash
# 1. Stop the PeSIT server

# 2. Restore from backup
mysql -h localhost -u pesit -p pesit < pesit_backup.sql

# 3. Start the PeSIT server
```

## Kubernetes Backup

### Using CronJob

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: pesit-backup
spec:
  schedule: "0 1 * * *"  # 1 AM daily
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:15
            command:
            - /bin/sh
            - -c
            - |
              pg_dump -h $DB_HOST -U $DB_USER -d $DB_NAME -F c \
                -f /backups/pesit_$(date +%Y%m%d_%H%M%S).dump
            env:
            - name: DB_HOST
              value: "pesit-postgresql"
            - name: DB_USER
              valueFrom:
                secretKeyRef:
                  name: pesit-db-credentials
                  key: username
            - name: PGPASSWORD
              valueFrom:
                secretKeyRef:
                  name: pesit-db-credentials
                  key: password
            - name: DB_NAME
              value: "pesit"
            volumeMounts:
            - name: backup-storage
              mountPath: /backups
          volumes:
          - name: backup-storage
            persistentVolumeClaim:
              claimName: pesit-backups
          restartPolicy: OnFailure
```

### Using Velero

For full cluster backup including PVCs:

```bash
# Install Velero
velero install --provider aws --bucket pesit-backups \
  --secret-file ./credentials-velero

# Create backup
velero backup create pesit-backup --include-namespaces pesit

# Restore
velero restore create --from-backup pesit-backup
```

## Disaster Recovery Procedures

### Complete System Recovery

1. **Deploy Infrastructure**
   ```bash
   # Deploy Kubernetes resources
   helm install pesit ../helm-charts/pesit-server -f values-prod.yaml
   ```

2. **Restore Database**
   ```bash
   # Scale down application
   kubectl scale deployment pesit-server --replicas=0
   
   # Restore database
   kubectl exec -it pesit-postgresql-0 -- \
     pg_restore -U pesit -d pesit /backups/latest.dump
   
   # Scale up application
   kubectl scale deployment pesit-server --replicas=3
   ```

3. **Verify Recovery**
   ```bash
   # Check health
   curl http://pesit-server/actuator/health
   
   # Verify data
   curl http://pesit-server/api/v1/transfers
   ```

### Point-in-Time Recovery (PostgreSQL)

For PostgreSQL with WAL archiving:

```bash
# 1. Stop PostgreSQL
pg_ctl stop -D /var/lib/postgresql/data

# 2. Clear data directory
rm -rf /var/lib/postgresql/data/*

# 3. Restore base backup
tar -xvf /backups/base.tar -C /var/lib/postgresql/data

# 4. Create recovery.conf
cat > /var/lib/postgresql/data/recovery.conf << EOF
restore_command = 'cp /backups/wal/%f %p'
recovery_target_time = '2024-12-03 14:00:00'
EOF

# 5. Start PostgreSQL
pg_ctl start -D /var/lib/postgresql/data
```

## Best Practices

1. **Test Restores Regularly**
   - Schedule monthly restore tests
   - Document restore time (RTO)
   - Verify data integrity after restore

2. **Multiple Backup Locations**
   - Local storage for quick recovery
   - Remote storage (S3, GCS) for disaster recovery
   - Different regions for geographic redundancy

3. **Encryption**
   - Encrypt backups at rest
   - Use secure transfer for remote backups
   - Protect backup credentials

4. **Monitoring**
   - Alert on backup failures
   - Monitor backup size trends
   - Track restore test results

5. **Documentation**
   - Keep runbooks up to date
   - Document recovery procedures
   - Maintain contact list for emergencies
