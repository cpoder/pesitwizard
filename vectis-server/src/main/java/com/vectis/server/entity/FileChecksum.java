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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for storing file checksums for integrity verification.
 * Supports SHA-256 and other hash algorithms.
 */
@Entity
@Table(name = "file_checksums", indexes = {
        @Index(name = "idx_checksum_hash", columnList = "checksumHash"),
        @Index(name = "idx_checksum_filename", columnList = "filename"),
        @Index(name = "idx_checksum_transfer", columnList = "transferId"),
        @Index(name = "idx_checksum_partner", columnList = "partnerId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileChecksum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The checksum hash value (hex encoded)
     */
    @Column(nullable = false, length = 128)
    private String checksumHash;

    /**
     * Hash algorithm used (SHA-256, SHA-512, MD5, etc.)
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private HashAlgorithm algorithm = HashAlgorithm.SHA_256;

    /**
     * Original filename
     */
    @Column(nullable = false)
    private String filename;

    /**
     * File size in bytes
     */
    @Column(nullable = false)
    private Long fileSize;

    /**
     * Associated transfer ID (if any)
     */
    @Column
    private String transferId;

    /**
     * Partner ID associated with the file
     */
    @Column
    private String partnerId;

    /**
     * Server ID where the file was received/sent
     */
    @Column
    private String serverId;

    /**
     * Direction of transfer
     */
    @Column
    @Enumerated(EnumType.STRING)
    private TransferDirection direction;

    /**
     * Local file path
     */
    @Column(length = 1024)
    private String localPath;

    /**
     * Verification status
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    /**
     * When the checksum was computed
     */
    @Column
    private Instant computedAt;

    /**
     * When the file was last verified
     */
    @Column
    private Instant verifiedAt;

    /**
     * Number of times this exact file has been seen (duplicate detection)
     */
    @Column
    @Builder.Default
    private Integer duplicateCount = 0;

    /**
     * First time this checksum was seen
     */
    @Column
    private Instant firstSeenAt;

    /**
     * Additional metadata (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (computedAt == null) {
            computedAt = Instant.now();
        }
        if (firstSeenAt == null) {
            firstSeenAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Supported hash algorithms
     */
    public enum HashAlgorithm {
        MD5,
        SHA_1,
        SHA_256,
        SHA_512
    }

    /**
     * Transfer direction
     */
    public enum TransferDirection {
        INBOUND,
        OUTBOUND
    }

    /**
     * Verification status
     */
    public enum VerificationStatus {
        PENDING, // Checksum computed, not yet verified
        VERIFIED, // Checksum verified successfully
        FAILED, // Verification failed (file corrupted or modified)
        DUPLICATE, // File is a duplicate of an existing file
        MISSING // File no longer exists at the path
    }
}
