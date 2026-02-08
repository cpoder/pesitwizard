# pesitwizard-security

Secrets management module providing encryption/decryption services with a dual-provider architecture supporting local AES-256-GCM and HashiCorp Vault.

## Key Classes

| Class | Description |
|-------|-------------|
| `SecretsService` | High-level facade for encrypt/decrypt operations |
| `CompositeSecretsProvider` | Routes to AES or Vault based on ciphertext prefix; enables zero-downtime migration |
| `AesSecretsProvider` | Local AES-256-GCM encryption (`AES:` prefix) |
| `VaultSecretsProvider` | HashiCorp Vault Transit engine integration (`vault:` prefix) |
| `VaultManager` | Vault client lifecycle, token refresh, health checks |
| `SecretsConfig` | Spring configuration for provider selection |
| `SecretsHealthIndicator` | Spring Boot health endpoint for secrets subsystem |
| `SecretsMetrics` | Micrometer metrics (encrypt/decrypt timers, circuit breaker counters) |
| `SecretsTracing` | Operation tracing with provider attribution |
| `AbstractEncryptionMigrationService` | Base class for migrating encrypted values between providers |

## Encryption Modes

| Mode | Provider | Use Case |
|------|----------|----------|
| `NONE` | Plaintext | Development only |
| `AES` | `AesSecretsProvider` | Local encryption (AES-256-GCM) |
| `VAULT` | `VaultSecretsProvider` | HashiCorp Vault Transit engine |

## Composite Provider Behavior

- **Encrypt**: Always uses the primary provider (Vault if configured, else AES)
- **Decrypt**: Routes by prefix (`AES:...` vs `vault:...`)
- **Migration**: Re-saving an AES-encrypted value automatically migrates to Vault

## Usage

```xml
<dependency>
    <groupId>com.pesitwizard</groupId>
    <artifactId>pesitwizard-security</artifactId>
    <version>${project.version}</version>
</dependency>
```

## Configuration

```yaml
pesitwizard:
  security:
    encryption-mode: AES           # NONE, AES, or VAULT
    encryption-key: ${ENCRYPTION_KEY}  # Required for AES mode
    vault:
      url: https://vault.example.com
      token: ${VAULT_TOKEN}
      transit-key: pesitwizard
```

## Building

```bash
mvn clean install
mvn test
```
