# Installation du Client PeSIT Wizard

## Options de déploiement

| Mode | Description | Recommandé pour |
|------|-------------|-----------------|
| Docker | Container autonome | Tests, petites installations |
| Docker Compose | Avec PostgreSQL | Production simple |
| Kubernetes | Helm chart | Production, haute disponibilité |
| JAR | Exécution directe | Développement |

## Docker (recommandé)

### Démarrage rapide

```bash
docker run -d \
  --name pesitwizard-client \
  -p 9081:9081 \
  -v pesitwizard-data:/data \
  ghcr.io/pesitwizard/pesitwizard-client:latest
```

### Avec PostgreSQL

```bash
docker run -d \
  --name pesitwizard-client \
  -p 9081:9081 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/pesitwizard \
  -e SPRING_DATASOURCE_USERNAME=pesitwizard \
  -e SPRING_DATASOURCE_PASSWORD=pesitwizard \
  ghcr.io/pesitwizard/pesitwizard-client:latest
```

## Docker Compose

Créez un fichier `docker-compose.yml` :

```yaml
services:
  pesitwizard-client-api:
    image: ghcr.io/pesitwizard/pesitwizard/pesitwizard-client:latest
    ports:
      - "9081:9081"
    environment:
      SERVER_PORT: 9081
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
      API_PORT: 9081
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

Lancez avec :

```bash
docker compose up -d
```

### Avec HashiCorp Vault

Pour une gestion sécurisée des secrets avec HashiCorp Vault :

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
      - "9081:9081"
    environment:
      SERVER_PORT: 9081
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
      API_PORT: 9081
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

> ⚠️ **Production** : Remplacez le token dev par une authentification **AppRole** avec `PESITWIZARD_SECURITY_VAULT_ROLE_ID` et `PESITWIZARD_SECURITY_VAULT_SECRET_ID`.

## Kubernetes (Helm)

```bash
# Ajouter le repo Helm
helm repo add pesitwizard https://pesitwizard.github.io/pesitwizard-helm-charts

# Installer le client
helm install pesitwizard-client pesitwizard/pesitwizard-client \
  --namespace pesitwizard \
  --create-namespace \
  --set postgresql.enabled=true
```

## JAR (développement)

### Prérequis

- Java 21+
- Maven 3.9+
- PostgreSQL

### Build

```bash
git clone https://github.com/pesitwizard/pesitwizard-client.git
cd pesitwizard-client
mvn package -DskipTests
```

### Exécution

```bash
java -jar target/pesitwizard-client-1.0.0-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/pesitwizard \
  --spring.datasource.username=pesitwizard \
  --spring.datasource.password=pesitwizard
```

## Vérification

Une fois démarré, vérifiez que le service fonctionne :

```bash
# Health check
curl http://localhost:9081/actuator/health

# Réponse attendue
{"status":"UP"}
```

L'interface web est accessible sur :
- API : http://localhost:9081
- UI : http://localhost:3001 (si déployée séparément)
- Swagger : http://localhost:9081/swagger-ui.html

## Prochaines étapes

- [Configuration](/guide/client/configuration)
- [Utilisation](/guide/client/usage)
