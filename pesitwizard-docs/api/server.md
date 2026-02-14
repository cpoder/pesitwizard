# Server API

Base URL: `http://localhost:8080`

Authentication: Basic Auth (`admin:admin` by default)

## PeSIT Servers

### List Servers

```http
GET /api/v1/servers
Authorization: Basic YWRtaW46YWRtaW4=
```

**Response**:
```json
[
  {
    "serverId": "PESIT_SERVER",
    "port": 6502,
    "tlsPort": 5001,
    "status": "RUNNING",
    "autoStart": true,
    "activeConnections": 2
  }
]
```

### Create a Server

```http
POST /api/v1/servers
Content-Type: application/json

{
  "serverId": "PESIT_SERVER",
  "port": 6502,
  "tlsPort": 5001,
  "autoStart": true,
  "maxConnections": 100,
  "readTimeout": 60000
}
```

### Start a Server

```http
POST /api/v1/servers/{serverId}/start
```

**Response**:
```json
{
  "serverId": "PESIT_SERVER",
  "status": "RUNNING",
  "message": "Server started"
}
```

### Stop a Server

```http
POST /api/v1/servers/{serverId}/stop
```

### Server Status

```http
GET /api/v1/servers/{serverId}/status
```

**Response**:
```json
{
  "serverId": "PESIT_SERVER",
  "status": "RUNNING",
  "port": 6502,
  "tlsPort": 5001,
  "activeConnections": 2,
  "totalTransfers": 1523,
  "uptime": 86400
}
```

## Partners

### List Partners

```http
GET /api/v1/config/partners
```

**Response**:
```json
[
  {
    "partnerId": "CLIENT_XYZ",
    "name": "Client XYZ",
    "enabled": true,
    "lastConnection": "2025-01-10T10:30:00Z",
    "transferCount": 150
  }
]
```

### Create a Partner

```http
POST /api/v1/config/partners
Content-Type: application/json

{
  "partnerId": "CLIENT_XYZ",
  "name": "Client XYZ",
  "password": "secret123",
  "enabled": true,
  "allowedOperations": ["READ", "WRITE"]
}
```

### Get a Partner

```http
GET /api/v1/config/partners/{id}
```

### Update a Partner

```http
PUT /api/v1/config/partners/{id}
Content-Type: application/json

{
  "name": "Client XYZ (Production)",
  "enabled": true
}
```

### Delete a Partner

```http
DELETE /api/v1/config/partners/{id}
```

## Virtual Files

### List Virtual Files

```http
GET /api/v1/config/files
```

**Response**:
```json
[
  {
    "fileId": "TRANSFERS",
    "name": "Transfer files",
    "sendDirectory": "/data/send/transfers",
    "receiveDirectory": "/data/received/transfers",
    "filenamePattern": "*.xml"
  }
]
```

### Create a Virtual File

```http
POST /api/v1/config/files
Content-Type: application/json

{
  "fileId": "TRANSFERS",
  "name": "Transfer files",
  "sendDirectory": "/data/send/transfers",
  "receiveDirectory": "/data/received/transfers",
  "filenamePattern": "*.xml"
}
```

### Get a Virtual File

```http
GET /api/v1/config/files/{id}
```

### Update a Virtual File

```http
PUT /api/v1/config/files/{id}
```

### Delete a Virtual File

```http
DELETE /api/v1/config/files/{id}
```

## Cluster

### Cluster Status

```http
GET /api/v1/cluster/status
```

**Response**:
```json
{
  "clusterName": "pesitwizard-cluster",
  "isLeader": true,
  "nodeName": "pesitwizard-server-abc123",
  "members": [
    {
      "name": "pesitwizard-server-abc123",
      "address": "10.42.0.100",
      "isLeader": true
    },
    {
      "name": "pesitwizard-server-def456",
      "address": "10.42.0.101",
      "isLeader": false
    }
  ],
  "memberCount": 2
}
```

## Transfer History

### List Transfers

```http
GET /api/v1/transfers?page=0&size=20
```

**Query parameters**:

| Parameter | Description |
|-----------|-------------|
| `partnerId` | Filter by partner |
| `direction` | SEND or RECEIVE |
| `status` | COMPLETED, FAILED |
| `from` | Start date |
| `to` | End date |

**Response**:
```json
{
  "content": [
    {
      "id": 1,
      "partnerId": "CLIENT_XYZ",
      "direction": "RECEIVE",
      "filename": "TRANSFER.XML",
      "virtualFileId": "TRANSFERS",
      "size": 15234,
      "status": "COMPLETED",
      "startTime": "2025-01-10T10:30:00Z",
      "endTime": "2025-01-10T10:30:05Z"
    }
  ],
  "totalElements": 1523
}
```

## Health & Monitoring

### Health Check

```http
GET /actuator/health
```

### Prometheus Metrics

```http
GET /actuator/prometheus
```

### Info

```http
GET /actuator/info
```
