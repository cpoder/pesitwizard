package com.pesitwizard.security;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing secrets throughout the application.
 * Provides a unified API for encrypting/decrypting sensitive data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecretsService {

    private final SecretsProvider secretsProvider;

    /**
     * Encrypt a sensitive value before storing in database.
     */
    public String encryptForStorage(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        return secretsProvider.encrypt(plaintext);
    }

    /**
     * Encrypt a sensitive value with contextual path for better Vault organization.
     * Creates human-readable paths like "registry/github/password" in Vault.
     * 
     * @param plaintext  The value to encrypt
     * @param entityType The entity type (e.g., "registry", "orchestrator",
     *                   "partner", "cluster")
     * @param entityName The entity name (e.g., "github", "docker-hub")
     * @param fieldName  The field name (e.g., "password", "token")
     * @return The encrypted value with vault reference
     */
    public String encryptForStorage(String plaintext, String entityType, String entityName, String fieldName) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        String context = String.format("%s/%s/%s", entityType, entityName, fieldName);
        return secretsProvider.encrypt(plaintext, context);
    }

    /**
     * Decrypt a value retrieved from database.
     */
    public String decryptFromStorage(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return ciphertext;
        }
        return secretsProvider.decrypt(ciphertext);
    }

    /**
     * Store a secret in external provider (Vault).
     * For AES mode, this is a no-op as secrets are stored encrypted in DB.
     */
    public void storeSecret(String key, String value) {
        secretsProvider.storeSecret(key, value);
    }

    /**
     * Retrieve a secret from external provider.
     */
    public String getSecret(String key) {
        return secretsProvider.getSecret(key);
    }

    /**
     * Delete a secret.
     */
    public void deleteSecret(String key) {
        secretsProvider.deleteSecret(key);
    }

    /**
     * Get current encryption mode.
     */
    public String getEncryptionMode() {
        return secretsProvider.getProviderType();
    }

    /**
     * Check if encryption is enabled.
     */
    public boolean isEncryptionEnabled() {
        return secretsProvider.isAvailable() && !"NONE".equals(secretsProvider.getProviderType());
    }

    /**
     * Check if a value is already encrypted.
     */
    public boolean isEncrypted(String value) {
        if (value == null)
            return false;
        return value.startsWith("AES:") || value.startsWith("vault:") || value.startsWith("ENC:");
    }

    /**
     * Get provider status for admin UI.
     */
    public SecretsProviderStatus getStatus() {
        return new SecretsProviderStatus(
                secretsProvider.getProviderType(),
                secretsProvider.isAvailable(),
                getStatusMessage());
    }

    /**
     * Direct encrypt method (alias for encryptForStorage).
     */
    public String encrypt(String plaintext) {
        return encryptForStorage(plaintext);
    }

    /**
     * Direct decrypt method (alias for decryptFromStorage).
     */
    public String decrypt(String ciphertext) {
        return decryptFromStorage(ciphertext);
    }

    /**
     * Check if any encryption is available.
     */
    public boolean isAvailable() {
        return secretsProvider.isAvailable();
    }

    /**
     * Check if Vault is the active provider.
     */
    public boolean isVaultAvailable() {
        return "VAULT".equals(secretsProvider.getProviderType()) && secretsProvider.isAvailable();
    }

    /**
     * Store a secret directly in Vault with a specific key.
     * Returns a vault reference to store in the database.
     */
    public String storeInVault(String key, String plaintext) {
        if (!isVaultAvailable()) {
            throw new IllegalStateException("Vault is not available");
        }
        secretsProvider.storeSecret(key, plaintext);
        return "vault:" + key;
    }

    private String getStatusMessage() {
        String type = secretsProvider.getProviderType();
        boolean available = secretsProvider.isAvailable();

        if ("NONE".equals(type)) {
            return "⚠️ No encryption configured. Secrets are stored in plaintext.";
        } else if ("AES".equals(type) && available) {
            return "✅ AES-256-GCM encryption active";
        } else if ("VAULT".equals(type) && available) {
            return "✅ HashiCorp Vault integration active";
        } else {
            return "❌ Encryption configured but not available";
        }
    }

    public record SecretsProviderStatus(
            String providerType,
            boolean available,
            String message) {
    }
}
