# What is PeSIT Wizard?

**PeSIT Wizard** is a modern, fully open-source implementation of the **PeSIT E** file-transfer
protocol, written in Rust. It ships as a single self-contained node — the `pesitwizard` binary — that
both **listens** for incoming transfers and **initiates** outgoing ones, with a built-in web console
and a REST API.

## The PeSIT protocol

PeSIT (*Protocole d'Échange pour un Système Interbancaire de Télécompensation*) is the standard
file-transfer protocol used by French banks and on the SIT network. PeSIT Wizard implements **PeSIT
E** — the TLS-capable version — and is validated for interoperability against IBM Sterling
Connect:Express 1.5.

See the [protocol reference](/guide/reference/protocol) for the full feature list.

## One node

A single process does everything:

- **Listens** for partners connecting in and receives or serves files (the server role).
- **Initiates** outgoing transfers to remote servers (the client role).
- **Serves** a web console at `/` and two REST surfaces backed by one shared store.

There is no separate server, client or admin console to install, and no commercial edition — every
feature is in the one binary under Apache-2.0.

## What it can do

- Full **PeSIT E**: CRC, compression, segmentation, synchronisation points with windowing, restart /
  resynchronisation, text / binary record formats, EBCDIC translation, TLS and pre-connection.
- **Partners**, **virtual files** and **listeners** configured over REST or the console.
- **Storage connectors** (S3 / SFTP / local) backing virtual files.
- **Certificates & PKI**: a local CA, native HashiCorp Vault PKI, rotation, CRL and an OCSP responder.
- **Clustering** over NATS / JetStream with configuration replication and distributed schedules.
- **Operations**: Prometheus metrics, health probes, an audit log, and signed configuration backups.

## Next steps

- [Quick start](/guide/quickstart) — run a node and configure your first transfer in minutes.
- [Architecture](/guide/architecture) — how the node is built.
- [The node](/guide/server/installation) — running and operating it.
