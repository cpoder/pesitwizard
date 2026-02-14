# Server Configuration

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | JDBC PostgreSQL URL | - |
| `SPRING_DATASOURCE_USERNAME` | DB user | pesitwizard |
| `SPRING_DATASOURCE_PASSWORD` | DB password | pesitwizard |
| `PESIT_CLUSTER_ENABLED` | Enable clustering | false |
| `POD_NAME` | Pod name (K8s) | - |
| `POD_NAMESPACE` | Namespace (K8s) | default |

## application.yml File

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pesitwizard
    username: pesitwizard
    password: pesitwizard

pesitwizard:
  # Clustering configuration
  cluster:
    enabled: true
    name: pesitwizard-cluster

  # API security
  admin:
    username: admin
    password: admin
```

## PeSIT Server Configuration

A PeSIT Wizard server can host multiple "logical PeSIT servers" on different ports.

### Via API

```bash
curl -X POST http://localhost:8080/api/v1/servers \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": "PESIT_SERVER",
    "port": 6502,
    "tlsPort": 5001,
    "autoStart": true,
    "maxConnections": 100,
    "readTimeout": 60000
  }'
```

### Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `serverId` | Server identifier (PI_04) | - |
| `port` | TCP listening port | 6502 |
| `tlsPort` | TLS listening port | 5001 |
| `autoStart` | Start automatically | true |
| `maxConnections` | Max simultaneous connections | 100 |
| `readTimeout` | Read timeout (ms) | 60000 |

## Partner Configuration

Partners are the clients authorized to connect.

### Via API

```bash
curl -X POST http://localhost:8080/api/v1/config/partners \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "partnerId": "CLIENT_COMPANY",
    "name": "My Client",
    "password": "secret123",
    "enabled": true,
    "allowedOperations": ["READ", "WRITE"]
  }'
```

### Parameters

| Parameter | Description |
|-----------|-------------|
| `partnerId` | Partner identifier (PI_03) |
| `name` | Display name |
| `password` | Password (PI_05) |
| `enabled` | Partner active |
| `allowedOperations` | Allowed operations (READ, WRITE) |

## Virtual File Configuration

Virtual files define the storage paths.

### Via API

```bash
curl -X POST http://localhost:8080/api/v1/config/files \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "fileId": "PAYMENTS",
    "name": "Payment files",
    "sendDirectory": "/data/send/payments",
    "receiveDirectory": "/data/received/payments",
    "filenamePattern": "*.xml"
  }'
```

### Parameters

| Parameter | Description |
|-----------|-------------|
| `fileId` | Virtual file identifier (PI_12) |
| `name` | Display name |
| `sendDirectory` | Directory for files to send |
| `receiveDirectory` | Directory for received files |
| `filenamePattern` | Filename pattern |

## Storage Directories

```
/data
├── send/           # Files to send
│   ├── payments/
│   └── statements/
├── received/       # Received files
│   ├── payments/
│   └── statements/
└── temp/           # Temporary files
```

### Volume Configuration (Kubernetes)

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pesitwizard-data
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 50Gi
```

## Logs and Monitoring

### Log Levels

```yaml
logging:
  level:
    com.pesitwizard: INFO
    com.pesitwizard.server.handler: DEBUG  # Session details
    com.pesitwizard.protocol: DEBUG        # PeSIT messages
```

### Prometheus Metrics

The server exposes metrics on `/actuator/prometheus`:

- `pesitwizard.connections.active`: Active connections
- `pesitwizard.transfers.total`: Total number of transfers
- `pesitwizard.transfers.bytes.total`: Total volume transferred
- `pesitwizard.errors.total`: Number of errors

### Health Checks

```bash
# Readiness (ready to receive traffic)
curl http://localhost:8080/actuator/health/readiness

# Liveness (application alive)
curl http://localhost:8080/actuator/health/liveness

# Full health
curl http://localhost:8080/actuator/health
```
