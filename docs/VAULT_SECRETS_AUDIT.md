# Vault Secrets Audit

This document lists all sensitive fields that should be stored in HashiCorp Vault.

## Client Module (`pesitwizard-client`)

### PesitServer Entity
| Field | Type | Vault Path | Status |
|-------|------|------------|--------|
| `truststorePassword` | String | `secret/data/pesitwizard-client/server/{id}/truststorePassword` | ✅ Migrated |
| `keystorePassword` | String | `secret/data/pesitwizard-client/server/{id}/keystorePassword` | ✅ Migrated |

### StorageConnection Entity
| Field | Type | Vault Path | Status |
|-------|------|------------|--------|
| `configJson.password` | JSON field | `secret/data/pesitwizard-client/connection/{id}/password` | ✅ Migrated |
| `configJson.secretAccessKey` | JSON field | `secret/data/pesitwizard-client/connection/{id}/secretAccessKey` | ✅ Migrated |
| `configJson.privateKey` | JSON field | `secret/data/pesitwizard-client/connection/{id}/privateKey` | ✅ Migrated |
| `configJson.apiKey` | JSON field | `secret/data/pesitwizard-client/connection/{id}/apiKey` | ✅ Migrated |

### TransferConfig Entity
No sensitive fields.

### FavoriteTransfer Entity
No sensitive fields.

### ScheduledTransfer Entity
No sensitive fields (references PesitServer and StorageConnection).

### BusinessCalendar Entity
No sensitive fields.

### TransferHistory Entity
No sensitive fields.

---

## Server Module (`pesitwizard-server`)

### Partner Entity
| Field | Type | Vault Path | Status |
|-------|------|------------|--------|
| `password` | String | `secret/data/pesitwizard-server/partner/{id}/password` | ❌ TODO |

### CertificateStore Entity
| Field | Type | Vault Path | Status |
|-------|------|------------|--------|
| `storePassword` | String | `secret/data/pesitwizard-server/certstore/{id}/storePassword` | ❌ TODO |
| `keyPassword` | String | `secret/data/pesitwizard-server/certstore/{id}/keyPassword` | ❌ TODO |

### ApiKey Entity
| Field | Type | Vault Path | Status |
|-------|------|------------|--------|
| `keyHash` | String | Already hashed (SHA-256), no Vault needed | N/A |

### SecretEntry Entity
| Field | Type | Vault Path | Status |
|-------|------|------------|--------|
| `encryptedValue` | String | Already encrypted with AES, could migrate to Vault | ⚠️ Optional |

### PesitServerConfig Entity
No sensitive fields.

### AuditEvent Entity
No sensitive fields.

### FileChecksum Entity
No sensitive fields.

### TransferRecord Entity
No sensitive fields.

### VirtualFile Entity
No sensitive fields.

---

## Admin Module (`pesitwizard-admin` - Enterprise)

### ContainerOrchestrator Entity
| Field | Type | Vault Path | Status |
|-------|------|------------|--------|
| `serviceAccountToken` | String | `secret/data/pesitwizard-admin/orchestrator/{id}/token` | ✅ Migrated |
| `clientKey` | String | `secret/data/pesitwizard-admin/orchestrator/{id}/clientKey` | ✅ Migrated |

### ContainerRegistry Entity
| Field | Type | Vault Path | Status |
|-------|------|------------|--------|
| `password` | String | `secret/data/pesitwizard-admin/registry/{id}/password` | ✅ Migrated |

### PesitwizardCluster Entity
| Field | Type | Vault Path | Status |
|-------|------|------------|--------|
| `apiPassword` | String | `secret/data/pesitwizard-admin/cluster/{id}/apiPassword` | ✅ Migrated |
| `apiKey` | String | `secret/data/pesitwizard-admin/cluster/{id}/apiKey` | ✅ Migrated |

---

## Required Vault Policies

### Client Policy (`pesitwizard-client-policy`)
```hcl
path "secret/data/pesitwizard-client/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

path "secret/metadata/pesitwizard-client/*" {
  capabilities = ["list", "read", "delete"]
}
```

### Server Policy (`pesitwizard-server-policy`)
```hcl
path "secret/data/pesitwizard-server/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

path "secret/metadata/pesitwizard-server/*" {
  capabilities = ["list", "read", "delete"]
}
```

### Admin Policy (`pesitwizard-admin-policy`)
```hcl
path "secret/data/pesitwizard-admin/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

path "secret/metadata/pesitwizard-admin/*" {
  capabilities = ["list", "read", "delete"]
}
```

---

## Environment Variables

### Client (`pesitwizard-client`)
```bash
PESITWIZARD_SECURITY_MODE=VAULT
PESITWIZARD_SECURITY_VAULT_ADDRESS=http://vault:8200
PESITWIZARD_SECURITY_VAULT_PATH=secret/data/pesitwizard/client

# Token auth
PESITWIZARD_SECURITY_VAULT_TOKEN=<token>

# OR AppRole auth (recommended)
PESITWIZARD_SECURITY_VAULT_AUTH_METHOD=approle
PESITWIZARD_SECURITY_VAULT_ROLE_ID=<role-id>
PESITWIZARD_SECURITY_VAULT_SECRET_ID=<secret-id>
```

### Server (`pesitwizard-server`)
```bash
PESITWIZARD_SECURITY_MODE=VAULT
PESITWIZARD_SECURITY_VAULT_ADDRESS=http://vault:8200
PESITWIZARD_SECURITY_VAULT_PATH=secret/data/pesitwizard/server
PESITWIZARD_SECURITY_VAULT_TOKEN=<token>
```

---

## Action Items

1. ✅ **Client Module**: Migration service created for PesitServer and StorageConnection
2. ✅ **Server Module**: Migration service created for Partner and CertificateStore
3. **Verify Vault policies**: Ensure all policies are applied correctly
4. **Test migration**: Run migration on test environment before production
