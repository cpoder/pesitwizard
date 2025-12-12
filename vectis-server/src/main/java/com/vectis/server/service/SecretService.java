package com.vectis.server.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vectis.server.entity.SecretEntry;
import com.vectis.server.entity.SecretEntry.SecretScope;
import com.vectis.server.entity.SecretEntry.SecretType;
import com.vectis.server.repository.SecretRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing encrypted secrets.
 * Uses AES-256-GCM for encryption.
 */
@SuppressWarnings("null") // Spring Data JPA methods never return null for save/findById
@Slf4j
@Service
@RequiredArgsConstructor
public class SecretService {

    private final SecretRepository secretRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${pesit.secrets.encryption-key:#{null}}")
    private String configuredEncryptionKey;

    private SecretKey encryptionKey;

    @PostConstruct
    public void init() {
        if (configuredEncryptionKey != null && !configuredEncryptionKey.isBlank()) {
            // Use configured key (from environment or Kubernetes secret)
            byte[] keyBytes = Base64.getDecoder().decode(configuredEncryptionKey);
            encryptionKey = new SecretKeySpec(keyBytes, "AES");
            log.info("Using configured encryption key for secrets");
        } else {
            // Generate a random key (for development - secrets won't survive restart)
            byte[] keyBytes = new byte[32]; // 256 bits
            secureRandom.nextBytes(keyBytes);
            encryptionKey = new SecretKeySpec(keyBytes, "AES");
            log.warn("Using random encryption key - secrets will be lost on restart. " +
                    "Set PESIT_SECRETS_ENCRYPTION_KEY for production.");
        }
    }

    // ========== CRUD Operations ==========

    /**
     * Create a new secret
     */
    @Transactional
    public SecretEntry createSecret(String name, String plainValue, String description,
            SecretType type, SecretScope scope, String partnerId, String serverId,
            Instant expiresAt, String createdBy) {

        if (secretRepository.existsByName(name)) {
            throw new IllegalArgumentException("Secret already exists: " + name);
        }

        // Encrypt the value
        EncryptedData encrypted = encrypt(plainValue);

        SecretEntry secret = SecretEntry.builder()
                .name(name)
                .description(description)
                .secretType(type)
                .encryptedValue(encrypted.ciphertext)
                .iv(encrypted.iv)
                .scope(scope)
                .partnerId(partnerId)
                .serverId(serverId)
                .version(1)
                .active(true)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy(createdBy)
                .build();

        secret = secretRepository.save(secret);
        log.info("Created secret: {} (type: {}, scope: {})", name, type, scope);

        return secret;
    }

    /**
     * Get decrypted secret value
     */
    public Optional<String> getSecretValue(String name) {
        return secretRepository.findByNameAndActiveTrue(name)
                .filter(SecretEntry::isValid)
                .map(secret -> decrypt(secret.getEncryptedValue(), secret.getIv()));
    }

    /**
     * Get secret entry (without decrypted value)
     */
    public Optional<SecretEntry> getSecret(String name) {
        return secretRepository.findByName(name);
    }

    /**
     * Get secret by ID
     */
    public Optional<SecretEntry> getSecretById(Long id) {
        return secretRepository.findById(id);
    }

    /**
     * List all secrets (without values)
     */
    public List<SecretEntry> getAllSecrets() {
        return secretRepository.findAll();
    }

    /**
     * List secrets by type
     */
    public List<SecretEntry> getSecretsByType(SecretType type) {
        return secretRepository.findBySecretTypeOrderByNameAsc(type);
    }

    /**
     * List secrets by scope
     */
    public List<SecretEntry> getSecretsByScope(SecretScope scope) {
        return secretRepository.findByScopeOrderByNameAsc(scope);
    }

    /**
     * Get secrets for a partner
     */
    public List<SecretEntry> getSecretsForPartner(String partnerId) {
        return secretRepository.findActiveSecretsForPartner(partnerId);
    }

    /**
     * Get secrets for a server
     */
    public List<SecretEntry> getSecretsForServer(String serverId) {
        return secretRepository.findActiveSecretsForServer(serverId);
    }

    /**
     * Update a secret value
     */
    @Transactional
    public SecretEntry updateSecretValue(String name, String newPlainValue, String updatedBy) {
        SecretEntry secret = secretRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Secret not found: " + name));

        // Encrypt new value
        EncryptedData encrypted = encrypt(newPlainValue);

        secret.setEncryptedValue(encrypted.ciphertext);
        secret.setIv(encrypted.iv);
        secret.setVersion(secret.getVersion() + 1);
        secret.setLastRotatedAt(Instant.now());
        secret.setUpdatedAt(Instant.now());
        secret.setUpdatedBy(updatedBy);

        secret = secretRepository.save(secret);
        log.info("Updated secret value: {} (version: {})", name, secret.getVersion());

        return secret;
    }

    /**
     * Update secret metadata
     */
    @Transactional
    public SecretEntry updateSecretMetadata(Long id, String description, SecretType type,
            Boolean active, Instant expiresAt, String updatedBy) {

        SecretEntry secret = secretRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Secret not found: " + id));

        if (description != null) {
            secret.setDescription(description);
        }
        if (type != null) {
            secret.setSecretType(type);
        }
        if (active != null) {
            secret.setActive(active);
        }
        if (expiresAt != null) {
            secret.setExpiresAt(expiresAt);
        }

        secret.setUpdatedAt(Instant.now());
        secret.setUpdatedBy(updatedBy);

        return secretRepository.save(secret);
    }

    /**
     * Rotate a secret (update value and increment version)
     */
    @Transactional
    public SecretEntry rotateSecret(String name, String newPlainValue, String rotatedBy) {
        return updateSecretValue(name, newPlainValue, rotatedBy);
    }

    /**
     * Delete a secret
     */
    @Transactional
    public void deleteSecret(Long id) {
        SecretEntry secret = secretRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Secret not found: " + id));

        secretRepository.delete(secret);
        log.info("Deleted secret: {}", secret.getName());
    }

    /**
     * Deactivate a secret
     */
    @Transactional
    public SecretEntry deactivateSecret(Long id) {
        SecretEntry secret = secretRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Secret not found: " + id));

        secret.setActive(false);
        secret.setUpdatedAt(Instant.now());

        return secretRepository.save(secret);
    }

    // ========== Encryption ==========

    /**
     * Encrypt a plain text value
     */
    private EncryptedData encrypt(String plainText) {
        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

            // Encrypt
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return new EncryptedData(
                    Base64.getEncoder().encodeToString(cipherText),
                    Base64.getEncoder().encodeToString(iv));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt secret", e);
        }
    }

    /**
     * Decrypt an encrypted value
     */
    private String decrypt(String cipherText, String ivString) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivString);
            byte[] encrypted = Base64.getDecoder().decode(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

            byte[] plainText = cipher.doFinal(encrypted);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt secret", e);
        }
    }

    /**
     * Generate a new encryption key (for key rotation)
     */
    public String generateEncryptionKey() {
        byte[] keyBytes = new byte[32];
        secureRandom.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    // ========== Statistics ==========

    /**
     * Get secret statistics
     */
    public SecretStatistics getStatistics() {
        SecretStatistics stats = new SecretStatistics();
        stats.setTotalSecrets(secretRepository.count());
        stats.setActiveSecrets(secretRepository.countByActiveTrue());
        stats.setExpiredSecrets(secretRepository.findExpiredSecrets().size());
        stats.setPasswordSecrets(secretRepository.countBySecretType(SecretType.PASSWORD));
        stats.setApiKeySecrets(secretRepository.countBySecretType(SecretType.API_KEY));
        stats.setCertificateSecrets(secretRepository.countBySecretType(SecretType.CERTIFICATE));
        return stats;
    }

    // ========== Helper Classes ==========

    private record EncryptedData(String ciphertext, String iv) {
    }

    @lombok.Data
    public static class SecretStatistics {
        private long totalSecrets;
        private long activeSecrets;
        private int expiredSecrets;
        private long passwordSecrets;
        private long apiKeySecrets;
        private long certificateSecrets;
    }
}
