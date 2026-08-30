# REST API

Everything the console does is a REST call. The node exposes two surfaces, both backed by the same
store. Responses are JSON.

## Authentication

The **admin API** is guarded by an API key: send `X-API-Key: <PESIT_API_KEY>` on every request. If
`PESIT_API_KEY` is unset the admin API is open (development only). The operational endpoints
(`/actuator/health`, `/metrics`, `/ocsp`) and the transfer API need no key.

## Admin API — `--api-port` (default 8080)

| Method & path | Purpose |
|---|---|
| `GET/POST /api/v1/config/partners` · `…/{id}` | Partners (PI 3) |
| `GET/POST /api/v1/config/files` · `…/{id}` | Virtual files (PI 12) |
| `GET/POST /api/v1/config/remote-partners` · `…/{id}` | Remote partner policy |
| `GET/POST /api/v1/config/connectors` · `…/{id}` · `…/{id}/test` | Storage connectors |
| `GET/POST /api/v1/servers` · `…/{id}/start\|stop\|status` | Listeners |
| `GET /api/v1/transfers` · `…/{id}` · `…/active` · `…/stats` | Inbound transfer records |
| `GET/POST /api/v1/certificates/*` | Keystores, truststores, local CA, Vault, rotation, CRL |
| `GET/POST /api/v1/schedules` · `…/{id}` · `…/{id}/run` | Scheduled transfers |
| `GET /api/v1/cluster` · `…/transfers` | Cluster membership and aggregated history |
| `GET /api/v1/audit` · `GET /api/v1/backup` · `POST /api/v1/backup/restore` | Audit log, backup / restore |

The transfer API below is also mounted here under `/client/api/v1/...` for the single-origin console.

## Transfer API — `--transfer-port` (default 9081)

| Method & path | Purpose |
|---|---|
| `GET/POST /api/v1/servers` · `…/{id}` | Remote servers to connect out to |
| `POST /api/v1/transfers/send\|receive\|message` | Start an outgoing transfer or message |
| `GET /api/v1/transfers` · `…/{id}` | Outbound history |
| `POST /api/v1/transfers/{id}/cancel\|retry` | Cancel or restart a transfer |

## Operational endpoints (unauthenticated, admin port)

| Path | Purpose |
|---|---|
| `GET /actuator/health` (+ `/liveness`, `/readiness`) | Kubernetes probes |
| `GET /metrics` | Prometheus metrics |
| `POST /ocsp` · `GET /ocsp/{b64}` | OCSP responder (RFC 6960) |

## Example

```bash
KEY=secret; A=http://localhost:8080
# create a partner
curl -s -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  "$A/api/v1/config/partners" -X POST -d '{"id":"BANK_A","enabled":true,"accessType":"BOTH"}'
# start an outgoing transfer (transfer API mounted under /client)
curl -s -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  "$A/client/api/v1/transfers/send" -X POST \
  -d '{"server":"bank-a","partnerId":"PWSRV01","filename":"/data/send/f.dat","remoteFilename":"PAYMENTS"}'
```
