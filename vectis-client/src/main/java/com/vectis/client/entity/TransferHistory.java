package com.vectis.client.entity;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing transfer history/audit log
 */
@Entity
@Table(name = "transfer_history", indexes = {
        @Index(name = "idx_transfer_history_server", columnList = "serverId"),
        @Index(name = "idx_transfer_history_status", columnList = "status"),
        @Index(name = "idx_transfer_history_started", columnList = "startedAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Reference to the server used */
    private String serverId;
    private String serverName;

    /** Partner ID used for authentication */
    private String partnerId;

    /** Transfer direction: SEND, RECEIVE, MESSAGE */
    @Enumerated(EnumType.STRING)
    private TransferDirection direction;

    /** Local filename */
    private String localFilename;

    /** Remote filename */
    private String remoteFilename;

    /** File size in bytes */
    private Long fileSize;

    /** Bytes actually transferred */
    private Long bytesTransferred;

    /** Transfer status */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING;

    /** Error message if failed */
    @Column(length = 2000)
    private String errorMessage;

    /** PeSIT diagnostic code if error */
    private String diagnosticCode;

    /** SHA-256 checksum of transferred data */
    private String checksum;

    /** Transfer config used */
    private String transferConfigId;
    private String transferConfigName;

    /** User who initiated the transfer */
    private String initiatedBy;

    /** Correlation ID for tracing */
    private String correlationId;

    /** OpenTelemetry trace ID */
    private String traceId;

    /** OpenTelemetry span ID */
    private String spanId;

    private Instant startedAt;
    private Instant completedAt;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public enum TransferDirection {
        SEND, RECEIVE, MESSAGE
    }

    public enum TransferStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }

    /** Calculate duration in milliseconds */
    public Long getDurationMs() {
        if (startedAt != null && completedAt != null) {
            return java.time.Duration.between(startedAt, completedAt).toMillis();
        }
        return null;
    }
}
