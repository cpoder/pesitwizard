# PeSIT Wizard High Availability Guide

This guide covers deploying PeSIT Wizard in a highly available configuration.

## Architecture Overview

PeSIT Wizard supports High Availability through:
1. **Active-Passive Clustering** - Leader election ensures only one node handles PeSIT connections
2. **Shared Database** - PostgreSQL stores configuration and transfer state
3. **Load Balancer** - Distributes REST API traffic and handles failover

```
                    ┌─────────────────┐
                    │  Load Balancer  │
                    │   (HAProxy/LB)  │
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              │              │              │
        ┌─────▼─────┐  ┌─────▼─────┐  ┌─────▼─────┐
        │  Node 1   │  │  Node 2   │  │  Node 3   │
        │ (Leader)  │  │ (Standby) │  │ (Standby) │
        │ PeSIT:6502│  │ REST only │  │ REST only │
        │ REST:8080 │  │ REST:8080 │  │ REST:8080 │
        └─────┬─────┘  └─────┬─────┘  └─────┬─────┘
              │              │              │
              └──────────────┼──────────────┘
                             │
                    ┌────────▼────────┐
                    │   PostgreSQL    │
                    │   (Primary)     │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │   PostgreSQL    │
                    │   (Replica)     │
                    └─────────────────┘
```

## Deployment Patterns

### Pattern 1: Kubernetes with Helm

The recommended approach for cloud deployments.

```bash
# Install with HA configuration
helm install pesitwizard ./pesitwizard-helm-charts \
  --set replicaCount=3 \
  --set cluster.enabled=true \
  --set persistence.enabled=true \
  --set postgresql.replication.enabled=true
```

Helm values for HA:
```yaml
replicaCount: 3

cluster:
  enabled: true
  id: production-cluster

persistence:
  enabled: true
  storageClass: "fast-ssd"
  size: 50Gi

postgresql:
  primary:
    persistence:
      size: 100Gi
  replication:
    enabled: true
    replicas: 2
```

### Pattern 2: Docker Compose

For smaller deployments or on-premises.

```yaml
# docker-compose-ha.yml
version: '3.8'

services:
  pesitwizard-1:
    image: pesitwizard/server:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/pesitwizard
      - PESIT_CLUSTER_ENABLED=true
      - PESIT_CLUSTER_ID=node-1
    ports:
      - "8081:8080"
      - "6502:6502"
    depends_on:
      - postgres

  pesitwizard-2:
    image: pesitwizard/server:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/pesitwizard
      - PESIT_CLUSTER_ENABLED=true
      - PESIT_CLUSTER_ID=node-2
    ports:
      - "8082:8080"
    depends_on:
      - postgres

  pesitwizard-3:
    image: pesitwizard/server:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/pesitwizard
      - PESIT_CLUSTER_ENABLED=true
      - PESIT_CLUSTER_ID=node-3
    ports:
      - "8083:8080"
    depends_on:
      - postgres

  postgres:
    image: postgres:15
    environment:
      - POSTGRES_DB=pesitwizard
      - POSTGRES_USER=pesitwizard
      - POSTGRES_PASSWORD=secret
    volumes:
      - pgdata:/var/lib/postgresql/data

  haproxy:
    image: haproxy:2.8
    ports:
      - "8080:8080"  # REST API
      - "6502:6502"  # PeSIT (routes to leader)
    volumes:
      - ./haproxy.cfg:/usr/local/etc/haproxy/haproxy.cfg:ro
    depends_on:
      - pesitwizard-1
      - pesitwizard-2
      - pesitwizard-3

volumes:
  pgdata:
```

### Pattern 3: VM-Based Deployment

For traditional infrastructure.

1. **Database Layer:**
   - PostgreSQL Primary + Streaming Replica
   - Use Patroni for automatic failover

2. **Application Layer:**
   - 3+ PeSIT Wizard nodes
   - Shared filesystem for received files (NFS/GlusterFS)

3. **Load Balancer:**
   - HAProxy or F5 for traffic distribution
   - Health checks on `/actuator/health`

## Configuration

### Cluster Configuration

```yaml
# application.yml
pesit:
  cluster:
    enabled: true
    id: ${PESIT_CLUSTER_ID:node-1}

spring:
  datasource:
    url: jdbc:postgresql://postgres-primary:5432/pesitwizard
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000
```

### Leader Election

Leader election uses database locking:
- Only the leader node runs PeSIT server instances
- Standby nodes serve REST API only
- Leadership transfers on node failure

Check current leader:
```bash
curl http://any-node:8080/api/v1/cluster/leader
```

### Server Ownership

Each PeSIT server instance is "owned" by a cluster node:
```bash
curl http://any-node:8080/api/v1/cluster/ownership
```

Response:
```json
{
  "PWSERVER": "node-1",
  "BACKUPSVR": "node-1"
}
```

## Load Balancer Configuration

### HAProxy Example

```
# haproxy.cfg
global
    log stdout format raw local0

defaults
    mode http
    timeout connect 5s
    timeout client 50s
    timeout server 50s
    option httpchk GET /actuator/health

# REST API - round robin across all nodes
frontend rest_api
    bind *:8080
    default_backend rest_servers

backend rest_servers
    balance roundrobin
    server node1 pesitwizard-1:8080 check
    server node2 pesitwizard-2:8080 check
    server node3 pesitwizard-3:8080 check

# PeSIT - route to leader only
frontend pesit_tcp
    bind *:6502
    mode tcp
    default_backend pesit_server

backend pesit_server
    mode tcp
    balance first
    option tcp-check
    server node1 pesitwizard-1:6502 check port 8080
    server node2 pesitwizard-2:6502 check port 8080 backup
    server node3 pesitwizard-3:6502 check port 8080 backup
```

## Failover Procedures

### Automatic Failover

1. Leader node fails
2. Database lock is released after timeout (default: 30s)
3. Standby node acquires lock, becomes leader
4. New leader starts all autoStart=true servers
5. Load balancer health checks route traffic to new leader

### Manual Failover

For planned maintenance:

1. **Drain connections:**
   ```bash
   # Stop accepting new connections
   curl -X POST http://leader:8080/api/v1/servers/PWSERVER/stop
   ```

2. **Wait for active transfers to complete:**
   ```bash
   curl http://leader:8080/api/v1/transfers/active
   ```

3. **Stop the leader:**
   ```bash
   systemctl stop pesitwizard
   ```

4. **Verify new leader:**
   ```bash
   curl http://any-node:8080/api/v1/cluster/leader
   ```

### Recovery After Failover

1. Fix the failed node
2. Start the application
3. Node joins as standby automatically
4. Verify cluster status:
   ```bash
   curl http://any-node:8080/api/v1/cluster/members
   ```

## Monitoring

### Health Endpoints

- `/actuator/health` - Overall health
- `/actuator/health/liveness` - Kubernetes liveness probe
- `/actuator/health/readiness` - Kubernetes readiness probe
- `/api/v1/cluster/status` - Cluster-specific health

### Key Metrics

Monitor these Prometheus metrics:
- `pesit_active_connections` - Current connections per server
- `pesit_transfers_total` - Transfer count by status
- `pesit_cluster_is_leader` - Leader status (1 = leader)
- `pesit_cluster_members` - Number of cluster members

### Alerts

Recommended alerts:
```yaml
groups:
  - name: pesitwizard
    rules:
      - alert: NoLeader
        expr: sum(pesit_cluster_is_leader) == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "No PeSIT Wizard leader elected"

      - alert: HighFailureRate
        expr: rate(pesit_transfers_total{status="FAILED"}[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High transfer failure rate"
```

## Disaster Recovery

### Backup Strategy

1. **Database:** Daily PostgreSQL backups with point-in-time recovery
2. **Configuration:** Export via REST API or database dump
3. **Files:** Sync received files to backup storage

### Backup Command
```bash
curl -X POST http://localhost:8080/api/v1/backup \
  -H "X-API-Key: admin-key"
```

### Restore Procedure

1. Restore PostgreSQL database
2. Deploy new cluster pointing to restored database
3. Import configuration backup:
   ```bash
   curl -X POST http://localhost:8080/api/v1/backup/restore/backup-2024-01-01 \
     -H "X-API-Key: admin-key"
   ```
4. Verify partners and virtual files are restored
5. Start servers and validate connectivity

## Best Practices

1. **Use at least 3 nodes** for quorum-based leader election
2. **Deploy across availability zones** for resilience
3. **Use persistent storage** for received files
4. **Enable sync points** for large file restart capability
5. **Monitor cluster health** with alerting
6. **Test failover regularly** in non-production environments
7. **Document runbooks** for operational procedures
