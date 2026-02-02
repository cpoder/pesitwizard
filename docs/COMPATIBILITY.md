# PeSIT Wizard Compatibility Matrix

## Supported Platforms

### Java Runtime

| JVM Version | Status | Notes |
|-------------|--------|-------|
| Java 21+ | **Required** | LTS, recommended |
| Java 17 | Not supported | Requires source changes |
| Java 11 | Not supported | Missing required features |

**Tested JVM implementations:**
- Eclipse Temurin (recommended)
- OpenJDK
- Amazon Corretto
- GraalVM

### Operating Systems

| OS | Status | Notes |
|----|--------|-------|
| Linux (x64) | **Fully supported** | Primary development platform |
| Linux (ARM64) | Supported | Tested on ARM servers |
| Windows Server | Supported | Requires PowerShell for scripts |
| macOS (ARM) | Development only | Apple Silicon tested |
| Docker | **Recommended** | eclipse-temurin:21-jre-alpine |

### Container Platforms

| Platform | Status | Notes |
|----------|--------|-------|
| Docker | Supported | Dockerfile included |
| Kubernetes | Supported | Helm charts included |
| OpenShift | Supported | Compatible with Kubernetes |
| Podman | Supported | Docker-compatible |

### Databases

| Database | Status | Notes |
|----------|--------|-------|
| H2 | Development | Default, embedded |
| PostgreSQL 15+ | **Production** | Recommended for HA |
| PostgreSQL 14 | Supported | Minimum version |
| MySQL 8+ | Supported | Requires config changes |

## PeSIT Protocol Compatibility

### Protocol Versions

| Version | Status | Notes |
|---------|--------|-------|
| PeSIT E (Hors-SIT) | **Fully supported** | September 1989 spec |
| PeSIT SIT | Not supported | Banking-specific extensions |

### Transport Protocols

| Transport | Status | Port | Notes |
|-----------|--------|------|-------|
| TCP | Supported | 5000 | Plain text |
| TLS 1.2 | Supported | 5001 | Required for CX |
| TLS 1.3 | Supported | 5001 | Preferred |
| X.25 | Not supported | - | Legacy |

### TLS Configuration

| Feature | Status | Notes |
|---------|--------|-------|
| Server authentication | Supported | Standard |
| Client authentication (mTLS) | Supported | Optional |
| Certificate validation | Supported | X.509 chain |
| Session resumption | Supported | Performance |

**Supported cipher suites (default order):**
1. TLS_AES_256_GCM_SHA384
2. TLS_AES_128_GCM_SHA256
3. TLS_CHACHA20_POLY1305_SHA256
4. ECDHE-RSA-AES256-GCM-SHA384
5. ECDHE-RSA-AES128-GCM-SHA256

## Partner System Compatibility

### Verified Interoperability

| System | Version | TCP | TLS | Notes |
|--------|---------|-----|-----|-------|
| IBM Sterling Connect:Express | 1.5+ | Yes | Yes | TLSv1.2 required |
| Axway Transfer CFT | 3.x | Yes | Untested | Should work |
| GoAnywhere MFT | 6.x | Yes | Untested | PeSIT module |
| Otonet | Various | Yes | Untested | French banks |

### Connect:Express Specifics

**Protocol differences:**
- TCP framing: `[2-byte length] + [FPDU]`
- TLS framing: `[FPDU]` only (no length prefix)

**TLS requirements:**
- Protocol: TLSv1.2 (TLSv1.3 not always supported)
- Certificate format: PKCS12
- SSLPARM configuration required

**Tested scenarios:**
- [x] PW Client -> CX Server (TCP)
- [x] PW Client -> CX Server (TLS)
- [x] CX Client -> PW Server (TCP)
- [x] CX Client -> PW Server (TLS)
- [x] 5MB file transfers over TLS
- [x] Sync point restart

## Storage Connector Compatibility

### Local Filesystem

| Feature | Status |
|---------|--------|
| Read files | Supported |
| Write files | Supported |
| Subdirectories | Supported |
| Symbolic links | Follows links |
| Network shares (NFS/SMB) | Supported |

### SFTP

| Feature | Status | Notes |
|---------|--------|-------|
| Password auth | Supported | |
| Key auth | Supported | RSA, ECDSA, Ed25519 |
| Host key verification | Supported | Recommended |
| Proxy jump | Not supported | |

### AWS S3 / MinIO

| Feature | Status | Notes |
|---------|--------|-------|
| Read objects | Supported | |
| Write objects | Supported | |
| IAM authentication | Supported | |
| Access keys | Supported | |
| S3-compatible (MinIO) | Supported | |

## API Compatibility

### REST API

| Version | Status | Notes |
|---------|--------|-------|
| v1 | Current | Stable |

**Content types:**
- application/json (default)
- multipart/form-data (file uploads)

### Authentication

| Method | Status | Notes |
|--------|--------|-------|
| API Key | Supported | X-API-Key header |
| OAuth2/OIDC | Supported | Keycloak tested |
| Basic Auth | Not supported | |

## Browser Compatibility (UI)

| Browser | Status |
|---------|--------|
| Chrome 90+ | Supported |
| Firefox 88+ | Supported |
| Safari 14+ | Supported |
| Edge 90+ | Supported |

## Version History

| PeSIT Wizard | Java | Spring Boot | Release |
|--------------|------|-------------|---------|
| 1.0.0 | 21 | 3.5.x | Current |

## Testing Interoperability

To test compatibility with a new partner system:

1. **TCP connectivity:**
   ```bash
   # Test port connectivity
   nc -zv partner-host 5000
   ```

2. **TLS handshake:**
   ```bash
   # Verify TLS
   openssl s_client -connect partner-host:5001 -CAfile ca.pem
   ```

3. **Protocol test:**
   ```bash
   # Use PW Client to send a small file
   curl -X POST http://localhost:9081/api/v1/transfers/send \
     -H "Content-Type: application/json" \
     -d '{"server":"partner","filename":"/test.txt","remoteFilename":"TESTFILE"}'
   ```

4. **Verify transfer:**
   ```bash
   # Check transfer status
   curl http://localhost:9081/api/v1/transfers/{id}
   ```

## Known Limitations

1. **PeSIT SIT profile** - Not implemented (banking-specific)
2. **X.25 transport** - Not implemented (legacy)
3. **EBCDIC encoding** - Partial support (converted to ASCII)
4. **Compression** - Not implemented
5. **Multi-node file locks** - Requires shared filesystem
