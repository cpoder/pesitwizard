package com.vectis.server.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vectis.server.entity.CertificateStore;
import com.vectis.server.entity.CertificateStore.CertificatePurpose;
import com.vectis.server.entity.CertificateStore.StoreFormat;
import com.vectis.server.entity.CertificateStore.StoreType;
import com.vectis.server.repository.CertificateStoreRepository;
import com.vectis.server.ssl.SslConfigurationException;
import com.vectis.server.ssl.SslContextFactory;
import com.vectis.server.ssl.SslContextFactory.CertificateInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing SSL certificates centrally.
 * Provides CRUD operations and certificate lifecycle management.
 */
@SuppressWarnings("null") // Spring Data JPA methods never return null for save/findById
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateStoreRepository certificateRepository;
    private final SslContextFactory sslContextFactory;

    // ========== CRUD Operations ==========

    /**
     * Create a new certificate store
     */
    @Transactional
    public CertificateStore createCertificateStore(
            String name,
            String description,
            StoreType storeType,
            StoreFormat format,
            byte[] storeData,
            String storePassword,
            String keyPassword,
            String keyAlias,
            CertificatePurpose purpose,
            String partnerId,
            boolean isDefault,
            String createdBy) throws SslConfigurationException {

        // Check for duplicate name
        if (certificateRepository.existsByNameAndStoreType(name, storeType)) {
            throw new IllegalArgumentException("Certificate store already exists: " + name);
        }

        // Build the entity
        CertificateStore store = CertificateStore.builder()
                .name(name)
                .description(description)
                .storeType(storeType)
                .format(format)
                .storeData(storeData)
                .storePassword(storePassword)
                .keyPassword(keyPassword)
                .keyAlias(keyAlias)
                .purpose(purpose)
                .partnerId(partnerId)
                .isDefault(isDefault)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy(createdBy)
                .build();

        // Validate the store
        sslContextFactory.validateStore(store);

        // Extract certificate info
        try {
            CertificateInfo info = sslContextFactory.extractCertificateInfo(store);
            store.setSubjectDn(info.getSubjectDn());
            store.setIssuerDn(info.getIssuerDn());
            store.setSerialNumber(info.getSerialNumber());
            store.setValidFrom(info.getValidFrom());
            store.setExpiresAt(info.getExpiresAt());
            store.setFingerprint(info.getFingerprint());
            if (keyAlias == null && info.getAlias() != null) {
                store.setKeyAlias(info.getAlias());
            }
        } catch (Exception e) {
            log.warn("Could not extract certificate info: {}", e.getMessage());
        }

        // If this is set as default, unset other defaults
        if (isDefault) {
            unsetOtherDefaults(storeType);
        }

        store = certificateRepository.save(store);
        log.info("Created certificate store: {} ({})", name, storeType);

        return store;
    }

    /**
     * Update an existing certificate store
     */
    @Transactional
    public CertificateStore updateCertificateStore(
            Long id,
            String description,
            byte[] storeData,
            String storePassword,
            String keyPassword,
            String keyAlias,
            Boolean active,
            Boolean isDefault,
            String updatedBy) throws SslConfigurationException {

        CertificateStore store = certificateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate store not found: " + id));

        if (description != null) {
            store.setDescription(description);
        }

        if (storeData != null && storeData.length > 0) {
            store.setStoreData(storeData);
            store.setStorePassword(storePassword);
            store.setKeyPassword(keyPassword);
            if (keyAlias != null) {
                store.setKeyAlias(keyAlias);
            }

            // Validate and extract info
            sslContextFactory.validateStore(store);
            try {
                CertificateInfo info = sslContextFactory.extractCertificateInfo(store);
                store.setSubjectDn(info.getSubjectDn());
                store.setIssuerDn(info.getIssuerDn());
                store.setSerialNumber(info.getSerialNumber());
                store.setValidFrom(info.getValidFrom());
                store.setExpiresAt(info.getExpiresAt());
                store.setFingerprint(info.getFingerprint());
            } catch (Exception e) {
                log.warn("Could not extract certificate info: {}", e.getMessage());
            }
        }

        if (active != null) {
            store.setActive(active);
        }

        if (isDefault != null && isDefault && !store.getIsDefault()) {
            unsetOtherDefaults(store.getStoreType());
            store.setIsDefault(true);
        } else if (isDefault != null && !isDefault) {
            store.setIsDefault(false);
        }

        store.setUpdatedAt(Instant.now());
        store.setUpdatedBy(updatedBy);

        store = certificateRepository.save(store);
        log.info("Updated certificate store: {}", store.getName());

        return store;
    }

    /**
     * Delete a certificate store
     */
    @Transactional
    public void deleteCertificateStore(Long id) {
        CertificateStore store = certificateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate store not found: " + id));

        certificateRepository.delete(store);
        log.info("Deleted certificate store: {}", store.getName());
    }

    /**
     * Get a certificate store by ID
     */
    public Optional<CertificateStore> getCertificateStore(Long id) {
        return certificateRepository.findById(id);
    }

    /**
     * Get a certificate store by name
     */
    public Optional<CertificateStore> getCertificateStoreByName(String name) {
        return certificateRepository.findByName(name);
    }

    /**
     * Get all certificate stores
     */
    public List<CertificateStore> getAllCertificateStores() {
        return certificateRepository.findAll();
    }

    /**
     * Get certificate stores by type
     */
    public List<CertificateStore> getCertificateStoresByType(StoreType type) {
        return certificateRepository.findByStoreTypeOrderByNameAsc(type);
    }

    /**
     * Get active certificate stores by type
     */
    public List<CertificateStore> getActiveCertificateStoresByType(StoreType type) {
        return certificateRepository.findByStoreTypeAndActiveOrderByNameAsc(type, true);
    }

    /**
     * Get the default certificate store for a type
     */
    public Optional<CertificateStore> getDefaultCertificateStore(StoreType type) {
        return certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(type);
    }

    /**
     * Set a certificate store as the default
     */
    @Transactional
    public CertificateStore setAsDefault(Long id) {
        CertificateStore store = certificateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate store not found: " + id));

        unsetOtherDefaults(store.getStoreType());
        store.setIsDefault(true);
        store.setUpdatedAt(Instant.now());

        return certificateRepository.save(store);
    }

    /**
     * Activate a certificate store
     */
    @Transactional
    public CertificateStore activate(Long id) {
        CertificateStore store = certificateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate store not found: " + id));

        store.setActive(true);
        store.setUpdatedAt(Instant.now());

        return certificateRepository.save(store);
    }

    /**
     * Deactivate a certificate store
     */
    @Transactional
    public CertificateStore deactivate(Long id) {
        CertificateStore store = certificateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate store not found: " + id));

        store.setActive(false);
        store.setUpdatedAt(Instant.now());

        return certificateRepository.save(store);
    }

    // ========== Certificate Info ==========

    /**
     * Get certificate information for a store
     */
    public CertificateInfo getCertificateInfo(Long id) throws SslConfigurationException {
        CertificateStore store = certificateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate store not found: " + id));

        return sslContextFactory.extractCertificateInfo(store);
    }

    /**
     * Validate a certificate store
     */
    public void validateCertificateStore(Long id) throws SslConfigurationException {
        CertificateStore store = certificateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificate store not found: " + id));

        sslContextFactory.validateStore(store);
    }

    // ========== Expiration Management ==========

    /**
     * Get certificates expiring within given days
     */
    public List<CertificateStore> getExpiringCertificates(int days) {
        Instant threshold = Instant.now().plus(days, ChronoUnit.DAYS);
        return certificateRepository.findExpiringBefore(threshold);
    }

    /**
     * Get expired certificates
     */
    public List<CertificateStore> getExpiredCertificates() {
        return certificateRepository.findExpired();
    }

    /**
     * Check for expiring certificates (scheduled task)
     */
    @Scheduled(cron = "0 0 8 * * ?") // Run at 8 AM daily
    public void checkExpiringCertificates() {
        // Check for certificates expiring in 30 days
        List<CertificateStore> expiring = getExpiringCertificates(30);
        for (CertificateStore cert : expiring) {
            Long daysLeft = cert.getDaysUntilExpiry();
            if (daysLeft != null) {
                if (daysLeft <= 0) {
                    log.error("Certificate EXPIRED: {} ({})", cert.getName(), cert.getSubjectDn());
                } else if (daysLeft <= 7) {
                    log.error("Certificate expires in {} days: {} ({})", daysLeft, cert.getName(), cert.getSubjectDn());
                } else if (daysLeft <= 14) {
                    log.warn("Certificate expires in {} days: {} ({})", daysLeft, cert.getName(), cert.getSubjectDn());
                } else {
                    log.info("Certificate expires in {} days: {} ({})", daysLeft, cert.getName(), cert.getSubjectDn());
                }
            }
        }
    }

    // ========== Partner Certificates ==========

    /**
     * Get certificates for a partner
     */
    public List<CertificateStore> getPartnerCertificates(String partnerId) {
        return certificateRepository.findByPartnerIdAndActiveOrderByNameAsc(partnerId, true);
    }

    /**
     * Get partner keystore
     */
    public Optional<CertificateStore> getPartnerKeystore(String partnerId) {
        return certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue(partnerId, StoreType.KEYSTORE);
    }

    /**
     * Get partner truststore
     */
    public Optional<CertificateStore> getPartnerTruststore(String partnerId) {
        return certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue(partnerId, StoreType.TRUSTSTORE);
    }

    // ========== Helper Methods ==========

    /**
     * Unset default flag on other stores of the same type
     */
    private void unsetOtherDefaults(StoreType type) {
        List<CertificateStore> defaults = certificateRepository.findByStoreTypeAndActiveOrderByNameAsc(type, true);
        for (CertificateStore store : defaults) {
            if (store.getIsDefault()) {
                store.setIsDefault(false);
                store.setUpdatedAt(Instant.now());
                certificateRepository.save(store);
            }
        }
    }

    // ========== Keystore/Truststore Creation ==========

    /**
     * Create an empty keystore
     */
    @Transactional
    public CertificateStore createEmptyKeystore(
            String name,
            String description,
            StoreFormat format,
            String storePassword,
            CertificatePurpose purpose,
            String partnerId,
            boolean isDefault,
            String createdBy) throws SslConfigurationException {

        if (certificateRepository.existsByNameAndStoreType(name, StoreType.KEYSTORE)) {
            throw new IllegalArgumentException("Keystore already exists: " + name);
        }

        try {
            // Create empty keystore
            byte[] emptyStore = sslContextFactory.createEmptyKeyStore(format, storePassword);

            CertificateStore store = CertificateStore.builder()
                    .name(name)
                    .description(description)
                    .storeType(StoreType.KEYSTORE)
                    .format(format)
                    .storeData(emptyStore)
                    .storePassword(storePassword)
                    .purpose(purpose)
                    .partnerId(partnerId)
                    .isDefault(isDefault)
                    .active(true)
                    .createdAt(java.time.Instant.now())
                    .updatedAt(java.time.Instant.now())
                    .createdBy(createdBy)
                    .build();

            if (isDefault) {
                unsetOtherDefaults(StoreType.KEYSTORE);
            }

            store = certificateRepository.save(store);
            log.info("Created empty keystore: {}", name);
            return store;

        } catch (Exception e) {
            throw new SslConfigurationException("Failed to create empty keystore: " + e.getMessage(), e);
        }
    }

    /**
     * Create an empty truststore
     */
    @Transactional
    public CertificateStore createEmptyTruststore(
            String name,
            String description,
            StoreFormat format,
            String storePassword,
            String partnerId,
            boolean isDefault,
            String createdBy) throws SslConfigurationException {

        if (certificateRepository.existsByNameAndStoreType(name, StoreType.TRUSTSTORE)) {
            throw new IllegalArgumentException("Truststore already exists: " + name);
        }

        try {
            // Create empty truststore
            byte[] emptyStore = sslContextFactory.createEmptyKeyStore(format, storePassword);

            CertificateStore store = CertificateStore.builder()
                    .name(name)
                    .description(description)
                    .storeType(StoreType.TRUSTSTORE)
                    .format(format)
                    .storeData(emptyStore)
                    .storePassword(storePassword)
                    .purpose(CertificatePurpose.CA)
                    .partnerId(partnerId)
                    .isDefault(isDefault)
                    .active(true)
                    .createdAt(java.time.Instant.now())
                    .updatedAt(java.time.Instant.now())
                    .createdBy(createdBy)
                    .build();

            if (isDefault) {
                unsetOtherDefaults(StoreType.TRUSTSTORE);
            }

            store = certificateRepository.save(store);
            log.info("Created empty truststore: {}", name);
            return store;

        } catch (Exception e) {
            throw new SslConfigurationException("Failed to create empty truststore: " + e.getMessage(), e);
        }
    }

    /**
     * Add a certificate to a truststore
     */
    @Transactional
    public CertificateStore addCertificateToTruststore(Long truststoreId, byte[] certificateData, String alias)
            throws SslConfigurationException {

        CertificateStore store = certificateRepository.findById(truststoreId)
                .orElseThrow(() -> new IllegalArgumentException("Truststore not found: " + truststoreId));

        if (store.getStoreType() != StoreType.TRUSTSTORE) {
            throw new IllegalArgumentException("Not a truststore: " + store.getName());
        }

        try {
            byte[] updatedStore = sslContextFactory.addCertificateToStore(store, certificateData, alias);
            store.setStoreData(updatedStore);
            store.setUpdatedAt(java.time.Instant.now());

            // Update certificate info
            try {
                SslContextFactory.CertificateInfo info = sslContextFactory.extractCertificateInfo(store);
                store.setSubjectDn(info.getSubjectDn());
                store.setIssuerDn(info.getIssuerDn());
                store.setFingerprint(info.getFingerprint());
            } catch (Exception e) {
                log.warn("Could not extract certificate info: {}", e.getMessage());
            }

            store = certificateRepository.save(store);
            log.info("Added certificate '{}' to truststore '{}'", alias, store.getName());
            return store;

        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to add certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Add a key pair (certificate + private key) to a keystore
     */
    @Transactional
    public CertificateStore addKeyPairToKeystore(
            Long keystoreId,
            byte[] certificateData,
            byte[] privateKeyData,
            String alias,
            String keyPassword) throws SslConfigurationException {

        CertificateStore store = certificateRepository.findById(keystoreId)
                .orElseThrow(() -> new IllegalArgumentException("Keystore not found: " + keystoreId));

        if (store.getStoreType() != StoreType.KEYSTORE) {
            throw new IllegalArgumentException("Not a keystore: " + store.getName());
        }

        try {
            byte[] updatedStore = sslContextFactory.addKeyPairToStore(
                    store, certificateData, privateKeyData, alias, keyPassword);
            store.setStoreData(updatedStore);
            store.setKeyAlias(alias);
            store.setKeyPassword(keyPassword);
            store.setUpdatedAt(java.time.Instant.now());

            // Update certificate info
            try {
                SslContextFactory.CertificateInfo info = sslContextFactory.extractCertificateInfo(store);
                store.setSubjectDn(info.getSubjectDn());
                store.setIssuerDn(info.getIssuerDn());
                store.setSerialNumber(info.getSerialNumber());
                store.setValidFrom(info.getValidFrom());
                store.setExpiresAt(info.getExpiresAt());
                store.setFingerprint(info.getFingerprint());
            } catch (Exception e) {
                log.warn("Could not extract certificate info: {}", e.getMessage());
            }

            store = certificateRepository.save(store);
            log.info("Added key pair '{}' to keystore '{}'", alias, store.getName());
            return store;

        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to add key pair: " + e.getMessage(), e);
        }
    }

    /**
     * List entries in a certificate store
     */
    public List<StoreEntry> listStoreEntries(Long storeId) throws SslConfigurationException {
        CertificateStore store = certificateRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        return sslContextFactory.listStoreEntries(store);
    }

    /**
     * Remove an entry from a certificate store
     */
    @Transactional
    public CertificateStore removeStoreEntry(Long storeId, String alias) throws SslConfigurationException {
        CertificateStore store = certificateRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        try {
            byte[] updatedStore = sslContextFactory.removeEntryFromStore(store, alias);
            store.setStoreData(updatedStore);
            store.setUpdatedAt(java.time.Instant.now());

            store = certificateRepository.save(store);
            log.info("Removed entry '{}' from store '{}'", alias, store.getName());
            return store;

        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to remove entry: " + e.getMessage(), e);
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class StoreEntry {
        private String alias;
        private String type; // "certificate" or "key"
        private String subjectDn;
        private java.time.Instant expiresAt;
    }

    // ========== Statistics ==========

    /**
     * Get certificate statistics
     */
    public CertificateStatistics getStatistics() {
        CertificateStatistics stats = new CertificateStatistics();
        stats.setTotalKeystores(certificateRepository.countByStoreTypeAndActive(StoreType.KEYSTORE, true));
        stats.setTotalTruststores(certificateRepository.countByStoreTypeAndActive(StoreType.TRUSTSTORE, true));
        stats.setExpiredCount(certificateRepository.findExpired().size());
        stats.setExpiringIn30Days(getExpiringCertificates(30).size());
        stats.setExpiringIn7Days(getExpiringCertificates(7).size());
        return stats;
    }

    @lombok.Data
    public static class CertificateStatistics {
        private long totalKeystores;
        private long totalTruststores;
        private int expiredCount;
        private int expiringIn30Days;
        private int expiringIn7Days;
    }
}
