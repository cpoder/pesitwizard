# Connect:Express Integration Tests

This directory contains tools and scripts for integration testing between PeSIT Wizard and IBM Sterling Connect:Express (CX).

## Prerequisites

1. **Connect:Express** installed at `/home/cpo/cx` (or set `TOM_DIR`)
2. **PeSIT Wizard** built and ready to run
3. **GCC** for compiling C tools

## Setup

### 1. Build the C tools

```bash
cd integration-tests/cx-integration
make TOM_DIR=/home/cpo/cx
```

This builds:
- `cx-setup-partner` - Creates partner definitions in CX
- `cx-setup-file` - Creates virtual file definitions in CX

### 2. Start Connect:Express

```bash
source /home/cpo/cx/profile
$start_tom
```

Verify CX is running:
```bash
$sterm
```

### 3. Run the setup script

```bash
./setup-cx-for-pesitwizard.sh
```

This will:
- Create partner `PWSERVER` pointing to PeSIT Wizard server
- Create virtual files `PWRECV`, `PWSEND`, `PWTEST`
- Create test data directories under `/tmp/pesitwizard-tests`

## Test Scenarios

### Test 1: PW Client -> CX Server

Tests sending files from PeSIT Wizard client to Connect:Express server.

```bash
# Start PeSIT Wizard client
cd pesitwizard-client
mvn spring-boot:run

# In another terminal, run tests
./test-pw-client-to-cx.sh
```

### Test 2: CX Client -> PW Server

Tests sending files from Connect:Express to PeSIT Wizard server.

```bash
# Start PeSIT Wizard server
cd pesitwizard-server
mvn spring-boot:run

# In another terminal, run tests
./test-cx-client-to-pw.sh
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `TOM_DIR` | `/home/cpo/cx` | CX installation directory |
| `PW_SERVER_HOST` | `localhost` | PW server hostname |
| `PW_SERVER_PORT` | `05001` | PW server PeSIT port |
| `PW_SERVER_DPCSID` | `PWSRV01` | PW server DPCSID |
| `CX_SERVER_HOST` | `localhost` | CX server hostname |
| `CX_SERVER_PORT` | `05000` | CX server PeSIT port |
| `CX_SERVER_DPCSID` | `CETOM1` | CX server DPCSID |
| `TEST_DATA_DIR` | `/tmp/pesitwizard-tests` | Test data directory |

### Virtual File Configuration

Virtual files in CX are configured with these parameters:

| Parameter | Value | Notes |
|-----------|-------|-------|
| Format | `BV` (Binary Variable) | Supports variable-length records |
| Record Length | `04096` | Maximum article size |
| Direction | `B` (Both), `R` (Receive), or `T` (Transmit) | |
| State | `H` (Hold/Enabled) | File is active |

#### Format Codes
- `TF` - Text Fixed (fixed-length text records)
- `TV` - Text Variable (variable-length text records)
- `BF` - Binary Fixed (fixed-length binary records)
- `BV` - Binary Variable (variable-length binary records)

### Partner Configuration

Partners are configured with:

| Parameter | Value | Notes |
|-----------|-------|-------|
| Nature | `T` (TCP/IP) | Network protocol |
| Protocol | `3` (PeSIT) | File transfer protocol |
| Session Table | `1` | Default session parameters |
| Max Sessions | `10` | Total simultaneous sessions |
| Max Incoming | `05` | Incoming session limit |
| Max Outgoing | `05` | Outgoing session limit |
| Connection Type | `M` (Mixed) | Both initiator and responder |

## Entity/Article Size Considerations

PeSIT negotiates entity sizes during connection setup:

1. **PI_25 (Max Entity Size)**: Maximum FPDU data payload size
   - CX default: typically 4096 bytes
   - PW default: 32768 bytes
   - Negotiated to the smaller value

2. **PI_32 (Record Length)**: Maximum single article size
   - Should match the virtual file's record length
   - For variable-length files, this is the maximum

3. **Multi-Article DTF**: When sending multiple small records
   - DTF format: `[len(2)][data][len(2)][data]...`
   - Total must fit within negotiated entity size

### Session Tables

CX session tables control protocol parameters. Check/modify via:
```bash
$sterm_v  # View configuration
```

Key parameters:
- Synchronization interval (for restart support)
- Compression settings
- Timeout values

### Presentation Tables

Used for character encoding conversion. For binary transfers, leave blank (` `).

## Troubleshooting

### CX Return Codes

Common return codes from L0B8Z20 API:
- `0000` - Success
- `0017` - Duplicate entry (partner/file already exists)
- `0004` - Not found
- `0008` - Invalid parameter

### p1b8preq Errors

Check request status:
```bash
$p1b8pret /RQN=<request_number>
```

View CX logs:
```bash
tail -f $TOM_DIR/log/tom.log
```

### Connection Issues

1. Verify CX is listening:
   ```bash
   netstat -tlnp | grep 5000
   ```

2. Check partner configuration:
   ```bash
   $sterm
   # Then: L P (List Partners)
   ```

3. Verify file definition:
   ```bash
   $sterm
   # Then: L F (List Files)
   ```

## Manual Testing with p1b8preq

```bash
# Send a file (Transmit)
$p1b8preq /SFN=PWSEND/SPN=PWSERVER/DIR=T/DSN=/path/to/file.dat

# Receive a file
$p1b8preq /SFN=PWRECV/SPN=PWSERVER/DIR=R/DSN=/path/to/output.dat

# With options
$p1b8preq /SFN=PWSEND/SPN=PWSERVER/DIR=T/DSN=/path/to/file.dat/TYP=B/ORG=S
```

## Files in this Directory

```
cx-integration/
├── Makefile              # Build configuration
├── README.md             # This file
├── cx-setup-partner.c    # Tool to create CX partners
├── cx-setup-file.c       # Tool to create CX virtual files
├── setup-cx-for-pesitwizard.sh  # Initial setup script
├── test-pw-client-to-cx.sh      # PW client -> CX server tests
└── test-cx-client-to-pw.sh      # CX client -> PW server tests
```
