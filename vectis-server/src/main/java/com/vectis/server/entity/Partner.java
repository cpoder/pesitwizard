package com.vectis.server.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a PeSIT partner (remote system that can connect).
 * Partners are identified by their ID which must match PI 3 (Demandeur).
 */
@Entity
@Table(name = "partners")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Partner {

    /**
     * Partner identifier - must match PI 3 (Demandeur)
     */
    @Id
    @Column(length = 64)
    private String id;

    /**
     * Partner description
     */
    private String description;

    /**
     * Password for access control (PI 5) - empty means no password required
     */
    private String password;

    /**
     * Whether this partner is enabled
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Allowed access types: READ, WRITE, BOTH
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AccessType accessType = AccessType.BOTH;

    /**
     * Maximum concurrent connections from this partner
     */
    @Builder.Default
    private int maxConnections = 10;

    /**
     * Comma-separated list of allowed file patterns (empty = all)
     */
    @Column(length = 1000)
    private String allowedFiles;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum AccessType {
        READ, // Partner can only read (SELECT)
        WRITE, // Partner can only write (CREATE)
        BOTH // Partner can read and write
    }

    /**
     * Check if partner can perform write operations
     */
    public boolean canWrite() {
        return accessType == AccessType.WRITE || accessType == AccessType.BOTH;
    }

    /**
     * Check if partner can perform read operations
     */
    public boolean canRead() {
        return accessType == AccessType.READ || accessType == AccessType.BOTH;
    }

    /**
     * Check if partner can access a specific file
     */
    public boolean canAccessFile(String filename) {
        if (allowedFiles == null || allowedFiles.isEmpty()) {
            return true; // No restrictions
        }
        String[] patterns = allowedFiles.split(",");
        for (String pattern : patterns) {
            pattern = pattern.trim();
            if (pattern.equals(filename) || filename.matches(pattern.replace("*", ".*"))) {
                return true;
            }
        }
        return false;
    }
}
