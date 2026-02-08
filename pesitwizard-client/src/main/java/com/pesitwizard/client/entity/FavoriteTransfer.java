package com.pesitwizard.client.entity;

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
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Favorite transfer configuration for quick replay
 */
@Entity
@Table(name = "favorite_transfers", indexes = {
        @Index(name = "idx_favorite_name", columnList = "name"),
        @Index(name = "idx_favorite_server", columnList = "serverId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** User-friendly name for this favorite */
    @Column(nullable = false)
    private String name;

    /** Optional description */
    private String description;

    /** Server ID to connect to */
    @Column(nullable = false)
    private String serverId;

    /** Server name for display */
    private String serverName;

    /** Partner ID for authentication */
    private String partnerId;

    /** Transfer direction */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferHistory.TransferDirection direction;

    /** Source storage connection ID (null = local filesystem) */
    private String sourceConnectionId;

    /** Destination storage connection ID (null = local filesystem) */
    private String destinationConnectionId;

    /** Filename (relative path on connector, or local path if no connector) */
    private String filename;

    /** @deprecated Use filename instead */
    @Deprecated
    private String localPath;

    /** Remote filename (virtual file ID) */
    private String remoteFilename;

    /** Virtual file name */
    private String virtualFile;

    /** Encrypted PeSIT partner password (encrypted via SecretsService) */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;

    /** Transfer config ID */
    private String transferConfigId;

    /** Number of times this favorite has been used */
    @Builder.Default
    private int usageCount = 0;

    /** Last time this favorite was used */
    private Instant lastUsedAt;

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

    /**
     * Increment usage count and update last used timestamp
     */
    public void markUsed() {
        this.usageCount++;
        this.lastUsedAt = Instant.now();
    }
}
