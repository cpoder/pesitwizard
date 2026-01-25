# PeSIT Wizard Integration Tests

This directory contains a complete integration testing environment for PeSIT Wizard using k3s (lightweight Kubernetes).

## Prerequisites

- [Vagrant](https://www.vagrantup.com/) (2.3+)
- [VirtualBox](https://www.virtualbox.org/) (7.0+)
- At least 8GB RAM and 4 CPU cores available

## Quick Start

```bash
# Start the VM and deploy the stack
vagrant up

# SSH into the VM
vagrant ssh

# Run the integration tests
cd /home/vagrant/pesitwizard/integration-tests/tests
./run-all-tests.sh
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    k3s Cluster (VM)                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  PostgreSQL │  │   PeSIT     │  │      PeSIT          │  │
│  │   Database  │◄─┤   Server    │◄─┤      Client         │  │
│  │             │  │  :30080 API │  │     :30081 API      │  │
│  │             │  │  :30500 TCP │  │                     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
         ▲                ▲                    ▲
         │                │                    │
    Port 5432        Port 30080/30500     Port 30081
```

## Test Suites

| Suite | Description |
|-------|-------------|
| `test-server-api.sh` | Server REST API, server management, health checks |
| `test-client-api.sh` | Client REST API, partner management, jobs |
| `test-transfers.sh` | File transfer operations, retry/pause/resume |
| `test-certificates.sh` | Certificate/keystore/truststore management |
| `test-partners.sh` | Partner CRUD, virtual files, audit |
| `test-audit.sh` | Audit events, metrics, monitoring |

## Running Individual Tests

```bash
# Inside the VM
cd /home/vagrant/pesitwizard/integration-tests/tests

# Run specific test suite
source test-server-api.sh

# Or run all tests
./run-all-tests.sh
```

## Test Reports

Reports are generated in `reports/` directory:
- `integration-test-YYYYMMDD_HHMMSS.md` - Markdown report with all results

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_API` | `http://localhost:30080` | Server API base URL |
| `CLIENT_API` | `http://localhost:30081` | Client API base URL |
| `PESIT_HOST` | `localhost` | PeSIT TCP host |
| `PESIT_PORT` | `30500` | PeSIT TCP port |

## Troubleshooting

### Check pod status
```bash
kubectl get pods -n pesitwizard
```

### View logs
```bash
kubectl logs -n pesitwizard deployment/pesitwizard-server
kubectl logs -n pesitwizard deployment/pesitwizard-client
```

### Restart deployment
```bash
kubectl rollout restart deployment/pesitwizard-server -n pesitwizard
```

### Re-run deployment
```bash
vagrant provision --provision-with shell
```

### Destroy and recreate
```bash
vagrant destroy -f
vagrant up
```

## Directory Structure

```
integration-tests/
├── Vagrantfile              # VM configuration
├── README.md                # This file
├── scripts/
│   ├── provision.sh         # VM setup (k3s, tools)
│   └── deploy.sh            # Deploy PeSIT stack
├── k8s/
│   └── postgresql.yaml      # PostgreSQL deployment
├── helm-values/
│   ├── server-values.yaml   # Server Helm values
│   └── client-values.yaml   # Client Helm values
├── tests/
│   ├── run-all-tests.sh     # Main test runner
│   ├── test-server-api.sh   # Server API tests
│   ├── test-client-api.sh   # Client API tests
│   ├── test-transfers.sh    # Transfer tests
│   ├── test-certificates.sh # Certificate tests
│   ├── test-partners.sh     # Partner tests
│   └── test-audit.sh        # Audit tests
└── reports/                 # Generated test reports
```

## CI/CD Integration

These tests can be integrated into GitHub Actions:

```yaml
integration-test:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - name: Setup k3s
      uses: debianmaster/actions-k3s@master
    - name: Deploy stack
      run: ./integration-tests/scripts/deploy.sh
    - name: Run tests
      run: ./integration-tests/tests/run-all-tests.sh
```
