# PeSIT Wizard Server

PeSIT server (Protocole d'Echange pour un Systeme Interbancaire de Telecompensation) implemented in Spring Boot.

## Features

- **PeSIT Off-SIT Protocol**: File send and receive
- **REST API**: Configuration and monitoring via HTTP
- **Partner Management**: Configuration of incoming/outgoing connections
- **Virtual Files**: Logical file mapping
- **TLS**: SSL/TLS encryption support

## Prerequisites

- Java 21+
- Maven 3.9+

## Build

```bash
# First install the protocol library
cd ../pesitwizard-pesit
mvn install -DskipTests

# Build the server
cd ../pesitwizard-server
mvn package -DskipTests
```

## Running

```bash
java -jar target/pesitwizard-server-1.0.0-SNAPSHOT.jar
```

- **PeSIT Port**: 6502
- **PeSIT TLS Port**: 5001
- **REST API Port**: 8080

## Configuration

`application.yml` file:

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/pesitwizard-db  # H2 by default, PostgreSQL recommended

pesitwizard:
  server:
    port: 6502
    tls-port: 5001
    ssl:
      enabled: false
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PESIT_SERVER_PORT` | PeSIT port | `6502` |
| `PESIT_SERVER_TLS_PORT` | PeSIT TLS port | `5001` |
| `SERVER_PORT` | REST API port | `8080` |
| `SPRING_DATASOURCE_URL` | JDBC URL | H2 file |

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/servers` | List configured servers |
| POST | `/api/v1/servers/{id}/start` | Start a server |
| POST | `/api/v1/servers/{id}/stop` | Stop a server |
| GET | `/api/v1/config/partners` | List partners |
| GET | `/api/v1/config/files` | List virtual files |
| GET | `/api/v1/transfers` | Transfer history |

## Monitoring

Actuator endpoints:

- Health: `GET /actuator/health`
- Metrics: `GET /actuator/metrics`

## Docker

```bash
docker build -t pesitwizard-server .
docker run -p 6502:6502 -p 5001:5001 -p 8080:8080 pesitwizard-server
```

## Tech Stack

- Spring Boot 3.x
- Java 21
- H2 / PostgreSQL

## Enterprise

For high availability clustering and the administration console, see [PeSIT Wizard Enterprise](https://pesitwizard.com).
