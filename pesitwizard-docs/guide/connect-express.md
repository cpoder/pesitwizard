# Connect:Express Interoperability

Configuration guide for interoperability between PeSIT Wizard and IBM Sterling Connect:Express (CX).

## Overview

PeSIT Wizard is fully compatible with Connect:Express for bidirectional transfers:

| Direction | Source | Destination | Status |
|-----------|--------|-------------|--------|
| CX → PW Server | Connect:Express | PeSIT Wizard Server | ✅ Validated |
| PW Client → CX | PeSIT Wizard Client | Connect:Express | ✅ Validated |

## Prerequisites

### Connect:Express
- Version 1.5.x or higher
- PeSIT license activated
- Access to `$sterm`, `$p1b8preq` commands

### PeSIT Wizard
- Server or Client version 1.0.0+
- Java 21+

---

## Configuration CX → PW Server

This configuration allows Connect:Express to send files to PeSIT Wizard Server.

### 1. Create the partner in CX

```bash
# Using the cx-setup-partner tool
./cx-setup-partner PWSERVER pw-server 05001 PWSRV01

# Or manually via $sterm:
# C P (Create Partner)
# Symbolic name: PWSERVER
# Nature: T (TCP/IP)
# Protocol: 3 (PeSIT)
# TCP Host: pw-server (or IP)
# TCP Port: 05001
# DPCSID: PWSRV01
```

**Important parameters**:
| Parameter | Value | Description |
|-----------|-------|-------------|
| Nature | `T` | TCP/IP (or `S` for SSL) |
| Protocol | `3` | PeSIT |
| Session Table | `1` | Default session table |
| Max Links | `10` | Max simultaneous sessions |
| Link Type | `M` | Mixed (initiator + responder) |

### 2. Create the virtual file in CX

```bash
# Using the cx-setup-file tool
./cx-setup-file PWSEND T /tmp/cx-send PWSERVER BV 04096

# Or via $sterm:
# C F (Create File)
# Symbolic name: PWSEND
# Direction: T (Transmit)
# DSN: /tmp/cx-send/&REQNUMB
# Partner: PWSERVER
# Format: BV (Binary Variable) or **
# Record length: 04096
```

### 3. Configure PW Server

```yaml
# application.yml (PW Server)
pesitwizard:
  server:
    port: 5001
    server-id: PWSRV01
```

Create the CX partner:
```bash
curl -X POST http://localhost:8080/api/v1/config/partners \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "id": "CETOM1",
    "description": "Connect:Express",
    "enabled": true,
    "accessType": "BOTH"
  }'
```

Create the virtual file:
```bash
curl -X POST http://localhost:8080/api/v1/config/files \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "id": "PWSEND",
    "direction": "RECEIVE",
    "receiveDirectory": "/data/received",
    "receiveFilenamePattern": "from_cx_${transferId}",
    "enabled": true
  }'
```

### 4. Test the transfer

```bash
# From CX, send a file
$p1b8preq /SFN=PWSEND/SPN=PWSERVER/DIR=T/DSN=/path/to/file.dat

# Verify on PW Server
ls -la /data/received/
```

---

## Configuration PW Client → CX

This configuration allows PeSIT Wizard Client to send files to Connect:Express.

### 1. Configure CX as server

Verify that CX is listening on the PeSIT port:
```bash
netstat -tlnp | grep 5000
# Or
$sterm
# Then: D S (Display System)
```

Create the partner for PW Client:
```bash
./cx-setup-partner PWCLIENT pw-client 08080 PWSRV01
```

Create the virtual file for reception:
```bash
./cx-setup-file PWRECV R /tmp/cx-received PWCLIENT ** 04096
```

**Note**: Use `FORMAT=**` to accept any format sent by PW Client.

### 2. Configure PW Client

Add the CX server:
```bash
curl -X POST http://localhost:8080/api/v1/servers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "cx-server",
    "host": "cx-server",
    "port": 5000,
    "serverId": "CETOM1",
    "description": "Connect:Express Server",
    "tlsEnabled": false,
    "enabled": true
  }'
```

### 3. Send a file

```bash
curl -X POST http://localhost:8080/api/v1/transfers/send \
  -H "Content-Type: application/json" \
  -d '{
    "server": "cx-server",
    "partnerId": "PWSRV01",
    "filename": "/data/send/test.dat",
    "remoteFilename": "PWRECV",
    "syncPointsEnabled": true
  }'
```

---

## Compatibility Parameters

### Entity Size (PI_25)

PW and CX negotiate the maximum FPDU size:

| System | Default Value | Recommendation |
|--------|---------------|----------------|
| PW Server | 32768 | OK |
| PW Client | 32768 | OK |
| CX | 4096 | Increase if possible |

The negotiated value will be the minimum of the two. For better performance, configure CX with a higher value in the session table.

### Record Format

| CX Format | Description | PW Compatibility |
|-----------|-------------|------------------|
| `BV` | Binary Variable | ✅ Recommended |
| `BF` | Binary Fixed | ✅ OK |
| `TV` | Text Variable | ✅ OK |
| `TF` | Text Fixed | ✅ OK |
| `**` | Any format | ✅ Flexible |

### Sync Points

Sync points are supported for restart after interruption:

```yaml
# PW Client - enable sync points
syncPointsEnabled: true

# CX - configure the interval in the session table
# Typically 100KB or 256KB
```

---

## CX Troubleshooting

### Check CX Status

```bash
# Monitor status
$sterm
# D S (Display System)

# List partners
# L P (List Partners)

# List virtual files
# L F (List Files)

# View transfers in progress
# L R (List Requests)
```

### CX Logs

```bash
# Main log
tail -f $TOM_DIR/log/tom.log

# Request log
$p1b8pret /RQN=<request_number>
```

### Common Errors

| CX Error | Cause | Solution |
|----------|-------|----------|
| `RTCF 0017` | Duplicate entry | Partner/file already exists |
| `RTCF 0004` | Not found | Check the exact name |
| `RTCF 0008` | Invalid parameter | Check the syntax |
| `CONNECT refused` | Auth failed | Check DPCSID/password |
| `File not found` | Unknown virtual file | Create the file in CX |

### Connectivity Test

```bash
# From the CX machine to PW Server
nc -zv pw-server 5001

# From PW to CX
nc -zv cx-server 5000
```

---

## TLS Configuration (Advanced)

### CX with SSL

To enable TLS between PW and CX:

1. **Change the partner nature** from `T` (TCP) to `S` (SSL):
   ```c
   memcpy(param->uni.zreq_tom_part.nature, "S", 1);
   ```

2. **Configure the SSL parameters** (SSLPARM1, SSLPARM2) in CX

3. **Import certificates** via CX scripts:
   - `CXAPISCA` - API certificate
   - `CXROOTCA` - Root CA certificate
   - `SSLPARM1` - SSL parameters

4. **Configure PW** with the corresponding certificates:
   ```yaml
   pesit:
     ssl:
       enabled: true
       keystore-name: cx-compatible-keystore
       truststore-name: cx-ca-truststore
   ```

**Note**: TLS configuration with CX requires thorough testing for cipher suite and TLS version compatibility.

---

## Docker Integration Tests

A complete Docker environment is available for testing interoperability:

```bash
cd integration-tests/cx-integration/docker

# Start the environment
docker compose up -d

# Run the tests (16 tests)
docker compose up test-runner

# View the results
docker compose logs test-runner
```

### Included Tests

| Phase | Tests | Description |
|-------|-------|-------------|
| 1 | 5 | CX → PW Server (small, 5MB, config) |
| 2 | 3 | PW Client → CX (small, 5MB, health) |
| 3 | 1 | Sync points (10MB with MD5) |
| 4 | 1 | Concurrent transfers (3x simultaneous) |
| 5 | 3 | Error handling |
| 6 | 3 | Edge cases (empty, spaces, 1 byte) |

---

## Compatibility Matrix

| Feature | PW ↔ PW | PW ↔ CX | Notes |
|---------|---------|---------|-------|
| SEND Transfer | ✅ | ✅ | |
| RECEIVE Transfer | ✅ | ✅ | |
| Sync Points | ✅ | ✅ | |
| Restart/Resume | ✅ | ✅ | After sync points fix |
| Simple TLS | ✅ | ⚠️ | To be validated |
| mTLS | ✅ | ⚠️ | To be validated |
| Compression | ✅ | ❓ | Not tested |
| Multi-article DTF | ✅ | ✅ | |
| Files > 1GB | ✅ | ✅ | Tested up to 10MB |
