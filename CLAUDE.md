# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PeSIT Wizard is an open-source Java implementation of the PeSIT protocol (Protocole d'Échange pour un Système Interbancaire de Télécompensation), used by French banks for secure file transfers. The project consists of a multi-module Maven build with both server and client components.

## Build Commands

### Maven Build (Java Components)

```bash
# Build all modules from root
mvn clean install

# Skip tests for faster builds
mvn clean install -DskipTests

# Build specific module
cd pesitwizard-client
mvn package

# Run tests
mvn test

# Run tests with coverage
mvn verify

# Run single test class
mvn test -Dtest=SendLargeFileWithRestartTest

# Run single test method
mvn test -Dtest=SendLargeFileWithRestartTest#testSendLargeFile
```

### Running Applications

**Server** (PeSIT protocol on port 5000, REST API on port 8080):
```bash
cd pesitwizard-server
mvn spring-boot:run
# OR after building:
java -jar target/pesitwizard-server-1.0.0-SNAPSHOT.jar
```

**Client** (REST API on port 9081):
```bash
cd pesitwizard-client
mvn spring-boot:run
# OR after building:
java -jar target/pesitwizard-client-1.0.0-SNAPSHOT.jar
```

### UI Development (Vue.js)

**Client UI**:
```bash
cd pesitwizard-client-ui
npm install
npm run dev           # Development server
npm run build         # Production build
npm run test          # Run Vitest tests
npm run test:coverage # With coverage
```

**Documentation**:
```bash
cd pesitwizard-docs
npm install
npm run dev     # Development server
npm run build   # Build static site
```

## Architecture Overview

### Module Structure

The project is organized as a Maven multi-module build:

- **pesitwizard-pesit**: Core protocol implementation (FPDU encoding/decoding, session management)
- **pesitwizard-client**: Client application with REST API for sending/receiving files
- **pesitwizard-server**: Server application that listens for PeSIT connections
- **pesitwizard-security**: Secrets management (AES encryption, HashiCorp Vault integration)
- **pesitwizard-connector-api**: SPI for storage backends
- **pesitwizard-connector-local**: Local filesystem connector
- **pesitwizard-connector-sftp**: SFTP connector
- **pesitwizard-connector-s3**: AWS S3/MinIO connector

### PeSIT Protocol Implementation

The protocol implementation follows the PeSIT E specification (September 1989):

#### FPDU Structure
File Protocol Data Units (FPDUs) are the atomic message units:
```
[Length(2)][Phase(1)][Type(1)][IdDst(1)][IdSrc(1)][Parameters or Data]
```

- **Phase**: 0x40 (session), 0xC0 (file), 0x00 (data transfer)
- **Type**: One of 30+ FPDU types (CONNECT, CREATE, SELECT, OPEN, WRITE, DTF, etc.)
- **Parameters**: PI (Parameter Information) codes with typed values

**Key classes**:
- `Fpdu.java`: Core FPDU serialization/deserialization
- `FpduType.java`: Enum defining all FPDU types and their parameter requirements
- `ParameterIdentifier.java`: 96 PI definitions with types and lengths

#### Message Flow Pattern
PeSIT uses request-response with acknowledgments:
1. Client sends FPDU expecting ACK
2. Server responds with corresponding ACK type
3. Non-zero diagnostic code (PI_02_DIAG) indicates error
4. ABORT or RCONNECT signals protocol violation

**Key class**: `PesitSession.java` - manages FPDU exchange, ACK validation, error handling

### Client Architecture Layers

```
REST API (TransferController)
    ↓
TransferService (dispatcher, history, cancellation)
    ↓
PesitSendService / PesitReceiveService (async orchestration)
    ↓
PesitTransferExecutor (protocol implementation)
    ├─ SendOperation (inner class)
    └─ ReceiveOperation (inner class)
    ↓
PesitSession (FPDU exchange)
    ↓
TransportChannel (TCP/TLS)
```

#### Transfer Orchestration
- **TransferService**: Delegates to specialized services, persists history, manages cancellation
- **PesitSendService/PesitReceiveService**: Async execution with `@Async("transferExecutor")`
- **PesitTransferExecutor**: Inner class pattern separates send/receive logic while sharing context
- **TransferContext**: Wraps ClientState with progress tracking, sync points, event emission

#### State Machine
The client uses a strict hierarchical state machine (`ClientState.java`) to prevent protocol violations:

```
CN01_REPOS (idle)
  → CN02A_CONNECT_PENDING → CN03_CONNECTED
      → SF01A_CREATE_PENDING → SF03_FILE_SELECTED
      → SF02A_SELECT_PENDING → SF03_FILE_SELECTED
          → OF01A_OPEN_PENDING → OF02_TRANSFER_READY
              → TDE01A_WRITE_PENDING (send) → TDE02A_SENDING_DATA
              → TDL01A_READ_PENDING (receive) → TDL02A_RECEIVING_DATA
```

State transitions are validated with `canTransitionTo()` to ensure protocol correctness.

### File Transfer Mechanics

#### Chunking Strategy
- **FpduWriter**: Automatic chunking based on negotiated PI_25 (max entity size)
- **Multi-Article DTF**: For variable-format files, batches multiple records per FPDU
- **Record length**: PI_32 limits single article size
- **Available data per DTF**: `maxEntitySize - 6` (FPDU header overhead)

#### Concatenated FPDU Handling
**FpduReader** implements PeSIT section 4.5 concatenation:
- Transport packets may contain multiple FPDUs: `[len1][fpdu1][len2][fpdu2]...`
- Aggregates DTF* types transparently, merging their data payloads
- Optimizes transport efficiency while presenting unified FPDU stream

#### Restart Mechanism
Large file transfers support resume via sync points:
1. Client and server negotiate sync interval (PI_07) during CONNECT (e.g., every 256KB)
2. After sending N bytes >= sync interval, send SYN FPDU with sync number
3. Server ACKs SYN, both record byte position and sync number
4. On interruption, next attempt specifies PI_18_POINT_RELANCE (restart point)
5. Server skips to that point, transfer resumes

**Critical detail**: When IDT (interrupt) received with diagnostic code 4 → throws `RestartRequiredException` with last sync point and byte position.

### Storage Connector Architecture

Pluggable backend system using Service Provider Interface (SPI) pattern:

- **StorageConnector interface**: Unified API for read/write/delete/metadata operations
- **StorageConnectorFactory**: Creates connectors with decrypted credentials
- **ConnectorRegistry**: SPI-based discovery via `META-INF/services/`
- **Configuration**: Stored encrypted in database, decrypted at runtime by SecretsService

**Offset support**: `read(path, offset)` enables resume for partial downloads.

### Security & Secrets Management

Dual-provider architecture for credential encryption:

- **SecretsService**: High-level facade for encryption/decryption
- **CompositeSecretsProvider**: Transparent migration from AES to Vault
  - New encryption: Always uses primary provider (Vault if available, else AES)
  - Reading: Routes based on prefix (`AES:...` vs `vault:...`)
  - Auto-migration: Re-saving AES-encrypted value migrates to Vault

**Modes**:
- `NONE`: Plaintext (development only)
- `AES`: AES-256-GCM (local encryption)
- `VAULT`: HashiCorp Vault integration

**Usage**: Connector passwords, TLS keystores, and all sensitive config stored encrypted.

### Transport Layer

- **TransportChannel**: Abstraction for TCP/TLS/X.25
- **TlsTransportChannel**: Supports mutual TLS (mTLS) with custom truststore/keystore
- **Dynamic timeout**: `baseTimeout + (fileSize / 50MB) * 60s`, capped at 30 minutes

### Event System

Real-time updates via event bus pattern:
```
TransferEventBus → Spring ApplicationEventPublisher
               → WebSocket (STOMP over SockJS)
               → Database (audit trail)
```

Events include state changes, progress updates, errors. Progress updates throttled to 100ms intervals to avoid flooding.

## Critical Implementation Patterns

### 1. Inner Class for Operation Encapsulation
`PesitTransferExecutor` uses inner classes `SendOperation` and `ReceiveOperation` to isolate send/receive logic while sharing executor context. This avoids code duplication for common setup (connection, state machine initialization).

### 2. Builder Pattern for FPDU Construction
Message builders (e.g., `ConnectMessageBuilder`, `CreateMessageBuilder`) provide type-safe parameter construction. Missing mandatory parameters are caught at build time.

### 3. Composite Provider for Encryption
`CompositeSecretsProvider` enables zero-downtime migration from local AES to Vault by detecting encryption prefix and routing to appropriate provider.

### 4. SPI for Storage Backends
`META-INF/services/com.pesitwizard.connector.ConnectorFactory` lists implementation classes, enabling drop-in connector JARs without core changes.

### 5. Strict State Machine Validation
`ClientState.canTransitionTo()` prevents illegal state transitions, simplifying concurrency and ensuring protocol correctness.

## Key Protocol Details

### FPDU Type Hierarchy
- **Session-level (0x40)**: CONNECT, ACONNECT, RELEASE, RELCONF, ABORT, RCONNECT
- **File-level (0xC0)**: CREATE, SELECT, OPEN, WRITE, READ, CLOSE, DESELECT, TRANS_END, plus ACKs
- **Data Transfer (0x00)**: DTF (Data Transfer Frame), DTFDA, DTFMA, DTFFA

### Critical Parameters (PI)
- **PI_02_DIAG**: Diagnostic codes (3 bytes) - indicates errors in ACKs
- **PI_07_SYNC_POINTS**: Synchronization interval in KB (negotiated during CONNECT)
- **PI_18_POINT_RELANCE**: Restart point for interrupted transfers
- **PI_25_TAILLE_MAX_ENTITE**: Maximum entity size (negotiated during CREATE) - limits DTF chunk size
- **PI_32_LONG_ARTICLE**: Record length for variable-format files

### Send Operation Flow
```
1. CONNECT (negotiate sync interval PI_07)
2. CREATE (propose record length PI_32, max entity size PI_25)
3. Negotiate PI_25 via binary search if needed
4. OPEN (prepare file for transmission)
5. WRITE (enter data send mode)
6. Send DTF FPDUs with FpduWriter
7. Periodic SYN points (synchronization)
8. DTF_END (signal data complete)
9. TRANS_END (finish transfer)
10. CLOSE, DESELECT, RELEASE (cleanup)
```

### Receive Operation Flow
```
1. CONNECT (with read access)
2. SELECT (identify file to receive, negotiate PI_25)
3. Parse file size from ACK_SELECT response
4. OPEN + READ (with optional restart point PI_18)
5. FpduReader aggregates concatenated DTFs
6. Handle SYN points (periodically reset byte counter)
7. Handle IDT (interrupt) for restart capability
8. DTF_END, TRANS_END, cleanup
```

## Important File Locations

### Core Protocol
- `/pesitwizard-pesit/src/main/java/com/pesitwizard/fpdu/` - FPDU core classes
- `/pesitwizard-pesit/src/main/java/com/pesitwizard/session/PesitSession.java` - Session management
- `/pesitwizard-pesit/src/main/java/com/pesitwizard/transport/` - Transport layer

### Client Implementation
- `/pesitwizard-client/src/main/java/com/pesitwizard/client/pesit/` - Client services and state machine
- `/pesitwizard-client/src/main/java/com/pesitwizard/client/service/TransferService.java` - Transfer orchestration
- `/pesitwizard-client/src/main/java/com/pesitwizard/client/controller/` - REST API controllers

### Storage Connectors
- `/pesitwizard-connector-api/src/main/java/com/pesitwizard/connector/StorageConnector.java` - Connector interface
- `/pesitwizard-connector-local/`, `/pesitwizard-connector-sftp/`, `/pesitwizard-connector-s3/` - Implementations

### Security
- `/pesitwizard-security/src/main/java/com/pesitwizard/security/` - Secrets management

### Server
- `/pesitwizard-server/src/main/java/com/pesitwizard/server/` - Server implementation

## Configuration Files

- `application.yml` - Spring Boot configuration (server ports, database, security)
- `pom.xml` - Maven dependencies and build configuration
- `package.json` - UI dependencies (Vue.js client and docs)

## Testing Notes

- Integration tests demonstrate full protocol flows (e.g., `SendLargeFileWithRestartTest.java`)
- Tests verify sync point negotiation, chunking, restart mechanism
- Use `-DskipTests` for faster builds during development
- Coverage reports generated with `mvn verify` (JaCoCo)

## Requirements

- **Java**: 21+
- **Maven**: 3.9+
- **Node.js**: Latest LTS (for UI components)
- **Spring Boot**: 3.x (embedded in dependencies)
