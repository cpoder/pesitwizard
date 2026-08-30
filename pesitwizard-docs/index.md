---
layout: home

hero:
  name: PeSIT Wizard
  text: Open-source PeSIT E file transfer
  tagline: A single self-contained node that both listens and initiates PeSIT transfers — with a built-in web console, REST API, clustering, PKI and storage connectors. Apache-2.0.
  actions:
    - theme: brand
      text: Quick start
      link: /guide/quickstart
    - theme: alt
      text: Guide
      link: /guide/
    - theme: alt
      text: GitHub
      link: https://github.com/pesitwizard/pesitwizard-rs

features:
  - icon: 🧩
    title: One self-contained node
    details: A single binary that listens for incoming transfers and initiates outgoing ones, with the web console built in. Run it directly, as a container, or on Kubernetes.
  - icon: 🔁
    title: Full PeSIT E protocol
    details: CRC, compression, article segmentation, synchronisation points with windowing, restart / resynchronisation, EBCDIC translation and TLS. Validated against IBM Sterling Connect:Express.
  - icon: 🖥️
    title: Built-in web console
    details: Configure listeners, partners, virtual files, connectors and certificates, and follow transfers live — served by the node itself, no separate front-end to host.
  - icon: 🔐
    title: Certificates & PKI
    details: A local CA, native HashiCorp Vault PKI, certificate rotation, CRL and an online OCSP responder — all in the box.
  - icon: 🗄️
    title: Storage connectors
    details: Back a virtual file with S3, SFTP or a local directory. Received files are staged and uploaded; sent files are fetched and streamed.
  - icon: 🌐
    title: Clustering over NATS
    details: Run several nodes as a cluster over NATS / JetStream — live configuration replication, leader election and schedules distributed across members.
---

## Why PeSIT Wizard?

The **PeSIT** protocol (*Protocole d'Échange pour un Système Interbancaire de Télécompensation*) is the
standard used by French banks for secure file exchanges. **PeSIT Wizard** is a modern implementation
of **PeSIT E**, written in Rust and **fully open source** under Apache-2.0 — no editions, no license
keys, every feature in one binary.

It is a drop-in alternative to legacy solutions such as Axway CFT / IBM Sterling Connect:Express: it
speaks the same protocol on the wire (validated against Connect:Express 1.5), exposes everything over
a REST API and a built-in web console, and runs anywhere a single binary or container can.

## Use cases

- **Automated transfers** — push your SEPA payment files to your bank from your ERP, on a schedule.
- **Statement retrieval** — pull your account statements every morning into your accounting system.
- **Multi-bank centralisation** — manage all your exchanges with several banks from one node.

## One node, no split

Earlier releases shipped a separate server, client and admin console. PeSIT Wizard is now a **single
`pesitwizard` node** that both listens and initiates, exposes two REST surfaces backed by one store,
and serves its own web console. There is nothing else to deploy.

<div style="text-align: center; margin-top: 2rem;">
  <a href="/guide/quickstart" style="display: inline-block; padding: 12px 24px; background: #3451b2; color: white; border-radius: 8px; text-decoration: none; font-weight: 500; margin-right: 1rem;">
    Quick start
  </a>
  <a href="https://github.com/pesitwizard/pesitwizard-rs" style="display: inline-block; padding: 12px 24px; background: #24292e; color: white; border-radius: 8px; text-decoration: none; font-weight: 500;">
    GitHub
  </a>
</div>
