---
layout: home

hero:
  name: PeSIT Wizard
  text: PeSIT Bank Transfers
  tagline: Open source solution to automate your file exchanges with banks.
  actions:
    - theme: brand
      text: Get Started
      link: /guide/quickstart
    - theme: alt
      text: GitHub
      link: https://github.com/pesitwizard/pesitwizard

features:
  - icon: 📡
    title: PeSIT Protocol
    details: Complete implementation of PeSIT D and E protocol for bank exchanges.
  - icon: ⚡
    title: Easy to Deploy
    details: Simple configuration, complete documentation. No PeSIT expert needed.
  - icon: 🔌
    title: REST API
    details: Easily integrate your ERP and accounting software via our REST API.
  - icon: 🐳
    title: Docker Ready
    details: Docker images available for rapid deployment.
  - icon: 🔒
    title: Secure
    details: TLS 1.3, certificate authentication, end-to-end encryption.
  - icon: 📖
    title: Open Source
    details: Apache 2.0 License. Source code available on GitHub.
---

## Why PeSIT Wizard?

The **PeSIT** protocol (Protocole d'Echange pour un Systeme Interbancaire de Telecompensation) is the standard used by French banks for secure file exchanges.

**PeSIT Wizard** is a modern open source implementation of the PeSIT protocol:
- **Free**: Apache 2.0 License
- **Simple**: Complete documentation, REST API
- **Modern**: Java 21, Spring Boot, Docker

## Use Cases

### Automated Transfers
Automatically send your SEPA transfer files to your bank from your ERP.

### Statement Retrieval
Automatically retrieve your account statements each morning to integrate them into your accounting system.

### Multi-Bank Centralization
Manage all your exchanges with multiple banks from a single interface.

## Components

| Module | Description |
|--------|-------------|
| **pesitwizard-server** | Complete PeSIT server |
| **pesitwizard-client** | Java client for sending/receiving files |
| **pesitwizard-client-ui** | Graphical interface for the client |
| **pesitwizard-pesit** | Protocol implementation library |

---

<div style="text-align: center; margin-top: 2rem;">
  <a href="/guide/quickstart" style="display: inline-block; padding: 12px 24px; background: #3451b2; color: white; border-radius: 8px; text-decoration: none; font-weight: 500; margin-right: 1rem;">
    Documentation
  </a>
  <a href="https://github.com/pesitwizard/pesitwizard" style="display: inline-block; padding: 12px 24px; background: #24292e; color: white; border-radius: 8px; text-decoration: none; font-weight: 500;">
    GitHub
  </a>
</div>
