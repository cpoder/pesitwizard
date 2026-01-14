# PeSIT Wizard Scripts

Helper scripts for managing PeSIT Wizard server, client, and C:X integration testing.

## Server Management

### `start-server.sh`
Start the PeSIT Wizard server.
```bash
./scripts/start-server.sh
```

### `install-server.sh`
Install and configure the PeSIT Wizard server.
```bash
./scripts/install-server.sh
```

## Client Management

### `start-client.sh`
Start the PeSIT Wizard client.
```bash
./scripts/start-client.sh
```

### `install-client.sh`
Install and configure the PeSIT Wizard client.
```bash
./scripts/install-client.sh
```

## C:X Integration Testing

### `cx-test-send.sh`
Test sending a file from IBM Sterling Connect:Express (C:X) to PeSIT Wizard server.

**Usage:**
```bash
./scripts/cx-test-send.sh
```

**With custom parameters:**
```bash
PARTNER_NAME=PESITSRV FILE_NAME=TESTFILE SOURCE_FILE=/path/to/file.txt ./scripts/cx-test-send.sh
```

**Environment Variables:**
- `PARTNER_NAME` - C:X partner name (default: PESITSRV)
- `FILE_NAME` - File route name in C:X (default: TESTFILE)
- `SOURCE_FILE` - Source file path (default: $TOM_DIR/out/test_to_server.txt)

### `cx-test-receive.sh`
Test receiving a file from PeSIT Wizard server to C:X.

**Usage:**
```bash
./scripts/cx-test-receive.sh
```

**With custom parameters:**
```bash
PARTNER_NAME=PESITSRV FILE_NAME=TESTFILE DEST_FILE=/path/to/save.txt ./scripts/cx-test-receive.sh
```

**Environment Variables:**
- `PARTNER_NAME` - C:X partner name (default: PESITSRV)
- `FILE_NAME` - File route name in C:X (default: TESTFILE)
- `DEST_FILE` - Destination file path (default: $TOM_DIR/in/received_from_server.txt)

### `cx-setup-guide.md`
Comprehensive guide for configuring C:X to work with PeSIT Wizard server.

**Topics covered:**
- Partner configuration
- File/Route configuration
- Troubleshooting
- Quick reference table
- Starting from scratch

## Typical Testing Workflow

1. **Start PeSIT Wizard server:**
   ```bash
   ./scripts/start-server.sh
   ```

2. **Create PESITSRV server instance via REST API:**
   ```bash
   curl -X POST http://localhost:8080/api/servers \
     -H "Content-Type: application/json" \
     -d '{
       "serverId": "PESITSRV",
       "port": 5000,
       "bindAddress": "0.0.0.0",
       "protocolVersion": 2,
       "maxConnections": 10,
       "connectionTimeout": 30000,
       "readTimeout": 300000,
       "receiveDirectory": "/tmp/pesit-server-data/receive",
       "sendDirectory": "/tmp/pesit-server-data/send",
       "maxEntitySize": 4096,
       "syncPointsEnabled": true,
       "syncIntervalKb": 32,
       "resyncEnabled": false,
       "strictPartnerCheck": false,
       "strictFileCheck": false,
       "autoStart": true
     }'
   ```

3. **Start the server instance:**
   ```bash
   curl -X POST http://localhost:8080/api/servers/PESITSRV/start
   ```

4. **Configure C:X partner (see cx-setup-guide.md)**

5. **Test file transfer:**
   ```bash
   ./scripts/cx-test-send.sh
   ```

6. **Check received file:**
   ```bash
   ls -lh /tmp/pesit-server-data/receive/
   cat /tmp/pesit-server-data/receive/*
   ```

7. **Check server logs:**
   ```bash
   tail -f /home/cpo/pesitwizard/server.log
   ```

## Integration Tests

The `integration-tests/` directory contains automated test scripts for validating protocol compliance and file transfer scenarios.

## Uninstall

### `uninstall.sh`
Remove PeSIT Wizard installation.
```bash
./scripts/uninstall.sh
```

## Requirements

- **Java**: 21+
- **Maven**: 3.9+
- **C:X**: IBM Sterling Connect:Express (for C:X integration tests)
- **curl**: For REST API calls
- **jq**: For JSON parsing (optional)

## Environment Variables

- `TOM_DIR` - C:X installation directory (typically `/home/cpo/cexp`)
- `JAVA_HOME` - Java JDK installation directory

## Troubleshooting

### Scripts fail with "Permission denied"
Make sure scripts are executable:
```bash
chmod +x scripts/*.sh
```

### C:X commands not found
Ensure `TOM_DIR` environment variable is set:
```bash
export TOM_DIR=/home/cpo/cexp
```

### Server won't start
Check if port 8080 or 5000 is already in use:
```bash
netstat -an | grep -E '8080|5000'
```

### Authentication failures
Check server partner configuration:
```bash
curl http://localhost:8080/api/partners | jq
```

## Documentation

For detailed protocol information, see:
- `/home/cpo/pesitwizard/pesitwizard-pesit/pesit.html` - PeSIT E specification
- `/home/cpo/pesitwizard/CLAUDE.md` - Project documentation
- `cx-setup-guide.md` - C:X configuration guide
