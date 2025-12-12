package com.vectis.server.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vectis.server.entity.CertificateStore;
import com.vectis.server.entity.CertificateStore.CertificatePurpose;
import com.vectis.server.entity.CertificateStore.StoreType;

/**
 * Repository for certificate store management.
 */
@Repository
public interface CertificateStoreRepository extends JpaRepository<CertificateStore, Long> {

    /**
     * Find by name and type
     */
    Optional<CertificateStore> findByNameAndStoreType(String name, StoreType storeType);

    /**
     * Find by name
     */
    Optional<CertificateStore> findByName(String name);

    /**
     * Find all by store type
     */
    List<CertificateStore> findByStoreTypeOrderByNameAsc(StoreType storeType);

    /**
     * Find all active stores by type
     */
    List<CertificateStore> findByStoreTypeAndActiveOrderByNameAsc(StoreType storeType, Boolean active);

    /**
     * Find the default store for a type
     */
    Optional<CertificateStore> findByStoreTypeAndIsDefaultTrue(StoreType storeType);

    /**
     * Find active default store for a type
     */
    Optional<CertificateStore> findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType storeType);

    /**
     * Find by purpose
     */
    List<CertificateStore> findByPurposeAndActiveOrderByNameAsc(CertificatePurpose purpose, Boolean active);

    /**
     * Find by partner ID
     */
    List<CertificateStore> findByPartnerIdAndActiveOrderByNameAsc(String partnerId, Boolean active);

    /**
     * Find partner-specific keystore
     */
    Optional<CertificateStore> findByPartnerIdAndStoreTypeAndActiveTrue(String partnerId, StoreType storeType);

    /**
     * Find certificates expiring before a date
     */
    @Query("SELECT c FROM CertificateStore c WHERE c.active = true AND c.expiresAt IS NOT NULL AND c.expiresAt < :date ORDER BY c.expiresAt ASC")
    List<CertificateStore> findExpiringBefore(@Param("date") Instant date);

    /**
     * Find expired certificates
     */
    @Query("SELECT c FROM CertificateStore c WHERE c.active = true AND c.expiresAt IS NOT NULL AND c.expiresAt < CURRENT_TIMESTAMP ORDER BY c.expiresAt ASC")
    List<CertificateStore> findExpired();

    /**
     * Find by fingerprint
     */
    Optional<CertificateStore> findByFingerprint(String fingerprint);

    /**
     * Find by serial number
     */
    Optional<CertificateStore> findBySerialNumber(String serialNumber);

    /**
     * Check if name exists for type
     */
    boolean existsByNameAndStoreType(String name, StoreType storeType);

    /**
     * Count active stores by type
     */
    long countByStoreTypeAndActive(StoreType storeType, Boolean active);

    /**
     * Find all active keystores for server TLS
     */
    @Query("SELECT c FROM CertificateStore c WHERE c.storeType = 'KEYSTORE' AND c.purpose = 'SERVER' AND c.active = true ORDER BY c.isDefault DESC, c.name ASC")
    List<CertificateStore> findActiveServerKeystores();

    /**
     * Find all active truststores
     */
    @Query("SELECT c FROM CertificateStore c WHERE c.storeType = 'TRUSTSTORE' AND c.active = true ORDER BY c.isDefault DESC, c.name ASC")
    List<CertificateStore> findActiveTruststores();
}
