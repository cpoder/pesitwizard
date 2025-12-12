# Vectis Server - Enterprise File Transfer Solution

Solution complète de transfert de fichiers basée sur le protocole Vectis, déployable sur Kubernetes avec une console d'administration web.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Admin Console (UI)                           │
│                     http://localhost:3000                           │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Admin Backend (API)                            │
│                     http://localhost:9080                           │
│  - Gestion des clusters                                             │
│  - Déploiement Kubernetes                                           │
│  - Proxy vers les serveurs Vectis                                    │
└─────────────────────────────────────────────────────────────────────┘
           │                                    │
           │ (port-forward)                     │ (direct K8s API)
           ▼                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                              │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              Vectis Server Deployment (3 replicas)            │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │   │
│  │  │   Pod 1     │  │   Pod 2     │  │   Pod 3     │          │   │
│  │  │  (Leader)   │  │  (Standby)  │  │  (Standby)  │          │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘          │   │
│  │                    JGroups Cluster                           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   PostgreSQL (Shared DB)                     │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

## Composants

| Composant | Description | Port |
|-----------|-------------|------|
| `vectis-server` | Serveur Vectis (protocole de transfert) | 5000 (Vectis), 8080 (API) |
| `vectis-admin` | Backend d'administration | 9080 |
| `vectis-admin-ui` | Interface web d'administration | 3000 |
| `vectis-client` | Client Vectis pour tests/intégration | 9081 |
| PostgreSQL | Base de données | 5435 |

## Prérequis

- **Java 21+**
- **Maven 3.9+**
- **Node.js 18+** (pour l'UI)
- **Podman** ou Docker (pour PostgreSQL)
- **kubectl** configuré avec accès au cluster Kubernetes
- **Cluster Kubernetes** (K3s, Minikube, ou cloud)

## Démarrage rapide

### 1. Démarrer l'environnement de développement

```bash
# Démarrer tous les services (PostgreSQL, Admin Backend, Admin UI)
./scripts/start-admin.sh

# Vérifier le statut
./scripts/status.sh
```

### 2. Accéder à l'interface

- **Admin UI** : http://localhost:3000
- **Identifiants** : `admin` / `admin`

### 3. Créer et déployer un cluster Vectis

1. Aller dans "Clusters" > "New Cluster"
2. Configurer le cluster (nom, namespace, replicas)
3. Cliquer sur "Deploy"

### 4. Configurer le port-forwarding (IMPORTANT)

Pour que l'admin puisse communiquer avec les serveurs Vectis déployés, un port-forwarding est nécessaire :

```bash
# Démarrer le port-forwarding vers l'API du serveur Vectis
kubectl port-forward svc/vectis-server-api 8080:8080 -n default &
```

**Note** : Sans ce port-forwarding, les fonctionnalités suivantes ne fonctionneront pas :
- Gestion des partenaires
- Gestion des fichiers virtuels
- Consultation des transferts
- Audit logs du serveur

## Scripts disponibles

| Script | Description |
|--------|-------------|
| `./scripts/start-admin.sh` | Démarre PostgreSQL, Admin Backend et Admin UI |
| `./scripts/start-client.sh` | Démarre le client Vectis |
| `./scripts/start-all.sh` | Démarre tous les composants |
| `./scripts/stop-all.sh` | Arrête tous les composants |
| `./scripts/status.sh` | Affiche le statut des services |

## Configuration

### Variables d'environnement

| Variable | Description | Défaut |
|----------|-------------|--------|
| `DATABASE_URL` | URL JDBC PostgreSQL | `jdbc:postgresql://localhost:5435/vectis` |
| `SERVER_PORT` | Port du backend admin | `9080` |
| `PESIT_ADMIN_USERNAME` | Utilisateur admin | `admin` |
| `PESIT_ADMIN_PASSWORD` | Mot de passe admin | `admin` |

### Configuration du cluster

Dans l'UI, lors de la création d'un cluster :

- **API URL** : URL pour accéder à l'API du serveur Vectis
  - En développement avec port-forward : `http://localhost:8080`
  - En production : URL du LoadBalancer ou Ingress

## Développement

### Build complet

```bash
# Backend admin
cd vectis-admin && mvn package -DskipTests

# Serveur Vectis
cd vectis-server && mvn package -DskipTests

# UI admin
cd vectis-admin-ui && npm install && npm run build
```

### Logs

```bash
# Admin backend
tail -f /tmp/vectis-admin.log

# Admin UI
tail -f /tmp/vectis-admin-ui.log

# Serveur Vectis (Kubernetes)
kubectl logs -f deployment/vectis-server -n default
```

## Dépannage

### L'admin ne peut pas communiquer avec le serveur Vectis

1. Vérifier que le port-forwarding est actif :
   ```bash
   ps aux | grep port-forward
   ```

2. Démarrer le port-forwarding si nécessaire :
   ```bash
   kubectl port-forward svc/vectis-server-api 8080:8080 -n default &
   ```

3. Vérifier l'API URL du cluster dans l'UI (doit être `http://localhost:8080`)

### PostgreSQL ne démarre pas

```bash
# Vérifier le conteneur
podman ps -a | grep postgres

# Redémarrer
podman start vectis-postgres
```

### Le backend admin ne démarre pas

```bash
# Vérifier les logs
tail -50 /tmp/vectis-admin.log

# Vérifier que PostgreSQL est accessible
psql -h localhost -p 5435 -U vectis -d vectis -c "SELECT 1"
```

## Structure du projet

```
vectis/
├── vectis-server/        # Serveur Vectis (déployé sur K8s)
├── vectis-admin/         # Backend d'administration
├── vectis-admin-ui/      # Interface web Vue.js
├── vectis-client/        # Client Vectis
├── vectis-common/        # Code partagé
├── vectis-fpdu/          # Parseur protocole Vectis
├── vectis-docs/          # Documentation
└── scripts/             # Scripts de démarrage
```

## Licence

Propriétaire - Tous droits réservés
