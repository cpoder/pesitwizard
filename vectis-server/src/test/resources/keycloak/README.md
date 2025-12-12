# Keycloak Test Environment

This directory contains configuration for testing OAuth2/OIDC authentication with Keycloak.

## Quick Start

```bash
# Start Keycloak
cd src/test/resources/keycloak
docker-compose up -d

# Wait for Keycloak to be ready (about 30 seconds)
docker-compose logs -f keycloak

# Access Keycloak Admin Console
# URL: http://localhost:8180
# Admin: admin/admin
```

## Pre-configured Realm

The `realm-export.json` file creates a `pesit` realm with:

### Users

| Username | Password | Roles | Description |
|----------|----------|-------|-------------|
| admin | admin | ADMIN | Full administrative access |
| operator | operator | OPERATOR | Server management access |
| user | user | USER | Basic user access |
| partner_admin | partner | USER | Partner-specific user |

### Clients

| Client ID | Type | Description |
|-----------|------|-------------|
| pesit-server | Confidential | Main API client (secret: `pesit-secret`) |
| pesit-cli | Public | CLI client for direct access grants |

### Roles

- **ADMIN** - Full access to all endpoints
- **OPERATOR** - Server management access
- **USER** - Basic transfer access

## Testing with Keycloak

### 1. Get Access Token

```bash
# Using password grant (for testing)
curl -X POST http://localhost:8180/realms/pesit/protocol/openid-connect/token \
  -d 'grant_type=password' \
  -d 'client_id=pesit-server' \
  -d 'client_secret=pesit-secret' \
  -d 'username=admin' \
  -d 'password=admin'
```

### 2. Use Token with API

```bash
# Extract token from response
TOKEN=$(curl -s -X POST http://localhost:8180/realms/pesit/protocol/openid-connect/token \
  -d 'grant_type=password' \
  -d 'client_id=pesit-server' \
  -d 'client_secret=pesit-secret' \
  -d 'username=admin' \
  -d 'password=admin' | jq -r '.access_token')

# Call API with token
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/transfers
```

### 3. Configure PeSIT Server

Update `application.yml` to use Keycloak:

```yaml
pesit.security:
  enabled: true
  mode: oauth2
  oauth2:
    enabled: true
    issuer-uri: http://localhost:8180/realms/pesit
    jwk-set-uri: http://localhost:8180/realms/pesit/protocol/openid-connect/certs
    client-id: pesit-server
    username-claim: preferred_username
    roles-claim: roles
```

## Useful Endpoints

| Endpoint | Description |
|----------|-------------|
| `http://localhost:8180` | Keycloak Admin Console |
| `http://localhost:8180/realms/pesit/.well-known/openid-configuration` | OIDC Discovery |
| `http://localhost:8180/realms/pesit/protocol/openid-connect/certs` | JWK Set |
| `http://localhost:8180/realms/pesit/protocol/openid-connect/token` | Token Endpoint |
| `http://localhost:8180/realms/pesit/protocol/openid-connect/userinfo` | UserInfo Endpoint |

## Cleanup

```bash
# Stop and remove containers
docker-compose down

# Remove volumes (if using persistent storage)
docker-compose down -v
```
