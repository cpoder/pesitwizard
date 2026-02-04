# CX Integration Tests - Docker Environment

This directory contains Docker-based integration tests for validating PeSIT Wizard's compatibility with IBM Sterling Connect:Express.

## Prerequisites

1. **Docker** and **Docker Compose** installed
2. **Connect:Express installation files** at `/home/cpo/pesit/CX/`:
   - `install.sh`
   - `cx1stinst.tar`
   - `cxbase.tar`
   - `cxbins.tar`
   - `cxopenssl.tar`

## Quick Start

Run all integration tests:

```bash
./build-and-test.sh
```

This script will:
1. Build the PeSIT Wizard server JAR
2. Copy CX installation files
3. Build Docker images (PW server + CX)
4. Run the integration tests
5. Clean up containers and temporary files

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Network                            │
│                                                             │
│  ┌─────────────────┐         ┌─────────────────┐           │
│  │   pw-server     │◄────────│   cx-server     │           │
│  │                 │         │                 │           │
│  │  PeSIT: 5001    │         │  PeSIT: 5000    │           │
│  │  API:   8080    │         │                 │           │
│  └─────────────────┘         └─────────────────┘           │
│          ▲                           ▲                      │
│          │                           │                      │
│          └───────────┬───────────────┘                      │
│                      │                                      │
│              ┌───────┴───────┐                              │
│              │  test-runner  │                              │
│              │               │                              │
│              │ Runs tests    │                              │
│              └───────────────┘                              │
└─────────────────────────────────────────────────────────────┘
```

## Services

### pw-server (PeSIT Wizard Server)
- **Port 5001**: PeSIT protocol
- **Port 8080**: REST API
- Server ID: `PWSERVER`
- Configured partners: `PWSRV01`, `CETOM1`
- Virtual files: `PWSEND`, `PWRECV`

### cx-server (Connect:Express)
- **Port 5000**: PeSIT protocol
- DPCSID: `CETOM1`
- Configured partner: `PWSERVER` (pointing to pw-server:5001)
- Files: `PWSEND` (transmit), `PWRECV` (receive)

### test-runner
- Runs automated tests
- Validates configuration
- Tests file transfers

## Manual Testing

Start containers without running tests:

```bash
docker compose up -d pw-server cx-server
```

Access CX shell:

```bash
docker exec -it cx-server bash
. $TOM_DIR/profile
# Submit transfer
$TOM_DIR/itom/p1b8preq /SFN=PWSEND/DIR=T/SPN=PWSERVER/SID=PWSRV01 /DSN=/tmp/cx-send/test.dat
```

Check PW server API:

```bash
curl -H "X-API-Key: integration-test-key" http://localhost:8080/api/v1/servers
```

## Test Cases

1. **Small file transfer** (CX → PW): Basic connectivity test
2. **Large file transfer** (50MB): Performance and reliability
3. **API health check**: Server is responsive
4. **Partner configuration**: Partners are correctly set up
5. **Virtual file configuration**: Files are correctly configured

## Troubleshooting

### View logs

```bash
# PW Server logs
docker logs pw-server

# CX logs
docker exec cx-server cat $TOM_DIR/config/LOG
```

### Check CX configuration

```bash
docker exec cx-server bash -c '. $TOM_DIR/profile && $TOM_DIR/config/tom_prm LIST'
```

### Rebuild from scratch

```bash
docker compose down -v
docker compose build --no-cache
docker compose up
```

## Cleanup

Remove all containers and volumes:

```bash
docker compose down -v
```

Remove temporary files:

```bash
rm -rf cx/CX pw-server/pesitwizard-server.jar
```
