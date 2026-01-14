# PeSIT Wizard Scripts

Helper scripts for managing PeSIT Wizard server and client.

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
- **curl**: For REST API calls
- **jq**: For JSON parsing (optional)

## Environment Variables

- `JAVA_HOME` - Java JDK installation directory

## Troubleshooting

### Scripts fail with "Permission denied"
Make sure scripts are executable:
```bash
chmod +x scripts/*.sh
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
