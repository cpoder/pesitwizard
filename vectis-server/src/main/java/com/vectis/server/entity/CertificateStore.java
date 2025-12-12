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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for storing keystores and truststores centrally in the database.
 * Supports JKS, PKCS12, and PEM formats.
 * 
 * This enables cluster-wide certificate management where all nodes
 * share the same certificates from the central database.
 */
@Entity
@Table(name = "certificate_stores", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
        "storeType" }), indexes = {
                @Index(name = "idx_cert_name", columnList = "name"),
                @Index(name = "idx_cert_type", columnList = "storeType"),
                @Index(name = "idx_cert_active", columnList = "active"),
                @Index(name = "idx_cert_expiry", columnList = "expiresAt")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique name for this certificate store
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Description of the certificate store
     */
    @Column(length = 500)
    private String description;

    /**
     * Type of store: KEYSTORE or TRUSTSTORE
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreType storeType;

    /**
     * Format of the store: JKS, PKCS12, or PEM
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private StoreFormat format = StoreFormat.PKCS12;

    /**
     * The binary content of the keystore/truststore
     * For PEM format, this contains the concatenated certificates/keys
     */
    @Lob
    @Column(nullable = false, columnDefinition = "BLOB")
    private byte[] storeData;

    /**
     * Password for the store (encrypted)
     * For PEM format with encrypted private keys, this is the key password
     */
    @Column(length = 500)
    private String storePassword;

    /**
     * Key password (for keystores where key password differs from store password)
     */
    @Column(length = 500)
    private String keyPassword;

    /**
     * Alias of the primary key entry (for keystores)
     */
    @Column(length = 100)
    private String keyAlias;

    /**
     * Subject DN of the certificate
     */
    @Column(length = 500)
    private String subjectDn;

    /**
     * Issuer DN of the certificate
     */
    @Column(length = 500)
    private String issuerDn;

    /**
     * Serial number of the certificate
     */
    @Column(length = 100)
    private String serialNumber;

    /**
     * Certificate valid from date
     */
    private Instant validFrom;

    /**
     * Certificate expiration date
     */
    private Instant expiresAt;

    /**
     * SHA-256 fingerprint of the certificate
     */
    @Column(length = 64)
    private String fingerprint;

    /**
     * Whether this store is active and should be used
     */
    @Builder.Default
    private Boolean active = true;

    /**
     * Whether this is the default store for its type
     */
    @Builder.Default
    private Boolean isDefault = false;

    /**
     * Purpose/usage of this certificate
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private CertificatePurpose purpose = CertificatePurpose.SERVER;

    /**
     * Partner ID if this certificate is partner-specific
     */
    @Column(length = 64)
    private String partnerId;

    /**
     * Creation timestamp
     */
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Last update timestamp
     */
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * User who created this store
     */
    @Column(length = 100)
    private String createdBy;

    /**
     * User who last updated this store
     */
    @Column(length = 100)
    private String updatedBy;

    /**
     * Version for optimistic locking
     */
    @jakarta.persistence.Version
    private Long version;

    /**
     * Store type enum
     */
    public enum StoreType {
        KEYSTORE, // Contains private key and certificate chain
        TRUSTSTORE // Contains trusted CA certificates
    }

    /**
     * Store format enum
     */
    public enum StoreFormat {
        JKS, // Java KeyStore
        PKCS12, // PKCS#12 format (recommended)
        PEM // PEM encoded certificates/keys
    }

    /**
     * Certificate purpose enum
     */
    public enum CertificatePurpose {
        SERVER, // Server certificate for TLS
        CLIENT, // Client certificate for mutual TLS
        CA, // Certificate Authority
        PARTNER // Partner-specific certificate
    }

    /**
     * Check if certificate is expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if certificate expires within given days
     */
    public boolean expiresWithinDays(int days) {
        if (expiresAt == null)
            return false;
        Instant threshold = Instant.now().plusSeconds(days * 24L * 60 * 60);
        return expiresAt.isBefore(threshold);
    }

    /**
     * Get days until expiration
     */
    public Long getDaysUntilExpiry() {
        if (expiresAt == null)
            return null;
        long seconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return seconds / (24 * 60 * 60);
    }
}
