# Dépannage

Ce guide couvre les problèmes courants et leur résolution.

## Erreurs de connexion

### CONNECTION_REFUSED

**Symptôme**: Le transfert échoue immédiatement avec "Connection refused"

**Causes possibles**:
| Cause | Diagnostic | Solution |
|-------|------------|----------|
| Serveur arrêté | `netstat -tlnp \| grep 5000` | Démarrer le serveur PeSIT |
| Mauvais port | Vérifier config serveur | Corriger le port dans la config |
| Firewall | `telnet host 5000` | Ouvrir le port dans le firewall |
| DNS | `nslookup hostname` | Vérifier résolution DNS |

**Commandes de diagnostic**:
```bash
# Tester la connectivité TCP
nc -zv pesit-server.example.com 5000

# Vérifier que le serveur écoute
ss -tlnp | grep 5000

# Tester depuis le conteneur Docker
docker exec pw-client nc -zv cx-server 5000
```

### CONNECTION_TIMEOUT

**Symptôme**: Le transfert reste bloqué puis échoue après le timeout

**Causes possibles**:
| Cause | Diagnostic | Solution |
|-------|------------|----------|
| Réseau lent | `ping host` | Augmenter connectionTimeout |
| Firewall silencieux | `traceroute host` | Vérifier règles firewall |
| Serveur surchargé | Logs serveur | Augmenter ressources serveur |

**Configuration timeout**:
```yaml
# application.yml (client)
pesitwizard:
  transfer:
    connection-timeout: 30000  # 30 secondes
    read-timeout: 120000       # 2 minutes
```

---

## Erreurs d'authentification

### AUTH_FAILED / Diagnostic 0x010101

**Symptôme**: Connexion établie mais rejetée avec code diagnostic

**Codes diagnostic courants**:
| Code | Signification | Solution |
|------|---------------|----------|
| `0x010101` | Partner ID inconnu | Vérifier partnerId côté serveur |
| `0x010102` | Mot de passe incorrect | Vérifier password |
| `0x010103` | Partenaire désactivé | Activer le partenaire |
| `0x010104` | Sessions max atteintes | Attendre ou augmenter limite |

**Vérification côté serveur**:
```bash
# Lister les partenaires configurés
curl -s -u admin:admin http://localhost:8080/api/v1/config/partners | jq '.[].id'

# Vérifier un partenaire spécifique
curl -s -u admin:admin http://localhost:8080/api/v1/config/partners/PARTNER_ID | jq
```

**Vérification côté CX**:
```bash
# Lister les partenaires CX
$sterm
# Puis: L P (List Partners)
```

### PARTNER_UNKNOWN

**Symptôme**: "Partner not found" ou "Unknown partner"

**Checklist**:
- [ ] Le partnerId est exactement identique (casse, espaces)
- [ ] Le partenaire existe côté serveur
- [ ] Le partenaire est activé (enabled: true)
- [ ] Le mot de passe correspond (si requis)

---

## Erreurs de fichier virtuel

### FILE_NOT_FOUND / Diagnostic 0x020201

**Symptôme**: Le fichier virtuel n'est pas trouvé sur le serveur

**Codes diagnostic**:
| Code | Signification | Solution |
|------|---------------|----------|
| `0x020201` | Fichier virtuel inconnu | Créer le fichier virtuel |
| `0x020202` | Direction incompatible | Vérifier SEND vs RECEIVE |
| `0x020203` | Fichier désactivé | Activer le fichier virtuel |

**Vérification PW Server**:
```bash
# Lister les fichiers virtuels
curl -s -u admin:admin http://localhost:8080/api/v1/config/files | jq '.[].id'

# Détails d'un fichier
curl -s -u admin:admin http://localhost:8080/api/v1/config/files/FILENAME | jq
```

**Vérification CX**:
```bash
$sterm
# Puis: L F (List Files)
```

### FORMAT_MISMATCH

**Symptôme**: Erreur de format lors du transfert

**Causes**:
- Format (BV/BF/TV/TF) incompatible
- Record length différent
- Compression non supportée

**Solution**:
```bash
# CX: Utiliser FORMAT=** pour accepter tout format
# setup-partners.sh
memcpy(param->uni.zreq_tom_file.format, "**", 2);
```

---

## Erreurs TLS/SSL

### SSL_HANDSHAKE_FAILURE

**Symptôme**: "SSL handshake failed" ou "Certificate error"

**Causes possibles**:
| Cause | Diagnostic | Solution |
|-------|------------|----------|
| Certificat expiré | `openssl x509 -enddate` | Renouveler le certificat |
| CA non trustée | Vérifier truststore | Importer le certificat CA |
| CN mismatch | Vérifier hostname | Corriger le CN ou SAN |
| Protocol mismatch | Logs TLS | Aligner versions TLS |

**Diagnostic OpenSSL**:
```bash
# Tester la connexion TLS
openssl s_client -connect pesit-server:5000 -tls1_3

# Vérifier le certificat serveur
openssl s_client -connect pesit-server:5000 < /dev/null 2>/dev/null | \
  openssl x509 -text -noout

# Vérifier la date d'expiration
openssl s_client -connect pesit-server:5000 < /dev/null 2>/dev/null | \
  openssl x509 -enddate -noout
```

**Vérifier les certificats**:
```bash
# Lister les certificats dans un keystore PKCS12
keytool -list -keystore keystore.p12 -storepass changeit

# Vérifier la chaîne de confiance
openssl verify -CAfile ca-cert.pem server-cert.pem
```

### CERTIFICATE_EXPIRED

**Symptôme**: "Certificate has expired"

**Solution**:
```bash
# 1. Générer un nouveau certificat
curl -X POST "http://localhost:8080/api/v1/certificates/ca/partner/PARTNER/generate" \
  -u admin:admin \
  -d "validityDays=365"

# 2. Distribuer le nouveau certificat au partenaire
# 3. Redémarrer les connexions
```

---

## Erreurs de transfert

### TRANSFER_INTERRUPTED

**Symptôme**: Transfert interrompu en cours de route

**Diagnostic**:
```bash
# Vérifier l'historique du transfert
curl -s http://localhost:9081/api/v1/transfers/TRANSFER_ID | jq

# Vérifier les sync points
curl -s http://localhost:9081/api/v1/transfers/TRANSFER_ID | jq '{
  status,
  bytesTransferred,
  lastSyncPoint,
  bytesAtLastSyncPoint,
  errorMessage
}'
```

**Reprise du transfert**:
```bash
# Si sync points > 0, le transfert peut être repris
curl -X POST http://localhost:9081/api/v1/transfers/TRANSFER_ID/resume
```

### DISK_FULL

**Symptôme**: "No space left on device"

**Diagnostic**:
```bash
# Vérifier l'espace disque
df -h /data/received

# Trouver les gros fichiers
du -sh /data/received/* | sort -rh | head -10
```

**Solution**:
- Nettoyer les anciens fichiers
- Augmenter l'espace disque
- Configurer la purge automatique

### CHECKSUM_MISMATCH

**Symptôme**: MD5/SHA mismatch après transfert

**Causes possibles**:
- Corruption réseau
- Problème de conversion de format
- Bug dans le multi-article DTF

**Diagnostic**:
```bash
# Comparer les checksums
md5sum fichier_source
md5sum fichier_recu

# Comparer les tailles
ls -la fichier_source fichier_recu
```

---

## Problèmes de performance

### TRANSFER_SLOW

**Symptôme**: Transferts anormalement lents

**Diagnostic**:
```bash
# Mesurer le débit réseau
iperf3 -c pesit-server -p 5201

# Vérifier la latence
ping -c 10 pesit-server

# Vérifier l'utilisation CPU/mémoire
docker stats pw-client pw-server
```

**Optimisations**:
```yaml
# Augmenter la taille des chunks
pesitwizard:
  transfer:
    chunk-size: 32768      # 32KB au lieu de 4KB
    max-entity-size: 65535 # Maximum PeSIT
```

### MEMORY_LEAK

**Symptôme**: Mémoire qui augmente continuellement

**Diagnostic**:
```bash
# Surveiller la mémoire Java
jcmd $(pgrep -f pesitwizard) VM.native_memory summary

# Heap dump pour analyse
jmap -dump:format=b,file=heap.hprof $(pgrep -f pesitwizard)
```

---

## Logs et diagnostics

### Activer les logs DEBUG

```yaml
# application.yml
logging:
  level:
    com.pesitwizard: DEBUG
    com.pesitwizard.fpdu: TRACE  # Détail des FPDU
    com.pesitwizard.session: DEBUG
```

### Analyser les logs

```bash
# Filtrer les erreurs
grep -E "ERROR|WARN" /var/log/pesitwizard/*.log

# Suivre un transfert spécifique
grep "transfer-id-xxx" /var/log/pesitwizard/*.log

# Analyser les FPDU
grep "FPDU" /var/log/pesitwizard/*.log | tail -50
```

### Logs Docker

```bash
# Logs en temps réel
docker logs -f pw-client

# Logs avec timestamps
docker logs --timestamps pw-client 2>&1 | tail -100

# Filtrer par pattern
docker logs pw-client 2>&1 | grep -i error
```

---

## Codes diagnostic PeSIT

### Session (0x01xxxx)

| Code | Description |
|------|-------------|
| `0x010101` | Partenaire inconnu |
| `0x010102` | Mot de passe incorrect |
| `0x010103` | Partenaire désactivé |
| `0x010104` | Nombre max de sessions atteint |
| `0x010105` | Type d'accès non autorisé |

### Fichier (0x02xxxx)

| Code | Description |
|------|-------------|
| `0x020201` | Fichier virtuel inconnu |
| `0x020202` | Direction non autorisée |
| `0x020203` | Fichier désactivé |
| `0x020204` | Fichier déjà ouvert |

### Transfert (0x03xxxx)

| Code | Description |
|------|-------------|
| `0x030301` | Erreur de format |
| `0x030302` | Taille dépassée |
| `0x030303` | Sync point refusé |
| `0x030304` | Transfert annulé |

---

## Outils de diagnostic

### Test de connectivité complet

```bash
#!/bin/bash
# test-pesit-connectivity.sh

HOST=$1
PORT=${2:-5000}

echo "=== Test de connectivité PeSIT ==="
echo "Host: $HOST"
echo "Port: $PORT"
echo ""

echo "1. DNS resolution..."
nslookup $HOST || echo "FAIL: DNS"

echo ""
echo "2. TCP connectivity..."
nc -zv $HOST $PORT && echo "OK: TCP" || echo "FAIL: TCP"

echo ""
echo "3. TLS handshake..."
timeout 5 openssl s_client -connect $HOST:$PORT < /dev/null 2>/dev/null && \
  echo "OK: TLS" || echo "INFO: TLS not enabled or failed"

echo ""
echo "4. Certificate check..."
echo | openssl s_client -connect $HOST:$PORT 2>/dev/null | \
  openssl x509 -noout -dates 2>/dev/null || echo "INFO: No certificate"
```

### Capture réseau

```bash
# Capturer le trafic PeSIT (non-TLS)
tcpdump -i any port 5000 -w pesit-capture.pcap

# Analyser avec Wireshark
wireshark pesit-capture.pcap
```
