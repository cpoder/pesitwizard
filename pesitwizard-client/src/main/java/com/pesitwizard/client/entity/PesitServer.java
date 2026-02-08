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
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

    /** Truststore data (PKCS12 format) for TLS */
    @Column(columnDefinition = "bytea")
    @JsonIgnore
    @ToString.Exclude
    private byte[] truststoreData;

    /** Truststore password */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String truststorePassword;

    /** Keystore data (PKCS12 format) for mutual TLS */
    @Column(columnDefinition = "bytea")
    @JsonIgnore
    @ToString.Exclude
    private byte[] keystoreData;

    /** Keystore password */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String keystorePassword;

    /** Whether to verify the server certificate's hostname (CN/SAN) matches the target host.
     *  Defaults to true (secure). Disable only for testing or legacy servers with misconfigured certificates. */
    @Builder.Default
    private boolean hostnameVerification = true;

    /** Use Connect:Express TLS framing (no 2-byte length prefix).
     *  When false (default), uses standard PeSIT framing compatible with PeSIT Wizard server.
     *  Set to true when connecting to Connect:Express servers over TLS. */
    @Builder.Default
    private boolean connectExpressMode = false;

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
