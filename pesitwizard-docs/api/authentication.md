# Authentication

## Supported Methods

| API | Method | Usage |
|-----|--------|-------|
| Client API | Bearer Token / API Key | Production |
| Admin API | Basic Auth | Administration |
| Server API | Basic Auth | Configuration |

## Basic Authentication

For Admin and Server APIs:

```bash
curl -u admin:password http://localhost:8080/api/v1/clusters
```

Or with a header:
```bash
curl -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ=" http://localhost:8080/api/v1/clusters
```

## API Key (Client API)

### Generate an API Key

```bash
curl -X POST http://localhost:8080/api/v1/apikeys \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ERP Integration",
    "expiresAt": "2026-01-01T00:00:00Z"
  }'
```

Response:
```json
{
  "id": "ak_123456789",
  "key": "pk_live_abc123def456...",
  "name": "ERP Integration",
  "createdAt": "2025-01-10T10:00:00Z",
  "expiresAt": "2026-01-01T00:00:00Z"
}
```

::: warning
The full key is only displayed once. Store it in a safe place.
:::

### Use the API Key

```bash
curl -H "X-API-Key: pk_live_abc123def456..." \
  http://localhost:8080/api/transfers
```

Or via query parameter:
```bash
curl "http://localhost:8080/api/transfers?api_key=pk_live_abc123def456..."
```

### Revoke a Key

```bash
curl -X DELETE http://localhost:8080/api/v1/apikeys/ak_123456789 \
  -u admin:admin
```

## JWT (Optional)

For OAuth2/OIDC integrations:

### Configuration

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.mycompany.com/realms/pesitwizard
```

### Usage

```bash
# Obtain a token (Keycloak example)
TOKEN=$(curl -X POST https://auth.mycompany.com/realms/pesitwizard/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=pesitwizard-client" \
  -d "client_secret=secret" | jq -r '.access_token')

# Use the token
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/transfers
```

## Best Practices

### Credential Storage

**Do not do this**:
```bash
# Hardcoded credentials in code
curl -u admin:password123 ...
```

**Do this instead**:
```bash
# Via environment variables
curl -u "$PESIT_USER:$PESIT_PASSWORD" ...

# Via .netrc file
curl --netrc http://localhost:8080/api/transfers
```

### Key Rotation

- Generate new keys regularly (every 90 days)
- Keep the old key active during the transition
- Delete the old key once the migration is complete

### Audit

All authentications are logged:

```
2025-01-10 10:30:00 INFO  [AUTH] Method=API_KEY KeyId=ak_123456789 IP=192.168.1.100 Status=SUCCESS
2025-01-10 10:30:01 WARN  [AUTH] Method=BASIC User=admin IP=192.168.1.200 Status=FAILED Reason=INVALID_PASSWORD
```
