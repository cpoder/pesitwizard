package com.vectis.server.ssl;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.springframework.stereotype.Component;

import com.vectis.server.entity.CertificateStore;
import com.vectis.server.entity.CertificateStore.StoreType;
import com.vectis.server.repository.CertificateStoreRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating SSL contexts from centrally managed certificates.
 * Supports JKS, PKCS12, and PEM formats.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SslContextFactory {

    private final CertificateStoreRepository certificateRepository;

    private static final String TLS_PROTOCOL = "TLSv1.3";
    private static final String FALLBACK_PROTOCOL = "TLSv1.2";

    /**
     * Create an SSL context using the default keystore and truststore
     */
    public SSLContext createDefaultSslContext() throws SslConfigurationException {
        CertificateStore keystore = certificateRepository
                .findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.KEYSTORE)
                .orElseThrow(() -> new SslConfigurationException("No default keystore configured"));

        CertificateStore truststore = certificateRepository
                .findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.TRUSTSTORE)
                .orElse(null); // Truststore is optional

        return createSslContext(keystore, truststore);
    }

    /**
     * Create an SSL context using named keystore and truststore
     */
    public SSLContext createSslContext(String keystoreName, String truststoreName)
            throws SslConfigurationException {

        CertificateStore keystore = certificateRepository
                .findByNameAndStoreType(keystoreName, StoreType.KEYSTORE)
                .orElseThrow(() -> new SslConfigurationException("Keystore not found: " + keystoreName));

        if (!keystore.getActive()) {
            throw new SslConfigurationException("Keystore is not active: " + keystoreName);
        }

        CertificateStore truststore = null;
        if (truststoreName != null) {
            truststore = certificateRepository
                    .findByNameAndStoreType(truststoreName, StoreType.TRUSTSTORE)
                    .orElseThrow(() -> new SslConfigurationException("Truststore not found: " + truststoreName));

            if (!truststore.getActive()) {
                throw new SslConfigurationException("Truststore is not active: " + truststoreName);
            }
        }

        return createSslContext(keystore, truststore);
    }

    /**
     * Create an SSL context for a specific partner (mutual TLS)
     */
    public SSLContext createPartnerSslContext(String partnerId) throws SslConfigurationException {
        // Try to find partner-specific keystore
        CertificateStore keystore = certificateRepository
                .findByPartnerIdAndStoreTypeAndActiveTrue(partnerId, StoreType.KEYSTORE)
                .orElseGet(() -> certificateRepository
                        .findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.KEYSTORE)
                        .orElse(null));

        if (keystore == null) {
            throw new SslConfigurationException("No keystore available for partner: " + partnerId);
        }

        // Try to find partner-specific truststore
        CertificateStore truststore = certificateRepository
                .findByPartnerIdAndStoreTypeAndActiveTrue(partnerId, StoreType.TRUSTSTORE)
                .orElseGet(() -> certificateRepository
                        .findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.TRUSTSTORE)
                        .orElse(null));

        return createSslContext(keystore, truststore);
    }

    /**
     * Create an SSL context from certificate store entities
     */
    public SSLContext createSslContext(CertificateStore keystore, CertificateStore truststore)
            throws SslConfigurationException {
        try {
            // Load keystore
            KeyStore ks = loadKeyStore(keystore);

            // Initialize key manager
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            String keyPassword = keystore.getKeyPassword() != null
                    ? keystore.getKeyPassword()
                    : keystore.getStorePassword();
            kmf.init(ks, keyPassword != null ? keyPassword.toCharArray() : null);

            // Initialize trust manager
            TrustManagerFactory tmf = null;
            if (truststore != null) {
                KeyStore ts = loadKeyStore(truststore);
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ts);
            }

            // Create SSL context
            SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance(TLS_PROTOCOL);
            } catch (Exception e) {
                log.warn("TLSv1.3 not available, falling back to TLSv1.2");
                sslContext = SSLContext.getInstance(FALLBACK_PROTOCOL);
            }

            sslContext.init(
                    kmf.getKeyManagers(),
                    tmf != null ? tmf.getTrustManagers() : null,
                    null);

            log.info("SSL context created with keystore '{}' and truststore '{}'",
                    keystore.getName(), truststore != null ? truststore.getName() : "default");

            return sslContext;

        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to create SSL context: " + e.getMessage(), e);
        }
    }

    /**
     * Create an SSL server socket factory
     */
    public SSLServerSocketFactory createServerSocketFactory() throws SslConfigurationException {
        return createDefaultSslContext().getServerSocketFactory();
    }

    /**
     * Create an SSL server socket factory with specific stores
     */
    public SSLServerSocketFactory createServerSocketFactory(String keystoreName, String truststoreName)
            throws SslConfigurationException {
        return createSslContext(keystoreName, truststoreName).getServerSocketFactory();
    }

    /**
     * Create an SSL socket factory for client connections
     */
    public SSLSocketFactory createSocketFactory() throws SslConfigurationException {
        return createDefaultSslContext().getSocketFactory();
    }

    /**
     * Create an SSL socket factory for partner connections
     */
    public SSLSocketFactory createPartnerSocketFactory(String partnerId) throws SslConfigurationException {
        return createPartnerSslContext(partnerId).getSocketFactory();
    }

    /**
     * Load a KeyStore from a CertificateStore entity
     */
    public KeyStore loadKeyStore(CertificateStore store) throws SslConfigurationException {
        try {
            KeyStore keyStore;

            switch (store.getFormat()) {
                case JKS:
                    keyStore = KeyStore.getInstance("JKS");
                    break;
                case PKCS12:
                    keyStore = KeyStore.getInstance("PKCS12");
                    break;
                case PEM:
                    return loadPemStore(store);
                default:
                    throw new SslConfigurationException("Unsupported store format: " + store.getFormat());
            }

            char[] password = store.getStorePassword() != null
                    ? store.getStorePassword().toCharArray()
                    : null;

            try (ByteArrayInputStream bis = new ByteArrayInputStream(store.getStoreData())) {
                keyStore.load(bis, password);
            }

            return keyStore;

        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to load keystore '" + store.getName() + "': " + e.getMessage(),
                    e);
        }
    }

    /**
     * Load a PEM-encoded certificate/key store
     */
    private KeyStore loadPemStore(CertificateStore store) throws SslConfigurationException {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);

            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            try (ByteArrayInputStream bis = new ByteArrayInputStream(store.getStoreData())) {
                int certIndex = 0;
                while (bis.available() > 0) {
                    try {
                        Certificate cert = cf.generateCertificate(bis);
                        String alias = store.getKeyAlias() != null
                                ? store.getKeyAlias() + "_" + certIndex
                                : "cert_" + certIndex;
                        keyStore.setCertificateEntry(alias, cert);
                        certIndex++;
                    } catch (Exception e) {
                        // End of certificates or parsing error
                        break;
                    }
                }
            }

            if (keyStore.size() == 0) {
                throw new SslConfigurationException("No certificates found in PEM store: " + store.getName());
            }

            return keyStore;

        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to load PEM store '" + store.getName() + "': " + e.getMessage(),
                    e);
        }
    }

    /**
     * Extract certificate information from a keystore
     */
    public CertificateInfo extractCertificateInfo(CertificateStore store) throws SslConfigurationException {
        try {
            KeyStore ks = loadKeyStore(store);

            Enumeration<String> aliases = ks.aliases();
            if (!aliases.hasMoreElements()) {
                throw new SslConfigurationException("No entries found in store: " + store.getName());
            }

            String alias = store.getKeyAlias() != null ? store.getKeyAlias() : aliases.nextElement();
            Certificate cert = ks.getCertificate(alias);

            if (!(cert instanceof X509Certificate)) {
                throw new SslConfigurationException("Certificate is not X.509: " + store.getName());
            }

            X509Certificate x509 = (X509Certificate) cert;

            CertificateInfo info = new CertificateInfo();
            info.setAlias(alias);
            info.setSubjectDn(x509.getSubjectX500Principal().getName());
            info.setIssuerDn(x509.getIssuerX500Principal().getName());
            info.setSerialNumber(x509.getSerialNumber().toString(16));
            info.setValidFrom(x509.getNotBefore().toInstant());
            info.setExpiresAt(x509.getNotAfter().toInstant());
            info.setFingerprint(calculateFingerprint(x509));
            info.setKeyUsage(getKeyUsage(x509));
            info.setHasPrivateKey(ks.isKeyEntry(alias));

            return info;

        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to extract certificate info: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate SHA-256 fingerprint of a certificate
     */
    public String calculateFingerprint(X509Certificate cert) throws SslConfigurationException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to calculate fingerprint: " + e.getMessage(), e);
        }
    }

    /**
     * Get key usage as a list of strings
     */
    private List<String> getKeyUsage(X509Certificate cert) {
        List<String> usages = new ArrayList<>();
        boolean[] keyUsage = cert.getKeyUsage();
        if (keyUsage != null) {
            String[] usageNames = {
                    "digitalSignature", "nonRepudiation", "keyEncipherment",
                    "dataEncipherment", "keyAgreement", "keyCertSign",
                    "cRLSign", "encipherOnly", "decipherOnly"
            };
            for (int i = 0; i < keyUsage.length && i < usageNames.length; i++) {
                if (keyUsage[i]) {
                    usages.add(usageNames[i]);
                }
            }
        }
        return usages;
    }

    /**
     * Validate that a certificate store is properly configured
     */
    public void validateStore(CertificateStore store) throws SslConfigurationException {
        if (store.getStoreData() == null || store.getStoreData().length == 0) {
            throw new SslConfigurationException("Store data is empty: " + store.getName());
        }

        // Try to load the store to validate it
        KeyStore ks = loadKeyStore(store);

        try {
            if (ks.size() == 0) {
                throw new SslConfigurationException("Store contains no entries: " + store.getName());
            }

            // For keystores, verify we have a private key
            if (store.getStoreType() == StoreType.KEYSTORE) {
                boolean hasPrivateKey = false;
                Enumeration<String> aliases = ks.aliases();
                while (aliases.hasMoreElements()) {
                    if (ks.isKeyEntry(aliases.nextElement())) {
                        hasPrivateKey = true;
                        break;
                    }
                }
                if (!hasPrivateKey) {
                    throw new SslConfigurationException("Keystore contains no private key: " + store.getName());
                }
            }
        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Store validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create an empty keystore
     */
    public byte[] createEmptyKeyStore(CertificateStore.StoreFormat format, String password)
            throws SslConfigurationException {
        try {
            KeyStore keyStore;
            switch (format) {
                case JKS:
                    keyStore = KeyStore.getInstance("JKS");
                    break;
                case PKCS12:
                default:
                    keyStore = KeyStore.getInstance("PKCS12");
                    break;
            }

            keyStore.load(null, password != null ? password.toCharArray() : null);

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            keyStore.store(bos, password != null ? password.toCharArray() : null);
            return bos.toByteArray();

        } catch (Exception e) {
            throw new SslConfigurationException("Failed to create empty keystore: " + e.getMessage(), e);
        }
    }

    /**
     * Add a certificate to a store (for truststores)
     */
    public byte[] addCertificateToStore(CertificateStore store, byte[] certificateData, String alias)
            throws SslConfigurationException {
        try {
            KeyStore keyStore = loadKeyStore(store);

            // Parse certificate
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certificateData));

            // Add to store
            keyStore.setCertificateEntry(alias, cert);

            // Export updated store
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            char[] password = store.getStorePassword() != null ? store.getStorePassword().toCharArray() : null;
            keyStore.store(bos, password);
            return bos.toByteArray();

        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to add certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Add a key pair to a store (for keystores)
     */
    public byte[] addKeyPairToStore(
            CertificateStore store,
            byte[] certificateData,
            byte[] privateKeyData,
            String alias,
            String keyPassword) throws SslConfigurationException {
        try {
            KeyStore keyStore = loadKeyStore(store);

            // Parse certificate
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certificateData));

            // Parse private key (PEM format)
            java.security.PrivateKey privateKey = parsePrivateKey(privateKeyData);

            // Add to store
            char[] keyPwd = keyPassword != null ? keyPassword.toCharArray()
                    : (store.getStorePassword() != null ? store.getStorePassword().toCharArray() : null);
            keyStore.setKeyEntry(alias, privateKey, keyPwd, new Certificate[] { cert });

            // Export updated store
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            char[] storePwd = store.getStorePassword() != null ? store.getStorePassword().toCharArray() : null;
            keyStore.store(bos, storePwd);
            return bos.toByteArray();

        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to add key pair: " + e.getMessage(), e);
        }
    }

    /**
     * Parse a PEM-encoded private key
     */
    private java.security.PrivateKey parsePrivateKey(byte[] keyData) throws SslConfigurationException {
        try {
            String keyPem = new String(keyData, java.nio.charset.StandardCharsets.UTF_8);

            // Remove PEM headers/footers
            keyPem = keyPem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decoded = java.util.Base64.getDecoder().decode(keyPem);

            java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(decoded);
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);

        } catch (Exception e) {
            throw new SslConfigurationException("Failed to parse private key: " + e.getMessage(), e);
        }
    }

    /**
     * List entries in a certificate store
     */
    public List<com.vectis.server.service.CertificateService.StoreEntry> listStoreEntries(CertificateStore store)
            throws SslConfigurationException {
        try {
            KeyStore keyStore = loadKeyStore(store);
            List<com.vectis.server.service.CertificateService.StoreEntry> entries = new ArrayList<>();

            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                String type = keyStore.isKeyEntry(alias) ? "key" : "certificate";

                Certificate cert = keyStore.getCertificate(alias);
                String subjectDn = null;
                Instant expiresAt = null;

                if (cert instanceof X509Certificate) {
                    X509Certificate x509 = (X509Certificate) cert;
                    subjectDn = x509.getSubjectX500Principal().getName();
                    expiresAt = x509.getNotAfter().toInstant();
                }

                entries.add(new com.vectis.server.service.CertificateService.StoreEntry(
                        alias, type, subjectDn, expiresAt));
            }

            return entries;

        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to list store entries: " + e.getMessage(), e);
        }
    }

    /**
     * Remove an entry from a certificate store
     */
    public byte[] removeEntryFromStore(CertificateStore store, String alias) throws SslConfigurationException {
        try {
            KeyStore keyStore = loadKeyStore(store);

            if (!keyStore.containsAlias(alias)) {
                throw new SslConfigurationException("Alias not found: " + alias);
            }

            keyStore.deleteEntry(alias);

            // Export updated store
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            char[] password = store.getStorePassword() != null ? store.getStorePassword().toCharArray() : null;
            keyStore.store(bos, password);
            return bos.toByteArray();

        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to remove entry: " + e.getMessage(), e);
        }
    }

    /**
     * Certificate information DTO
     */
    @lombok.Data
    public static class CertificateInfo {
        private String alias;
        private String subjectDn;
        private String issuerDn;
        private String serialNumber;
        private Instant validFrom;
        private Instant expiresAt;
        private String fingerprint;
        private List<String> keyUsage;
        private boolean hasPrivateKey;

        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }

        public boolean isNotYetValid() {
            return validFrom != null && Instant.now().isBefore(validFrom);
        }
    }
}
