# Quick Start

This guide will help you complete your first PeSIT Wizard transfer in under 15 minutes.

## Prerequisites

- Docker and Docker Compose
- Access to a PeSIT Wizard server (your bank or our test server)

## 1. Launch the PeSIT Wizard Client

```bash
# Download and launch the client
docker run -d \
  --name pesitwizard-client \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:h2:file:./data/pesitwizard-client \
  ghcr.io/pesitwizard/pesitwizard/pesitwizard-client:latest
```

The web interface is accessible at http://localhost:8080

## 2. Configure a Target Server

In the web interface, go to **Servers** > **Add**:

| Field | Value |
|-------|-------|
| Name | My Bank |
| Host | pesitwizard.mybank.com |
| Port | 6502 |
| Server ID | BANK_SERVER |
| Client ID | MY_COMPANY |
| Password | (provided by the bank) |

## 3. Send a File

1. Go to **Transfers** > **Send**
2. Select the configured server
3. Choose the file to send
4. Enter the remote name (e.g., `PAYMENT_20250110.XML`)
5. Click **Send**

## 4. Receive a File

1. Go to **Transfers** > **Receive**
2. Select the server
3. Enter the remote file name (e.g., `STATEMENT_20250110.XML`)
4. Click **Receive**

## Test Environment

To test without bank access, you can launch our test server:

```bash
# Launch a PeSIT Wizard test server
# Uses port 8081 for REST API to avoid conflict with the client on 8080
docker run -d \
  --name pesitwizard-server \
  -p 6502:6502 \
  -p 5001:5001 \
  -p 8081:8080 \
  ghcr.io/pesitwizard/pesitwizard/pesitwizard-server:latest
```

Then configure the client with:
- Host: `localhost`
- Port: `6502`
- Server ID: `PESIT_SERVER`
- Client ID: `TEST_CLIENT`

## Next Steps

- [Advanced client configuration](/guide/client/configuration)
- [ERP integration](/guide/client/erp-integration)
- [Production deployment](/guide/server/installation)
