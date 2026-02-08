package com.pesitwizard.client.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Entity representing a configured storage connection instance.
 * Each instance is created from a connector type with specific configuration.
 */
@Entity
@Table(name = "storage_connections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(unique = true)
    private String name;

    private String description;

    /** Connector type (e.g., "local", "sftp", "s3") */
    @NotBlank
    private String connectorType;

    /** JSON configuration for the connector (may contain credentials) */
    @Lob
    @Column(columnDefinition = "TEXT")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String configJson;

    /** Whether this connection is enabled */
    @Builder.Default
    private boolean enabled = true;

    /** Last successful connection test time */
    private Instant lastTestedAt;

    /** Last connection test result */
    private Boolean lastTestSuccess;

    /** Last error message if test failed */
    private String lastTestError;

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
