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
version: '3.8'

services:
  pesitwizard-client:
    image: ghcr.io/pesitwizard/pesitwizard-client:latest
    ports:
      - "9081:9081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pesitwizard
      SPRING_DATASOURCE_USERNAME: pesitwizard
      SPRING_DATASOURCE_PASSWORD: pesitwizard
    depends_on:
      - postgres
    volumes:
      - client-data:/data

  pesitwizard-client-ui:
    image: ghcr.io/pesitwizard/pesitwizard-client-ui:latest
    ports:
      - "3001:80"
    environment:
      VITE_API_URL: http://localhost:9081

  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: pesitwizard
      POSTGRES_USER: pesitwizard
      POSTGRES_PASSWORD: pesitwizard
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  client-data:
  postgres-data:
```

Lancez avec :

```bash
docker-compose up -d
```

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
