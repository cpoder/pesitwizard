# API Reference

## Overview

PeSIT Wizard exposes two REST APIs:

| API | Port | Base URL | Description |
|-----|------|----------|-------------|
| **Client API** | 8080 | `/api/v1` | File send/receive |
| **Server API** | 8080 | `/api` | Server configuration |

## Interactive Documentation

Each API exposes Swagger/OpenAPI documentation:

- Client: http://localhost:8080/swagger-ui.html
- Server: http://localhost:8080/swagger-ui.html

## OpenAPI Specifications

OAS (OpenAPI Specification) files are available:

- [Client API (OAS 3.0)](/api/openapi-client.yaml)
- [Server API (OAS 3.0)](/api/openapi-server.yaml)

## Response Format

All APIs return JSON:

```json
{
  "data": { ... },
  "error": null,
  "timestamp": "2025-01-10T10:30:00Z"
}
```

### HTTP Codes

| Code | Description |
|------|-------------|
| 200 | Success |
| 201 | Created |
| 400 | Invalid request |
| 401 | Not authenticated |
| 403 | Not authorized |
| 404 | Not found |
| 500 | Server error |

### Errors

```json
{
  "error": {
    "code": "PARTNER_NOT_FOUND",
    "message": "Partner 'UNKNOWN' not found",
    "details": null
  },
  "timestamp": "2025-01-10T10:30:00Z"
}
```

## Pagination

List endpoints support pagination:

```bash
GET /api/transfers?page=0&size=20&sort=startTime,desc
```

Response:
```json
{
  "content": [...],
  "totalElements": 150,
  "totalPages": 8,
  "number": 0,
  "size": 20
}
```

## Filtering

Use query parameters to filter:

```bash
GET /api/transfers?status=COMPLETED&direction=SEND&from=2025-01-01
```

## Rate Limiting

Rate limiting is **disabled** by default. It can be enabled by setting `PESIT_RATE_LIMITING_ENABLED=true`.

When enabled, the APIs are limited to:
- 100 requests/minute per IP (public API)
- 1000 requests/minute per token (authenticated API)

Response headers:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1704880260
```
