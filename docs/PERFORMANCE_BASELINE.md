# PeSIT Wizard Performance Baseline

This document captures the performance characteristics of PeSIT Wizard under various load conditions. These baselines should be updated after each major release and used as a reference for performance regression testing.

## Table of Contents

- [Test Environment](#test-environment)
- [Baseline Metrics](#baseline-metrics)
- [Load Test Results](#load-test-results)
- [Capacity Planning](#capacity-planning)
- [Tuning Recommendations](#tuning-recommendations)

---

## Test Environment

### Hardware Specifications

| Component | Specification |
|-----------|--------------|
| CPU | 4 vCPU (Intel Xeon or AMD EPYC) |
| Memory | 8 GB RAM |
| Storage | 100 GB SSD (gp3) |
| Network | 1 Gbps |

### Software Configuration

| Component | Version |
|-----------|---------|
| Java | OpenJDK 21 |
| Spring Boot | 3.x |
| PostgreSQL | 15.x |
| Kubernetes | 1.28+ |

### JVM Settings

```
-Xms2g -Xmx4g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+HeapDumpOnOutOfMemoryError
```

---

## Baseline Metrics

### Single Node Performance

| Metric | Value | Notes |
|--------|-------|-------|
| Max Concurrent Connections | 200 | Per node |
| Max Transfers/Second | 50 | 1MB files |
| Average Transfer Latency (1MB) | 500ms | P50 |
| P95 Transfer Latency (1MB) | 2s | |
| P99 Transfer Latency (1MB) | 5s | |
| Max Throughput | 100 MB/s | Network limited |
| Memory Usage (Idle) | 512 MB | Heap |
| Memory Usage (100 connections) | 1.5 GB | Heap |
| CPU Usage (Idle) | 5% | |
| CPU Usage (100 connections) | 40% | |

### Cluster Performance (3 Nodes)

| Metric | Value | Notes |
|--------|-------|-------|
| Max Concurrent Connections | 500 | Across cluster |
| Max Transfers/Second | 120 | 1MB files |
| Failover Time | < 30s | Leader election |
| Inter-Node Latency | < 10ms | Same region |
| Cluster State Sync | < 100ms | JGroups |

---

## Load Test Results

### Test 1: 200 Concurrent Transfers (Target Load)

**Configuration:**
- Duration: 30 minutes
- File Size: 10 MB
- Ramp Up: 5 minutes

**Results:**

```
Successful Transfers: 15,240
Failed Transfers: 12
Error Rate: 0.08%
Total Data Transferred: 152.4 GB

Latency:
  Average: 2,340 ms
  P50: 1,890 ms
  P95: 5,420 ms
  P99: 12,100 ms

Throughput: 84.7 MB/s

Resource Usage:
  CPU: 65% (avg), 85% (peak)
  Memory: 2.8 GB (avg), 3.2 GB (peak)
  Network: 680 Mbps (avg)
```

**Verdict:** PASS - Meets SLO requirements

### Test 2: 500 Concurrent Transfers (Stress Test)

**Configuration:**
- Duration: 15 minutes
- File Size: 1 MB
- Ramp Up: 5 minutes

**Results:**

```
Successful Transfers: 42,100
Failed Transfers: 1,890
Error Rate: 4.3%
Total Data Transferred: 42.1 GB

Latency:
  Average: 8,920 ms
  P50: 6,450 ms
  P95: 22,340 ms
  P99: 45,200 ms

Resource Usage:
  CPU: 92% (avg), 100% (peak)
  Memory: 3.5 GB (avg), 3.9 GB (peak)
```

**Verdict:** DEGRADED - Graceful degradation under stress

### Test 3: Breaking Point Analysis

**Finding:** System reaches breaking point at approximately **650 concurrent transfers** on a single node.

**Symptoms at Breaking Point:**
- Connection timeouts increase significantly
- Error rate exceeds 10%
- Memory pressure triggers frequent GC
- Response times become unpredictable

---

## Capacity Planning

### Transfer Capacity by File Size

| File Size | Max Concurrent | Throughput |
|-----------|----------------|------------|
| 1 KB | 500 | 50 MB/s |
| 100 KB | 400 | 80 MB/s |
| 1 MB | 300 | 100 MB/s |
| 10 MB | 200 | 100 MB/s |
| 100 MB | 100 | 100 MB/s |
| 1 GB | 20 | 80 MB/s |

### Scaling Guidelines

```
Nodes Required = ceil(Expected Peak Concurrent Transfers / 150)

Example:
- 200 concurrent: 2 nodes (with 1 for failover = 3 nodes)
- 500 concurrent: 4 nodes (with 1 for failover = 5 nodes)
- 1000 concurrent: 7 nodes (with 1 for failover = 8 nodes)
```

### Resource Requirements per 100 Concurrent Transfers

| Resource | Requirement |
|----------|-------------|
| CPU | 2 vCPU |
| Memory | 2 GB |
| Network | 200 Mbps |
| Disk IOPS | 500 |

---

## Tuning Recommendations

### For High Throughput

```yaml
# application.yml
pesit:
  server:
    max-connections: 200
    connection-timeout: 30000
    read-timeout: 60000
    max-entity-size: 8192

spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
```

### For Low Latency

```yaml
# application.yml
pesit:
  server:
    max-connections: 100
    sync-points-enabled: false  # Reduces checkpointing overhead
    session-recording-enabled: false

# JVM flags
-XX:MaxGCPauseMillis=50
-XX:+UseZGC
```

### For Large Files (> 100MB)

```yaml
# application.yml
pesit:
  server:
    max-entity-size: 16384
    sync-points-enabled: true
    resync-enabled: true

server:
  tomcat:
    max-http-form-post-size: 500MB
```

### Connection Pool Sizing

```
Optimal Pool Size = (core_count * 2) + effective_spindle_count

For SSD: Pool Size = (4 * 2) + 1 = 9 (minimum)
For production: 20-50 connections recommended
```

---

## Monitoring Dashboards

### Key Metrics to Monitor

1. **Transfer Success Rate**
   ```
   sum(rate(pesitwizard_transfers_total{status="completed"}[5m])) /
   sum(rate(pesitwizard_transfers_total[5m]))
   ```

2. **Transfer Latency P95**
   ```
   histogram_quantile(0.95,
     sum(rate(pesitwizard_transfer_duration_seconds_bucket[5m])) by (le)
   )
   ```

3. **Connection Pool Usage**
   ```
   hikaricp_connections_active / hikaricp_connections_max
   ```

4. **Memory Usage**
   ```
   jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
   ```

### Alert Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| Transfer Success Rate | < 99% | < 95% |
| P95 Latency | > 30s | > 60s |
| Connection Pool | > 80% | > 95% |
| Heap Memory | > 80% | > 90% |
| Error Rate | > 1% | > 5% |

---

## Historical Baselines

### Version History

| Version | Date | Max Concurrent | P95 Latency | Notes |
|---------|------|----------------|-------------|-------|
| 1.0.0 | YYYY-MM-DD | 200 | 5.4s | Initial baseline |

---

## Running Performance Tests

### Quick Smoke Test (5 minutes)

```bash
cd deployment/loadtest
k6 run --vus 10 --duration 5m k6-scripts/transfer-load.js
```

### Standard Load Test (30 minutes)

```bash
k6 run k6-scripts/transfer-load.js
```

### Extended Soak Test (4 hours)

```bash
mvn test -Dtest=SustainedThroughputTest#testSustained80PercentCapacity4Hours \
    -Dload-test=true
```

### Full Benchmark Suite

```bash
# Run all load tests
mvn test -Dgroups=load-test -Dload-test=true

# Generate report
./scripts/generate-performance-report.sh
```

---

## Appendix

### Test Data Generation

```bash
# Generate test files of various sizes
dd if=/dev/urandom of=test-1mb.bin bs=1M count=1
dd if=/dev/urandom of=test-10mb.bin bs=1M count=10
dd if=/dev/urandom of=test-100mb.bin bs=1M count=100
```

### Profiling Commands

```bash
# Enable JFR (Java Flight Recorder)
java -XX:StartFlightRecording=duration=300s,filename=profile.jfr -jar app.jar

# Analyze with JFR
jfr print --events jdk.GCPhasePause profile.jfr
```
