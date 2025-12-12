package com.vectis.server.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for storing encrypted secrets in the database.
 * Provides centralized secret management for the cluster.
 */
@Entity
@Table(name = "secrets", indexes = {
        @Index(name = "idx_secret_name", columnList = "name"),
        @Index(name = "idx_secret_type", columnList = "secretType"),
        @Index(name = "idx_secret_scope", columnList = "scope")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecretEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique name for this secret
     */
    @Column(nullable = false, unique = true, length = 255)
    private String name;

    /**
     * Description of the secret
     */
    @Column(length = 500)
    private String description;

    /**
     * Type of secret
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SecretType secretType = SecretType.GENERIC;

    /**
     * Encrypted value of the secret
     * Uses AES-256-GCM encryption
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedValue;

    /**
     * Initialization vector for encryption
     */
    @Column(nullable = false, length = 32)
    private String iv;

    /**
     * Scope of the secret
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SecretScope scope = SecretScope.GLOBAL;

    /**
     * Partner ID if scope is PARTNER
     */
    @Column(length = 64)
    private String partnerId;

    /**
     * Server ID if scope is SERVER
     */
    @Column(length = 64)
    private String serverId;

    /**
     * Version number for secret rotation
     */
    @Builder.Default
    private Integer version = 1;

    /**
     * Whether this secret is active
     */
    @Builder.Default
    private Boolean active = true;

    /**
     * Expiration date (null = never expires)
     */
    private Instant expiresAt;

    /**
     * Last rotation date
     */
    private Instant lastRotatedAt;

    /**
     * Creation timestamp
     */
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Last update timestamp
     */
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * User who created this secret
     */
    @Column(length = 100)
    private String createdBy;

    /**
     * User who last updated this secret
     */
    @Column(length = 100)
    private String updatedBy;

    /**
     * Secret type enum
     */
    public enum SecretType {
        GENERIC, // Generic secret value
        PASSWORD, // Password
        API_KEY, // External API key
        DATABASE, // Database credentials
        CERTIFICATE, // Certificate password
        ENCRYPTION_KEY, // Encryption key
        TOKEN, // OAuth token or similar
        CONNECTION_STRING // Connection string
    }

    /**
     * Secret scope enum
     */
    public enum SecretScope {
        GLOBAL, // Available to all servers
        SERVER, // Specific to a server instance
        PARTNER // Specific to a partner
    }

    /**
     * Check if secret is expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if secret is valid (active and not expired)
     */
    public boolean isValid() {
        return active && !isExpired();
    }
}
