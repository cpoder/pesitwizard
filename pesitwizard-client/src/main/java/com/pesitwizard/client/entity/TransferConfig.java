package com.pesitwizard.client.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing transfer configuration profiles
 */
@Entity
@Table(name = "transfer_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(unique = true)
    private String name;

    private String description;

    /** Chunk size for data transfer in bytes */
    @Builder.Default
    @Min(56)
    @Max(1048576)
    private Integer chunkSize = 32768; // 32KB - good balance for performance and compatibility

    /** Enable compression (PI 21) */
    @Builder.Default
    private boolean compressionEnabled = false;

    /** Compression algorithm: 0=none, 1=horizontal, 2=vertical */
    @Builder.Default
    @Min(0)
    @Max(2)
    private Integer compressionType = 0;

    /** Enable CRC checking */
    @Builder.Default
    private boolean crcEnabled = true;

    /** Enable sync points for restart capability */
    @Builder.Default
    private boolean syncPointsEnabled = false;

    /** Sync point interval (number of records) */
    @Builder.Default
    @Min(1)
    private Integer syncPointInterval = 100;

    /** Enable resynchronization */
    @Builder.Default
    private boolean resyncEnabled = false;

    /** Transfer priority (0-9, 0=highest) */
    @Builder.Default
    @Min(0)
    @Max(9)
    private Integer priority = 5;

    /** Maximum retry attempts */
    @Builder.Default
    @Min(0)
    @Max(10)
    private Integer maxRetries = 3;

    /** Retry delay in milliseconds */
    @Builder.Default
    @Min(1000)
    private Integer retryDelayMs = 5000;

    /** Record format: F=fixed, V=variable, U=undefined */
    @Builder.Default
    private String recordFormat = "V";

    /** Record length (for fixed format) */
    @Builder.Default
    private Integer recordLength = 1024;

    /** Data code: A=ASCII, E=EBCDIC, B=Binary */
    @Builder.Default
    private String dataCode = "B";

    /** Whether this is the default config */
    @Builder.Default
    private boolean defaultConfig = false;

    /** Source storage connection ID (null = local filesystem) */
    private String sourceConnectionId;

    /** Source path pattern on the storage connection */
    private String sourcePath;

    /** Destination storage connection ID (null = local filesystem) */
    private String destinationConnectionId;

    /** Destination path pattern on the storage connection */
    private String destinationPath;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
