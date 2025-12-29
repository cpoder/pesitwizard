package com.pesitwizard.security;

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

import lombok.extern.slf4j.Slf4j;

/**
 * AES-GCM based secrets provider for local encryption.
 * Uses a master key from environment variable or configuration.
 * 
 * Format: BASE64(IV + CIPHERTEXT + AUTH_TAG)
 * Prefix: "AES:" to identify encrypted values
 */
@Slf4j
public class AesSecretsProvider implements SecretsProvider {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 65536;
    private static final String PREFIX = "AES:";
    private static final String SALT = "PeSITWizardEnterprise"; // Static salt, key derivation from master password

    private final SecretKey secretKey;
    private final boolean available;

    public AesSecretsProvider(@Value("${pesitwizard.security.master-key:}") String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            log.warn("No master key configured (PESITWIZARD_SECURITY_MASTER_KEY). " +
                    "AES encryption will not be available. Secrets will be stored in plaintext.");
            this.secretKey = null;
            this.available = false;
        } else {
            try {
                this.secretKey = deriveKey(masterKey);
                this.available = true;
                log.info("AES secrets provider initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize AES secrets provider: {}", e.getMessage());
                throw new RuntimeException("Failed to initialize AES encryption", e);
            }
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

    @Override
    public String encrypt(String plaintext) {
        if (!available || plaintext == null) {
            return plaintext;
        }

        // Don't re-encrypt already encrypted values
        if (plaintext.startsWith(PREFIX)) {
            return plaintext;
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (!available || ciphertext == null) {
            return ciphertext;
        }

        // Not encrypted, return as-is
        if (!ciphertext.startsWith(PREFIX)) {
            return ciphertext;
        }

        try {
            String encoded = ciphertext.substring(PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(encoded);

            // Extract IV and ciphertext
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

    @Override
    public void storeSecret(String key, String value) {
        // For AES provider, secrets are stored encrypted in the database
        // This method is a no-op as encryption happens via encrypt()
        log.debug("AES provider: storeSecret is handled by encrypt() for database storage");
    }

    @Override
    public String getSecret(String key) {
        // For AES provider, secrets are retrieved from the database
        // This method is a no-op as decryption happens via decrypt()
        log.debug("AES provider: getSecret is handled by decrypt() for database retrieval");
        return null;
    }

    @Override
    public void deleteSecret(String key) {
        // For AES provider, deletion is handled by database operations
        log.debug("AES provider: deleteSecret is handled by database deletion");
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getProviderType() {
        return "AES";
    }

    /**
     * Check if a value is encrypted with this provider.
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
