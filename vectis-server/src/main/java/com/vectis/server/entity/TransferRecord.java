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
 * Persistent record of a file transfer.
 * Tracks all transfers for audit, retry, and monitoring purposes.
 */
@Entity
@Table(name = "transfer_records", indexes = {
        @Index(name = "idx_transfer_status", columnList = "status"),
        @Index(name = "idx_transfer_partner", columnList = "partnerId"),
        @Index(name = "idx_transfer_filename", columnList = "filename"),
        @Index(name = "idx_transfer_started", columnList = "startedAt"),
        @Index(name = "idx_transfer_server", columnList = "serverId"),
        @Index(name = "idx_transfer_session", columnList = "sessionId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique transfer identifier (UUID)
     */
    @Column(nullable = false, unique = true, length = 36)
    private String transferId;

    /**
     * Session ID this transfer belongs to
     */
    @Column(nullable = false, length = 36)
    private String sessionId;

    /**
     * Server instance handling this transfer
     */
    @Column(nullable = false, length = 64)
    private String serverId;

    /**
     * Cluster node ID (for HA tracking)
     */
    @Column(length = 64)
    private String nodeId;

    /**
     * Transfer direction
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransferDirection direction;

    /**
     * Current transfer status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransferStatus status = TransferStatus.INITIATED;

    /**
     * Partner ID (PI 3 - Demandeur)
     */
    @Column(nullable = false, length = 64)
    private String partnerId;

    /**
     * Logical filename (PI 12)
     */
    @Column(nullable = false, length = 255)
    private String filename;

    /**
     * Local file path on disk
     */
    @Column(length = 1024)
    private String localPath;

    /**
     * File size in bytes (expected or actual)
     */
    @Builder.Default
    private Long fileSize = 0L;

    /**
     * Bytes transferred so far
     */
    @Builder.Default
    private Long bytesTransferred = 0L;

    /**
     * Transfer progress percentage (0-100)
     */
    @Builder.Default
    private Integer progressPercent = 0;

    /**
     * Last sync point position
     */
    @Builder.Default
    private Long lastSyncPoint = 0L;

    /**
     * Number of sync points acknowledged
     */
    @Builder.Default
    private Integer syncPointCount = 0;

    /**
     * Number of retry attempts
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Maximum retry attempts allowed
     */
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * Transfer start time
     */
    @Column(nullable = false)
    private Instant startedAt;

    /**
     * Transfer completion time
     */
    private Instant completedAt;

    /**
     * Last update time
     */
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Remote client address
     */
    @Column(length = 64)
    private String remoteAddress;

    /**
     * PeSIT protocol version used
     */
    @Builder.Default
    private Integer protocolVersion = 2;

    /**
     * Access type (PI 22)
     */
    @Builder.Default
    private Integer accessType = 0;

    /**
     * Error code if transfer failed (PI 2 diagnostic)
     */
    @Column(length = 10)
    private String errorCode;

    /**
     * Error message if transfer failed
     */
    @Column(length = 1024)
    private String errorMessage;

    /**
     * Checksum of the file (SHA-256)
     */
    @Column(length = 64)
    private String checksum;

    /**
     * Checksum algorithm used
     */
    @Column(length = 20)
    @Builder.Default
    private String checksumAlgorithm = "SHA-256";

    /**
     * Whether this transfer can be resumed
     */
    @Builder.Default
    private Boolean resumable = true;

    /**
     * Parent transfer ID (for retries)
     */
    @Column(length = 36)
    private String parentTransferId;

    /**
     * Additional metadata (JSON)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * Transfer direction enum
     */
    public enum TransferDirection {
        SEND, // Server sending to client
        RECEIVE // Server receiving from client
    }

    /**
     * Transfer status enum
     */
    public enum TransferStatus {
        INITIATED, // Transfer request received
        IN_PROGRESS, // Data transfer ongoing
        PAUSED, // Transfer paused (sync point)
        INTERRUPTED, // Transfer interrupted, can resume
        COMPLETED, // Transfer completed successfully
        FAILED, // Transfer failed
        CANCELLED, // Transfer cancelled by user
        RETRY_PENDING // Waiting for retry
    }

    /**
     * Calculate transfer speed in bytes per second
     */
    public Long getTransferSpeed() {
        if (startedAt == null || bytesTransferred == null || bytesTransferred == 0) {
            return 0L;
        }
        Instant end = completedAt != null ? completedAt : Instant.now();
        long durationMs = end.toEpochMilli() - startedAt.toEpochMilli();
        if (durationMs <= 0) {
            return 0L;
        }
        return (bytesTransferred * 1000) / durationMs;
    }

    /**
     * Calculate estimated time remaining in seconds
     */
    public Long getEstimatedTimeRemaining() {
        if (fileSize == null || fileSize == 0 || bytesTransferred == null) {
            return null;
        }
        Long speed = getTransferSpeed();
        if (speed == null || speed == 0) {
            return null;
        }
        long remaining = fileSize - bytesTransferred;
        return remaining / speed;
    }

    /**
     * Update progress based on bytes transferred
     */
    public void updateProgress(long bytes) {
        this.bytesTransferred = bytes;
        this.updatedAt = Instant.now();
        if (fileSize != null && fileSize > 0) {
            this.progressPercent = (int) ((bytes * 100) / fileSize);
        }
    }

    /**
     * Mark transfer as completed
     */
    public void markCompleted() {
        this.status = TransferStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
        this.progressPercent = 100;
    }

    /**
     * Mark transfer as failed
     */
    public void markFailed(String errorCode, String errorMessage) {
        this.status = TransferStatus.FAILED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * Check if transfer can be retried
     */
    public boolean canRetry() {
        return resumable && retryCount < maxRetries &&
                (status == TransferStatus.FAILED || status == TransferStatus.INTERRUPTED);
    }
}
