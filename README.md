# Vectis - Open Source PeSIT File Transfer

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/)

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
| `vectis-server` | Serveur PeSIT complet avec API REST |
| `vectis-client` | Client Java pour envoyer/recevoir des fichiers |
| `vectis-client-ui` | Interface graphique pour le client |
| `vectis-pesit` | Bibliothèque d'implémentation du protocole PeSIT |
| `vectis-docs` | Documentation |

## Démarrage rapide

### Prérequis

- Java 21+
- Maven 3.9+

### Installation

```bash
# Cloner le repo
git clone https://github.com/cpoder/vectis.git
cd vectis

# Builder le serveur
cd vectis-server
mvn package -DskipTests

# Lancer le serveur
java -jar target/vectis-server-1.0.0-SNAPSHOT.jar
```

Le serveur démarre sur :
- **Port 5000** : Protocole PeSIT
- **Port 8080** : API REST

### Envoyer un fichier

```bash
# Avec le client Java
cd vectis-client
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
vectis:
  server:
    port: 5000
    ssl:
      enabled: false
  
spring:
  datasource:
    url: jdbc:h2:file:./data/vectis-db
```

### Variables d'environnement

| Variable | Description | Défaut |
|----------|-------------|--------|
| `VECTIS_SERVER_PORT` | Port PeSIT | `5000` |
| `SERVER_PORT` | Port API REST | `8080` |

## Docker

```bash
# Builder l'image
docker build -t vectis-server ./vectis-server

# Lancer
docker run -p 5000:5000 -p 8080:8080 vectis-server
```

## Documentation

- [Guide de démarrage](https://docs.vectis.cloud/guide/quickstart)
- [Configuration serveur](https://docs.vectis.cloud/guide/server/configuration)
- [API REST](https://docs.vectis.cloud/api/)

## Structure du projet

```
vectis/
├── vectis-server/       # Serveur PeSIT
├── vectis-client/       # Client Java
├── vectis-client-ui/    # Interface client (Vue.js)
├── vectis-pesit/        # Bibliothèque protocole
├── vectis-docs/         # Documentation (VitePress)
└── scripts/             # Scripts utilitaires
```

## Contribuer

Les contributions sont les bienvenues ! Voir [CONTRIBUTING.md](CONTRIBUTING.md).

## Enterprise

Pour les fonctionnalités entreprise (clustering HA, console d'administration, support), voir [Vectis Enterprise](https://vectis.cloud).

## Licence

[Apache License 2.0](LICENSE)

---

**Vectis** - Solution PeSIT moderne et open source.
