# Deployment

The node is a single binary / container. Persist the database and checkpoint directories on a volume,
and expose the admin, transfer and listener ports.

## Docker

```bash
docker run -d --name pesitwizard \
  -p 8080:8080 -p 9081:9081 -p 5001:5001 \
  -e PESIT_API_KEY=secret \
  -e PESIT_DB=/data/pesitwizard.db \
  -v pesit-data:/data \
  ghcr.io/pesitwizard/pesitwizard:latest
```

## Kubernetes

Deploy it as a normal `Deployment` + `Service`, with a `PersistentVolumeClaim` for `/data` and the
built-in probes:

```yaml
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
```

Scrape `/metrics` with Prometheus (no API key). For a highly-available deployment, run several
replicas joined to a [NATS cluster](/guide/server/clustering) so configuration replicates and
schedules are distributed; listener ports and connector credentials stay per-pod.

## Configuration as code

Mount a `PESIT_CONFIG` YAML file (partners, virtual files, remote partners, listeners). It is applied
at start-up and re-applied on `SIGHUP`, so a `kubectl rollout restart` or a config-map change can
refresh configuration without losing the node's state.
