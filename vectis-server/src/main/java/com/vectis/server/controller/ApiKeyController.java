package com.vectis.server.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.server.entity.ApiKey;
import com.vectis.server.security.ApiKeyService;
import com.vectis.server.security.ApiKeyService.ApiKeyResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for API key management.
 * All endpoints require ADMIN role.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/apikeys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /**
     * List all API keys
     */
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys() {
        List<ApiKeyResponse> keys = apiKeyService.getAllApiKeys().stream()
                .map(ApiKeyResponse::from)
                .toList();
        return ResponseEntity.ok(keys);
    }

    /**
     * List active API keys
     */
    @GetMapping("/active")
    public ResponseEntity<List<ApiKeyResponse>> listActiveApiKeys() {
        List<ApiKeyResponse> keys = apiKeyService.getActiveApiKeys().stream()
                .map(ApiKeyResponse::from)
                .toList();
        return ResponseEntity.ok(keys);
    }

    /**
     * Get API key by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiKeyResponse> getApiKey(@PathVariable Long id) {
        return apiKeyService.getApiKey(id)
                .map(key -> ResponseEntity.ok(ApiKeyResponse.from(key)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new API key
     */
    @PostMapping
    public ResponseEntity<?> createApiKey(@RequestBody CreateApiKeyRequest request) {
        try {
            ApiKeyResult result = apiKeyService.createApiKey(
                    request.getName(),
                    request.getDescription(),
                    request.getRoles(),
                    request.getExpiresAt(),
                    request.getAllowedIps(),
                    request.getRateLimit(),
                    request.getPartnerId(),
                    "api" // TODO: Get from security context
            );

            log.info("Created API key: {}", request.getName());

            // Return the plain key only once
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new CreateApiKeyResponse(
                            ApiKeyResponse.from(result.getApiKey()),
                            result.getPlainKey()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Update an API key
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateApiKey(@PathVariable Long id, @RequestBody UpdateApiKeyRequest request) {
        try {
            ApiKey key = apiKeyService.updateApiKey(
                    id,
                    request.getDescription(),
                    request.getRoles(),
                    request.getActive(),
                    request.getExpiresAt(),
                    request.getAllowedIps(),
                    request.getRateLimit());
            return ResponseEntity.ok(ApiKeyResponse.from(key));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Revoke (deactivate) an API key
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<Void> revokeApiKey(@PathVariable Long id) {
        try {
            apiKeyService.revokeApiKey(id);
            log.info("Revoked API key: {}", id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Regenerate an API key
     */
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<?> regenerateApiKey(@PathVariable Long id) {
        try {
            ApiKeyResult result = apiKeyService.regenerateApiKey(id);
            log.info("Regenerated API key: {}", result.getApiKey().getName());

            return ResponseEntity.ok(new CreateApiKeyResponse(
                    ApiKeyResponse.from(result.getApiKey()),
                    result.getPlainKey()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete an API key
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiKey(@PathVariable Long id) {
        try {
            apiKeyService.deleteApiKey(id);
            log.info("Deleted API key: {}", id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ========== Request/Response DTOs ==========

    @lombok.Data
    public static class CreateApiKeyRequest {
        private String name;
        private String description;
        private List<String> roles;
        private Instant expiresAt;
        private String allowedIps;
        private Integer rateLimit;
        private String partnerId;
    }

    @lombok.Data
    public static class UpdateApiKeyRequest {
        private String description;
        private List<String> roles;
        private Boolean active;
        private Instant expiresAt;
        private String allowedIps;
        private Integer rateLimit;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CreateApiKeyResponse {
        private ApiKeyResponse apiKey;
        private String key; // Plain key - only returned at creation
    }

    @lombok.Data
    @lombok.Builder
    public static class ApiKeyResponse {
        private Long id;
        private String name;
        private String description;
        private String keyPrefix;
        private List<String> roles;
        private Boolean active;
        private Instant expiresAt;
        private Instant lastUsedAt;
        private String allowedIps;
        private Integer rateLimit;
        private String partnerId;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;
        private boolean expired;

        public static ApiKeyResponse from(ApiKey key) {
            return ApiKeyResponse.builder()
                    .id(key.getId())
                    .name(key.getName())
                    .description(key.getDescription())
                    .keyPrefix(key.getKeyPrefix())
                    .roles(key.getRoles())
                    .active(key.getActive())
                    .expiresAt(key.getExpiresAt())
                    .lastUsedAt(key.getLastUsedAt())
                    .allowedIps(key.getAllowedIps())
                    .rateLimit(key.getRateLimit())
                    .partnerId(key.getPartnerId())
                    .createdAt(key.getCreatedAt())
                    .updatedAt(key.getUpdatedAt())
                    .createdBy(key.getCreatedBy())
                    .expired(key.isExpired())
                    .build();
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ErrorResponse {
        private String error;
    }
}
