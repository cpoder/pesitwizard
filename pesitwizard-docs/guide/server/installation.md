# The node

PeSIT Wizard is a single process — the `pesitwizard` binary — that both **listens** for incoming
PeSIT E transfers and **initiates** outgoing ones, over one shared configuration store, with the web
console built in.

## Running it

```bash
PESIT_API_KEY=secret pesitwizard        # default subcommand: serve
```

Or as a container:

```bash
docker run -p 8080:8080 -p 9081:9081 -p 5001:5001 \
  -e PESIT_API_KEY=secret ghcr.io/pesitwizard/pesitwizard:latest
```

## Two REST surfaces

Both are backed by the same store:

- **Admin API** on `--api-port` (default 8080, guarded by `X-API-Key`): partners, virtual files,
  listeners (`/api/v1/servers` + `/start|/stop|/status`), inbound transfer records, certificates,
  connectors, schedules, cluster, audit and backup — and the **web console at `/`**. The transfer API
  is also mounted here under `/client`.
- **Transfer API** on `--transfer-port` (default 9081): remote servers (`/api/v1/servers`),
  `/api/v1/transfers/send|receive|message`, outbound history, and `/{id}/cancel|retry`.

Unauthenticated operational endpoints on the admin port: `/actuator/health` (+ `/liveness`,
`/readiness`), `/metrics` (Prometheus) and `/ocsp` (OCSP responder).

## Configuration

| Flag / env | Purpose |
|---|---|
| `--api-port` / `PESIT_API_PORT` | Admin API + console port (default 8080) |
| `--transfer-port` / `PESIT_TRANSFER_PORT` | Transfer API port (default 9081) |
| `--api-key` / `PESIT_API_KEY` | Admin API key (unset = no authentication) |
| `--db` / `PESIT_DB` | Configuration database path |
| `--config` / `PESIT_CONFIG` | YAML bootstrap file, re-applied on `SIGHUP` |
| `--cluster-nats` / `PESIT_CLUSTER_NATS` | NATS URL to join a cluster |
| `--cert-rotation-days` / `PESIT_CERT_ROTATION_DAYS` | Auto-rotate keystores this many days before expiry |
| `--audit-max-entries` / `PESIT_AUDIT_MAX_ENTRIES` | Audit-log retention cap (default 50 000) |

See [Configuration](/guide/server/configuration) for partners, virtual files and listeners.

## Graceful shutdown

On `SIGTERM` or `Ctrl-C` the HTTP servers stop accepting and let in-flight requests finish before the
process exits, then listeners are stopped — so a rolling restart or a Kubernetes pod eviction does not
abruptly cut the admin API.
