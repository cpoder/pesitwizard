package com.pesitwizard.client.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for encrypting/decrypting sensitive data in the client application.
 * Supports two modes:
 * - AES: Local AES-256-GCM encryption with master key
 * - VAULT: External HashiCorp Vault for secrets storage
 * 
 * AES Format: "ENC:" + BASE64(IV + CIPHERTEXT + AUTH_TAG)
 * Vault Format: "vault:" + key-path
 */
@Slf4j
@Service
public class SecretsService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 65536;
    private static final String AES_PREFIX = "ENC:";
    private static final String VAULT_PREFIX = "vault:";
    private static final String SALT = "PeSITWizardClient";

    private final SecretKey secretKey;
    private final boolean aesAvailable;
    private final VaultClient vaultClient;

    public SecretsService(
            @Value("${pesitwizard.security.master-key:}") String masterKey,
            @Value("${pesitwizard.security.mode:VAULT}") String mode,
            @Value("${pesitwizard.security.vault.address:}") String vaultAddress,
            @Value("${pesitwizard.security.vault.token:}") String vaultToken,
            @Value("${pesitwizard.security.vault.path:secret/data/pesitwizard-client}") String vaultPath,
            @Value("${pesitwizard.security.vault.auth-method:token}") String vaultAuthMethod,
            @Value("${pesitwizard.security.vault.role-id:}") String vaultRoleId,
            @Value("${pesitwizard.security.vault.secret-id:}") String vaultSecretId) {

        // Initialize AES (required for bootstrap)
        SecretKey key = null;
        boolean aesAvail = false;
        if (masterKey != null && !masterKey.isBlank()) {
            try {
                key = deriveKey(masterKey);
                aesAvail = true;
                log.info("AES-256-GCM encryption initialized (for bootstrap)");
            } catch (Exception e) {
                log.error("Failed to initialize AES encryption: {}", e.getMessage());
            }
        }
        this.secretKey = key;
        this.aesAvailable = aesAvail;

        // Initialize Vault (required for production)
        if ("VAULT".equalsIgnoreCase(mode) && vaultAddress != null && !vaultAddress.isBlank()) {
            if ("approle".equalsIgnoreCase(vaultAuthMethod)) {
                // AppRole authentication (recommended)
                this.vaultClient = new VaultClient(vaultAddress, vaultPath, vaultRoleId, vaultSecretId);
            } else {
                // Token authentication
                this.vaultClient = new VaultClient(vaultAddress, vaultToken, vaultPath);
            }
            if (this.vaultClient.isAvailable()) {
                log.info("✅ Vault encryption initialized: {} (auth: {})", vaultAddress, vaultAuthMethod);
            } else {
                log.error("🔴 Vault configured but not available: {}", vaultAddress);
            }
        } else {
            this.vaultClient = null;
        }

        // SECURITY: Encryption is MANDATORY for PeSIT
        if (!aesAvail && (vaultClient == null || !vaultClient.isAvailable())) {
            log.error("🔴 SECURITY ERROR: No encryption configured!");
            log.error("🔴 PeSIT transfers contain sensitive data - encryption is MANDATORY.");
            log.error("🔴 Configure Vault with: PESITWIZARD_SECURITY_VAULT_ADDRESS and AppRole credentials.");
            throw new IllegalStateException(
                    "Encryption is mandatory for PeSIT. Configure Vault (recommended) or AES master key.");
        }
    }

    private SecretKey deriveKey(String masterKey) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(
                masterKey.toCharArray(),
                SALT.getBytes(StandardCharsets.UTF_8),
                ITERATIONS,
                KEY_LENGTH);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypt a sensitive value before storing in database.
     * Uses Vault if configured, otherwise AES.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }

        // Don't re-encrypt
        if (isEncrypted(plaintext)) {
            return plaintext;
        }

        // Try Vault first if available
        if (vaultClient != null && vaultClient.isAvailable()) {
            String key = java.util.UUID.randomUUID().toString();
            return vaultClient.storeSecret(key, plaintext);
        }

        // Fall back to AES
        if (!aesAvailable) {
            log.debug("Encryption not available, storing plaintext");
            return plaintext;
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return AES_PREFIX + Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt a value retrieved from database.
     * Handles both Vault references and AES-encrypted values.
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return ciphertext;
        }

        // Handle Vault reference
        if (ciphertext.startsWith(VAULT_PREFIX)) {
            if (vaultClient != null && vaultClient.isAvailable()) {
                return vaultClient.resolveReference(ciphertext);
            }
            log.warn("Cannot decrypt Vault reference: Vault not available");
            return ciphertext;
        }

        // Handle AES encrypted
        if (!ciphertext.startsWith(AES_PREFIX)) {
            return ciphertext; // Not encrypted
        }

        if (!aesAvailable) {
            log.warn("Cannot decrypt: AES encryption not available");
            return ciphertext;
        }

        try {
            String encoded = ciphertext.substring(AES_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(encoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Check if a value is encrypted (AES or Vault).
     */
    public boolean isEncrypted(String value) {
        return value != null && (value.startsWith(AES_PREFIX) || value.startsWith(VAULT_PREFIX));
    }

    /**
     * Check if any encryption is available.
     */
    public boolean isAvailable() {
        return aesAvailable || (vaultClient != null && vaultClient.isAvailable());
    }

    /**
     * Get encryption status for UI display.
     */
    public EncryptionStatus getStatus() {
        if (vaultClient != null && vaultClient.isAvailable()) {
            return new EncryptionStatus(true, "VAULT", "Vault encryption active: " + vaultClient.getVaultAddr());
        } else if (aesAvailable) {
            return new EncryptionStatus(true, "AES-256-GCM", "AES encryption active");
        } else {
            return new EncryptionStatus(false, "NONE", "No encryption configured");
        }
    }

    /**
     * Get the current encryption mode.
     */
    public String getMode() {
        if (vaultClient != null && vaultClient.isAvailable()) {
            return "VAULT";
        } else if (aesAvailable) {
            return "AES";
        }
        return "NONE";
    }

    /**
     * Check if Vault is available.
     */
    public boolean isVaultAvailable() {
        return vaultClient != null && vaultClient.isAvailable();
    }

    public record EncryptionStatus(boolean enabled, String mode, String message) {
    }
}
