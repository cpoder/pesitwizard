# Security

## Overview

PeSIT Wizard server security relies on multiple layers:

1. **PeSIT Authentication**: Partner ID/password credentials
2. **TLS Encryption**: PeSIT-E over TLS 1.2/1.3
3. **API Authentication**: Basic Auth or JWT
4. **Network**: Firewall, VPN, IP whitelisting

## PeSIT Authentication

### Partner Configuration

Each partner must be registered with an ID and password:

```bash
curl -X POST http://localhost:8080/api/v1/config/partners \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "partnerId": "BANK_XYZ",
    "password": "ComplexPassword123!",
    "enabled": true
  }'
```

### Password Policy

Recommendations:
- Minimum 12 characters
- Mix of uppercase/lowercase/numbers/symbols
- Rotate every 90 days
- No reuse of the last 5 passwords

## TLS and mTLS Encryption

PeSIT Wizard supports two TLS security modes:

| Mode | Description | Use Case |
|------|-------------|----------|
| **Simple TLS** | Only the server presents a certificate | Trusted partners on a secure network |
| **mTLS** | Both client AND server present a certificate | Production, high security |

### Architecture with Private CA

PeSIT Wizard integrates a **private Certificate Authority (CA)** to simplify certificate management:

```
                    ┌─────────────────────┐
                    │   Private PeSIT CA   │
                    │   (pesit-ca)         │
                    └──────────┬──────────┘
                               │ signs
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
     ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
     │ Server Cert │   │ Client Cert │   │ Client Cert │
     │  PeSIT      │   │  Partner A  │   │  Partner B  │
     └─────────────┘   └─────────────┘   └─────────────┘
```

## Private CA Management (Administration)

::: warning Security
The private CA is a critical security component. Its access must be **strictly limited to security administrators**. In production, consider using an HSM (Hardware Security Module) to protect CA keys.
:::

### Separation of Responsibilities Principle

```
┌─────────────────────────────────────────────────────────────────┐
│              Private CA (Security Administration)                │
│  - Access restricted to security administrators                 │
│  - One-time CA initialization                                   │
│  - Signing server and client certificates                       │
│  - Secure key storage (HSM recommended in production)           │
└─────────────────────────────────────────────────────────────────┘
                              │ distributes certificates
           ┌──────────────────┼──────────────────┐
           ▼                                     ▼
┌─────────────────────┐              ┌─────────────────────┐
│  PeSIT Server       │              │  PeSIT Client       │
│  - Receives keystore│              │  - Receives keystore│
│    signed by CA     │              │    signed by CA     │
│  - CA truststore    │              │  - CA truststore    │
└─────────────────────┘              └─────────────────────┘
```

### CA Initialization (one time only)

The security administrator initializes the CA via the server API:

```bash
# Initialize the CA (admin access required)
curl -X POST http://localhost:8080/api/v1/certificates/ca/initialize \
  -u admin:admin
```

### Certificate Generation

The administrator generates certificates for each server and client:

```bash
# Certificate for a server
curl -X POST "http://localhost:8080/api/v1/certificates/ca/partner/SERVER_PROD/generate" \
  -u admin:admin \
  -d "commonName=pesit.example.com&purpose=SERVER&validityDays=365"

# Certificate for a client/partner
curl -X POST "http://localhost:8080/api/v1/certificates/ca/partner/BANK_XYZ/generate" \
  -u admin:admin \
  -d "commonName=bank-xyz.example.com&purpose=CLIENT&validityDays=365"
```

### Secure Distribution

The administrator then distributes:
1. **To the server**: Its keystore + the CA truststore
2. **To clients**: Their keystore + the CA certificate (for their truststore)

---

## Client-Side TLS Configuration

### Client User Interface

TLS configuration is accessible **per server** in the PeSIT Wizard client. Each server configured with TLS enabled has a **TLS** button for importing certificates.

In the server list, click the **TLS** button for the relevant server:

![Navigate to TLS Config](/screenshots/tls-config-nav.png)

### 1. Import the CA Certificate (Truststore)

To trust the PeSIT server, import the CA certificate:

![Import CA Certificate](/screenshots/tls-import-truststore.png)

1. Click **Import CA Certificate**
2. Select the CA file received from the administrator (.pem, .crt, .p12, or .jks)
3. Enter the password if necessary

### 2. Import the Client Certificate (Keystore)

For mTLS authentication, import your client certificate:

![Import Client Certificate](/screenshots/tls-import-keystore.png)

1. Click **Import Client Certificate**
2. Select the keystore file received from the administrator (.p12 or .jks)
3. Enter the keystore password

::: tip Note
The client certificate import button is only active after importing the CA Certificate.
:::

### 3. TLS Status

Once the certificates are imported, the TLS status is visible in the configuration:

![TLS Configuration](/screenshots/tls-enabled.png)

---

## Server-Side mTLS Configuration

### Configuration File

```yaml
pesitwizard:
  ssl:
    enabled: true
    keystore-name: default-keystore       # Server certificate
    truststore-name: pesit-ca-truststore  # CA for validating clients
    client-auth: NEED                     # NONE, WANT, NEED
    protocol: TLSv1.3
    verify-certificate-chain: true

  ca:
    enabled: true
    ca-keystore-name: pesit-ca-keystore
    ca-truststore-name: pesit-ca-truststore
    ca-keystore-password: ${PESIT_CA_PASSWORD:changeit}
```

### Client Authentication Modes

| Mode | Behavior |
|------|----------|
| `NONE` | Simple TLS, no client certificate required |
| `WANT` | Server requests a certificate but accepts connections without one |
| `NEED` | **Mandatory mTLS** - the client MUST present a valid certificate |

---

## Client Guide: Obtaining a Signed Certificate

PeSIT clients must obtain a certificate signed by the server's CA to connect via mTLS.

### Option 1: Certificate Generated by the Administrator (recommended)

The PeSIT Wizard server administrator generates a certificate for the partner via the interface:

1. **Administrator**: Generates the certificate via the UI (see "Generate a certificate for a partner" section)
2. **Administrator**: Exports and transmits the keystore to the client securely
3. **Client**: Imports the received keystore into their configuration

### Option 2: CSR Provided by the Client (more secure)

If the client prefers to generate their own private key (more secure):

#### Client Side: Generate a Key and a CSR

```bash
# 1. Generate the private key (the client keeps it secret)
openssl genrsa -out client-key.pem 2048

# 2. Generate the Certificate Signing Request (CSR)
openssl req -new \
  -key client-key.pem \
  -out client.csr \
  -subj "/CN=bank-xyz.example.com/O=Bank XYZ/C=FR"

# 3. Display the CSR in base64 to send to the server
cat client.csr
```

#### Server Side: Sign the CSR via the UI

The administrator signs the CSR via the API:

1. Paste the CSR received from the client
2. Select **Client (mTLS)** as purpose
3. Click **Sign CSR**
4. Download the signed certificate and send it to the client

Alternatively, via API:

```bash
curl -X POST "http://localhost:8080/api/v1/certificates/ca/sign" \
  -u admin:admin \
  -d "csrPem=$(cat client.csr)" \
  -d "purpose=CLIENT" \
  -d "validityDays=365" \
  -d "partnerId=BANK_XYZ"
```

#### Client Side: Create the Keystore

```bash
# 1. Save the signed certificate
echo "-----BEGIN CERTIFICATE-----
MIID....
-----END CERTIFICATE-----" > client-cert.pem

# 2. Download the CA certificate (for the truststore)
curl -o ca-cert.pem http://localhost:8080/api/v1/certificates/ca/certificate \
  -u admin:admin

# 3. Create the PKCS12 keystore with the key and certificate
openssl pkcs12 -export \
  -in client-cert.pem \
  -inkey client-key.pem \
  -out client-keystore.p12 \
  -name client \
  -password pass:changeit

# 4. Create the truststore with the CA certificate
keytool -importcert \
  -alias pesit-ca \
  -file ca-cert.pem \
  -keystore client-truststore.p12 \
  -storetype PKCS12 \
  -storepass changeit \
  -noprompt
```

---

## Client Guide: Import the Server Certificate

For the client to trust the PeSIT Wizard server, it must import the CA certificate into its truststore.

### Download the CA Certificate via the UI

The CA certificate can be downloaded via the API:

```bash
curl -o pesit-ca.pem \
  http://localhost:8080/api/v1/certificates/ca/certificate \
  -u admin:admin
```

### Import into a Java Truststore (PKCS12)

```bash
# Create or update the truststore
keytool -importcert \
  -alias pesit-wizard-ca \
  -file pesit-ca.pem \
  -keystore truststore.p12 \
  -storetype PKCS12 \
  -storepass changeit \
  -noprompt

# Verify the import
keytool -list -keystore truststore.p12 -storepass changeit
```

### Import into a JKS Truststore (legacy)

```bash
keytool -importcert \
  -alias pesit-wizard-ca \
  -file pesit-ca.pem \
  -keystore truststore.jks \
  -storetype JKS \
  -storepass changeit \
  -noprompt
```

### Java Client Configuration

```java
// Load the keystore (client certificate)
KeyStore keyStore = KeyStore.getInstance("PKCS12");
keyStore.load(new FileInputStream("client-keystore.p12"), "changeit".toCharArray());

// Load the truststore (server CA certificate)
KeyStore trustStore = KeyStore.getInstance("PKCS12");
trustStore.load(new FileInputStream("client-truststore.p12"), "changeit".toCharArray());

// Create the SSLContext
KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
kmf.init(keyStore, "changeit".toCharArray());

TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
tmf.init(trustStore);

SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
```

---

## Verify a Certificate

### Verify that a Certificate is Signed by the CA

```bash
curl -X POST "http://localhost:8080/api/v1/certificates/ca/verify" \
  -u admin:admin \
  -d "certificatePem=$(cat client-cert.pem)"
```

Response if valid:
```json
{
  "message": "Certificate is valid and signed by our CA"
}
```

### Verify a Certificate with OpenSSL

```bash
# Verify the chain of trust
openssl verify -CAfile pesit-ca.pem client-cert.pem

# Display certificate details
openssl x509 -in client-cert.pem -text -noout
```

---

## CA Endpoint Summary

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/certificates/ca/initialize` | POST | Initialize the private CA |
| `/api/v1/certificates/ca/certificate` | GET | Download the CA certificate (PEM) |
| `/api/v1/certificates/ca/csr` | POST | Generate a CSR |
| `/api/v1/certificates/ca/sign` | POST | Sign a CSR |
| `/api/v1/certificates/ca/partner/{id}/generate` | POST | Generate a complete certificate for a partner |
| `/api/v1/certificates/ca/verify` | POST | Verify a certificate |

---

## Complete mTLS Workflow

```
┌──────────────────────────────────────────────────────────────────┐
│                      PESIT WIZARD SERVER                         │
├──────────────────────────────────────────────────────────────────┤
│  1. POST /ca/initialize         → Create the CA                 │
│  2. Configure ssl.client-auth: NEED                             │
│  3. POST /ca/partner/XXX/generate  → Generate partner cert      │
│  4. GET /ca/certificate         → Distribute CA cert            │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                         PESIT CLIENT                             │
├──────────────────────────────────────────────────────────────────┤
│  1. Retrieve the partner keystore (key + signed cert)           │
│     OR generate CSR and have it signed                          │
│  2. Import CA cert into truststore                              │
│  3. Configure the client with keystore + truststore             │
│  4. mTLS connection to server port 6502 (or TLS port 5001)     │
└──────────────────────────────────────────────────────────────────┘
```

## REST API Security

### Basic Authentication

```yaml
pesitwizard:
  admin:
    username: admin
    password: ${ADMIN_PASSWORD}  # Via environment variable
```

### Change the Admin Password

```bash
# Via environment variable
docker run -e PESIT_ADMIN_PASSWORD=NewPassword ...
```

### HTTPS for the API

In production, place a reverse proxy (nginx, traefik) in front of the API:

```nginx
server {
    listen 443 ssl;
    server_name pesitwizard-admin.mycompany.com;

    ssl_certificate /etc/ssl/certs/server.crt;
    ssl_certificate_key /etc/ssl/private/server.key;

    location / {
        proxy_pass http://pesitwizard-server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## Network Security

### Firewall

Ports to open:

| Port | Service | Access |
|------|---------|--------|
| 6502 | PeSIT | Partners only |
| 5001 | PeSIT TLS | Partners only |
| 8080 | REST API | Internal only |

### IP Whitelisting

Restrict PeSIT access to known IPs:

```yaml
# Kubernetes NetworkPolicy
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: pesitwizard-server-policy
spec:
  podSelector:
    matchLabels:
      app: pesitwizard-server
  policyTypes:
  - Ingress
  ingress:
  - from:
    - ipBlock:
        cidr: 10.0.0.0/8  # Internal network
    - ipBlock:
        cidr: 203.0.113.0/24  # Bank IPs
    ports:
    - port: 6502
    - port: 5001
```

### VPN

For sensitive exchanges, use a site-to-site VPN:

```
[Your network] ──VPN IPSec── [Bank network]
      │                            │
      ▼                            ▼
 PeSIT Wizard Client              PeSIT Wizard Server
```

## Audit and Traceability

### Access Logs

All access is logged:

```
2025-01-10 10:30:00 INFO  [CONNECT] Partner=BANK_XYZ IP=203.0.113.50 Status=SUCCESS
2025-01-10 10:30:01 INFO  [CREATE] Partner=BANK_XYZ File=PAYMENT.XML Status=SUCCESS
2025-01-10 10:30:05 INFO  [RELEASE] Partner=BANK_XYZ Duration=5s Bytes=15234
```

### Log Retention

```yaml
logging:
  file:
    name: /var/log/pesitwizard/pesitwizard-server.log
    max-size: 100MB
    max-history: 90  # 90 days
```

### Export to SIEM

Configure Filebeat or Fluentd to send logs to your SIEM:

```yaml
# filebeat.yml
filebeat.inputs:
- type: log
  paths:
    - /var/log/pesitwizard/*.log

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "pesitwizard-logs-%{+yyyy.MM.dd}"
```

## Security Checklist

- [ ] Complex partner passwords
- [ ] TLS enabled (PeSIT-E)
- [ ] Valid and non-expired certificates
- [ ] API protected by HTTPS
- [ ] Admin password changed
- [ ] Firewall configured
- [ ] IP whitelisting enabled
- [ ] Centralized logs
- [ ] Alerts configured
- [ ] Backups tested
