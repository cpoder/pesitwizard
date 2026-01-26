# PeSIT Wizard Integration Tests

Complete integration testing environment for PeSIT Wizard.

## Quick Start - WSL2 / Docker (Recommended)

> **Best option for Windows/WSL2** - uses k3d (k3s in Docker)

**Prerequisites:** Docker Desktop with WSL2 backend

```bash
cd integration-tests/scripts

# 1. Setup k3d cluster (one-time)
./setup-k3d.sh

# 2. Build and import images
./build-images.sh

# 3. Deploy the stack
./deploy-k3d.sh

# 4. Run tests
cd ../tests
./run-all-tests.sh

# Cleanup when done
cd ../scripts
./cleanup-k3d.sh
```

## Alternative - Vagrant (Linux/macOS native only)

> ⚠️ **Vagrant + VirtualBox doesn't work on WSL2** (nested virtualization)

```bash
vagrant up && vagrant ssh
cd /home/vagrant/pesitwizard/integration-tests/tests
./run-all-tests.sh
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  k3d Cluster (Docker)                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  PostgreSQL │  │   PeSIT     │  │      PeSIT          │  │
│  │   Database  │◄─┤   Server    │◄─┤      Client         │  │
│  │             │  │  :30080 API │  │     :30081 API      │  │
│  │             │  │  :30500 TCP │  │                     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## Test Suites (~90 tests)

| Suite | Description |
|-------|-------------|
| `test-server-api.sh` | Server API, health, CRUD servers |
| `test-client-api.sh` | Client API, partners, jobs |
| `test-transfers.sh` | Transfers, retry/pause/resume |
| `test-certificates.sh` | Keystores, truststores |
| `test-partners.sh` | Partner CRUD, files, audit |
| `test-audit.sh` | Audit, metrics, Prometheus |

## Troubleshooting

```bash
kubectl get pods -n pesitwizard
kubectl logs -n pesitwizard deployment/pesitwizard-server
kubectl rollout restart deployment/pesitwizard-server -n pesitwizard
```
