# PeSIT Wizard Client Installation

## Deployment Options

| Mode | Description | Recommended For |
|------|-------------|-----------------|
| Docker | Standalone container | Testing, small installations |
| Docker Compose | With PostgreSQL | Simple production |
| Kubernetes | Helm chart | Production, high availability |
| JAR | Direct execution | Development |

## Docker (recommended)

### Quick Start

```bash
docker run -d \
  --name pesitwizard-client \
  -p 8080:8080 \
  -v pesitwizard-data:/data \
  ghcr.io/pesitwizard/pesitwizard/pesitwizard-client:latest
```

### With PostgreSQL

```bash
docker run -d \
  --name pesitwizard-client \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/pesitwizard \
  -e SPRING_DATASOURCE_USERNAME=pesitwizard \
  -e SPRING_DATASOURCE_PASSWORD=pesitwizard \
  ghcr.io/pesitwizard/pesitwizard/pesitwizard-client:latest
```

## Docker Compose

Create a `docker-compose.yml` file:

```yaml
services:
  pesitwizard-client-api:
    image: ghcr.io/pesitwizard/pesitwizard/pesitwizard-client:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pesitwizard
      SPRING_DATASOURCE_USERNAME: pesitwizard
      SPRING_DATASOURCE_PASSWORD: pesitwizard
    depends_on:
      - postgres
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

### With HashiCorp Vault

For secure secrets management with HashiCorp Vault:

```yaml
services:
  vault:
    image: hashicorp/vault:1.15
    cap_add:
      - IPC_LOCK
    ports:
      - "8200:8200"
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: pesitwizard-dev-token
      VAULT_DEV_LISTEN_ADDRESS: 0.0.0.0:8200
    command: server -dev
    networks:
      - client-network
    healthcheck:
      test: ["CMD", "vault", "status"]
      interval: 10s
      timeout: 5s
      retries: 3

  vault-init:
    image: hashicorp/vault:1.15
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
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pesitwizard
      SPRING_DATASOURCE_USERNAME: pesitwizard
      SPRING_DATASOURCE_PASSWORD: pesitwizard
      PESITWIZARD_SECURITY_MODE: VAULT
      PESITWIZARD_SECURITY_VAULT_ADDRESS: http://vault:8200
      PESITWIZARD_SECURITY_VAULT_TOKEN: pesitwizard-dev-token
      PESITWIZARD_SECURITY_VAULT_PATH: secret/data/pesitwizard/client
    depends_on:
      postgres:
        condition: service_started
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

```bash
# Add the Helm repo
helm repo add pesitwizard https://pesitwizard.github.io/pesitwizard-helm-charts

# Install the client
helm install pesitwizard-client pesitwizard/pesitwizard-client \
  --namespace pesitwizard \
  --create-namespace \
  --set postgresql.enabled=true
```

## JAR (development)

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL

### Build

```bash
git clone https://github.com/pesitwizard/pesitwizard-client.git
cd pesitwizard-client
mvn package -DskipTests
```

### Run

```bash
java -jar target/pesitwizard-client-1.0.0-SNAPSHOT.jar \
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
- API: http://localhost:8080
- UI: http://localhost:3001 (if deployed separately)
- Swagger: http://localhost:8080/swagger-ui.html

## Next Steps

- [Configuration](/guide/client/configuration)
- [Usage](/guide/client/usage)
