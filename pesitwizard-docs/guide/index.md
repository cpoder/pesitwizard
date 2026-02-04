# What is PeSIT?

**PeSIT** (Protocole d'Echange pour un Systeme Interbancaire de Telecompensation) is a file transfer protocol developed by the French banking sector in the 1980s.

## History

The protocol was created by the **GSIT** (Groupement pour un Systeme Interbancaire de Telecompensation) to enable file exchanges between banks and their corporate clients.

## Characteristics

- **Reliability**: Error recovery mechanisms, synchronization points
- **Security**: Partner authentication, encryption (PeSIT-E over TLS)
- **Traceability**: Complete transfer history
- **Interoperability**: Standard recognized by all French banks

## Versions

| Version | Transport | Security |
|---------|-----------|----------|
| PeSIT D | TCP/IP | Simple authentication |
| PeSIT E | TCP/IP + TLS | Encryption, certificates |

## Who Uses PeSIT?

- **Banks**: BNP Paribas, Societe Generale, BPCE, Credit Agricole...
- **Companies**: To automate banking file exchanges
- **Software vendors**: Integration in ERP and accounting software
- **Service providers**: Processing centers, PSPs

## Our Solution: PeSIT Wizard

**PeSIT Wizard** implements the PeSIT protocol in a modern architecture:

- **PeSIT Wizard Client**: To send/receive files to/from banks
- **PeSIT Wizard Server**: To receive files from partners
- **Administration Console**: To manage the entire system

[Get started quickly -->](/guide/quickstart)
