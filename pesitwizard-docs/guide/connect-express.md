# Interopérabilité Connect:Express

Guide de configuration pour l'interopérabilité entre PeSIT Wizard et IBM Sterling Connect:Express (CX).

## Vue d'ensemble

PeSIT Wizard est entièrement compatible avec Connect:Express pour les transferts bidirectionnels:

| Direction | Source | Destination | Status |
|-----------|--------|-------------|--------|
| CX → PW Server | Connect:Express | PeSIT Wizard Server | ✅ Validé |
| PW Client → CX | PeSIT Wizard Client | Connect:Express | ✅ Validé |

## Prérequis

### Connect:Express
- Version 1.5.x ou supérieure
- Licence PeSIT activée
- Accès aux commandes `$sterm`, `$p1b8preq`

### PeSIT Wizard
- Server ou Client version 1.0.0+
- Java 21+

---

## Configuration CX → PW Server

Cette configuration permet à Connect:Express d'envoyer des fichiers vers PeSIT Wizard Server.

### 1. Créer le partenaire dans CX

```bash
# Via l'outil cx-setup-partner
./cx-setup-partner PWSERVER pw-server 05001 PWSRV01

# Ou manuellement via $sterm:
# C P (Create Partner)
# Nom symbolique: PWSERVER
# Nature: T (TCP/IP)
# Protocole: 3 (PeSIT)
# TCP Host: pw-server (ou IP)
# TCP Port: 05001
# DPCSID: PWSRV01
```

**Paramètres importants**:
| Paramètre | Valeur | Description |
|-----------|--------|-------------|
| Nature | `T` | TCP/IP (ou `S` pour SSL) |
| Protocole | `3` | PeSIT |
| Tab Session | `1` | Table de session par défaut |
| Nb Liaisons | `10` | Max sessions simultanées |
| Type Liaison | `M` | Mixed (initiateur + répondeur) |

### 2. Créer le fichier virtuel dans CX

```bash
# Via l'outil cx-setup-file
./cx-setup-file PWSEND T /tmp/cx-send PWSERVER BV 04096

# Ou via $sterm:
# C F (Create File)
# Nom symbolique: PWSEND
# Direction: T (Transmit)
# DSN: /tmp/cx-send/&REQNUMB
# Partenaire: PWSERVER
# Format: BV (Binary Variable) ou **
# Long article: 04096
```

### 3. Configurer PW Server

```yaml
# application.yml (PW Server)
pesitwizard:
  server:
    port: 5001
    server-id: PWSRV01
```

Créer le partenaire CX:
```bash
curl -X POST http://localhost:8080/api/v1/config/partners \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "id": "CETOM1",
    "description": "Connect:Express",
    "enabled": true,
    "accessType": "BOTH"
  }'
```

Créer le fichier virtuel:
```bash
curl -X POST http://localhost:8080/api/v1/config/files \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "id": "PWSEND",
    "direction": "RECEIVE",
    "receiveDirectory": "/data/received",
    "receiveFilenamePattern": "from_cx_${transferId}",
    "enabled": true
  }'
```

### 4. Tester le transfert

```bash
# Depuis CX, envoyer un fichier
$p1b8preq /SFN=PWSEND/SPN=PWSERVER/DIR=T/DSN=/path/to/file.dat

# Vérifier sur PW Server
ls -la /data/received/
```

---

## Configuration PW Client → CX

Cette configuration permet à PeSIT Wizard Client d'envoyer des fichiers vers Connect:Express.

### 1. Configurer CX comme serveur

Vérifier que CX écoute sur le port PeSIT:
```bash
netstat -tlnp | grep 5000
# Ou
$sterm
# Puis: D S (Display System)
```

Créer le partenaire pour PW Client:
```bash
./cx-setup-partner PWCLIENT pw-client 09081 PWSRV01
```

Créer le fichier virtuel de réception:
```bash
./cx-setup-file PWRECV R /tmp/cx-received PWCLIENT ** 04096
```

**Note**: Utiliser `FORMAT=**` pour accepter tout format envoyé par PW Client.

### 2. Configurer PW Client

Ajouter le serveur CX:
```bash
curl -X POST http://localhost:9081/api/v1/servers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "cx-server",
    "host": "cx-server",
    "port": 5000,
    "serverId": "CETOM1",
    "description": "Connect:Express Server",
    "tlsEnabled": false,
    "enabled": true
  }'
```

### 3. Envoyer un fichier

```bash
curl -X POST http://localhost:9081/api/v1/transfers/send \
  -H "Content-Type: application/json" \
  -d '{
    "server": "cx-server",
    "partnerId": "PWSRV01",
    "filename": "/data/send/test.dat",
    "remoteFilename": "PWRECV",
    "syncPointsEnabled": true
  }'
```

---

## Paramètres de compatibilité

### Taille des entités (PI_25)

PW et CX négocient la taille maximale des FPDU:

| Système | Valeur par défaut | Recommandation |
|---------|-------------------|----------------|
| PW Server | 32768 | OK |
| PW Client | 32768 | OK |
| CX | 4096 | Augmenter si possible |

La valeur négociée sera le minimum des deux. Pour de meilleures performances, configurer CX avec une valeur plus élevée dans la table de session.

### Format d'enregistrement

| Format CX | Description | Compatibilité PW |
|-----------|-------------|------------------|
| `BV` | Binary Variable | ✅ Recommandé |
| `BF` | Binary Fixed | ✅ OK |
| `TV` | Text Variable | ✅ OK |
| `TF` | Text Fixed | ✅ OK |
| `**` | Tout format | ✅ Flexible |

### Sync Points

Les sync points sont supportés pour la reprise après interruption:

```yaml
# PW Client - activer les sync points
syncPointsEnabled: true

# CX - configurer l'intervalle dans la table de session
# Typiquement 100KB ou 256KB
```

---

## Dépannage CX

### Vérifier l'état de CX

```bash
# Status du moniteur
$sterm
# D S (Display System)

# Lister les partenaires
# L P (List Partners)

# Lister les fichiers virtuels
# L F (List Files)

# Voir les transferts en cours
# L R (List Requests)
```

### Logs CX

```bash
# Log principal
tail -f $TOM_DIR/log/tom.log

# Log des requêtes
$p1b8pret /RQN=<request_number>
```

### Erreurs courantes

| Erreur CX | Cause | Solution |
|-----------|-------|----------|
| `RTCF 0017` | Entrée dupliquée | Partenaire/fichier existe déjà |
| `RTCF 0004` | Non trouvé | Vérifier le nom exact |
| `RTCF 0008` | Paramètre invalide | Vérifier la syntaxe |
| `CONNECT refused` | Auth échouée | Vérifier DPCSID/password |
| `File not found` | Fichier virtuel inconnu | Créer le fichier dans CX |

### Test de connectivité

```bash
# Depuis la machine CX vers PW Server
nc -zv pw-server 5001

# Depuis PW vers CX
nc -zv cx-server 5000
```

---

## Configuration TLS (Avancé)

### CX avec SSL

Pour activer TLS entre PW et CX:

1. **Modifier la nature du partenaire** de `T` (TCP) à `S` (SSL):
   ```c
   memcpy(param->uni.zreq_tom_part.nature, "S", 1);
   ```

2. **Configurer les paramètres SSL** (SSLPARM1, SSLPARM2) dans CX

3. **Importer les certificats** via les scripts CX:
   - `CXAPISCA` - Certificat API
   - `CXROOTCA` - Certificat CA root
   - `SSLPARM1` - Paramètres SSL

4. **Configurer PW** avec les certificats correspondants:
   ```yaml
   pesit:
     ssl:
       enabled: true
       keystore-name: cx-compatible-keystore
       truststore-name: cx-ca-truststore
   ```

**Note**: La configuration TLS avec CX nécessite des tests approfondis pour la compatibilité des cipher suites et versions TLS.

---

## Tests d'intégration Docker

Un environnement Docker complet est disponible pour tester l'interopérabilité:

```bash
cd integration-tests/cx-integration/docker

# Démarrer l'environnement
docker compose up -d

# Lancer les tests (16 tests)
docker compose up test-runner

# Voir les résultats
docker compose logs test-runner
```

### Tests inclus

| Phase | Tests | Description |
|-------|-------|-------------|
| 1 | 5 | CX → PW Server (small, 5MB, config) |
| 2 | 3 | PW Client → CX (small, 5MB, health) |
| 3 | 1 | Sync points (10MB avec MD5) |
| 4 | 1 | Transferts concurrents (3x simultanés) |
| 5 | 3 | Gestion d'erreurs |
| 6 | 3 | Cas limites (empty, spaces, 1 byte) |

---

## Matrice de compatibilité

| Fonctionnalité | PW ↔ PW | PW ↔ CX | Notes |
|----------------|---------|---------|-------|
| Transfert SEND | ✅ | ✅ | |
| Transfert RECEIVE | ✅ | ✅ | |
| Sync Points | ✅ | ✅ | |
| Restart/Resume | ✅ | ✅ | Après fix sync points |
| TLS simple | ✅ | ⚠️ | À valider |
| mTLS | ✅ | ⚠️ | À valider |
| Compression | ✅ | ❓ | Non testé |
| Multi-article DTF | ✅ | ✅ | |
| Fichiers > 1GB | ✅ | ✅ | Testé jusqu'à 10MB |
