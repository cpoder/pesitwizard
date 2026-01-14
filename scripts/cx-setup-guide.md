# C:X Configuration Guide for PeSIT Wizard Testing

This guide documents the IBM Sterling Connect:Express (C:X) configuration needed to test file transfers with the PeSIT Wizard server.

## Prerequisites

- C:X installed at `$TOM_DIR` (typically `/home/cpo/cexp`)
- C:X services running
- PeSIT Wizard server running on localhost:5000

## Configuration Steps

### 1. Partner Configuration (PESITSRV)

The partner represents the PeSIT Wizard server that C:X will connect to.

**Via C:X Web Interface:**
1. Navigate to Partners → Add Partner
2. Configure:
   - Partner Name: `PESITSRV` (max 8 chars, uppercase)
   - Protocol: PeSIT E
   - Host: `localhost` or `127.0.0.1`
   - Port: `5000`
   - Partner ID (Serveur): `PESITSRV`
   - Local ID (Demandeur): `CXCLIENT` (or your C:X client identifier)
   - Password: (if authentication enabled on server)
   - Sync Points: Enabled
   - Sync Interval: `32` KB
   - Protocol Version: `2` (PeSIT E)
   - Max Entity Size: `4096` bytes

**Via C:X Command Line (if available):**
```bash
# Example command structure (adjust based on your C:X version)
p1b8padd \
  /PARTNER=PESITSRV \
  /PROTOCOL=PESIT \
  /HOST=localhost \
  /PORT=5000 \
  /SERVERID=PESITSRV \
  /CLIENTID=CXCLIENT \
  /VERSION=2 \
  /SYNCPOINTS=YES \
  /SYNCINTERVAL=32
```

### 2. File/Route Configuration (TESTFILE)

The file (or route) defines which files can be transferred to/from this partner.

**Via C:X Web Interface:**
1. Navigate to Files → Add File
2. Configure:
   - File Name: `TESTFILE`
   - Partner: `PESITSRV`
   - Direction: `Both` (or `Send` for testing)
   - Local Path: `$TOM_DIR/out/test_to_server.txt` (send)
   - Remote Path: Leave empty or specify if needed
   - File Type: `Binary` or `Text`
   - Record Format: `Variable` or `Fixed`

**Via C:X Command Line (if available):**
```bash
# Example command structure
p1b8fadd \
  /FILE=TESTFILE \
  /PARTNER=PESITSRV \
  /DIRECTION=BOTH \
  /LOCALPATH=$TOM_DIR/out/test_to_server.txt \
  /TYPE=BINARY
```

### 3. Verify Configuration

**Check Partner:**
```bash
# List configured partners
p1b8plist /TYPE=PARTNER

# Check specific partner details
p1b8pdisp /PARTNER=PESITSRV
```

**Check File/Route:**
```bash
# List configured files
p1b8plist /TYPE=FILE

# Check specific file details
p1b8pdisp /FILE=TESTFILE
```

## Testing File Transfers

### Send File to PeSIT Wizard Server

```bash
cd /home/cpo/pesitwizard
./scripts/cx-test-send.sh
```

Or manually:
```bash
$TOM_DIR/itom/p1b8preq "/SFN=TESTFILE/SPN=PESITSRV/DIR=T"
```

**Expected Result:**
- Return code: 0
- File appears in: `/tmp/pesit-server-data/receive/`
- Server log shows: CONNECT → CREATE → WRITE → DTF → TRANS_END → RELEASE

### Receive File from PeSIT Wizard Server

First, place a file in the server's send directory:
```bash
echo "Test from server" > /tmp/pesit-server-data/send/TESTFILE
```

Then request it from C:X:
```bash
cd /home/cpo/pesitwizard
./scripts/cx-test-receive.sh
```

Or manually:
```bash
$TOM_DIR/itom/p1b8preq "/SFN=TESTFILE/SPN=PESITSRV/DIR=R"
```

## Troubleshooting

### Connection Refused
- Check if PeSIT Wizard server is running: `curl http://localhost:8080/actuator/health`
- Check if server is listening: `netstat -an | grep 5000`
- Check firewall rules

### Authentication Failure (RCONNECT)
- Verify partner credentials in C:X match server configuration
- Check server logs: `tail -f /home/cpo/pesitwizard/server.log`
- Verify partner exists in server database

### Protocol Errors (ABORT)
- Check server logs for diagnostic codes
- Verify protocol version matches (PeSIT E = version 2)
- Verify parameter compatibility (sync points, max entity size)

### File Not Found
- Verify file/route configuration in C:X
- Check file permissions on both sides
- Verify send/receive directories exist and are writable

## C:X Log Files

- Transfer logs: `$TOM_DIR/trace/`
- Monitor logs: `$TOM_DIR/log/monitor.log`
- Request queue: `$TOM_DIR/queue/`

## Server Configuration Verification

Check PeSIT Wizard server configuration:
```bash
curl http://localhost:8080/api/servers | jq
```

Expected:
```json
{
  "serverId": "PESITSRV",
  "status": "RUNNING",
  "port": 5000,
  "protocolVersion": 2,
  "syncPointsEnabled": true,
  "syncIntervalKb": 32,
  "maxEntitySize": 4096
}
```

## Quick Reference

| Parameter | C:X Client | PeSIT Wizard Server |
|-----------|-----------|---------------------|
| Server ID | PESITSRV | PESITSRV |
| Client ID | CXCLIENT | (accepted by server) |
| Port | 5000 | 5000 |
| Protocol | PeSIT E | PeSIT E |
| Version | 2 | 2 |
| Sync Interval | 32 KB | 32 KB (negotiated) |
| Max Entity Size | 4096 | 4096 |
| Receive Dir | $TOM_DIR/in/ | /tmp/pesit-server-data/receive/ |
| Send Dir | $TOM_DIR/out/ | /tmp/pesit-server-data/send/ |

## Starting from Scratch

If you need to completely reconfigure C:X for testing:

1. **Stop C:X:**
   ```bash
   $TOM_DIR/config/stop_tom.sh
   ```

2. **Reset C:X database (CAUTION - destroys all config):**
   ```bash
   $TOM_DIR/config/reinit_base.sh
   ```

3. **Start C:X:**
   ```bash
   $TOM_DIR/config/start_tom.sh
   ```

4. **Reconfigure partner and file as described above**

5. **Test connection:**
   ```bash
   cd /home/cpo/pesitwizard
   ./scripts/cx-test-send.sh
   ```
