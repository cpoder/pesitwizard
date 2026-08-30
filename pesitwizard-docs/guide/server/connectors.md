# Storage connectors

A virtual file is normally backed by the local filesystem. A **connector** lets it be backed by an
external storage system instead: a file received over PeSIT is written to the connector, and a file
to send is read from it. Managed in the **Connectors** tab or under `/api/v1/config/connectors`.

![Connectors](/screenshots/connectors.png)

## Connector types

| Type    | Backend                                             |
|---------|-----------------------------------------------------|
| `s3`    | Any S3-compatible object store (AWS S3, MinIO, …)   |
| `sftp`  | An SFTP server                                       |
| `local` | A directory on the node                              |

```json
{ "id": "s3", "type": "s3", "bucket": "pesit", "region": "eu-west-1",
  "endpoint": "http://minio:9000", "accessKey": "…", "secretKey": "…", "pathStyle": true }
```

Every connector may set `maxRetries` (default 3): a transient fetch / store failure is retried with
exponential backoff. `POST /api/v1/config/connectors/{id}/test` checks reachability.

## Backing a virtual file

Set `connector` to a connector id and `connectorPath` to a target-path template on the virtual file:

```json
{ "id": "INVOICES", "enabled": true, "direction": "RECEIVE",
  "connector": "s3", "connectorPath": "incoming/${transferId}.dat" }
```

- **Receive**: the data is staged to a local working file; on completion it is uploaded to the
  connector and the staging file is removed.
- **Send**: the object is fetched from the connector into a staging file, streamed to the partner, and
  the staging file is removed.

Staging keeps checkpoint / restart, CRC and record-format handling exactly as for a local file.
Connectors are included in configuration backup / restore, but stay **node-local** (their credentials
are not replicated across a cluster).
