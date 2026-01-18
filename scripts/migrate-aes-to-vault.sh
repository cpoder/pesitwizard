#!/bin/bash
# Migration script: AES encrypted secrets to Vault
# This script helps migrate existing AES-encrypted data to Vault storage

set -e

echo "=== PeSIT Wizard: AES to Vault Migration ==="

# Check required environment variables
check_env() {
    if [ -z "$VAULT_ADDR" ]; then
        echo "ERROR: VAULT_ADDR not set"
        exit 1
    fi
    if [ -z "$VAULT_TOKEN" ] && [ -z "$VAULT_ROLE_ID" ]; then
        echo "ERROR: Set VAULT_TOKEN or VAULT_ROLE_ID/VAULT_SECRET_ID"
        exit 1
    fi
}

# Test Vault connection
test_vault() {
    echo "Testing Vault connection..."
    if curl -s -o /dev/null -w "%{http_code}" "$VAULT_ADDR/v1/sys/health" | grep -q "200\|429\|472\|473"; then
        echo "✅ Vault is reachable"
    else
        echo "❌ Cannot reach Vault at $VAULT_ADDR"
        exit 1
    fi
}

# Instructions
show_instructions() {
    cat << 'EOF'

Migration Steps:
================

1. BACKUP your database first:
   pg_dump pesitwizard > backup_before_migration.sql

2. Set environment variables:
   export VAULT_ADDR=http://vault:8200
   export VAULT_TOKEN=hvs.xxxxx
   export PESITWIZARD_SECURITY_MASTER_KEY=your-aes-key

3. Start the application with COMPOSITE mode:
   export PESITWIZARD_SECURITY_ENCRYPTION_MODE=VAULT
   # CompositeSecretsProvider will:
   # - Read AES: prefixed data with AES provider
   # - Write new data to Vault with vault: prefix

4. Trigger re-encryption by updating each secret:
   # The application will decrypt AES and re-encrypt to Vault

5. Verify migration:
   grep -c "AES:" database_dump.sql  # Should decrease over time
   grep -c "vault:" database_dump.sql  # Should increase

6. Once all data migrated, you can remove the AES master key

EOF
}

# Main
check_env
test_vault
show_instructions

echo ""
echo "Ready to migrate. Start PeSIT Wizard with VAULT mode."
