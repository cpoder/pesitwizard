# Key Rotation Procedure

## Overview

PeSIT Wizard uses AES-256-GCM encryption with PBKDF2 key derivation. The system supports key versioning to enable seamless key rotation.

## Encryption Formats

| Format | Prefix | Salt | Status |
|--------|--------|------|--------|
| v2 (current) | `AES:v2:` | Dynamic (file-based) | Active |
| Legacy | `AES:` | Static | Deprecated |

## Files

- **Master Key**: Environment variable `PESITWIZARD_SECURITY_MASTER_KEY`
- **Salt File**: `./config/encryption.salt` (auto-generated, 32 bytes)

## Key Rotation Steps

### 1. Prepare New Key

```bash
# Generate a new secure key
openssl rand -base64 32
```

### 2. Backup Current State

```bash
# Backup database
pg_dump pesitwizard > backup_before_rotation.sql

# Backup salt file
cp ./config/encryption.salt ./config/encryption.salt.backup
```

### 3. Migration Strategy

**Option A: Re-encrypt All Data (Recommended for small datasets)**

1. Stop the application
2. Export all encrypted values (decrypt with old key)
3. Update `PESITWIZARD_SECURITY_MASTER_KEY` to new key
4. Delete salt file to generate new salt: `rm ./config/encryption.salt`
5. Re-encrypt all values with new key
6. Restart application

**Option B: Dual-key Support (For large datasets)**

The system automatically supports legacy decryption:
- New data is encrypted with v2 format (new key + dynamic salt)
- Old data with `AES:` prefix is decrypted with legacy key (old key + static salt)

Migration can be done gradually:
1. Add new key while keeping old key in code
2. Re-encrypt data on read (decrypt old, encrypt new)
3. After all data migrated, remove legacy key support

### 4. Verify Rotation

```bash
# Check logs for decryption format
grep "Decryption successful" /var/log/pesitwizard/*.log

# Legacy format entries will show:
# "Decryption successful (legacy format - consider re-encrypting)"
```

## Security Notes

- **Never** store master key in code or config files
- Use environment variables or secret management (Vault)
- Salt file permissions should be `600` (owner read/write only)
- Rotate keys annually or after any suspected compromise

## Vault Mode

When using Vault mode, key rotation is handled by Vault's key management. Secrets are stored in Vault, not encrypted locally.

```bash
PESITWIZARD_SECURITY_MODE=VAULT
PESITWIZARD_SECURITY_VAULT_ADDRESS=http://vault:8200
```
