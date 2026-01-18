package com.pesitwizard.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

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
 * Format v2: "AES:v2:" + BASE64(IV + CIPHERTEXT + AUTH_TAG) with dynamic salt
 * Legacy format: "AES:" + BASE64(IV + CIPHERTEXT + AUTH_TAG) with static salt
 */
@Slf4j
public class AesSecretsProvider implements SecretsProvider {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 100000;
    private static final int SALT_LENGTH = 32;
    private static final String LEGACY_PREFIX = "AES:";
    private static final String V2_PREFIX = "AES:v2:";
    private static final String LEGACY_SALT = "PeSITWizardEnterprise";

    private final SecretKey secretKey;
    private final SecretKey legacySecretKey;
    private final boolean available;

    public AesSecretsProvider(
            @Value("${pesitwizard.security.master-key:}") String masterKey,
            @Value("${pesitwizard.security.salt-file:./config/encryption.salt}") String saltFilePath) {

        if (masterKey == null || masterKey.isBlank()) {
            log.warn("No master key configured. AES encryption not available.");
            this.secretKey = null;
            this.legacySecretKey = null;
            this.available = false;
        } else {
            try {
                byte[] salt = loadOrGenerateSalt(Paths.get(saltFilePath));
                this.secretKey = deriveKey(masterKey, salt);
                this.legacySecretKey = deriveKey(masterKey, LEGACY_SALT.getBytes(StandardCharsets.UTF_8));
                this.available = true;
                log.info("AES secrets provider initialized (v2 with dynamic salt)");
            } catch (Exception e) {
                log.error("Failed to initialize AES secrets provider");
                throw new EncryptionException("Failed to initialize AES encryption", e);
            }
        }
    }

    private byte[] loadOrGenerateSalt(Path saltFile) throws IOException {
        if (Files.exists(saltFile)) {
            log.debug("Loading existing salt from {}", saltFile);
            return Files.readAllBytes(saltFile);
        }

        log.info("Generating new salt and saving to {}", saltFile);
        byte[] newSalt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(newSalt);

        Files.createDirectories(saltFile.getParent());
        Files.write(saltFile, newSalt, StandardOpenOption.CREATE_NEW);

        try {
            Files.setPosixFilePermissions(saltFile,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException e) {
            log.warn("Cannot set POSIX permissions on {}", saltFile);
        }

        return newSalt;
    }

    private SecretKey deriveKey(String masterKey, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(masterKey.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        try {
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    @Override
    public String encrypt(String plaintext) {
        if (!available || plaintext == null) {
            return plaintext;
        }
        if (plaintext.startsWith(V2_PREFIX) || plaintext.startsWith(LEGACY_PREFIX)) {
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

            String result = V2_PREFIX + Base64.getEncoder().encodeToString(combined);
            log.debug("Encryption successful (v2 format, {} bytes plaintext)", plaintext.length());
            return result;

        } catch (Exception e) {
            log.error("Encryption failed");
            throw new EncryptionException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (!available || ciphertext == null) {
            return ciphertext;
        }

        if (ciphertext.startsWith(V2_PREFIX)) {
            return decryptV2(ciphertext);
        } else if (ciphertext.startsWith(LEGACY_PREFIX)) {
            return decryptLegacy(ciphertext);
        }

        return ciphertext;
    }

    private String decryptV2(String ciphertext) {
        try {
            String encoded = ciphertext.substring(V2_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(encoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(encrypted);
            log.debug("Decryption successful (v2 format)");
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed (v2)");
            throw new DecryptionException("Decryption failed", e);
        }
    }

    private String decryptLegacy(String ciphertext) {
        try {
            String encoded = ciphertext.substring(LEGACY_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(encoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, legacySecretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(encrypted);
            log.debug("Decryption successful (legacy format - consider re-encrypting)");
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed (legacy)");
            throw new DecryptionException("Decryption failed", e);
        }
    }

    @Override
    public void storeSecret(String key, String value) {
        log.debug("AES provider: storeSecret handled by encrypt()");
    }

    @Override
    public String getSecret(String key) {
        log.debug("AES provider: getSecret handled by decrypt()");
        return null;
    }

    @Override
    public void deleteSecret(String key) {
        log.debug("AES provider: deleteSecret handled by database");
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getProviderType() {
        return "AES";
    }

    public boolean isEncrypted(String value) {
        return value != null && (value.startsWith(V2_PREFIX) || value.startsWith(LEGACY_PREFIX));
    }
}
