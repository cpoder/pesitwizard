# Vectis Server

Serveur Vectis implémentant le protocole PeSIT (Protocole d'Échange pour un Système Interbancaire de Télécompensation) en Spring Boot. Conçu pour être déployé sur Kubernetes avec support du clustering.

## Fonctionnalités

- **Protocole PeSIT Hors-SIT** : Envoi et réception de fichiers
- **Clustering** : Haute disponibilité avec JGroups (élection de leader)
- **API REST** : Configuration et monitoring via HTTP
- **Multi-tenant** : Gestion de plusieurs partenaires et fichiers virtuels
- **Kubernetes-native** : Labeling automatique du pod leader

## Prérequis

- Java 21+
- Maven 3.9+
- PostgreSQL
- Kubernetes (pour le déploiement en cluster)

## Build

```bash
# Installer d'abord la bibliothèque protocole
cd ../vectis-protocol
mvn install -DskipTests

# Builder le serveur
cd ../vectis-server
mvn package -DskipTests
```

## Exécution locale

```bash
java -jar target/vectis-server-1.0.0-SNAPSHOT.jar
```

- Port PeSIT : **5000**
- Port HTTP/API : **8080**

## Configuration

Fichier `application.yml` :

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pesit
    username: pesit
    password: pesit

pesit:
  cluster:
    enabled: false  # true pour activer le clustering
```

## API REST

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/servers` | Liste des serveurs configurés |
| POST | `/api/servers/{id}/start` | Démarrer un serveur |
| POST | `/api/servers/{id}/stop` | Arrêter un serveur |
| GET | `/api/cluster/status` | État du cluster (leader, membres) |
| GET | `/api/partners` | Liste des partenaires |
| GET | `/api/virtual-files` | Liste des fichiers virtuels |

## Déploiement Kubernetes

Le serveur est conçu pour être déployé via `pesit-admin` qui gère automatiquement :
- Création du Deployment (3 replicas par défaut)
- Service LoadBalancer pointant vers le leader
- PersistentVolumeClaim pour le stockage
- RBAC pour le labeling des pods

### Variables d'environnement Kubernetes

| Variable | Description |
|----------|-------------|
| `POD_NAME` | Nom du pod (via Downward API) |
| `POD_NAMESPACE` | Namespace du pod |
| `SPRING_DATASOURCE_URL` | URL de connexion PostgreSQL |

## Monitoring

Endpoints Actuator sur le port 8080 :

- Health : `GET /actuator/health`
- Readiness : `GET /actuator/health/readiness`
- Metrics : `GET /actuator/metrics`

## Docker

```bash
docker build -t pesit-server .
docker run -p 5000:5000 -p 8080:8080 pesit-server
```

## Stack technique

- Spring Boot 3.x
- Java 21
- JGroups (clustering)
- PostgreSQL
- Fabric8 Kubernetes Client
