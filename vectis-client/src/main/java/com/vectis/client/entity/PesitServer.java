package com.vectis.client.entity;

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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a configured PeSIT server
 */
@Entity
@Table(name = "pesit_servers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PesitServer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(unique = true)
    private String name;

    @NotBlank
    private String host;

    @NotNull
    @Min(1)
    @Max(65535)
    private Integer port;

    @NotBlank
    private String serverId;

    private String description;

    /** Enable TLS/SSL */
    @Builder.Default
    private boolean tlsEnabled = false;

    /** Path to truststore for TLS */
    private String truststorePath;

    /** Truststore password (encrypted) */
    private String truststorePassword;

    /** Path to keystore for mutual TLS */
    private String keystorePath;

    /** Keystore password (encrypted) */
    private String keystorePassword;

    /** Connection timeout in milliseconds */
    @Builder.Default
    private Integer connectionTimeout = 30000;

    /** Read timeout in milliseconds */
    @Builder.Default
    private Integer readTimeout = 60000;

    /** Whether this server is enabled */
    @Builder.Default
    private boolean enabled = true;

    /** Default server flag */
    @Builder.Default
    private boolean defaultServer = false;

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
