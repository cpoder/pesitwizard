package com.vectis.server.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vectis.server.entity.ApiKey;
import com.vectis.server.repository.ApiKeyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for API key management.
 * Handles creation, validation, and lifecycle of API keys.
 */
@SuppressWarnings("null") // Spring Data JPA methods never return null for save/findById
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int KEY_LENGTH = 32; // 256 bits
    private static final String KEY_PREFIX = "psk_"; // PeSIT Server Key

    /**
     * Generate a new API key
     * 
     * @return The plain text key (only returned once, never stored)
     */
    @Transactional
    public ApiKeyResult createApiKey(String name, String description, List<String> roles,
            Instant expiresAt, String allowedIps, Integer rateLimit, String partnerId, String createdBy) {

        if (apiKeyRepository.existsByName(name)) {
            throw new IllegalArgumentException("API key with name already exists: " + name);
        }

        // Generate random key
        byte[] keyBytes = new byte[KEY_LENGTH];
        secureRandom.nextBytes(keyBytes);
        String plainKey = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);

        // Hash the key for storage
        String keyHash = hashKey(plainKey);
        String keyPrefix = plainKey.substring(0, 8);

        ApiKey apiKey = ApiKey.builder()
                .name(name)
                .description(description)
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .roles(roles != null ? roles : List.of("USER"))
                .active(true)
                .expiresAt(expiresAt)
                .allowedIps(allowedIps)
                .rateLimit(rateLimit)
                .partnerId(partnerId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy(createdBy)
                .build();

        apiKey = apiKeyRepository.save(apiKey);
        log.info("Created API key: {} (prefix: {})", name, keyPrefix);

        return new ApiKeyResult(apiKey, plainKey);
    }

    /**
     * Validate an API key and return the associated entity
     */
    @Transactional
    public Optional<ApiKey> validateKey(String plainKey, String clientIp) {
        if (plainKey == null || plainKey.isBlank()) {
            return Optional.empty();
        }

        String keyHash = hashKey(plainKey);
        Optional<ApiKey> optKey = apiKeyRepository.findActiveByKeyHash(keyHash);

        if (optKey.isEmpty()) {
            log.debug("API key not found or inactive");
            return Optional.empty();
        }

        ApiKey apiKey = optKey.get();

        // Check expiration
        if (apiKey.isExpired()) {
            log.debug("API key expired: {}", apiKey.getName());
            return Optional.empty();
        }

        // Check IP restriction
        if (clientIp != null && !apiKey.isIpAllowed(clientIp)) {
            log.warn("API key IP not allowed: {} from {}", apiKey.getName(), clientIp);
            return Optional.empty();
        }

        // Update last used
        apiKey.setLastUsedAt(Instant.now());
        apiKeyRepository.save(apiKey);

        return Optional.of(apiKey);
    }

    /**
     * Get API key by ID
     */
    public Optional<ApiKey> getApiKey(Long id) {
        return apiKeyRepository.findById(id);
    }

    /**
     * Get API key by name
     */
    public Optional<ApiKey> getApiKeyByName(String name) {
        return apiKeyRepository.findByName(name);
    }

    /**
     * List all API keys
     */
    public List<ApiKey> getAllApiKeys() {
        return apiKeyRepository.findAll();
    }

    /**
     * List active API keys
     */
    public List<ApiKey> getActiveApiKeys() {
        return apiKeyRepository.findByActiveTrue();
    }

    /**
     * Update API key
     */
    @Transactional
    public ApiKey updateApiKey(Long id, String description, List<String> roles,
            Boolean active, Instant expiresAt, String allowedIps, Integer rateLimit) {

        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + id));

        if (description != null) {
            apiKey.setDescription(description);
        }
        if (roles != null) {
            apiKey.setRoles(roles);
        }
        if (active != null) {
            apiKey.setActive(active);
        }
        if (expiresAt != null) {
            apiKey.setExpiresAt(expiresAt);
        }
        if (allowedIps != null) {
            apiKey.setAllowedIps(allowedIps);
        }
        if (rateLimit != null) {
            apiKey.setRateLimit(rateLimit);
        }

        apiKey.setUpdatedAt(Instant.now());
        return apiKeyRepository.save(apiKey);
    }

    /**
     * Revoke (deactivate) an API key
     */
    @Transactional
    public void revokeApiKey(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + id));

        apiKey.setActive(false);
        apiKey.setUpdatedAt(Instant.now());
        apiKeyRepository.save(apiKey);

        log.info("Revoked API key: {}", apiKey.getName());
    }

    /**
     * Delete an API key
     */
    @Transactional
    public void deleteApiKey(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + id));

        apiKeyRepository.delete(apiKey);
        log.info("Deleted API key: {}", apiKey.getName());
    }

    /**
     * Regenerate an API key (creates new key, invalidates old)
     */
    @Transactional
    public ApiKeyResult regenerateApiKey(Long id) {
        ApiKey existing = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + id));

        // Generate new key
        byte[] keyBytes = new byte[KEY_LENGTH];
        secureRandom.nextBytes(keyBytes);
        String plainKey = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);

        // Update with new hash
        existing.setKeyHash(hashKey(plainKey));
        existing.setKeyPrefix(plainKey.substring(0, 8));
        existing.setUpdatedAt(Instant.now());

        existing = apiKeyRepository.save(existing);
        log.info("Regenerated API key: {}", existing.getName());

        return new ApiKeyResult(existing, plainKey);
    }

    /**
     * Hash an API key using SHA-256
     */
    private String hashKey(String plainKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainKey.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash API key", e);
        }
    }

    /**
     * Result of API key creation (includes plain key)
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ApiKeyResult {
        private ApiKey apiKey;
        private String plainKey; // Only available at creation time
    }
}
