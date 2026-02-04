# PeSIT Wizard Client

Java client for performing file transfers via the PeSIT protocol. Exposes a REST API used by the `pesitwizard-client-ui` web interface.

## Features

- **File Sending** to PeSIT servers
- **File Receiving** from PeSIT servers
- **Server Management**: Configuration of multiple target PeSIT servers
- **Transfer History**: Database storage

## Prerequisites

- Java 21+
- Maven 3.9+

## Build

```bash
# First install the PeSIT library
cd ../pesitwizard-pesit
mvn install -DskipTests

# Build the client
cd ../pesitwizard-client
mvn package -DskipTests
```

## Running

```bash
java -jar target/pesitwizard-client-1.0.0-SNAPSHOT.jar
```

The server starts on port **8080**.

## Configuration

`application.yml` file:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/pesitwizard-client
```

## REST API

### Servers

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/servers` | List configured servers |
| POST | `/api/v1/servers` | Add a server |
| DELETE | `/api/v1/servers/{id}` | Delete a server |

### Transfers

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/transfers/send` | Send a file |
| POST | `/api/v1/transfers/receive` | Receive a file |
| GET | `/api/v1/transfers` | Transfer history |

### Send Example

```bash
curl -X POST http://localhost:8080/api/v1/transfers/send \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": 1,
    "remoteFilename": "DOCUMENT.PDF",
    "fileContent": "<base64-encoded content>"
  }'
```

## Docker

```bash
docker build -t pesitwizard-client .
docker run -p 8080:8080 pesitwizard-client
```

## Tech Stack

- Spring Boot 3.x
- Java 21
- H2 / PostgreSQL
- pesitwizard-pesit
