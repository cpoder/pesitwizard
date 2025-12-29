package com.pesitwizard.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for secrets management.
 * Supports AES (default) and Vault (recommended for production).
 * Encryption is ALWAYS enabled - there is no plaintext mode.
 */
@Slf4j
@Configuration
public class SecretsConfig {

    public enum EncryptionMode {
        AES, // Local AES-GCM encryption with master key (default)
        VAULT // HashiCorp Vault (recommended for production)
    }

    @Value("${pesitwizard.security.encryption-mode:AES}")
    private String encryptionMode;

    @Value("${pesitwizard.security.master-key:}")
    private String masterKey;

    @Value("${pesitwizard.security.vault.address:}")
    private String vaultAddress;

    @Value("${pesitwizard.security.vault.token:}")
    private String vaultToken;

    @Value("${pesitwizard.security.vault.secrets-path:secret/data/pesitwizard}")
    private String vaultSecretsPath;

    // AppRole authentication (recommended for production)
    @Value("${pesitwizard.security.vault.auth-method:token}")
    private String vaultAuthMethod;

    @Value("${pesitwizard.security.vault.role-id:}")
    private String vaultRoleId;

    @Value("${pesitwizard.security.vault.secret-id:}")
    private String vaultSecretId;

    @Bean
    @Primary
    public SecretsProvider secretsProvider() {
        EncryptionMode mode = parseMode(encryptionMode);

        log.info("Configuring secrets provider: mode={}", mode);

        // Always create AES provider for fallback decryption
        String keyToUse = masterKey;
        if (keyToUse == null || keyToUse.isBlank()) {
            keyToUse = generateDefaultMasterKey();
            log.warn(
                    "⚠️  Using auto-generated AES master key. For production, configure HashiCorp Vault or set PESITWIZARD_SECURITY_MASTER_KEY");
        }
        AesSecretsProvider aesProvider = new AesSecretsProvider(keyToUse);

        // Try Vault if configured
        if (mode == EncryptionMode.VAULT) {
            VaultSecretsProvider vaultProvider = createVaultProvider();
            if (vaultProvider != null && vaultProvider.isAvailable()) {
                log.info(
                        "Using Vault secrets provider with AES fallback for decryption (transparent migration enabled)");
                // Use composite provider for transparent AES->Vault migration
                return new CompositeSecretsProvider(vaultProvider, aesProvider);
            }
            log.warn("Vault not available, falling back to AES");
        }

        // AES only mode
        if (aesProvider.isAvailable()) {
            log.info("Using AES secrets provider");
            if (mode != EncryptionMode.VAULT) {
                log.info("💡 TIP: For production, consider using HashiCorp Vault for enhanced security");
            }
            return aesProvider;
        }

        // This should never happen - AES should always work
        log.error("CRITICAL: AES encryption failed to initialize! Check your Java installation.");
        throw new IllegalStateException("Encryption must be available - AES initialization failed");
    }

    private EncryptionMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return EncryptionMode.AES; // Default
        }
        try {
            return EncryptionMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid encryption mode '{}', defaulting to AES", mode);
            return EncryptionMode.AES;
        }
    }

    /**
     * Create Vault provider based on auth method (token or approle)
     */
    private VaultSecretsProvider createVaultProvider() {
        if (vaultAddress == null || vaultAddress.isBlank()) {
            log.warn("Vault address not configured");
            return null;
        }

        if ("approle".equalsIgnoreCase(vaultAuthMethod)) {
            // AppRole authentication (recommended for production)
            if (vaultRoleId == null || vaultRoleId.isBlank() || vaultSecretId == null || vaultSecretId.isBlank()) {
                log.warn(
                        "AppRole credentials not configured (PESITWIZARD_SECURITY_VAULT_ROLE_ID, PESITWIZARD_SECURITY_VAULT_SECRET_ID)");
                return null;
            }
            log.info("Using Vault AppRole authentication");
            return new VaultSecretsProvider(vaultAddress, vaultSecretsPath, vaultRoleId, vaultSecretId);
        } else {
            // Token authentication (default)
            if (vaultToken == null || vaultToken.isBlank()) {
                log.warn("Vault token not configured (PESITWIZARD_SECURITY_VAULT_TOKEN)");
                return null;
            }
            log.info("Using Vault token authentication");
            return new VaultSecretsProvider(vaultAddress, vaultToken, vaultSecretsPath);
        }
    }

    /**
     * Generate a default master key based on stable machine-specific data.
     * This ensures the same key is generated on restarts but is unique per
     * installation.
     */
    private String generateDefaultMasterKey() {
        try {
            // Use a combination of stable system properties as seed
            String seed = System.getProperty("user.home", "/tmp") +
                    System.getProperty("os.name", "unknown") +
                    "pesitwizard-default-key-v1";

            // Generate a deterministic key from the seed
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            // Fallback to a fixed default (not recommended but better than plaintext)
            log.error("Failed to generate default key, using fallback", e);
            return "cGVzaXR3aXphcmQtZGVmYXVsdC1rZXktdjE=";
        }
    }

}
