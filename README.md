# PeSIT Wizard - Open Source PeSIT File Transfer

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/)
[![Website](https://img.shields.io/badge/Website-pesitwizard.com-blue)](https://pesitwizard.com)

**[pesitwizard.com](https://pesitwizard.com)** - Official website with documentation and pricing

Open-source file transfer solution based on the **PeSIT** protocol (Protocole d'Echange pour un Systeme Interbancaire de Telecompensation).

## What is PeSIT?

PeSIT is the standard protocol used by French banks for secure file exchanges:
- SEPA transfers
- Account statements
- Direct debits
- Interbank exchanges

## Components

### Maven Modules

| Module | Description |
|--------|-------------|
| `pesitwizard-common` | Shared utilities (crypto, security helpers) |
| `pesitwizard-pesit` | PeSIT protocol implementation library |
| `pesitwizard-security` | Secrets management (AES, HashiCorp Vault) |
| `pesitwizard-connector-api` | Storage connector API (SPI) |
| `pesitwizard-connector-local` | Local filesystem connector |
| `pesitwizard-connector-sftp` | SFTP connector |
| `pesitwizard-connector-s3` | AWS S3 / MinIO connector |
| `pesitwizard-backup` | Backup and recovery |
| `pesitwizard-server` | PeSIT server with REST API |
| `pesitwizard-client` | Java client for sending/receiving files |

### Other Components

| Component | Description |
|-----------|-------------|
| `pesitwizard-client-ui` | Client web interface (Vue.js) |
| `pesitwizard-helm-charts` | Kubernetes Helm charts |
| `pesitwizard-docs` | Documentation site (VitePress) |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+

### Installation

```bash
# Clone the repo
git clone https://github.com/pesitwizard/pesitwizard.git
cd pesitwizard

# Build the server
cd pesitwizard-server
mvn package -DskipTests

# Start the server
java -jar target/pesitwizard-server-1.0.0-SNAPSHOT.jar
```

The REST API starts on **port 8080**. The PeSIT protocol listener must be created and started via the API:

```bash
# Create a PeSIT server instance (port 6502, or 5001 for TLS)
curl -X POST http://localhost:8080/api/v1/servers \
  -H "Content-Type: application/json" \
  -d '{"serverId":"MYSERVER","port":6502,"autoStart":true}'

# Create a partner (required for any incoming connection)
curl -X POST http://localhost:8080/api/v1/config/partners \
  -H "Content-Type: application/json" \
  -d '{"id":"PARTNER1","enabled":true,"accessType":"BOTH"}'

# Create a virtual file for receiving (maps PeSIT logical name to local path)
curl -X POST http://localhost:8080/api/v1/config/files \
  -H "Content-Type: application/json" \
  -d '{"id":"INCOMING","direction":"RECEIVE","receiveDirectory":"/data/received","receiveFilenamePattern":"${virtualFile}_${timestamp}"}'

# Create a virtual file for sending (maps PeSIT logical name to a physical file)
curl -X POST http://localhost:8080/api/v1/config/files \
  -H "Content-Type: application/json" \
  -d '{"id":"OUTGOING","direction":"SEND","sendFile":"/data/send/report.csv"}'
```

### REST API

```bash
# Server health
curl http://localhost:8080/actuator/health

# List transfers
curl http://localhost:8080/api/v1/transfers
```

## Configuration

### `application.yml`

```yaml
pesit:
  server:
    port: 6502
    tls-port: 5001
  ssl:
    enabled: false

spring:
  datasource:
    url: jdbc:h2:file:./data/pesitwizard-db
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PESIT_SSL_ENABLED` | Enable TLS for PeSIT protocol | `false` |
| `PESIT_CLUSTER_ID` | Cluster ID for multi-tenant mode | (empty) |
| `SERVER_PORT` | REST API port | `8080` |
| `PESIT_SECURITY_ENABLED` | Enable authentication | `true` |
| `PESIT_API_KEY_ADMIN` | Admin API key | (empty) |
| `PESIT_SECRETS_ENCRYPTION_KEY` | Master encryption key (Base64 AES-256) | (empty) |

## Security

### OAuth2/OIDC Authentication

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example.com/realms/pesitwizard
```

### Secrets Management

Two modes available via `pesitwizard-security`:

**AES mode (default)**:
```yaml
pesit.secrets:
  # Generate with: openssl rand -base64 32
  # Set via env var: PESIT_SECRETS_ENCRYPTION_KEY
  encryption-key: ${PESIT_SECRETS_ENCRYPTION_KEY:}
```

**HashiCorp Vault mode**:
```yaml
pesit.secrets:
  provider: vault
  vault:
    address: https://vault.example.com
    token: ${VAULT_TOKEN}
    path: secret/data/pesitwizard
```

## Storage Connectors

Connectors allow storing transferred files on different backends:

| Connector | Description | Configuration |
|-----------|-------------|---------------|
| `local` | Local filesystem | `path: /data/files` |
| `sftp` | Remote SFTP server | `host`, `port`, `username`, `password/key` |
| `s3` | AWS S3 or MinIO | `endpoint`, `bucket`, `access-key`, `secret-key` |

```yaml
pesitwizard:
  connector:
    type: sftp
    sftp:
      host: sftp.example.com
      port: 22
      username: pesit
      private-key-file: /app/secrets/id_rsa
```

## Observability

### Prometheus Metrics

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

Endpoint: `http://localhost:8080/actuator/prometheus`

### OpenTelemetry Tracing

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
otel:
  exporter:
    otlp:
      endpoint: http://jaeger:4317
```

## Docker

```bash
# Build the image
docker build -t pesitwizard-server ./pesitwizard-server

# Run
docker run -p 6502:6502 -p 5001:5001 -p 8080:8080 pesitwizard-server
```

## Kubernetes

### Quick Install

**PeSIT Wizard Client** (file transfer with UI):
```bash
curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/install-client.sh | bash
```

**PeSIT Wizard Server** (standalone):
```bash
curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/install-server.sh | bash
```

**Uninstall**:
```bash
curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/uninstall.sh | bash
```

### Helm Charts

Helm charts are available in `pesitwizard-helm-charts/`:
- `pesitwizard-client`: Client with API and UI
- `pesitwizard-server`: Standalone server

```bash
# Manual installation with Helm
helm install pesitwizard-client ./pesitwizard-helm-charts/pesitwizard-client -n pesitwizard --create-namespace
helm install pesitwizard-server ./pesitwizard-helm-charts/pesitwizard-server -n pesitwizard --create-namespace
```

## Documentation

- [Quick Start Guide](https://docs.pesitwizard.com/guide/quickstart)
- [Server Configuration](https://docs.pesitwizard.com/guide/server/configuration)
- [REST API](https://docs.pesitwizard.com/api/)

## Project Structure

```
pesitwizard/
├── pesitwizard-common/          # Shared utilities
├── pesitwizard-pesit/           # PeSIT protocol library
├── pesitwizard-security/        # Secrets management (AES, Vault)
├── pesitwizard-connector-api/   # Storage connector API
├── pesitwizard-connector-local/ # Local filesystem connector
├── pesitwizard-connector-sftp/  # SFTP connector
├── pesitwizard-connector-s3/    # S3/MinIO connector
├── pesitwizard-backup/          # Backup and recovery
├── pesitwizard-server/          # PeSIT server with REST API
├── pesitwizard-client/          # Java client (CLI + API)
├── pesitwizard-client-ui/       # Client web interface (Vue.js)
├── pesitwizard-helm-charts/     # Kubernetes Helm charts
├── pesitwizard-docs/            # Documentation (VitePress)
└── scripts/                     # Utility scripts
```

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md).

## Enterprise

For enterprise features (HA clustering, admin console, support), see [PeSIT Wizard Enterprise](https://pesitwizard.com).

## License

[Apache License 2.0](LICENSE)

---

**PeSIT Wizard** - Modern, open-source PeSIT solution.
