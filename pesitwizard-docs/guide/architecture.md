# Architecture

## Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        PeSIT Wizard                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│  │ Client UI    │     │ Admin UI     │     │ Server       │    │
│  │ (Vue.js)     │     │ (Vue.js)     │     │ (Spring Boot)│    │
│  └──────┬───────┘     └──────┬───────┘     └──────┬───────┘    │
│         │                    │                    │             │
│         ▼                    ▼                    ▼             │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│  │ Client API   │     │ Admin API    │     │ PeSIT Wizard │    │
│  │ (Spring Boot)│     │ (Spring Boot)│     │ Protocol     │    │
│  │ Port 8080    │     │ Port 8080    │     │ Port 6502    │    │
│  └──────┬───────┘     └──────┬───────┘     └──────┬───────┘    │
│         │                    │                    │             │
│         └────────────────────┼────────────────────┘             │
│                              │                                  │
│                              ▼                                  │
│                       ┌──────────────┐                          │
│                       │ PostgreSQL   │                          │
│                       │ Database     │                          │
│                       └──────────────┘                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Components

### PeSIT Wizard Client

The **client** allows sending and receiving files to/from external PeSIT Wizard servers (banks).

| Component | Description | Port |
|-----------|-------------|------|
| pesitwizard-client | Spring Boot backend | 8080 |
| pesitwizard-client-ui | Vue.js interface | 3001 |

**Features**:
- File sending (payments, direct debits)
- File receiving (statements, notices)
- Transfer history
- Multi-server configuration

### PeSIT Wizard Server

The **server** allows receiving files from external partners.

| Component | Description | Port |
|-----------|-------------|------|
| pesitwizard-server | PeSIT Server + API | 6502 (PeSIT), 5001 (PeSIT TLS), 8080 (HTTP) |

**Features**:
- File receiving
- File sending (on demand)
- Partner management
- Virtual files
- High-availability clustering

## Kubernetes Deployment

```
┌─────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    Namespace: pesitwizard            │    │
│  │                                                      │    │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐             │    │
│  │  │ Pod 1   │  │ Pod 2   │  │ Pod 3   │             │    │
│  │  │ (Leader)│  │         │  │         │             │    │
│  │  └────┬────┘  └─────────┘  └─────────┘             │    │
│  │       │                                             │    │
│  │       │ pesitwizard-leader=true                     │    │
│  │       ▼                                             │    │
│  │  ┌─────────────────────────────────────────┐        │    │
│  │  │         LoadBalancer Service             │        │    │
│  │  │  (selector: pesitwizard-leader=true)     │        │    │
│  │  └─────────────────────────────────────────┘        │    │
│  │                                                      │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### High Availability

- **3 replicas** by default
- **Leader election** via JGroups
- **Automatic labeling** of the leader pod
- **LoadBalancer** routes to the leader only

## Database

Each PeSIT Wizard cluster has its own PostgreSQL schema:

```
pesitwizard (database)
├── admin (schema)
│   ├── pesitwizard_clusters
│   ├── container_registries
│   └── container_orchestrators
│
└── cluster_<uuid> (schema)
    ├── pesitwizard_server_configs
    ├── partners
    ├── virtual_files
    └── transfer_history
```

## Data Flows

### Sending a File (Client to Bank)

```
1. User uploads file via UI
2. Client API stores the file temporarily
3. Client API opens PeSIT connection to the bank
4. CONNECT/ACONNECT exchange
5. CREATE/ACK exchange
6. Data transfer (DTF)
7. Close (DESELECT, RELEASE)
8. History updated in database
```

### Receiving a File (Bank to Client)

```
1. User requests file via UI
2. Client API opens PeSIT connection to the bank
3. CONNECT/ACONNECT exchange (read mode)
4. SELECT/ACK exchange
5. Data reception (DTF)
6. File stored locally
7. History updated in database
```
