package com.vectis.server.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for storing API keys in the database.
 * Supports role-based access control and key expiration.
 */
@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_apikey_key", columnList = "keyHash"),
        @Index(name = "idx_apikey_name", columnList = "name"),
        @Index(name = "idx_apikey_active", columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique name for this API key
     */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Description of the API key purpose
     */
    @Column(length = 500)
    private String description;

    /**
     * SHA-256 hash of the API key (never store plain text)
     */
    @Column(nullable = false, unique = true, length = 64)
    private String keyHash;

    /**
     * Key prefix for identification (first 8 chars)
     */
    @Column(nullable = false, length = 8)
    private String keyPrefix;

    /**
     * Roles assigned to this API key
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_roles", joinColumns = @JoinColumn(name = "api_key_id"))
    @Column(name = "role")
    @Builder.Default
    private List<String> roles = new ArrayList<>();

    /**
     * Whether this key is active
     */
    @Builder.Default
    private Boolean active = true;

    /**
     * Expiration date (null = never expires)
     */
    private Instant expiresAt;

    /**
     * Last used timestamp
     */
    private Instant lastUsedAt;

    /**
     * IP address restrictions (comma-separated, null = no restriction)
     */
    @Column(length = 1000)
    private String allowedIps;

    /**
     * Rate limit (requests per minute, null = no limit)
     */
    private Integer rateLimit;

    /**
     * Partner ID if this key is partner-specific
     */
    @Column(length = 64)
    private String partnerId;

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
     * User who created this key
     */
    @Column(length = 100)
    private String createdBy;

    /**
     * Check if key is expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if key is valid (active and not expired)
     */
    public boolean isValid() {
        return active && !isExpired();
    }

    /**
     * Check if IP is allowed
     */
    public boolean isIpAllowed(String ip) {
        if (allowedIps == null || allowedIps.isBlank()) {
            return true;
        }
        String[] allowed = allowedIps.split(",");
        for (String allowedIp : allowed) {
            if (allowedIp.trim().equals(ip) || allowedIp.trim().equals("*")) {
                return true;
            }
            // Simple CIDR support (e.g., 192.168.1.0/24)
            if (allowedIp.contains("/") && matchesCidr(ip, allowedIp.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple CIDR matching
     */
    private boolean matchesCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String network = parts[0];
            int prefix = Integer.parseInt(parts[1]);

            long ipLong = ipToLong(ip);
            long networkLong = ipToLong(network);
            long mask = -1L << (32 - prefix);

            return (ipLong & mask) == (networkLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        return (Long.parseLong(parts[0]) << 24) +
                (Long.parseLong(parts[1]) << 16) +
                (Long.parseLong(parts[2]) << 8) +
                Long.parseLong(parts[3]);
    }
}
