# Performance and Tuning

Performance optimization guide for PeSIT Wizard.

## Reference Metrics

### Typical Benchmarks

| Scenario | Size | Expected Throughput | Latency |
|----------|------|---------------------|---------|
| LAN (1Gbps) | 100MB | 80-100 MB/s | < 1s setup |
| WAN (100Mbps) | 100MB | 10-12 MB/s | 50-200ms RTT |
| Internet | 100MB | 1-5 MB/s | Variable |

### Limiting Factors

1. **Network bandwidth** - Primary factor
2. **Network latency** - Impact on handshake and sync points
3. **Chunk size** - Overhead per FPDU
4. **CPU** - TLS encryption, checksums
5. **Disk I/O** - File read/write

---

## Optimal Configuration

### Chunk Size (PI_25)

The maximum FPDU size directly impacts performance:

```yaml
# application.yml
pesitwizard:
  transfer:
    max-entity-size: 4096   # Default PeSIT entity size
    chunk-size: 32768       # 32KB default
```

| Size | Overhead | Recommendation |
|------|----------|----------------|
| 4096 | ~0.15% | Compatibility with legacy systems |
| 16384 | ~0.04% | Standard |
| 32768 | ~0.02% | Recommended |
| 65535 | ~0.01% | Maximum performance |

**Note**: The effective value is negotiated with the partner (minimum of the two).

### Sync Points

Sync points enable restart but add overhead:

```yaml
pesitwizard:
  transfer:
    sync-points-enabled: true
    sync-point-interval: 256  # KB between sync points
```

| Interval | Overhead | Use Case |
|----------|----------|----------|
| 10 KB | ~10% | Very unstable network |
| 100 KB | ~1% | Standard |
| 256 KB | ~0.4% | Recommended for production |
| 1024 KB | ~0.1% | Reliable network |
| Disabled | 0% | Very reliable network, small files |

### Buffer sizes

```yaml
pesitwizard:
  transfer:
    read-buffer-size: 65536   # File read buffer
    write-buffer-size: 65536  # File write buffer
    socket-buffer-size: 65536 # TCP socket buffer
```

---

## JVM Configuration

### Heap memory

```bash
# For large transfers
JAVA_OPTS="-Xms512m -Xmx2g"

# For many small files
JAVA_OPTS="-Xms256m -Xmx1g"
```

**General rule**:
- Heap min: 256MB
- Heap max: 2-4GB depending on volume

### Garbage Collector

```bash
# G1GC recommended for predictable latency
JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# ZGC for very low latency (Java 17+)
JAVA_OPTS="-XX:+UseZGC"
```

### Threads

```yaml
# Number of threads for concurrent transfers
pesitwizard:
  transfer:
    executor:
      core-pool-size: 4    # Permanent threads
      max-pool-size: 20    # Maximum
      queue-capacity: 100  # Queue
```

**Sizing**:
- `core-pool-size`: Typical number of simultaneous transfers
- `max-pool-size`: Peak load
- `queue-capacity`: Buffer for short peaks

---

## Network Optimization

### TCP Tuning (Linux)

```bash
# /etc/sysctl.conf

# Increase TCP buffers
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216

# Enable window scaling
net.ipv4.tcp_window_scaling = 1

# Reduce TIME_WAIT
net.ipv4.tcp_fin_timeout = 30

# Apply
sysctl -p
```

### Timeouts

```yaml
pesitwizard:
  transfer:
    connection-timeout: 30000   # 30s to establish the connection
    read-timeout: 120000        # 2min to read data
    write-timeout: 120000       # 2min to write
```

**For large files** (> 1GB):
```yaml
pesitwizard:
  transfer:
    read-timeout: 600000   # 10 minutes
    write-timeout: 600000
```

---

## TLS Optimization

### Fast Cipher Suites

```yaml
pesit:
  ssl:
    protocol: TLSv1.3
    cipher-suites:
      - TLS_AES_256_GCM_SHA384       # Faster with AES-NI
      - TLS_AES_128_GCM_SHA256
      - TLS_CHACHA20_POLY1305_SHA256 # Fast without AES-NI
```

### Session caching

TLS session caching reduces overhead from repeated handshakes:

```yaml
pesit:
  ssl:
    session-cache-size: 1000
    session-timeout: 86400  # 24h
```

### Verify AES-NI

```bash
# Check CPU support
grep -o aes /proc/cpuinfo | head -1

# If present, AES-GCM will be hardware-accelerated
```

---

## Performance Monitoring

### Micrometer Metrics

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,metrics
  metrics:
    tags:
      application: pesitwizard
```

### Key Metrics

| Metric | Description | Alert if |
|--------|-------------|----------|
| `pesit.transfer.duration` | Transfer duration | > 300s for 100MB |
| `pesit.transfer.throughput` | Throughput in bytes/s | < 1 MB/s LAN |
| `jvm.memory.used` | Heap memory | > 80% max |
| `jvm.gc.pause` | GC pauses | > 500ms |
| `system.cpu.usage` | System CPU | > 80% |

### Grafana Dashboard

```json
{
  "panels": [
    {
      "title": "Transfer Throughput",
      "targets": [{
        "expr": "rate(pesit_transfer_bytes_total[5m])"
      }]
    },
    {
      "title": "Transfer Duration (p95)",
      "targets": [{
        "expr": "histogram_quantile(0.95, rate(pesit_transfer_duration_seconds_bucket[5m]))"
      }]
    },
    {
      "title": "Active Transfers",
      "targets": [{
        "expr": "pesit_transfers_active"
      }]
    }
  ]
}
```

---

## Performance Benchmark

### Benchmark Script

```bash
#!/bin/bash
# benchmark.sh - PeSIT Wizard performance test

API_URL=${1:-http://localhost:8080}
SERVER=${2:-cx-server}
PARTNER=${3:-PWSRV01}
SIZES="1M 10M 100M"
ITERATIONS=3

echo "=== PeSIT Wizard Benchmark ==="
echo "Server: $SERVER"
echo "Partner: $PARTNER"
echo ""

for size in $SIZES; do
  echo "--- Testing $size file ---"

  # Create test file
  dd if=/dev/urandom of=/tmp/bench_$size.dat bs=$size count=1 2>/dev/null
  FILESIZE=$(stat -c%s /tmp/bench_$size.dat)

  total_time=0

  for i in $(seq 1 $ITERATIONS); do
    start=$(date +%s.%N)

    result=$(curl -s -X POST "$API_URL/api/v1/transfers/send" \
      -H "Content-Type: application/json" \
      -d "{
        \"server\": \"$SERVER\",
        \"partnerId\": \"$PARTNER\",
        \"filename\": \"/tmp/bench_$size.dat\",
        \"remoteFilename\": \"PWRECV\"
      }")

    id=$(echo $result | jq -r '.transferId // .id')

    # Wait for completion
    while true; do
      status=$(curl -s "$API_URL/api/v1/transfers/$id" | jq -r '.status')
      [ "$status" = "COMPLETED" ] || [ "$status" = "FAILED" ] && break
      sleep 0.5
    done

    end=$(date +%s.%N)
    duration=$(echo "$end - $start" | bc)
    total_time=$(echo "$total_time + $duration" | bc)

    throughput=$(echo "scale=2; $FILESIZE / $duration / 1048576" | bc)
    echo "  Run $i: ${duration}s (${throughput} MB/s)"
  done

  avg=$(echo "scale=2; $total_time / $ITERATIONS" | bc)
  avg_throughput=$(echo "scale=2; $FILESIZE / $avg / 1048576" | bc)
  echo "  Average: ${avg}s (${avg_throughput} MB/s)"
  echo ""

  rm /tmp/bench_$size.dat
done
```

### Expected Results

| Size | LAN 1Gbps | WAN 100Mbps | Internet |
|------|-----------|-------------|----------|
| 1 MB | < 0.5s | < 1s | 1-5s |
| 10 MB | < 1s | 2-3s | 10-30s |
| 100 MB | 2-5s | 15-30s | 2-5min |
| 1 GB | 15-30s | 3-5min | 20-60min |

---

## Advanced Optimizations

### Compression (if supported)

```yaml
pesitwizard:
  transfer:
    compression:
      enabled: true
      algorithm: GZIP
      level: 6  # 1-9, speed/ratio tradeoff
```

**Note**: PeSIT compression is not always supported by partners.

### Connection pooling

For many small transfers to the same server:

```yaml
pesitwizard:
  connection:
    pool:
      enabled: true
      max-connections-per-host: 10
      idle-timeout: 300000  # 5 minutes
```

### Parallel transfers

To transfer multiple files in parallel:

```bash
# Via API - transfers are naturally parallel
for file in /data/outbox/*.dat; do
  curl -X POST "$API_URL/api/v1/transfers/send" \
    -H "Content-Type: application/json" \
    -d "{...}" &
done
wait
```

---

## Known Limits

| Limit | Value | Workaround |
|-------|-------|------------|
| Max file size | Unlimited* | Use sync points |
| Max FPDU | 65535 bytes | PeSIT limitation |
| Simultaneous sessions | ~100 | Configurable |
| Memory per transfer | ~1-2 MB | Streaming, no full buffer |

*Files of several GB have been tested successfully.

## Performance Checklist

- [ ] `max-entity-size` configured to 4096 or higher
- [ ] `sync-point-interval` adapted to the network (256KB recommended)
- [ ] JVM heap sized correctly
- [ ] TCP buffers increased (Linux)
- [ ] TLS with AES-GCM cipher (if AES-NI available)
- [ ] Prometheus/Grafana monitoring in place
- [ ] Reference benchmarks established
