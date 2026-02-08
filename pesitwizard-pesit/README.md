# pesitwizard-pesit

Core PeSIT protocol library. Implements FPDU encoding/decoding, session management, and transport abstraction per the PeSIT E specification (September 1989).

## Packages

| Package | Description |
|---------|-------------|
| `com.pesitwizard.fpdu` | FPDU serialization, parsing, builders, and PI definitions |
| `com.pesitwizard.session` | PeSIT session management and FPDU exchange |
| `com.pesitwizard.transport` | Transport channel abstraction (TCP, TLS, X.25) |
| `com.pesitwizard.exception` | Protocol-specific exceptions |

## Key Classes

- **`Fpdu`** - Core FPDU data structure with serialization/deserialization
- **`FpduType`** - Enum of 30+ FPDU types with parameter requirements
- **`FpduParser`** / **`FpduBuilder`** - Parse raw bytes to FPDU and build FPDU to bytes
- **`FpduReader`** / **`FpduIO`** - Stream-level FPDU I/O with concatenated FPDU handling (PeSIT section 4.5)
- **`ParameterIdentifier`** - 96 PI definitions with types and max lengths
- **`PesitSession`** - Manages FPDU exchange, ACK validation, error handling
- **`TransportChannel`** - Abstraction over TCP/TLS sockets with dynamic timeout

## FPDU Structure

```
[Length(2)][Phase(1)][Type(1)][IdDst(1)][IdSrc(1)][Parameters or Data]
```

- **Phase**: `0x40` (session), `0xC0` (file), `0x00` (data transfer)
- **Parameters**: PI (Parameter Information) codes with typed values

## Usage

This module is a library dependency used by `pesitwizard-client` and `pesitwizard-server`.

```xml
<dependency>
    <groupId>com.pesitwizard</groupId>
    <artifactId>pesitwizard-pesit</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Create a CONNECT Message

```java
Fpdu connect = new Fpdu(FpduType.CONNECT)
    .withIdSrc(1)
    .withParameter(new ParameterValue(PI_03_DEMANDEUR, "MY_CLIENT"))
    .withParameter(new ParameterValue(PI_04_SERVEUR, "BANK_SERVER"));

byte[] data = FpduBuilder.buildFpdu(connect);
```

### Parse an FPDU

```java
byte[] received = // ... data from network
Fpdu fpdu = new FpduParser(received).parse();

if (fpdu.getFpduType() == FpduType.ACONNECT) {
    int serverId = fpdu.getIdSrc();
}
```

## Building

```bash
mvn clean install
mvn test
```

## Prerequisites

- Java 21+
- Maven 3.9+

## Reference

- PeSIT Specification Version E (September 1989) - GSIT
