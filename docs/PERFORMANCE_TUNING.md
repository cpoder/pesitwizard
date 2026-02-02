# PeSIT Wizard Performance Tuning Guide

This guide covers optimizing PeSIT Wizard for production workloads.

## Key Performance Parameters

### Max Entity Size (PI 25)

The `maxEntitySize` parameter controls the maximum FPDU data chunk size.

| Value | Use Case | Memory Impact |
|-------|----------|---------------|
| 4096 | Legacy systems (CX default) | Low |
| 8192 | Balanced | Medium |
| 16384 | Fast networks | Medium |
| 32768 | Maximum throughput | Higher |

**Configuration:**
```yaml
pesit:
  max-entity-size: 32768  # Server default
```

Per-server override:
```bash
curl -X POST http://localhost:8080/api/servers \
  -H "Content-Type: application/json" \
  -d '{"serverId":"FAST","port":5002,"maxEntitySize":32768}'
```

### Sync Point Interval

Sync points enable restart for large file transfers but add overhead.

| Interval | Use Case | Overhead |
|----------|----------|----------|
| 32 KB | Unreliable networks | High |
| 256 KB | Standard | Medium |
| 1 MB | Fast/reliable networks | Low |
| Disabled | Small files | None |

**Configuration:**
```yaml
pesit:
  sync-points-enabled: true
  sync-interval-kb: 256
```

### Connection Pooling

**Max connections per server:**
```yaml
pesit:
  max-connections: 100
```

**Database connection pool:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

## JVM Tuning

### Recommended Settings

```bash
JAVA_OPTS="-Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -Djava.net.preferIPv4Stack=true"
```

### Memory Sizing

| Concurrent Transfers | Heap Size | Notes |
|---------------------|-----------|-------|
| 1-10 | 512MB - 1GB | Development |
| 10-50 | 1GB - 2GB | Standard production |
| 50-100 | 2GB - 4GB | High throughput |
| 100+ | 4GB+ | Enterprise scale |

### GC Tuning

For latency-sensitive workloads:
```bash
-XX:+UseZGC  # Java 17+ - low pause times
```

For throughput optimization:
```bash
-XX:+UseG1GC
-XX:ParallelGCThreads=4
```

## Network Tuning

### TCP Buffer Sizes

For high-throughput transfers, increase TCP buffers:

**Linux:**
```bash
# /etc/sysctl.conf
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216
```

**Java:**
```yaml
pesit:
  socket-send-buffer: 65536
  socket-receive-buffer: 65536
```

### Connection Timeouts

```yaml
pesit:
  connection-timeout: 30000   # 30 seconds to establish
  read-timeout: 120000        # 2 minutes for slow transfers
```

For large files, increase dynamically:
```
timeout = baseTimeout + (fileSize / 50MB) * 60 seconds
```

## Disk I/O Optimization

### Storage Recommendations

| Workload | Storage Type | Notes |
|----------|-------------|-------|
| Low volume | HDD | Cost effective |
| Standard | SSD | Recommended |
| High volume | NVMe | Best performance |

### Directory Structure

Separate send and receive directories to different disks:
```yaml
pesit:
  receive-directory: /fast-ssd/received
  send-directory: /standard/send
```

### File System

- Use XFS or ext4 for large files
- Enable noatime for better write performance
- Consider separate partitions for logs

## Database Optimization

### PostgreSQL Settings

```sql
-- postgresql.conf
shared_buffers = 256MB
work_mem = 64MB
maintenance_work_mem = 256MB
effective_cache_size = 1GB
wal_buffers = 16MB
checkpoint_completion_target = 0.9
```

### Index Optimization

The most queried tables:
- `transfer_records` - Add index on (status, created_at)
- `audit_events` - Add index on (created_at, category)

### Connection Pooling

Use PgBouncer for high connection counts:
```ini
[pgbouncer]
pool_mode = transaction
max_client_conn = 1000
default_pool_size = 25
```

## TLS Performance

### TLS Session Caching

Enable session resumption:
```yaml
pesit:
  ssl:
    session-timeout: 86400  # 24 hours
    session-cache-size: 1000
```

### Cipher Suite Selection

Prefer faster ciphers:
```yaml
pesit:
  ssl:
    cipher-suites:
      - TLS_AES_128_GCM_SHA256
      - TLS_AES_256_GCM_SHA384
      - TLS_CHACHA20_POLY1305_SHA256
```

## Monitoring Metrics

### Key Metrics to Monitor

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| Transfer throughput | > 10 MB/s | < 1 MB/s |
| Active connections | < 80% of max | > 90% |
| Transfer success rate | > 99% | < 95% |
| Response time (P95) | < 100ms | > 500ms |
| GC pause time | < 50ms | > 200ms |

### Prometheus Metrics

```yaml
# Scrape config
- job_name: 'pesitwizard'
  metrics_path: '/actuator/prometheus'
  static_configs:
    - targets: ['localhost:8080']
```

## Scaling Guidelines

### Vertical Scaling

| Users | CPU | Memory | Storage |
|-------|-----|--------|---------|
| 1-10 | 2 cores | 2 GB | 50 GB |
| 10-50 | 4 cores | 4 GB | 200 GB |
| 50-100 | 8 cores | 8 GB | 500 GB |
| 100+ | 16+ cores | 16+ GB | 1+ TB |

### Horizontal Scaling

For high availability and load distribution:
1. Deploy 3+ nodes behind load balancer
2. Use shared database (PostgreSQL)
3. Use shared storage for received files
4. See HIGH_AVAILABILITY.md for details

## Benchmarking

### Test Tool

Use the included benchmark script:
```bash
./integration-tests/cx-integration/docker/tests/run-benchmark.sh
```

### Expected Results

| File Size | Expected Time | Throughput |
|-----------|---------------|------------|
| 1 MB | < 5s | > 200 KB/s |
| 10 MB | < 30s | > 350 KB/s |
| 100 MB | < 3 min | > 500 KB/s |
| 1 GB | < 20 min | > 800 KB/s |

*Times depend on network, storage, and partner capabilities.*
