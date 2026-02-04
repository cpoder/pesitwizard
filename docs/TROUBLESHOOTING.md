# PeSIT Wizard Troubleshooting Guide

This guide covers common issues and their solutions when running PeSIT Wizard.

## Connection Issues

### Error: Connection refused
**Symptoms:** Client cannot connect to server, "Connection refused" error.

**Solutions:**
1. Verify the server is running:
   ```bash
   curl http://localhost:8080/api/servers
   ```
   Check that the server status is "RUNNING".

2. Verify the correct port is configured and not blocked:
   ```bash
   netstat -tlnp | grep 6502
   ```

3. Check firewall rules allow the PeSIT port.

### Error: TLS handshake failed
**Symptoms:** "SSL handshake exception" or "Certificate validation failed".

**Solutions:**
1. Verify certificates are uploaded and configured:
   ```bash
   curl http://localhost:8080/api/v1/certificates/stats
   ```

2. Check certificate expiration:
   ```bash
   curl http://localhost:8080/api/v1/certificates/keystores
   ```
   Look for `expiringIn7Days` or `expiredCount` > 0.

3. Ensure TLS protocol compatibility:
   - PeSIT Wizard supports TLSv1.2 and TLSv1.3
   - Connect:Express typically requires TLSv1.2
   - Set `PESIT_SSL_PROTOCOL=TLSv1.2` for CX compatibility

4. Verify the truststore contains the CA certificate for the remote server.

### Error: Partner not configured (D3-301)
**Symptoms:** Connection rejected with diagnostic code D3-301.

**Solutions:**
1. Check partner exists:
   ```bash
   curl http://localhost:8080/api/v1/config/partners
   ```

2. Verify the partner ID matches exactly (case-sensitive, max 8 characters).

3. Create the partner if missing:
   ```bash
   curl -X POST http://localhost:8080/api/v1/config/partners \
     -H "Content-Type: application/json" \
     -d '{"id":"PARTNER1","description":"Partner description","enabled":true}'
   ```

### Error: Invalid password (D3-304)
**Symptoms:** Connection rejected with "Invalid password" error.

**Solutions:**
1. Verify the password in the partner configuration.
2. Check password encoding - PeSIT uses ISO-8859-1.
3. Ensure the password is correctly encrypted if using vault storage.

## Transfer Issues

### Error: File not found (D2-205)
**Symptoms:** Transfer fails with "File not found" error.

**Solutions:**
1. Verify the file exists at the configured path.
2. Check the virtual file configuration points to the correct directory.
3. Verify file permissions allow the application to read/write.

### Error: Transfer timeout
**Symptoms:** Transfer hangs and eventually times out.

**Solutions:**
1. Check network connectivity between client and server.
2. Increase timeout values in configuration:
   ```yaml
   pesit:
     connection-timeout: 60000
     read-timeout: 300000
   ```
3. For large files, ensure sync points are enabled to allow resume.

### Error: Entity size mismatch
**Symptoms:** "Max entity size exceeded" or negotiation failure.

**Solutions:**
1. Both sides must agree on max entity size (PI 25).
2. PeSIT Wizard default: 4096 bytes
3. Connect:Express typically uses 4096 bytes
4. The smaller value is used during negotiation.

### TLS vs TCP Protocol Framing
**Important:** TCP and TLS use different framing:
- **TCP (port 6502):** expects `[2-byte length prefix] + [FPDU]`
- **TLS (port 5001):** expects just `[FPDU]` (uses FPDU's built-in length)

If transfers work over TCP but fail over TLS, ensure:
1. You're using the correct port for each protocol
2. The TLS-specific framing is being used

## Performance Issues

### Slow transfers
**Solutions:**
1. Increase max entity size (up to 32768 recommended).
2. Enable sync points for large files (allows resume on failure).
3. Check network bandwidth and latency.
4. Review disk I/O performance on both ends.

### High memory usage
**Solutions:**
1. Reduce max connections per server.
2. Limit concurrent transfers.
3. Increase JVM heap if needed: `-Xmx2g`

## Logging and Diagnostics

### Enable debug logging
Add to `application.yml`:
```yaml
logging:
  level:
    com.pesitwizard: DEBUG
    com.pesitwizard.fpdu: TRACE  # For FPDU-level debugging
```

### View transfer history
```bash
curl http://localhost:8080/api/v1/transfers/search?status=FAILED
```

### View audit events
```bash
curl http://localhost:8080/api/v1/audit/recent?limit=100
```

## PeSIT Diagnostic Codes

| Code | Meaning | Solution |
|------|---------|----------|
| D0-000 | Success | No action needed |
| D2-205 | File not found | Check file path and permissions |
| D2-213 | Write error | Check disk space and permissions |
| D2-220 | Article length error | Check record length configuration |
| D2-222 | No sync point | Enable sync points for restart |
| D3-301 | Partner not configured | Add partner configuration |
| D3-304 | Access denied | Check password and access permissions |
| D3-311 | Protocol error | Check FPDU sequence and state machine |

## Getting Help

1. Check the logs: `tail -f /var/log/pesitwizard/application.log`
2. Enable FPDU tracing for protocol-level debugging
3. Review the API documentation at `/swagger-ui.html`
4. File issues at: https://github.com/anthropics/claude-code/issues
