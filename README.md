# PeSIT Wizard - Open Source PeSIT File Transfer

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/)
[![Website](https://img.shields.io/badge/Website-pesitwizard.com-blue)](https://pesitwizard.com)

🌐 **[pesitwizard.com](https://pesitwizard.com)** - Site officiel avec documentation et tarifs

Solution open source de transfert de fichiers basée sur le protocole **PeSIT** (Protocole d'Échange pour un Système Interbancaire de Télécompensation).

## Qu'est-ce que PeSIT ?

PeSIT est le protocole standard utilisé par les banques françaises pour les échanges de fichiers sécurisés :
- Virements SEPA
- Relevés de compte
- Prélèvements
- Échanges interbancaires

## Composants

| Module | Description |
|--------|-------------|
| `pesitwizard-server` | Serveur PeSIT complet avec API REST |
| `pesitwizard-client` | Client Java pour envoyer/recevoir des fichiers |
| `pesitwizard-client-ui` | Interface graphique pour le client (Vue.js) |
| `pesitwizard-pesit` | Bibliothèque d'implémentation du protocole PeSIT |
| `pesitwizard-security` | Gestion des secrets (AES, HashiCorp Vault) |
| `pesitwizard-connector-api` | API pour les connecteurs de stockage |
| `pesitwizard-connector-local` | Connecteur système de fichiers local |
| `pesitwizard-connector-sftp` | Connecteur SFTP |
| `pesitwizard-connector-s3` | Connecteur AWS S3 / MinIO |
| `pesitwizard-helm-charts` | Charts Helm pour Kubernetes |
| `pesitwizard-docs` | Documentation (VitePress) |

## Démarrage rapide

### Prérequis

- Java 21+
- Maven 3.9+

### Installation

```bash
# Cloner le repo
git clone https://github.com/pesitwizard/pesitwizard.git
cd pesitwizard

# Builder le serveur
cd pesitwizard-server
mvn package -DskipTests

# Lancer le serveur
java -jar target/pesitwizard-server-1.0.0-SNAPSHOT.jar
```

Le serveur démarre sur :
- **Port 5000** : Protocole PeSIT
- **Port 8080** : API REST

### Envoyer un fichier

```bash
# Avec le client Java
cd pesitwizard-client
mvn exec:java -Dexec.args="send -h localhost -p 5000 -f /path/to/file.txt"
```

### API REST

```bash
# Statut du serveur
curl http://localhost:8080/actuator/health

# Liste des transferts
curl http://localhost:8080/api/v1/transfers
```

## Configuration

### Fichier `application.yml`

```yaml
pesitwizard:
  server:
    port: 5000
    ssl:
      enabled: false
  
spring:
  datasource:
    url: jdbc:h2:file:./data/pesitwizard-db
```

### Variables d'environnement

| Variable | Description | Défaut |
|----------|-------------|--------|
| `PESITWIZARD_SERVER_PORT` | Port PeSIT | `5000` |
| `SERVER_PORT` | Port API REST | `8080` |

## Sécurité

### Authentification OAuth2/OIDC

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example.com/realms/pesitwizard
```

### Gestion des secrets

Deux modes disponibles via `pesitwizard-security` :

**Mode AES (par défaut)** :
```yaml
pesitwizard:
  secrets:
    provider: aes
    aes:
      key-file: /app/secrets/master.key
```

**Mode HashiCorp Vault** :
```yaml
pesitwizard:
  secrets:
    provider: vault
    vault:
      address: https://vault.example.com
      token: ${VAULT_TOKEN}
      path: secret/data/pesitwizard
```

## Connecteurs de stockage

Les connecteurs permettent de stocker les fichiers transférés sur différents backends :

| Connecteur | Description | Configuration |
|------------|-------------|---------------|
| `local` | Système de fichiers local | `path: /data/files` |
| `sftp` | Serveur SFTP distant | `host`, `port`, `username`, `password/key` |
| `s3` | AWS S3 ou MinIO | `endpoint`, `bucket`, `access-key`, `secret-key` |

```yaml
pesitwizard:
  connector:
    type: sftp
    sftp:
      host: sftp.example.com
      port: 22
      username: pesit
      private-key-file: /app/secrets/id_rsa
```

## Observabilité

### Métriques Prometheus

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

Endpoint : `http://localhost:8080/actuator/prometheus`

### Tracing OpenTelemetry

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
otel:
  exporter:
    otlp:
      endpoint: http://jaeger:4317
```

## Docker

```bash
# Builder l'image
docker build -t pesitwizard-server ./pesitwizard-server

# Lancer
docker run -p 5000:5000 -p 8080:8080 pesitwizard-server
```

## Kubernetes

### Installation rapide

**Client PeSIT Wizard** (transfert de fichiers avec UI) :
```bash
curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/install-client.sh | bash
```

**Serveur PeSIT Wizard** (standalone) :
```bash
curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/install-server.sh | bash
```

**Désinstallation** :
```bash
curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/uninstall.sh | bash
```

### Helm Charts

Les charts Helm sont disponibles dans `pesitwizard-helm-charts/` :
- `pesitwizard-client` : Client avec API et UI
- `pesitwizard-server` : Serveur standalone

```bash
# Installation manuelle avec Helm
helm install pesitwizard-client ./pesitwizard-helm-charts/pesitwizard-client -n pesitwizard --create-namespace
helm install pesitwizard-server ./pesitwizard-helm-charts/pesitwizard-server -n pesitwizard --create-namespace
```

## Documentation

- [Guide de démarrage](https://docs.pesitwizard.com/guide/quickstart)
- [Configuration serveur](https://docs.pesitwizard.com/guide/server/configuration)
- [API REST](https://docs.pesitwizard.com/api/)

## Structure du projet

```
pesitwizard/
├── pesitwizard-server/          # Serveur PeSIT avec API REST
├── pesitwizard-client/          # Client Java (CLI + API)
├── pesitwizard-client-ui/       # Interface client (Vue.js)
├── pesitwizard-pesit/           # Bibliothèque protocole PeSIT
├── pesitwizard-security/        # Gestion secrets (AES, Vault)
├── pesitwizard-connector-api/   # API connecteurs stockage
├── pesitwizard-connector-local/ # Connecteur fichiers locaux
├── pesitwizard-connector-sftp/  # Connecteur SFTP
├── pesitwizard-connector-s3/    # Connecteur S3/MinIO
├── pesitwizard-helm-charts/     # Charts Helm Kubernetes
├── pesitwizard-docs/            # Documentation (VitePress)
└── scripts/                     # Scripts utilitaires
```

## Contribuer

Les contributions sont les bienvenues ! Voir [CONTRIBUTING.md](CONTRIBUTING.md).

## Enterprise

Pour les fonctionnalités entreprise (clustering HA, console d'administration, support), voir [PeSIT Wizard Enterprise](https://pesitwizard.com).

## Licence

[Apache License 2.0](LICENSE)

---

**PeSIT Wizard** - Solution PeSIT moderne et open source.
