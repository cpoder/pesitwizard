# PeSIT Wizard Client Installation

## Deployment Options

| Mode | Description | Recommended For |
|------|-------------|-----------------|
| Docker Compose | API + UI + PostgreSQL | Testing, simple production |
| Kubernetes | Helm chart | Production, high availability |
| JAR | Direct execution | Development |

## Docker Compose (recommended)

Create a `docker-compose.yml` file:

```yaml
services:
  pesitwizard-client-api:
    image: ghcr.io/pesitwizard/pesitwizard/pesitwizard-client:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: nosecurity,postgres
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pesitwizard
      SPRING_DATASOURCE_USERNAME: pesitwizard
      SPRING_DATASOURCE_PASSWORD: pesitwizard
    depends_on:
      postgres:
        condition: service_healthy
    volumes:
      - client-data:/data
    networks:
      - client-network

  pesitwizard-client-ui:
    image: ghcr.io/pesitwizard/pesitwizard/pesitwizard-client-ui:latest
    ports:
      - "3001:8080"
    environment:
      NGINX_PORT: 8080
      API_HOST: pesitwizard-client-api
      API_PORT: 8080
    depends_on:
      - pesitwizard-client-api
    networks:
      - client-network

  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: pesitwizard
      POSTGRES_USER: pesitwizard
      POSTGRES_PASSWORD: pesitwizard
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pesitwizard -d pesitwizard"]
      interval: 5s
      timeout: 5s
      retries: 5
    networks:
      - client-network

networks:
  client-network:
    driver: bridge

volumes:
  client-data:
  postgres-data:
```

Start with:

```bash
docker compose up -d
```

The UI is available at http://localhost:3001 and the API at http://localhost:8080.

### File Storage Directories

The client uses configurable directories for file transfers and browsing:

| Property | Default | Env Variable | Purpose |
|----------|---------|-------------|---------|
| `pesit.client.receive-directory` | `./received` | `PESIT_CLIENT_RECEIVE_DIRECTORY` | Where received files are stored |
| `pesitwizard.storage.local-browse-path` | `./data` | `PESITWIZARD_STORAGE_LOCAL_BROWSE_PATH` | Base directory the UI file browser can access |

The **local browse path** controls which directory the UI's file picker can browse when selecting files to send. It must be set for the file browser to work.

**Bind-mounting host directories** (recommended for production):

```yaml
# In docker-compose.yml
services:
  pesitwizard-client-api:
    volumes:
      - /opt/pesit/data:/data
    environment:
      PESIT_CLIENT_RECEIVE_DIRECTORY: /data/received
      PESITWIZARD_STORAGE_LOCAL_BROWSE_PATH: /data
```

Files to send are specified per-transfer via the REST API (as a local file path or storage connector reference). The UI file browser uses `local-browse-path` to let users pick files visually.

### With HashiCorp Vault

For secure secrets management with HashiCorp Vault:

```yaml
services:
  vault:
    image: hashicorp/vault:1.21
    cap_add:
      - IPC_LOCK
    ports:
      - "8200:8200"
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: pesitwizard-dev-token
      VAULT_DEV_LISTEN_ADDRESS: 0.0.0.0:8200
      VAULT_ADDR: http://127.0.0.1:8200
    command: server -dev
    networks:
      - client-network
    healthcheck:
      test: ["CMD-SHELL", "wget --quiet --tries=1 --spider http://127.0.0.1:8200/v1/sys/health || exit 1"]
      interval: 5s
      timeout: 5s
      retries: 5

  vault-init:
    image: hashicorp/vault:1.21
    depends_on:
      vault:
        condition: service_healthy
    environment:
      VAULT_ADDR: http://vault:8200
      VAULT_TOKEN: pesitwizard-dev-token
    entrypoint: /bin/sh
    command:
      - -c
      - |
        vault secrets enable -path=secret kv-v2 2>/dev/null || true
        vault kv put secret/pesitwizard/client initialized=true
    networks:
      - client-network

  pesitwizard-client-api:
    image: ghcr.io/pesitwizard/pesitwizard/pesitwizard-client:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: nosecurity,postgres
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pesitwizard
      SPRING_DATASOURCE_USERNAME: pesitwizard
      SPRING_DATASOURCE_PASSWORD: pesitwizard
      PESITWIZARD_SECURITY_ENCRYPTION_MODE: VAULT
      PESITWIZARD_SECURITY_VAULT_ADDRESS: http://vault:8200
      PESITWIZARD_SECURITY_VAULT_TOKEN: pesitwizard-dev-token
      PESITWIZARD_SECURITY_VAULT_SECRETS_PATH: secret/data/pesitwizard/client
    depends_on:
      postgres:
        condition: service_healthy
      vault-init:
        condition: service_completed_successfully
    volumes:
      - client-data:/data
    networks:
      - client-network

  pesitwizard-client-ui:
    image: ghcr.io/pesitwizard/pesitwizard/pesitwizard-client-ui:latest
    ports:
      - "3001:8080"
    environment:
      NGINX_PORT: 8080
      API_HOST: pesitwizard-client-api
      API_PORT: 8080
    depends_on:
      - pesitwizard-client-api
    networks:
      - client-network

  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: pesitwizard
      POSTGRES_USER: pesitwizard
      POSTGRES_PASSWORD: pesitwizard
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pesitwizard -d pesitwizard"]
      interval: 5s
      timeout: 5s
      retries: 5
    networks:
      - client-network

networks:
  client-network:
    driver: bridge

volumes:
  client-data:
  postgres-data:
```

> **Production**: Replace the dev token with **AppRole** authentication using `PESITWIZARD_SECURITY_VAULT_ROLE_ID` and `PESITWIZARD_SECURITY_VAULT_SECRET_ID`.

## Kubernetes (Helm)

### Quick Install

```bash
curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/install-client.sh | bash
```

### Manual Install

Install from the OCI registry:

```bash
helm install pesitwizard-client oci://ghcr.io/pesitwizard/charts/pesitwizard-client \
  --version 0.1.0 \
  --namespace pesitwizard \
  --create-namespace
```

To use an external PostgreSQL database:

```bash
helm install pesitwizard-client oci://ghcr.io/pesitwizard/charts/pesitwizard-client \
  --version 0.1.0 \
  --namespace pesitwizard \
  --create-namespace \
  --set database.host=postgres \
  --set database.port=5432 \
  --set database.name=pesitwizard \
  --set database.username=pesitwizard \
  --set database.password=pesitwizard
```

## JAR (development)

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL (optional, H2 used by default)

### Build

The client is a module within the main repository:

```bash
git clone https://github.com/pesitwizard/pesitwizard.git
cd pesitwizard/pesitwizard-client
mvn package -DskipTests
```

### Run

With H2 (default, no database required):

```bash
java -jar target/pesitwizard-client-1.0.0-SNAPSHOT-exec.jar
```

With PostgreSQL:

```bash
java -jar target/pesitwizard-client-1.0.0-SNAPSHOT-exec.jar \
  --spring.profiles.active=nosecurity,postgres \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/pesitwizard \
  --spring.datasource.username=pesitwizard \
  --spring.datasource.password=pesitwizard
```

## Verification

Once started, verify that the service is running:

```bash
# Health check
curl http://localhost:8080/actuator/health

# Expected response
{"status":"UP"}
```

The web interface is available at:
- UI: http://localhost:3001
- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html

## Next Steps

- [Configuration](/guide/client/configuration)
- [Usage](/guide/client/usage)
