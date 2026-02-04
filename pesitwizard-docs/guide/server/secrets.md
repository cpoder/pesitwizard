# Secrets Management

The `pesitwizard-security` module provides secure secrets management (partner passwords, API keys, etc.).

## Available Providers

| Provider | Description | Use Case |
|----------|-------------|----------|
| `aes` | Local AES encryption | Development, small installations |
| `vault` | HashiCorp Vault | Production, multi-environment |

## AES Configuration (default)

The AES provider uses a master key to encrypt/decrypt secrets stored in the database.

```yaml
pesitwizard:
  secrets:
    provider: aes
    aes:
      key-file: /app/secrets/master.key
```

### Master Key Generation

```bash
# Generate an AES-256 key
openssl rand -base64 32 > master.key

# Secure the permissions
chmod 600 master.key
```

### Kubernetes Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: pesitwizard-secrets
type: Opaque
data:
  master.key: <base64-encoded-key>
```

```yaml
# In the Deployment
volumes:
  - name: secrets
    secret:
      secretName: pesitwizard-secrets
volumeMounts:
  - name: secrets
    mountPath: /app/secrets
    readOnly: true
```

## HashiCorp Vault Configuration

For production environments, Vault offers centralized secrets management.

```yaml
pesitwizard:
  secrets:
    provider: vault
    vault:
      address: https://vault.example.com
      token: ${VAULT_TOKEN}
      path: secret/data/pesitwizard
```

### Vault Authentication

**Token (development)**:
```yaml
pesitwizard:
  secrets:
    vault:
      token: ${VAULT_TOKEN}
```

**Kubernetes Auth (production)**:
```yaml
pesitwizard:
  secrets:
    vault:
      auth-method: kubernetes
      kubernetes:
        role: pesitwizard
        jwt-path: /var/run/secrets/kubernetes.io/serviceaccount/token
```

### Secrets Structure in Vault

```
secret/data/pesitwizard/
├── partners/
│   ├── PARTNER_ID_1/
│   │   └── password
│   └── PARTNER_ID_2/
│       └── password
└── global/
    └── master-key
```

### Vault Configuration (HCL)

```hcl
# Policy for PeSIT Wizard
path "secret/data/pesitwizard/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

# Kubernetes auth role
vault write auth/kubernetes/role/pesitwizard \
    bound_service_account_names=pesitwizard \
    bound_service_account_namespaces=pesitwizard \
    policies=pesitwizard \
    ttl=1h
```

## SecretsService API

The `SecretsService` service exposes a simple API for managing secrets:

```java
@Autowired
private SecretsService secretsService;

// Store a secret
secretsService.storeSecret("partners/BANK01/password", "s3cr3t");

// Retrieve a secret
String password = secretsService.getSecret("partners/BANK01/password");

// Delete a secret
secretsService.deleteSecret("partners/BANK01/password");
```

## Secrets Rotation

### AES Key Rotation

1. Generate a new key
2. Decrypt all secrets with the old key
3. Re-encrypt with the new key
4. Replace the key file

```bash
# Rotation script (implement according to your needs)
./scripts/rotate-master-key.sh old.key new.key
```

### Rotation with Vault

Vault automatically manages rotation via TTL policies.

## Security

::: warning Best Practices
- Never commit keys to Git
- Use restrictive permissions (600)
- Prefer Vault in production
- Enable audit logging
:::

## Environment Variables

| Variable | Description |
|----------|-------------|
| `PESITWIZARD_SECRETS_PROVIDER` | `aes` or `vault` |
| `PESITWIZARD_SECRETS_AES_KEY_FILE` | Path to the AES key |
| `VAULT_ADDR` | Vault URL |
| `VAULT_TOKEN` | Vault authentication token |
