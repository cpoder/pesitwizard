# Client API

Base URL: `http://localhost:8080/api/v1`

## Servers

### List Servers

```http
GET /api/v1/servers
```

**Response**:
```json
[
  {
    "id": 1,
    "name": "BNP Paribas",
    "host": "pesitwizard.bnpparibas.com",
    "port": 6502,
    "tlsPort": 5001,
    "serverId": "BNPP_SERVER",
    "clientId": "MY_COMPANY",
    "tlsEnabled": true,
    "status": "CONNECTED"
  }
]
```

### Create a Server

```http
POST /api/v1/servers
Content-Type: application/json

{
  "name": "BNP Paribas",
  "host": "pesitwizard.bnpparibas.com",
  "port": 6502,
  "tlsPort": 5001,
  "serverId": "BNPP_SERVER",
  "clientId": "MY_COMPANY",
  "password": "secret123",
  "tlsEnabled": true
}
```

### Update a Server

```http
PUT /api/v1/servers/{id}
Content-Type: application/json

{
  "name": "BNP Paribas Production",
  "password": "newpassword"
}
```

### Delete a Server

```http
DELETE /api/v1/servers/{id}
```

### Test Connection

```http
POST /api/v1/servers/{id}/test
```

**Response**:
```json
{
  "success": true,
  "latency": 45,
  "serverVersion": 2,
  "message": "Connection successful"
}
```

## Transfers

### Send a File

```http
POST /api/v1/transfers/send
Content-Type: application/json

{
  "serverId": 1,
  "remoteFilename": "TRANSFER_20250110.XML",
  "partnerId": "MY_COMPANY",
  "virtualFile": "TRANSFERS",
  "fileContent": "<base64-encoded content>"
}
```

**Response**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "direction": "SEND",
  "filename": "TRANSFER_20250110.XML",
  "size": 15234,
  "startTime": "2025-01-10T10:30:00Z",
  "endTime": "2025-01-10T10:30:05Z",
  "duration": 5000
}
```

### Receive a File

```http
POST /api/v1/transfers/receive
Content-Type: application/json

{
  "serverId": 1,
  "remoteFilename": "STATEMENT_20250110.XML",
  "partnerId": "MY_COMPANY",
  "virtualFile": "STATEMENTS"
}
```

**Response**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "status": "COMPLETED",
  "direction": "RECEIVE",
  "filename": "STATEMENT_20250110.XML",
  "filename": "/data/received/STATEMENT_20250110.XML",
  "size": 8542
}
```

### Get Transfer Details

```http
GET /api/v1/transfers/{id}
```

Returns a `TransferHistory` JSON object with full transfer details:

**Response**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "direction": "SEND",
  "filename": "TRANSFER_20250110.XML",
  "serverName": "BNP Paribas",
  "size": 15234,
  "startTime": "2025-01-10T10:30:00Z",
  "endTime": "2025-01-10T10:30:05Z",
  "duration": 5000
}
```

### Transfer History

```http
GET /api/v1/transfers/history?page=0&size=20
```

**Query parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| `page` | int | Page number (0-indexed) |
| `size` | int | Page size (default: 20) |
| `sort` | string | Sort order (e.g., `startTime,desc`) |
| `status` | string | Filter by status |
| `direction` | string | SEND or RECEIVE |
| `serverId` | int | Filter by server |
| `from` | date | Start date |
| `to` | date | End date |

**Response**:
```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "status": "COMPLETED",
      "direction": "SEND",
      "filename": "TRANSFER_20250110.XML",
      "serverName": "BNP Paribas",
      "size": 15234,
      "startTime": "2025-01-10T10:30:00Z"
    }
  ],
  "totalElements": 150,
  "totalPages": 8,
  "number": 0,
  "size": 20
}
```

### Replay a Transfer

```http
POST /api/v1/transfers/{id}/replay
```

### Statistics

```http
GET /api/v1/transfers/stats
```

## Transfer Statuses

| Status | Description |
|--------|-------------|
| `PENDING` | Waiting |
| `IN_PROGRESS` | In progress |
| `COMPLETED` | Completed successfully |
| `FAILED` | Failed |
| `CANCELLED` | Cancelled |

## Error Codes

| Code | Description |
|------|-------------|
| `SERVER_NOT_FOUND` | Server not found |
| `CONNECTION_FAILED` | Connection failed |
| `AUTH_FAILED` | Authentication failed |
| `PARTNER_UNKNOWN` | Partner not recognized |
| `FILE_NOT_FOUND` | File not found |
| `TRANSFER_FAILED` | Transfer failed |
| `TIMEOUT` | Timeout exceeded |
