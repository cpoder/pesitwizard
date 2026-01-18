# Security Configuration Reference

## Environment Variables

### AES Encryption
| Variable | Description | Default |
|----------|-------------|---------|
| `PESITWIZARD_SECURITY_ENCRYPTION_MODE` | `AES` or `VAULT` | `AES` |
| `PESITWIZARD_SECURITY_MASTER_KEY` | AES master key | auto-generated |
| `PESITWIZARD_SECURITY_MASTER_KEY_FILE` | Path to master key file | - |
| `PESITWIZARD_SECURITY_SALT_FILE` | Salt file path | `./config/encryption.salt` |
| `PESITWIZARD_SECURITY_MACHINE_ID` | Stable ID for K8s | - |

### Vault
| Variable | Description | Default |
|----------|-------------|---------|
| `PESITWIZARD_SECURITY_VAULT_ADDRESS` | Vault URL | - |
| `PESITWIZARD_SECURITY_VAULT_AUTH_METHOD` | `token` or `approle` | `token` |
| `PESITWIZARD_SECURITY_VAULT_TOKEN` | Vault token | - |
| `PESITWIZARD_SECURITY_VAULT_ROLE_ID` | AppRole role ID | - |
| `PESITWIZARD_SECURITY_VAULT_SECRET_ID` | AppRole secret ID | - |
| `PESITWIZARD_SECURITY_VAULT_SECRETS_PATH` | KV v2 path | `secret/data/pesitwizard` |

All variables support `*_FILE` suffix for Docker/K8s secrets.

## Best Practices

1. Use `PESITWIZARD_SECURITY_MACHINE_ID` in Kubernetes for stable key generation
2. Use `*_FILE` variants with mounted secrets
3. Use AppRole auth in production (not token)
4. Set explicit master key (don't rely on auto-generation)
