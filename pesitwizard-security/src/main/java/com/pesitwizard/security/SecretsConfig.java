package com.pesitwizard.security;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * Configuration for secrets management. Supports AES (default) and Vault (recommended for
 * production). Encryption is ALWAYS enabled - there is no plaintext mode.
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

    @Value("${pesitwizard.security.salt-file:./config/encryption.salt}")
    private String saltFile;

    // Base64-encoded encryption salt for multi-pod deployments (takes precedence over salt-file)
    @Value("${pesitwizard.security.encryption-salt:}")
    private String encryptionSalt;

    @Value("${pesitwizard.security.machine-id:}")
    private String machineId;

    @Value("${pesitwizard.security.vault.secret-id:}")
    private String vaultSecretId;

    // *_FILE support for reading secrets from files (more secure than env vars)
    @Value("${pesitwizard.security.master-key-file:}")
    private String masterKeyFile;

    @Value("${pesitwizard.security.vault.token-file:}")
    private String vaultTokenFile;

    @Value("${pesitwizard.security.vault.role-id-file:}")
    private String vaultRoleIdFile;

    @Value("${pesitwizard.security.vault.secret-id-file:}")
    private String vaultSecretIdFile;

    @Autowired private Environment environment;

    /**
     * Load secrets from files if *_FILE properties are set. File-based secrets take precedence over
     * environment variables.
     */
    @PostConstruct
    public void loadSecretsFromFiles() {
        masterKey = readFromFileOrValue(masterKeyFile, masterKey, "master-key");
        vaultToken = readFromFileOrValue(vaultTokenFile, vaultToken, "vault-token");
        vaultRoleId = readFromFileOrValue(vaultRoleIdFile, vaultRoleId, "vault-role-id");
        vaultSecretId = readFromFileOrValue(vaultSecretIdFile, vaultSecretId, "vault-secret-id");
    }

    /** Read secret from file if path is set, otherwise return the original value. */
    private String readFromFileOrValue(String filePath, String originalValue, String secretName) {
        if (filePath == null || filePath.isBlank()) {
            return originalValue;
        }
        // Check file existence and readability without passing filePath to logging methods
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            logSecretNotFound(secretName);
            return originalValue;
        }
        if (!Files.isReadable(path)) {
            logSecretNotReadable(secretName);
            return originalValue;
        }
        logSecretLoaded(secretName);
        return readSecretFileContent(filePath, originalValue);
    }

    private void logSecretNotFound(String secretName) {
        log.warn("Secret file not found for {} (using env var if set)", secretName);
    }

    private void logSecretNotReadable(String secretName) {
        log.error("Secret file for {} exists but is not readable", secretName);
    }

    private void logSecretLoaded(String secretName) {
        log.info("Loaded {} from file successfully", secretName);
    }

    /** Read the content of a secret file. No logging is performed to avoid leaking secrets. */
    private String readSecretFileContent(String filePath, String fallback) {
        try {
            return Files.readString(Path.of(filePath)).trim();
        } catch (IOException e) {
            return fallback;
        }
    }

    @Bean
    public SecretsService secretsService(SecretsProvider secretsProvider) {
        return new SecretsService(secretsProvider);
    }

    @Bean
    @Primary
    public SecretsProvider secretsProvider() {
        EncryptionMode mode = parseMode(encryptionMode);

        log.info("Configuring secrets provider: mode={}", mode);

        // AES is REQUIRED for bootstrap (storing AppRole credentials)
        String keyToUse = masterKey;
        boolean usingAutoGeneratedKey = false;
        if (keyToUse == null || keyToUse.isBlank()) {
            if (isProductionProfile()) {
                throw new IllegalStateException(
                        "PESITWIZARD_SECURITY_MASTER_KEY must be set in production. "
                                + "Auto-generated keys are NOT safe for production deployments. "
                                + "Generate a key with: openssl rand -base64 32");
            }
            keyToUse = generateDefaultMasterKey();
            usingAutoGeneratedKey = true;
            log.warn("Using auto-generated AES master key based on machine properties.");
            log.warn("This key will be DIFFERENT on another machine/container!");
            log.warn("For production, set PESITWIZARD_SECURITY_MASTER_KEY environment variable.");
        }
        AesSecretsProvider aesProvider = new AesSecretsProvider(keyToUse, encryptionSalt, saltFile);

        // Try Vault if configured
        if (mode == EncryptionMode.VAULT) {
            if (usingAutoGeneratedKey) {
                log.error("🔴 SECURITY RISK: Using auto-generated AES key with Vault mode!");
                log.error(
                        "🔴 AppRole credentials stored in DB will be UNREADABLE if you migrate to another machine.");
                log.error(
                        "🔴 Set PESITWIZARD_SECURITY_MASTER_KEY to a fixed value for production.");
            }
            VaultSecretsProvider vaultProvider = createVaultProvider();
            if (vaultProvider != null && vaultProvider.isAvailable()) {
                log.info("✅ Using Vault secrets provider with AES for bootstrap credentials");
                // Use composite provider for transparent AES->Vault migration
                return new CompositeSecretsProvider(vaultProvider, aesProvider);
            }
            log.warn("Vault not available, falling back to AES");
        }

        // AES only mode
        if (aesProvider.isAvailable()) {
            log.info("Using AES secrets provider");
            if (mode != EncryptionMode.VAULT) {
                log.info(
                        "💡 TIP: For production, consider using HashiCorp Vault for enhanced security");
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

    /** Create Vault provider based on auth method (token or approle) */
    private VaultSecretsProvider createVaultProvider() {
        if (vaultAddress == null || vaultAddress.isBlank()) {
            log.warn("Vault address not configured");
            return null;
        }

        if ("approle".equalsIgnoreCase(vaultAuthMethod)) {
            // AppRole authentication (recommended for production)
            if (vaultRoleId == null
                    || vaultRoleId.isBlank()
                    || vaultSecretId == null
                    || vaultSecretId.isBlank()) {
                log.warn(
                        "AppRole credentials not configured (PESITWIZARD_SECURITY_VAULT_ROLE_ID, PESITWIZARD_SECURITY_VAULT_SECRET_ID)");
                return null;
            }
            log.info("Using Vault AppRole authentication");
            return new VaultSecretsProvider(
                    vaultAddress, vaultSecretsPath, vaultRoleId, vaultSecretId);
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
     * Generate a default master key based on stable machine-specific data. This ensures the same
     * key is generated on restarts but is unique per installation.
     *
     * <p>The key incorporates: - Machine-specific properties (machine-id or user.home + os.name +
     * hostname) - A random salt file generated on first run (persisted for stability across
     * restarts)
     */
    private String generateDefaultMasterKey() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            if (machineId != null && !machineId.isBlank()) {
                // User-provided stable machine ID (recommended for Kubernetes)
                md.update(machineId.getBytes(StandardCharsets.UTF_8));
                log.info("Using configured machine-id for key generation");
            } else {
                // Auto-detect based on system properties
                md.update(System.getProperty("user.home", "/tmp").getBytes(StandardCharsets.UTF_8));
                md.update(
                        System.getProperty("os.name", "unknown").getBytes(StandardCharsets.UTF_8));
                // Add hostname for additional entropy in containers
                String hostname = getHostname();
                md.update(hostname.getBytes(StandardCharsets.UTF_8));
            }

            // Incorporate the persisted salt file for installation-specific entropy.
            // The salt file is generated with SecureRandom on first run by AesSecretsProvider,
            // or can be provided via PESITWIZARD_SECURITY_ENCRYPTION_SALT for multi-pod setups.
            byte[] saltBytes = loadSaltForKeyDerivation();
            if (saltBytes != null) {
                md.update(saltBytes);
            } else {
                log.warn(
                        "No salt file available yet for key derivation; "
                                + "key will be re-derived once salt is generated.");
            }

            byte[] hash = md.digest();
            return Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "Failed to generate master key: SHA-256 MessageDigest not available. "
                            + "This indicates a broken Java installation.",
                    e);
        }
    }

    /**
     * Load salt bytes for key derivation. Checks the encryption-salt property first, then falls
     * back to the salt file. Returns null if neither is available yet.
     */
    private byte[] loadSaltForKeyDerivation() {
        // Priority 1: Base64-encoded salt from environment (for K8s shared secrets)
        if (encryptionSalt != null && !encryptionSalt.isBlank()) {
            try {
                return Base64.getDecoder().decode(encryptionSalt);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid base64 in encryption-salt property, ignoring");
            }
        }

        // Priority 2: Salt file on disk
        if (saltFile != null && !saltFile.isBlank()) {
            try {
                Path path = Path.of(saltFile);
                if (Files.exists(path)) {
                    return Files.readAllBytes(path);
                }
            } catch (IOException e) {
                log.warn("Failed to read salt file for key derivation: {}", e.getMessage());
            }
        }

        return null;
    }

    /** Get the hostname for additional key derivation entropy. */
    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            return "unknown-host";
        }
    }

    /** Check if any production-like profile is active. */
    private boolean isProductionProfile() {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(
                        p ->
                                p.equalsIgnoreCase("prod")
                                        || p.equalsIgnoreCase("production")
                                        || p.equalsIgnoreCase("staging")
                                        || p.equalsIgnoreCase("postgres"));
    }
}
