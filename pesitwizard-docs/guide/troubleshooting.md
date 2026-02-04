# Troubleshooting

This guide covers common issues and their resolution.

## Connection Errors

### CONNECTION_REFUSED

**Symptom**: The transfer fails immediately with "Connection refused"

**Possible causes**:
| Cause | Diagnosis | Solution |
|-------|-----------|----------|
| Server stopped | `netstat -tlnp \| grep 6502` | Start the PeSIT server |
| Wrong port | Check server config | Fix the port in the config |
| Firewall | `telnet host 6502` | Open the port in the firewall |
| DNS | `nslookup hostname` | Check DNS resolution |

**Diagnostic commands**:
```bash
# Test TCP connectivity
nc -zv pesit-server.example.com 6502

# Verify that the server is listening
ss -tlnp | grep 6502

# Test from the Docker container
docker exec pw-client nc -zv cx-server 6502
```

### CONNECTION_TIMEOUT

**Symptom**: The transfer hangs then fails after the timeout

**Possible causes**:
| Cause | Diagnosis | Solution |
|-------|-----------|----------|
| Slow network | `ping host` | Increase connectionTimeout |
| Silent firewall | `traceroute host` | Check firewall rules |
| Overloaded server | Server logs | Increase server resources |

**Timeout configuration**:
```yaml
# application.yml (client)
pesitwizard:
  transfer:
    connection-timeout: 30000  # 30 seconds
    read-timeout: 120000       # 2 minutes
```

---

## Authentication Errors

### AUTH_FAILED / Diagnostic 0x010101

**Symptom**: Connection established but rejected with diagnostic code

**Common diagnostic codes**:
| Code | Meaning | Solution |
|------|---------|----------|
| `0x010101` | Unknown Partner ID | Check partnerId on the server side |
| `0x010102` | Incorrect password | Check password |
| `0x010103` | Partner disabled | Enable the partner |
| `0x010104` | Max sessions reached | Wait or increase the limit |

**Server-side verification**:
```bash
# List configured partners
curl -s -u admin:admin http://localhost:8080/api/v1/config/partners | jq '.[].id'

# Check a specific partner
curl -s -u admin:admin http://localhost:8080/api/v1/config/partners/PARTNER_ID | jq
```

**CX-side verification**:
```bash
# List CX partners
$sterm
# Then: L P (List Partners)
```

### PARTNER_UNKNOWN

**Symptom**: "Partner not found" or "Unknown partner"

**Checklist**:
- [ ] The partnerId is exactly identical (case, spaces)
- [ ] The partner exists on the server side
- [ ] The partner is enabled (enabled: true)
- [ ] The password matches (if required)

---

## Virtual File Errors

### FILE_NOT_FOUND / Diagnostic 0x020201

**Symptom**: The virtual file is not found on the server

**Diagnostic codes**:
| Code | Meaning | Solution |
|------|---------|----------|
| `0x020201` | Unknown virtual file | Create the virtual file |
| `0x020202` | Incompatible direction | Check SEND vs RECEIVE |
| `0x020203` | File disabled | Enable the virtual file |

**PW Server verification**:
```bash
# List virtual files
curl -s -u admin:admin http://localhost:8080/api/v1/config/files | jq '.[].id'

# File details
curl -s -u admin:admin http://localhost:8080/api/v1/config/files/FILENAME | jq
```

**CX verification**:
```bash
$sterm
# Then: L F (List Files)
```

### FORMAT_MISMATCH

**Symptom**: Format error during transfer

**Causes**:
- Incompatible format (BV/BF/TV/TF)
- Different record length
- Unsupported compression

**Solution**:
```bash
# CX: Use FORMAT=** to accept any format
# setup-partners.sh
memcpy(param->uni.zreq_tom_file.format, "**", 2);
```

---

## TLS/SSL Errors

### SSL_HANDSHAKE_FAILURE

**Symptom**: "SSL handshake failed" or "Certificate error"

**Possible causes**:
| Cause | Diagnosis | Solution |
|-------|-----------|----------|
| Expired certificate | `openssl x509 -enddate` | Renew the certificate |
| Untrusted CA | Check truststore | Import the CA certificate |
| CN mismatch | Check hostname | Fix the CN or SAN |
| Protocol mismatch | TLS logs | Align TLS versions |

**OpenSSL diagnostics**:
```bash
# Test the TLS connection
openssl s_client -connect pesit-server:5001 -tls1_3

# Check the server certificate
openssl s_client -connect pesit-server:5001 < /dev/null 2>/dev/null | \
  openssl x509 -text -noout

# Check the expiration date
openssl s_client -connect pesit-server:5001 < /dev/null 2>/dev/null | \
  openssl x509 -enddate -noout
```

**Check certificates**:
```bash
# List certificates in a PKCS12 keystore
keytool -list -keystore keystore.p12 -storepass changeit

# Verify the trust chain
openssl verify -CAfile ca-cert.pem server-cert.pem
```

### CERTIFICATE_EXPIRED

**Symptom**: "Certificate has expired"

**Solution**:
```bash
# 1. Generate a new certificate
curl -X POST "http://localhost:8080/api/v1/certificates/ca/partner/PARTNER/generate" \
  -u admin:admin \
  -d "validityDays=365"

# 2. Distribute the new certificate to the partner
# 3. Restart the connections
```

---

## Transfer Errors

### TRANSFER_INTERRUPTED

**Symptom**: Transfer interrupted mid-way

**Diagnostics**:
```bash
# Check the transfer history
curl -s http://localhost:8080/api/v1/transfers/TRANSFER_ID | jq

# Check the sync points
curl -s http://localhost:8080/api/v1/transfers/TRANSFER_ID | jq '{
  status,
  bytesTransferred,
  lastSyncPoint,
  bytesAtLastSyncPoint,
  errorMessage
}'
```

**Transfer resumption**:
```bash
# If sync points > 0, the transfer can be resumed
curl -X POST http://localhost:8080/api/v1/transfers/TRANSFER_ID/resume
```

### DISK_FULL

**Symptom**: "No space left on device"

**Diagnostics**:
```bash
# Check disk space
df -h /data/received

# Find large files
du -sh /data/received/* | sort -rh | head -10
```

**Solution**:
- Clean up old files
- Increase disk space
- Configure automatic purging

### CHECKSUM_MISMATCH

**Symptom**: MD5/SHA mismatch after transfer

**Possible causes**:
- Network corruption
- Format conversion issue
- Bug in multi-article DTF

**Diagnostics**:
```bash
# Compare checksums
md5sum source_file
md5sum received_file

# Compare sizes
ls -la source_file received_file
```

---

## Performance Issues

### TRANSFER_SLOW

**Symptom**: Abnormally slow transfers

**Diagnostics**:
```bash
# Measure network throughput
iperf3 -c pesit-server -p 5201

# Check latency
ping -c 10 pesit-server

# Check CPU/memory usage
docker stats pw-client pw-server
```

**Optimizations**:
```yaml
# Increase chunk size
pesitwizard:
  transfer:
    chunk-size: 32768      # 32KB instead of 4KB
    max-entity-size: 65535 # PeSIT maximum
```

### MEMORY_LEAK

**Symptom**: Memory continuously increasing

**Diagnostics**:
```bash
# Monitor Java memory
jcmd $(pgrep -f pesitwizard) VM.native_memory summary

# Heap dump for analysis
jmap -dump:format=b,file=heap.hprof $(pgrep -f pesitwizard)
```

---

## Logs and Diagnostics

### Enable DEBUG Logs

```yaml
# application.yml
logging:
  level:
    com.pesitwizard: DEBUG
    com.pesitwizard.fpdu: TRACE  # FPDU details
    com.pesitwizard.session: DEBUG
```

### Analyze Logs

```bash
# Filter errors
grep -E "ERROR|WARN" /var/log/pesitwizard/*.log

# Follow a specific transfer
grep "transfer-id-xxx" /var/log/pesitwizard/*.log

# Analyze FPDUs
grep "FPDU" /var/log/pesitwizard/*.log | tail -50
```

### Docker Logs

```bash
# Real-time logs
docker logs -f pw-client

# Logs with timestamps
docker logs --timestamps pw-client 2>&1 | tail -100

# Filter by pattern
docker logs pw-client 2>&1 | grep -i error
```

---

## PeSIT Diagnostic Codes

### Session (0x01xxxx)

| Code | Description |
|------|-------------|
| `0x010101` | Unknown partner |
| `0x010102` | Incorrect password |
| `0x010103` | Partner disabled |
| `0x010104` | Max session count reached |
| `0x010105` | Unauthorized access type |

### File (0x02xxxx)

| Code | Description |
|------|-------------|
| `0x020201` | Unknown virtual file |
| `0x020202` | Unauthorized direction |
| `0x020203` | File disabled |
| `0x020204` | File already open |

### Transfer (0x03xxxx)

| Code | Description |
|------|-------------|
| `0x030301` | Format error |
| `0x030302` | Size exceeded |
| `0x030303` | Sync point refused |
| `0x030304` | Transfer cancelled |

---

## Diagnostic Tools

### Full Connectivity Test

```bash
#!/bin/bash
# test-pesit-connectivity.sh

HOST=$1
PORT=${2:-6502}

echo "=== PeSIT Connectivity Test ==="
echo "Host: $HOST"
echo "Port: $PORT"
echo ""

echo "1. DNS resolution..."
nslookup $HOST || echo "FAIL: DNS"

echo ""
echo "2. TCP connectivity..."
nc -zv $HOST $PORT && echo "OK: TCP" || echo "FAIL: TCP"

echo ""
echo "3. TLS handshake..."
timeout 5 openssl s_client -connect $HOST:$PORT < /dev/null 2>/dev/null && \
  echo "OK: TLS" || echo "INFO: TLS not enabled or failed"

echo ""
echo "4. Certificate check..."
echo | openssl s_client -connect $HOST:$PORT 2>/dev/null | \
  openssl x509 -noout -dates 2>/dev/null || echo "INFO: No certificate"
```

### Network Capture

```bash
# Capture PeSIT traffic (non-TLS)
tcpdump -i any port 6502 -w pesit-capture.pcap

# Analyze with Wireshark
wireshark pesit-capture.pcap
```
