# Architecture

PeSIT Wizard is a single Rust process — the `pesitwizard` binary — organised as a small workspace of
crates.

## Crates

| Crate | Responsibility |
|-------|----------------|
| `pesit-core` | Protocol model: parameters, FPDUs, CRC, compression, article codecs, state tables (no I/O). |
| `pesit-io` | The async session engines (requester / responder) over tokio. |
| `pesit-app` | The embedded configuration store and REST utilities. |
| `pesit-client` | The outbound transfer engine (as a library). |
| `pesit-pki` | X.509 inspection, a local CA, native Vault PKI, OCSP and backup signing. |
| `pesit-cluster` | NATS / JetStream membership, leader election and configuration replication. |
| `pesit-connector` | S3 / SFTP / local storage staging. |
| `pesit-node` | The `pesitwizard` binary: listeners + outbound engine + REST APIs + web console. |

## One process, two REST surfaces

The node runs the listener manager and the outbound transfer engine over **one shared store**, and
exposes two REST surfaces:

- the **admin API** on `--api-port` (partners, virtual files, listeners, inbound records,
  certificates, connectors, schedules, cluster, audit, backup — and the web console at `/`),
- the **transfer API** on `--transfer-port` (remote servers, send / receive / message, outbound
  history).

The transfer API is also mounted under `/client` on the admin port, so the single-origin web console
reaches both. Unauthenticated operational endpoints (`/actuator/health`, `/metrics`, `/ocsp`) sit on
the admin port.

## State machine

The PeSIT session engines are driven by the protocol **state tables as data** (the same shape as the
Connect:Express tables), shared by the listening and initiating roles and run by an event loop over
network and application events. This is what lets the node handle RESYN / IDT collisions, windowed
synchronisation and restart correctly.

## Storage

Configuration lives in an embedded store (`--db`). Checkpoints for restart are persisted per transfer
under the checkpoint directories. Connector-backed virtual files stage to a local working file so
checkpoint / restart, CRC and record formats keep working.
