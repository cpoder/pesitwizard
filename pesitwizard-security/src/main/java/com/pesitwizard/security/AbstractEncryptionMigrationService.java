package com.pesitwizard.security;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractEncryptionMigrationService {

    protected final SecretsService secretsService;

    protected AbstractEncryptionMigrationService(SecretsService secretsService) {
        this.secretsService = secretsService;
    }

    public MigrationResult migrateAllToVault() {
        if (!secretsService.isVaultAvailable()) {
            return new MigrationResult(false, "Vault is not available", 0, 0, List.of());
        }
        return doMigration();
    }

    protected abstract MigrationResult doMigration();

    protected boolean isVaultRef(String value) {
        return value != null && value.startsWith("vault:");
    }

    protected String decryptIfNeeded(String value) {
        if (value == null)
            return null;
        if (secretsService.isEncrypted(value)) {
            return secretsService.decrypt(value);
        }
        return value;
    }

    protected String migrateToVault(String key, String value) {
        return secretsService.storeInVault(key, decryptIfNeeded(value));
    }

    public record MigrationResult(boolean success, String message, int totalMigrated, int totalSkipped,
            List<String> details) {
    }

    public record MigrationCount(int migrated, int skipped) {
    }
}
