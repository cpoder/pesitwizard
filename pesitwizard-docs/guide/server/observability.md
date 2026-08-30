# Observability

## Health probes

Unauthenticated Kubernetes-ready endpoints on the admin port:

- `GET /actuator/health` — overall health,
- `GET /actuator/health/liveness` — liveness probe,
- `GET /actuator/health/readiness` — readiness probe.

## Prometheus metrics

`GET /metrics` (unauthenticated) exposes Prometheus metrics derived from the store on each scrape:

```
pesitwizard_build_info{version="…"} 1
pesitwizard_transfers_total{kind="inbound|outbound",status="…"}  …
pesitwizard_bytes_transferred_total{kind="inbound|outbound"}     …
pesitwizard_listeners / pesitwizard_listeners_up                 …
pesitwizard_partners / pesitwizard_virtual_files                 …
```

Scrape it like any Prometheus target — no API key required.

## Audit log

Every configuration change, listener start / stop, certificate and Vault operation and transfer
outcome is recorded in an append-only audit log, visible in the **System** tab and under
`/api/v1/audit`. Retention is bounded by `PESIT_AUDIT_MAX_ENTRIES` (default 50 000; the oldest entries
are pruned).

![System](/screenshots/system.png)

## Backup & restore

Export the whole configuration — partners, virtual files, listeners, remote servers, connectors,
schedules and certificate material — as one JSON bundle, and import it back:

- From the **System** tab, or `/api/v1/backup` (export) and `/api/v1/backup/restore` (import).
- Offline, straight on the database, with the CLI:

  ```bash
  pesitwizard backup export --out backup.json
  pesitwizard backup import --file backup.json --db /path/to/new.sqlite
  ```

When a local CA is configured, bundles are **signed** with the CA key (ECDSA P-256) and the signature
is verified on import — a tampered bundle is rejected.
