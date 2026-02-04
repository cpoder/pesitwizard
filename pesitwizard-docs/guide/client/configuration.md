# Client Configuration

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP API port | 8080 |
| `SPRING_DATASOURCE_URL` | JDBC database URL | jdbc:h2:file:./data/pesitwizard-client |
| `SPRING_DATASOURCE_USERNAME` | DB user | pesitwizard |
| `SPRING_DATASOURCE_PASSWORD` | DB password | pesitwizard |

## application.yml File

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/pesitwizard-client;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update

# Transfer configuration
pesitwizard:
  client:
    id: PESIT_CLIENT

    # Directory for received files
    receive-directory: ./received

    # Connection timeout (ms)
    connection-timeout: 30000

    # Read timeout (ms)
    read-timeout: 60000

    # Number of retry attempts on failure
    retry-count: 3

    # Delay between retry attempts (ms)
    retry-delay: 5000

# Logging
logging:
  level:
    com.pesitwizard: INFO
    # For PeSIT Wizard debug
    # com.pesitwizard: DEBUG
```

## Target Server Configuration

Target PeSIT servers are configured via the API or the web interface.

### Via API

```bash
curl -X POST http://localhost:8080/api/servers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "BNP Paribas",
    "host": "pesitwizard.bnpparibas.com",
    "port": 6502,
    "serverId": "BNPP_SERVER",
    "clientId": "MY_COMPANY",
    "password": "secret123",
    "tlsEnabled": true
  }'
```

### Server Parameters

| Parameter | Description | Required |
|-----------|-------------|----------|
| `name` | Display name | Yes |
| `host` | Server address | Yes |
| `port` | PeSIT port | Yes (default: 6502) |
| `serverId` | Server identifier (PI_04) | Yes |
| `clientId` | Your identifier (PI_03) | Yes |
| `password` | Password (PI_05) | No |
| `tlsEnabled` | Enable TLS | No (default: false) |

## TLS Configuration

For secure connections (PeSIT-E):

### Generate a Client Keystore

```bash
# Generate a key pair
keytool -genkeypair \
  -alias client \
  -keyalg RSA \
  -keysize 2048 \
  -validity 365 \
  -keystore client-keystore.p12 \
  -storetype PKCS12 \
  -storepass changeit \
  -dname "CN=My Company, O=My Company, C=FR"

# Export the certificate (to send to the bank)
keytool -exportcert \
  -alias client \
  -keystore client-keystore.p12 \
  -storetype PKCS12 \
  -storepass changeit \
  -file client.crt
```

### Import the Bank's Certificate

```bash
# Import the server certificate into the truststore
keytool -importcert \
  -alias bank \
  -file bank-server.crt \
  -keystore client-truststore.p12 \
  -storetype PKCS12 \
  -storepass changeit \
  -noprompt
```

### application.yml Configuration

```yaml
pesitwizard:
  client:
    tls:
      enabled: true
      keystore-path: /config/client-keystore.p12
      keystore-password: changeit
      truststore-path: /config/client-truststore.p12
      truststore-password: changeit
```

## HTTP Proxy

If you are behind a proxy:

```yaml
pesitwizard:
  client:
    proxy:
      enabled: true
      host: proxy.company.com
      port: 8080
      username: user  # optional
      password: pass  # optional
```

## Logs and Debug

To enable detailed PeSIT protocol logs:

```yaml
logging:
  level:
    com.pesitwizard.protocol: DEBUG
    com.pesitwizard.client.service: DEBUG
```

The logs will show:
- Connections/disconnections
- Exchanged FPDU messages
- Transferred data (in hexadecimal)
