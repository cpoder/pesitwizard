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

import com.vectis.server.entity.SecretEntry;
import com.vectis.server.entity.SecretEntry.SecretScope;
import com.vectis.server.entity.SecretEntry.SecretType;
import com.vectis.server.service.SecretService;
import com.vectis.server.service.SecretService.SecretStatistics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for secret management.
 * All endpoints require ADMIN role.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/secrets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SecretController {

    private final SecretService secretService;

    // ========== List & Get ==========

    /**
     * List all secrets (without values)
     */
    @GetMapping
    public ResponseEntity<List<SecretResponse>> listSecrets() {
        List<SecretResponse> secrets = secretService.getAllSecrets().stream()
                .map(SecretResponse::from)
                .toList();
        return ResponseEntity.ok(secrets);
    }

    /**
     * List secrets by type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<SecretResponse>> listSecretsByType(@PathVariable SecretType type) {
        List<SecretResponse> secrets = secretService.getSecretsByType(type).stream()
                .map(SecretResponse::from)
                .toList();
        return ResponseEntity.ok(secrets);
    }

    /**
     * List secrets by scope
     */
    @GetMapping("/scope/{scope}")
    public ResponseEntity<List<SecretResponse>> listSecretsByScope(@PathVariable SecretScope scope) {
        List<SecretResponse> secrets = secretService.getSecretsByScope(scope).stream()
                .map(SecretResponse::from)
                .toList();
        return ResponseEntity.ok(secrets);
    }

    /**
     * Get secret by ID (without value)
     */
    @GetMapping("/{id}")
    public ResponseEntity<SecretResponse> getSecret(@PathVariable Long id) {
        return secretService.getSecretById(id)
                .map(secret -> ResponseEntity.ok(SecretResponse.from(secret)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get secret by name (without value)
     */
    @GetMapping("/name/{name}")
    public ResponseEntity<SecretResponse> getSecretByName(@PathVariable String name) {
        return secretService.getSecret(name)
                .map(secret -> ResponseEntity.ok(SecretResponse.from(secret)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get decrypted secret value
     */
    @GetMapping("/name/{name}/value")
    public ResponseEntity<?> getSecretValue(@PathVariable String name) {
        return secretService.getSecretValue(name)
                .map(value -> ResponseEntity.ok(new SecretValueResponse(name, value)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get secrets for a partner
     */
    @GetMapping("/partner/{partnerId}")
    public ResponseEntity<List<SecretResponse>> getSecretsForPartner(@PathVariable String partnerId) {
        List<SecretResponse> secrets = secretService.getSecretsForPartner(partnerId).stream()
                .map(SecretResponse::from)
                .toList();
        return ResponseEntity.ok(secrets);
    }

    /**
     * Get secrets for a server
     */
    @GetMapping("/server/{serverId}")
    public ResponseEntity<List<SecretResponse>> getSecretsForServer(@PathVariable String serverId) {
        List<SecretResponse> secrets = secretService.getSecretsForServer(serverId).stream()
                .map(SecretResponse::from)
                .toList();
        return ResponseEntity.ok(secrets);
    }

    // ========== Create ==========

    /**
     * Create a new secret
     */
    @PostMapping
    public ResponseEntity<?> createSecret(@RequestBody CreateSecretRequest request) {
        try {
            SecretEntry secret = secretService.createSecret(
                    request.getName(),
                    request.getValue(),
                    request.getDescription(),
                    request.getType() != null ? request.getType() : SecretType.GENERIC,
                    request.getScope() != null ? request.getScope() : SecretScope.GLOBAL,
                    request.getPartnerId(),
                    request.getServerId(),
                    request.getExpiresAt(),
                    "api" // TODO: Get from security context
            );

            log.info("Created secret: {}", request.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(SecretResponse.from(secret));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    // ========== Update ==========

    /**
     * Update secret value
     */
    @PutMapping("/name/{name}/value")
    public ResponseEntity<?> updateSecretValue(@PathVariable String name,
            @RequestBody UpdateSecretValueRequest request) {
        try {
            SecretEntry secret = secretService.updateSecretValue(name, request.getValue(), "api");
            log.info("Updated secret value: {}", name);
            return ResponseEntity.ok(SecretResponse.from(secret));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update secret metadata
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateSecretMetadata(@PathVariable Long id,
            @RequestBody UpdateSecretMetadataRequest request) {
        try {
            SecretEntry secret = secretService.updateSecretMetadata(
                    id,
                    request.getDescription(),
                    request.getType(),
                    request.getActive(),
                    request.getExpiresAt(),
                    "api");
            return ResponseEntity.ok(SecretResponse.from(secret));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Rotate a secret
     */
    @PostMapping("/name/{name}/rotate")
    public ResponseEntity<?> rotateSecret(@PathVariable String name, @RequestBody UpdateSecretValueRequest request) {
        try {
            SecretEntry secret = secretService.rotateSecret(name, request.getValue(), "api");
            log.info("Rotated secret: {} (version: {})", name, secret.getVersion());
            return ResponseEntity.ok(SecretResponse.from(secret));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ========== Delete ==========

    /**
     * Delete a secret
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSecret(@PathVariable Long id) {
        try {
            secretService.deleteSecret(id);
            log.info("Deleted secret: {}", id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Deactivate a secret
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<SecretResponse> deactivateSecret(@PathVariable Long id) {
        try {
            SecretEntry secret = secretService.deactivateSecret(id);
            log.info("Deactivated secret: {}", secret.getName());
            return ResponseEntity.ok(SecretResponse.from(secret));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ========== Utilities ==========

    /**
     * Generate a new encryption key
     */
    @GetMapping("/generate-key")
    public ResponseEntity<GeneratedKeyResponse> generateEncryptionKey() {
        String key = secretService.generateEncryptionKey();
        return ResponseEntity.ok(new GeneratedKeyResponse(key));
    }

    /**
     * Get secret statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<SecretStatistics> getStatistics() {
        return ResponseEntity.ok(secretService.getStatistics());
    }

    // ========== Request/Response DTOs ==========

    @lombok.Data
    public static class CreateSecretRequest {
        private String name;
        private String value;
        private String description;
        private SecretType type;
        private SecretScope scope;
        private String partnerId;
        private String serverId;
        private Instant expiresAt;
    }

    @lombok.Data
    public static class UpdateSecretValueRequest {
        private String value;
    }

    @lombok.Data
    public static class UpdateSecretMetadataRequest {
        private String description;
        private SecretType type;
        private Boolean active;
        private Instant expiresAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class SecretResponse {
        private Long id;
        private String name;
        private String description;
        private SecretType type;
        private SecretScope scope;
        private String partnerId;
        private String serverId;
        private Integer version;
        private Boolean active;
        private Instant expiresAt;
        private Instant lastRotatedAt;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;
        private String updatedBy;
        private boolean expired;
        private boolean valid;

        public static SecretResponse from(SecretEntry secret) {
            return SecretResponse.builder()
                    .id(secret.getId())
                    .name(secret.getName())
                    .description(secret.getDescription())
                    .type(secret.getSecretType())
                    .scope(secret.getScope())
                    .partnerId(secret.getPartnerId())
                    .serverId(secret.getServerId())
                    .version(secret.getVersion())
                    .active(secret.getActive())
                    .expiresAt(secret.getExpiresAt())
                    .lastRotatedAt(secret.getLastRotatedAt())
                    .createdAt(secret.getCreatedAt())
                    .updatedAt(secret.getUpdatedAt())
                    .createdBy(secret.getCreatedBy())
                    .updatedBy(secret.getUpdatedBy())
                    .expired(secret.isExpired())
                    .valid(secret.isValid())
                    .build();
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SecretValueResponse {
        private String name;
        private String value;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class GeneratedKeyResponse {
        private String key;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ErrorResponse {
        private String error;
    }
}
