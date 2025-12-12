package com.vectis.server.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a PeSIT server instance configuration.
 * Persisted to database for management of multiple server instances.
 */
@Entity
@Table(name = "pesit_server_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PesitServerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String serverId;

    @Column(nullable = false, unique = true)
    private int port;

    @Column
    @Builder.Default
    private String bindAddress = "0.0.0.0";

    @Column
    @Builder.Default
    private int protocolVersion = 2;

    @Column
    @Builder.Default
    private int maxConnections = 100;

    @Column
    @Builder.Default
    private int connectionTimeout = 30000;

    @Column
    @Builder.Default
    private int readTimeout = 60000;

    @Column
    @Builder.Default
    private String receiveDirectory = "./received";

    @Column
    @Builder.Default
    private String sendDirectory = "./send";

    @Column
    @Builder.Default
    private int maxEntitySize = 4096;

    @Column
    @Builder.Default
    private boolean syncPointsEnabled = true;

    @Column
    @Builder.Default
    private boolean resyncEnabled = true;

    @Column
    @Builder.Default
    private boolean strictPartnerCheck = true;

    @Column
    @Builder.Default
    private boolean strictFileCheck = true;

    @Column
    @Builder.Default
    private boolean autoStart = false;

    @Column
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ServerStatus status = ServerStatus.STOPPED;

    @Column
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @Column
    private Instant lastStartedAt;

    @Column
    private Instant lastStoppedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum ServerStatus {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING,
        ERROR
    }
}
