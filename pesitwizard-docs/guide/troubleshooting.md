# Troubleshooting

## The console is empty / 401

Paste the API key (the value of `PESIT_API_KEY`) in the field at the top right of the console. The
admin API returns `401` without a valid `X-API-Key`. The health, metrics and OCSP endpoints need no
key.

## A partner cannot connect

- Check the listener is **running** (Listeners tab / `GET /api/v1/servers/{id}/status`) and its port
  is reachable.
- Check the **partner** exists and its `accessType` allows the direction.
- If the partner uses TLS, check `sslEnabled` and the keystore / truststore, and whether the peer
  expects the transport-length header (`tcpipHeader`).
- Connect:Express partners of type T / O need `preconnectId` / `preconnectPassword`.

## A transfer fails or hangs

- Look at the **Transfers** tab / `GET /api/v1/transfers/{id}` for the diagnostic. PeSIT diagnostics
  carry a code and text (Annex D).
- A fixed-format (`BF`) file whose size is not a multiple of the record length is rejected as an
  unfilled record — set the right `recordLength` or use `BU`.
- For a connector-backed virtual file, a transient S3 / SFTP error is retried (`maxRetries`); a
  persistent one fails the transfer with the connector's error.

## Received text files lose their newlines / look wrong

Set `text: true` on the virtual file for line records, and `ebcdic: true` if the peer sends EBCDIC
data.

## Restart against Connect:Express does a full retransfer

That is expected: Connect:Express refuses a remote-driven reprise of a CREATE (D2-204), so the node
falls back to a full retransfer. See [Connect:Express interop](/guide/connect-express).

## Metrics / health for monitoring

Scrape `/metrics` (Prometheus) and probe `/actuator/health` — both unauthenticated. See
[Observability](/guide/server/observability).
