# PeSIT Wizard PeSIT

Java library implementing the PeSIT protocol (Protocole d'Echange pour un Systeme Interbancaire de Telecompensation).

## Features

- **FPDU Encoding/Decoding**: Binary serialization compliant with PeSIT E specification
- **All Message Types**: CONNECT, CREATE, SELECT, OPEN, WRITE, READ, DTF, etc.
- **PeSIT Parameters**: Full support for PI and PGI
- **PeSIT Session**: TCP connection management and message exchange

## Installation

```xml
<dependency>
    <groupId>com.pesitwizard</groupId>
    <artifactId>pesitwizard-pesit</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Build

```bash
mvn clean install
```

## Usage

### Create a CONNECT Message

```java
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterValue;
import static com.pesitwizard.fpdu.ParameterIdentifier.*;

Fpdu connect = new Fpdu(FpduType.CONNECT)
    .withIdSrc(1)
    .withParameter(new ParameterValue(PI_03_DEMANDEUR, "MY_CLIENT"))
    .withParameter(new ParameterValue(PI_04_SERVEUR, "BANK_SERVER"));

byte[] data = connect.toBytes();
```

### Decode an FPDU

```java
byte[] received = // ... data received from the network
Fpdu fpdu = Fpdu.fromBytes(received);

if (fpdu.getType() == FpduType.ACONNECT) {
    int serverId = fpdu.getIdSrc();
}
```

## Prerequisites

- Java 21+
- Maven 3.6+

## Reference

- PeSIT Specification Version E (September 1989) - GSIT
