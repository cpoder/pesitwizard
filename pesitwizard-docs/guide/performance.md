# Performance et Tuning

Guide d'optimisation des performances pour PeSIT Wizard.

## Métriques de référence

### Benchmarks typiques

| Scénario | Taille | Débit attendu | Latence |
|----------|--------|---------------|---------|
| LAN (1Gbps) | 100MB | 80-100 MB/s | < 1s setup |
| WAN (100Mbps) | 100MB | 10-12 MB/s | 50-200ms RTT |
| Internet | 100MB | 1-5 MB/s | Variable |

### Facteurs limitants

1. **Bande passante réseau** - Principal facteur
2. **Latence réseau** - Impact sur le handshake et sync points
3. **Taille des chunks** - Overhead par FPDU
4. **CPU** - Chiffrement TLS, checksums
5. **I/O disque** - Lecture/écriture fichiers

---

## Configuration optimale

### Taille des chunks (PI_25)

La taille maximale des FPDU impacte directement les performances:

```yaml
# application.yml
pesitwizard:
  transfer:
    max-entity-size: 65535  # Maximum PeSIT (64KB - 1)
    chunk-size: 32768       # 32KB par défaut
```

| Taille | Overhead | Recommandation |
|--------|----------|----------------|
| 4096 | ~0.15% | Compatibilité anciens systèmes |
| 16384 | ~0.04% | Standard |
| 32768 | ~0.02% | Recommandé |
| 65535 | ~0.01% | Performance maximale |

**Note**: La valeur effective est négociée avec le partenaire (minimum des deux).

### Sync Points

Les sync points permettent la reprise mais ajoutent de l'overhead:

```yaml
pesitwizard:
  transfer:
    sync-points-enabled: true
    sync-point-interval: 256  # KB entre sync points
```

| Intervalle | Overhead | Cas d'usage |
|------------|----------|-------------|
| 10 KB | ~10% | Réseau très instable |
| 100 KB | ~1% | Standard |
| 256 KB | ~0.4% | Recommandé production |
| 1024 KB | ~0.1% | Réseau fiable |
| Désactivé | 0% | Réseau très fiable, petits fichiers |

### Buffer sizes

```yaml
pesitwizard:
  transfer:
    read-buffer-size: 65536   # Buffer lecture fichier
    write-buffer-size: 65536  # Buffer écriture fichier
    socket-buffer-size: 65536 # Buffer socket TCP
```

---

## Configuration JVM

### Heap memory

```bash
# Pour des transferts volumineux
JAVA_OPTS="-Xms512m -Xmx2g"

# Pour des petits fichiers nombreux
JAVA_OPTS="-Xms256m -Xmx1g"
```

**Règle générale**:
- Heap min: 256MB
- Heap max: 2-4GB selon volume

### Garbage Collector

```bash
# G1GC recommandé pour latence prévisible
JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# ZGC pour très faible latence (Java 17+)
JAVA_OPTS="-XX:+UseZGC"
```

### Threads

```yaml
# Nombre de threads pour transferts concurrents
pesitwizard:
  transfer:
    executor:
      core-pool-size: 4    # Threads permanents
      max-pool-size: 20    # Maximum
      queue-capacity: 100  # File d'attente
```

**Dimensionnement**:
- `core-pool-size`: Nombre de transferts simultanés typiques
- `max-pool-size`: Pic de charge
- `queue-capacity`: Buffer pour pics courts

---

## Optimisation réseau

### TCP Tuning (Linux)

```bash
# /etc/sysctl.conf

# Augmenter les buffers TCP
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216

# Activer le window scaling
net.ipv4.tcp_window_scaling = 1

# Réduire le TIME_WAIT
net.ipv4.tcp_fin_timeout = 30

# Appliquer
sysctl -p
```

### Timeouts

```yaml
pesitwizard:
  transfer:
    connection-timeout: 30000   # 30s pour établir la connexion
    read-timeout: 120000        # 2min pour lire des données
    write-timeout: 120000       # 2min pour écrire
```

**Pour fichiers volumineux** (> 1GB):
```yaml
pesitwizard:
  transfer:
    read-timeout: 600000   # 10 minutes
    write-timeout: 600000
```

---

## Optimisation TLS

### Cipher suites rapides

```yaml
pesit:
  ssl:
    protocol: TLSv1.3
    cipher-suites:
      - TLS_AES_256_GCM_SHA384       # Plus rapide avec AES-NI
      - TLS_AES_128_GCM_SHA256
      - TLS_CHACHA20_POLY1305_SHA256 # Rapide sans AES-NI
```

### Session caching

Le TLS session caching réduit l'overhead des handshakes répétés:

```yaml
pesit:
  ssl:
    session-cache-size: 1000
    session-timeout: 86400  # 24h
```

### Vérifier AES-NI

```bash
# Vérifier le support CPU
grep -o aes /proc/cpuinfo | head -1

# Si présent, AES-GCM sera accéléré matériellement
```

---

## Monitoring des performances

### Métriques Micrometer

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,metrics
  metrics:
    tags:
      application: pesitwizard
```

### Métriques clés

| Métrique | Description | Alerte si |
|----------|-------------|-----------|
| `pesit.transfer.duration` | Durée des transferts | > 300s pour 100MB |
| `pesit.transfer.throughput` | Débit en bytes/s | < 1 MB/s LAN |
| `jvm.memory.used` | Mémoire heap | > 80% max |
| `jvm.gc.pause` | Pauses GC | > 500ms |
| `system.cpu.usage` | CPU système | > 80% |

### Dashboard Grafana

```json
{
  "panels": [
    {
      "title": "Transfer Throughput",
      "targets": [{
        "expr": "rate(pesit_transfer_bytes_total[5m])"
      }]
    },
    {
      "title": "Transfer Duration (p95)",
      "targets": [{
        "expr": "histogram_quantile(0.95, rate(pesit_transfer_duration_seconds_bucket[5m]))"
      }]
    },
    {
      "title": "Active Transfers",
      "targets": [{
        "expr": "pesit_transfers_active"
      }]
    }
  ]
}
```

---

## Benchmark de performance

### Script de benchmark

```bash
#!/bin/bash
# benchmark.sh - Test de performance PeSIT Wizard

API_URL=${1:-http://localhost:9081}
SERVER=${2:-cx-server}
PARTNER=${3:-PWSRV01}
SIZES="1M 10M 100M"
ITERATIONS=3

echo "=== PeSIT Wizard Benchmark ==="
echo "Server: $SERVER"
echo "Partner: $PARTNER"
echo ""

for size in $SIZES; do
  echo "--- Testing $size file ---"

  # Créer fichier test
  dd if=/dev/urandom of=/tmp/bench_$size.dat bs=$size count=1 2>/dev/null
  FILESIZE=$(stat -c%s /tmp/bench_$size.dat)

  total_time=0

  for i in $(seq 1 $ITERATIONS); do
    start=$(date +%s.%N)

    result=$(curl -s -X POST "$API_URL/api/v1/transfers/send" \
      -H "Content-Type: application/json" \
      -d "{
        \"server\": \"$SERVER\",
        \"partnerId\": \"$PARTNER\",
        \"filename\": \"/tmp/bench_$size.dat\",
        \"remoteFilename\": \"PWRECV\"
      }")

    id=$(echo $result | jq -r '.transferId // .id')

    # Attendre la fin
    while true; do
      status=$(curl -s "$API_URL/api/v1/transfers/$id" | jq -r '.status')
      [ "$status" = "COMPLETED" ] || [ "$status" = "FAILED" ] && break
      sleep 0.5
    done

    end=$(date +%s.%N)
    duration=$(echo "$end - $start" | bc)
    total_time=$(echo "$total_time + $duration" | bc)

    throughput=$(echo "scale=2; $FILESIZE / $duration / 1048576" | bc)
    echo "  Run $i: ${duration}s (${throughput} MB/s)"
  done

  avg=$(echo "scale=2; $total_time / $ITERATIONS" | bc)
  avg_throughput=$(echo "scale=2; $FILESIZE / $avg / 1048576" | bc)
  echo "  Average: ${avg}s (${avg_throughput} MB/s)"
  echo ""

  rm /tmp/bench_$size.dat
done
```

### Résultats attendus

| Taille | LAN 1Gbps | WAN 100Mbps | Internet |
|--------|-----------|-------------|----------|
| 1 MB | < 0.5s | < 1s | 1-5s |
| 10 MB | < 1s | 2-3s | 10-30s |
| 100 MB | 2-5s | 15-30s | 2-5min |
| 1 GB | 15-30s | 3-5min | 20-60min |

---

## Optimisations avancées

### Compression (si supportée)

```yaml
pesitwizard:
  transfer:
    compression:
      enabled: true
      algorithm: GZIP
      level: 6  # 1-9, compromise vitesse/ratio
```

**Note**: La compression PeSIT n'est pas toujours supportée par les partenaires.

### Connection pooling

Pour de nombreux petits transferts vers le même serveur:

```yaml
pesitwizard:
  connection:
    pool:
      enabled: true
      max-connections-per-host: 10
      idle-timeout: 300000  # 5 minutes
```

### Parallel transfers

Pour transférer plusieurs fichiers en parallèle:

```bash
# Via API - les transferts sont naturellement parallèles
for file in /data/outbox/*.dat; do
  curl -X POST "$API_URL/api/v1/transfers/send" \
    -H "Content-Type: application/json" \
    -d "{...}" &
done
wait
```

---

## Limites connues

| Limite | Valeur | Contournement |
|--------|--------|---------------|
| Taille max fichier | Illimitée* | Utiliser sync points |
| FPDU max | 65535 bytes | Limitation PeSIT |
| Sessions simultanées | ~100 | Configurable |
| Mémoire par transfert | ~1-2 MB | Streaming, pas de buffer complet |

*Les fichiers de plusieurs GB ont été testés avec succès.

## Checklist performance

- [ ] `max-entity-size` configuré à 32768 ou plus
- [ ] `sync-point-interval` adapté au réseau (256KB recommandé)
- [ ] JVM heap dimensionné correctement
- [ ] TCP buffers augmentés (Linux)
- [ ] TLS avec cipher AES-GCM (si AES-NI disponible)
- [ ] Monitoring Prometheus/Grafana en place
- [ ] Benchmarks de référence établis
