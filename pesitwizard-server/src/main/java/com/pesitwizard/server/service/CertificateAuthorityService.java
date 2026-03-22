package com.pesitwizard.server.service;

import com.pesitwizard.common.crypto.CertificateUtils;
import com.pesitwizard.common.crypto.CryptoException;
import com.pesitwizard.common.crypto.CsrUtils;
import com.pesitwizard.common.crypto.KeystoreUtils;
import com.pesitwizard.common.crypto.PemUtils;
import com.pesitwizard.security.SecretsService;
import com.pesitwizard.server.config.CaProperties;
import com.pesitwizard.server.entity.CertificateStore;
import com.pesitwizard.server.entity.CertificateStore.CertificatePurpose;
import com.pesitwizard.server.entity.CertificateStore.StoreFormat;
import com.pesitwizard.server.entity.CertificateStore.StoreType;
import com.pesitwizard.server.repository.CertificateStoreRepository;
import com.pesitwizard.server.ssl.SslConfigurationException;
import com.pesitwizard.server.ssl.SslContextFactory;
import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for Certificate Authority operations. Manages the private CA and signs certificates for
 * clients/partners.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateAuthorityService {

    private final CertificateStoreRepository certificateRepository;
    private final SslContextFactory sslContextFactory;
    private final CaProperties caProperties;
    private final SecretsService secretsService;

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int DEFAULT_KEY_SIZE = 2048;

    /** Initialize the CA if not already present. Creates a self-signed CA certificate. */
    @Transactional
    public CertificateStore initializeCa(String createdBy) throws SslConfigurationException {
        // Check if CA already exists
        Optional<CertificateStore> existingCa =
                certificateRepository.findByNameAndStoreType(
                        caProperties.getCaKeystoreName(), StoreType.KEYSTORE);

        if (existingCa.isPresent()) {
            log.info("CA already initialized: {}", caProperties.getCaKeystoreName());
            return existingCa.get();
        }

        log.info("Initializing new Certificate Authority...");

        try {
            // Generate CA key pair
            KeyPair caKeyPair = generateKeyPair(caProperties.getKeySize());

            // Build CA distinguished name
            X500Name caSubject =
                    new X500Name(
                            String.format(
                                    "CN=%s,OU=%s,O=%s,L=%s,ST=%s,C=%s",
                                    caProperties.getCaCommonName(),
                                    caProperties.getOrganizationalUnit(),
                                    caProperties.getOrganization(),
                                    caProperties.getLocality(),
                                    caProperties.getState(),
                                    caProperties.getCountry()));

            // Generate self-signed CA certificate
            X509Certificate caCert =
                    generateSelfSignedCertificate(
                            caKeyPair,
                            caSubject,
                            Duration.ofDays(caProperties.getCaValidityDays()),
                            true); // isCA = true

            // Create PKCS12 keystore with CA cert and key
            byte[] keystoreData =
                    createKeystore(
                            caCert,
                            caKeyPair.getPrivate(),
                            caProperties.getCaKeyAlias(),
                            caProperties.getCaKeystorePassword());

            // Store in database (encrypt passwords before persisting)
            CertificateStore caStore =
                    CertificateStore.builder()
                            .name(caProperties.getCaKeystoreName())
                            .description("PeSIT Private Certificate Authority")
                            .storeType(StoreType.KEYSTORE)
                            .format(StoreFormat.PKCS12)
                            .storeData(keystoreData)
                            .storePassword(
                                    encryptPassword(
                                            caProperties.getCaKeystorePassword(),
                                            caProperties.getCaKeystoreName(),
                                            "storePassword"))
                            .keyPassword(
                                    encryptPassword(
                                            caProperties.getCaKeystorePassword(),
                                            caProperties.getCaKeystoreName(),
                                            "keyPassword"))
                            .keyAlias(caProperties.getCaKeyAlias())
                            .purpose(CertificatePurpose.CA)
                            .isDefault(false)
                            .active(true)
                            .subjectDn(caCert.getSubjectX500Principal().getName())
                            .issuerDn(caCert.getIssuerX500Principal().getName())
                            .serialNumber(caCert.getSerialNumber().toString(16))
                            .validFrom(caCert.getNotBefore().toInstant())
                            .expiresAt(caCert.getNotAfter().toInstant())
                            .fingerprint(sslContextFactory.calculateFingerprint(caCert))
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .createdBy(createdBy)
                            .build();

            caStore = certificateRepository.save(caStore);

            // Also create and store CA truststore for distribution
            createCaTruststore(caCert, createdBy);

            log.info("Certificate Authority initialized successfully: {}", caStore.getSubjectDn());
            return caStore;

        } catch (SslConfigurationException e) {
            throw e;
        } catch (CryptoException | java.security.GeneralSecurityException | java.io.IOException e) {
            throw new SslConfigurationException("Failed to initialize CA: " + e.getMessage(), e);
        }
    }

    /** Create CA truststore for distribution to clients */
    private void createCaTruststore(X509Certificate caCert, String createdBy)
            throws java.security.GeneralSecurityException,
                    java.io.IOException,
                    SslConfigurationException {
        KeyStore truststore = KeyStore.getInstance("PKCS12");
        truststore.load(null, null);
        truststore.setCertificateEntry(caProperties.getCaKeyAlias(), caCert);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        truststore.store(bos, caProperties.getCaTruststorePassword().toCharArray());

        CertificateStore caTs =
                CertificateStore.builder()
                        .name(caProperties.getCaTruststoreName())
                        .description("PeSIT CA Truststore - distribute to clients")
                        .storeType(StoreType.TRUSTSTORE)
                        .format(StoreFormat.PKCS12)
                        .storeData(bos.toByteArray())
                        .storePassword(
                                encryptPassword(
                                        caProperties.getCaTruststorePassword(),
                                        caProperties.getCaTruststoreName(),
                                        "storePassword"))
                        .purpose(CertificatePurpose.CA)
                        .isDefault(true)
                        .active(true)
                        .subjectDn(caCert.getSubjectX500Principal().getName())
                        .fingerprint(sslContextFactory.calculateFingerprint(caCert))
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .createdBy(createdBy)
                        .build();

        certificateRepository.save(caTs);
        log.info("CA truststore created: {}", caProperties.getCaTruststoreName());
    }

    /** Generate a new key pair and CSR for a partner/client */
    public CertificateRequest generateCertificateRequest(
            String commonName,
            String organizationalUnit,
            String organization,
            CertificatePurpose purpose)
            throws SslConfigurationException {

        try {
            KeyPair keyPair = generateKeyPair(DEFAULT_KEY_SIZE);

            X500Name subject =
                    new X500Name(
                            String.format(
                                    "CN=%s,OU=%s,O=%s,C=%s",
                                    commonName,
                                    organizationalUnit != null
                                            ? organizationalUnit
                                            : caProperties.getOrganizationalUnit(),
                                    organization != null
                                            ? organization
                                            : caProperties.getOrganization(),
                                    caProperties.getCountry()));

            // Generate CSR
            JcaPKCS10CertificationRequestBuilder csrBuilder =
                    new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());

            ContentSigner signer =
                    new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keyPair.getPrivate());

            PKCS10CertificationRequest csr = csrBuilder.build(signer);

            // Convert to PEM
            String csrPem = toPem(csr);
            String privateKeyPem = toPem(keyPair.getPrivate());

            return new CertificateRequest(csrPem, privateKeyPem, commonName, purpose);

        } catch (CryptoException | org.bouncycastle.operator.OperatorCreationException e) {
            throw new SslConfigurationException("Failed to generate CSR: " + e.getMessage(), e);
        }
    }

    /** Sign a CSR with the CA certificate */
    @Transactional
    public SignedCertificate signCertificateRequest(
            String csrPem, CertificatePurpose purpose, int validityDays, String partnerId)
            throws SslConfigurationException {

        try {
            // Load CA keystore
            CertificateStore caStore =
                    certificateRepository
                            .findByNameAndStoreType(
                                    caProperties.getCaKeystoreName(), StoreType.KEYSTORE)
                            .orElseThrow(() -> new SslConfigurationException("CA not initialized"));

            KeyStore caKs = sslContextFactory.loadKeyStore(caStore);
            PrivateKey caPrivateKey =
                    (PrivateKey)
                            caKs.getKey(
                                    caProperties.getCaKeyAlias(),
                                    caProperties.getCaKeystorePassword().toCharArray());
            X509Certificate caCert =
                    (X509Certificate) caKs.getCertificate(caProperties.getCaKeyAlias());

            // Parse CSR
            PKCS10CertificationRequest csr = parseCsr(csrPem);

            // Extract public key from CSR
            java.security.spec.X509EncodedKeySpec keySpec =
                    new java.security.spec.X509EncodedKeySpec(
                            csr.getSubjectPublicKeyInfo().getEncoded());
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(keySpec);

            // Generate signed certificate using shared builder
            String purposeStr = purpose == CertificatePurpose.SERVER ? "SERVER" : "CLIENT";
            X509Certificate signedCert =
                    CertificateUtils.buildSignedCertificate(
                            csr.getSubject(),
                            publicKey,
                            caCert,
                            caPrivateKey,
                            validityDays,
                            purposeStr);

            // Convert to PEM
            String certPem = toPem(signedCert);
            String caCertPem = toPem(caCert);

            log.info(
                    "Certificate signed for {}: serial={}, expires={}",
                    csr.getSubject(),
                    signedCert.getSerialNumber().toString(16),
                    signedCert.getNotAfter());

            return new SignedCertificate(
                    certPem,
                    caCertPem,
                    signedCert.getSubjectX500Principal().getName(),
                    signedCert.getSerialNumber().toString(16),
                    signedCert.getNotAfter().toInstant(),
                    partnerId);

        } catch (SslConfigurationException e) {
            throw e;
        } catch (CryptoException | java.security.GeneralSecurityException | java.io.IOException e) {
            throw new SslConfigurationException("Failed to sign certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Generate and sign a complete certificate for a partner Returns a PKCS12 keystore ready for
     * use
     */
    @Transactional
    public CertificateStore generatePartnerCertificate(
            String partnerId,
            String commonName,
            CertificatePurpose purpose,
            int validityDays,
            String createdBy)
            throws SslConfigurationException {

        try {
            // Generate key pair and CSR
            CertificateRequest request =
                    generateCertificateRequest(
                            commonName, partnerId, caProperties.getOrganization(), purpose);

            // Sign the CSR
            SignedCertificate signed =
                    signCertificateRequest(request.getCsrPem(), purpose, validityDays, partnerId);

            // Parse certificate and private key
            X509Certificate cert = parseCertificate(signed.getCertificatePem());
            PrivateKey privateKey = parsePrivateKey(request.getPrivateKeyPem());

            // Load CA cert for chain
            X509Certificate caCert = parseCertificate(signed.getCaCertificatePem());

            // Create PKCS12 keystore with certificate chain
            String keystorePassword = generatePassword();
            byte[] keystoreData =
                    createKeystoreWithChain(
                            privateKey,
                            new Certificate[] {cert, caCert},
                            commonName,
                            keystorePassword);

            // Store in database
            String storeName =
                    String.format("%s-%s-keystore", partnerId, purpose.name().toLowerCase());

            CertificateStore store =
                    CertificateStore.builder()
                            .name(storeName)
                            .description(
                                    String.format(
                                            "Certificate for partner %s (%s)", partnerId, purpose))
                            .storeType(StoreType.KEYSTORE)
                            .format(StoreFormat.PKCS12)
                            .storeData(keystoreData)
                            .storePassword(
                                    encryptPassword(keystorePassword, storeName, "storePassword"))
                            .keyPassword(
                                    encryptPassword(keystorePassword, storeName, "keyPassword"))
                            .keyAlias(commonName)
                            .purpose(purpose)
                            .partnerId(partnerId)
                            .isDefault(false)
                            .active(true)
                            .subjectDn(cert.getSubjectX500Principal().getName())
                            .issuerDn(cert.getIssuerX500Principal().getName())
                            .serialNumber(cert.getSerialNumber().toString(16))
                            .validFrom(cert.getNotBefore().toInstant())
                            .expiresAt(cert.getNotAfter().toInstant())
                            .fingerprint(sslContextFactory.calculateFingerprint(cert))
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .createdBy(createdBy)
                            .build();

            store = certificateRepository.save(store);

            log.info(
                    "Partner certificate generated: {} for {} (expires: {})",
                    storeName,
                    partnerId,
                    cert.getNotAfter());

            return store;

        } catch (SslConfigurationException e) {
            throw e;
        } catch (CryptoException e) {
            throw new SslConfigurationException(
                    "Failed to generate partner certificate: " + e.getMessage(), e);
        }
    }

    /** Get the CA certificate in PEM format (for distribution) */
    public String getCaCertificatePem() throws SslConfigurationException {
        try {
            CertificateStore caStore =
                    certificateRepository
                            .findByNameAndStoreType(
                                    caProperties.getCaKeystoreName(), StoreType.KEYSTORE)
                            .orElseThrow(() -> new SslConfigurationException("CA not initialized"));

            KeyStore ks = sslContextFactory.loadKeyStore(caStore);
            X509Certificate caCert =
                    (X509Certificate) ks.getCertificate(caProperties.getCaKeyAlias());

            return toPem(caCert);

        } catch (SslConfigurationException e) {
            throw e;
        } catch (CryptoException | java.security.GeneralSecurityException e) {
            throw new SslConfigurationException(
                    "Failed to get CA certificate: " + e.getMessage(), e);
        }
    }

    /** Verify a certificate was signed by our CA */
    public boolean verifyCertificate(String certificatePem) {
        try {
            X509Certificate cert = parseCertificate(certificatePem);

            CertificateStore caStore =
                    certificateRepository
                            .findByNameAndStoreType(
                                    caProperties.getCaKeystoreName(), StoreType.KEYSTORE)
                            .orElse(null);

            if (caStore == null) {
                return false;
            }

            KeyStore ks = sslContextFactory.loadKeyStore(caStore);
            X509Certificate caCert =
                    (X509Certificate) ks.getCertificate(caProperties.getCaKeyAlias());

            cert.verify(caCert.getPublicKey());
            cert.checkValidity();

            return true;

        } catch (CryptoException
                | SslConfigurationException
                | java.security.GeneralSecurityException e) {
            log.warn("Certificate verification failed: {}", e.getMessage());
            return false;
        }
    }

    // ========== Password Encryption ==========

    /**
     * Encrypt a password for storage if not already encrypted. Returns null for null/blank input.
     */
    private String encryptPassword(String password, String storeName, String fieldName) {
        if (password == null || password.isBlank()) {
            return password;
        }
        if (secretsService.isEncrypted(password)) {
            return password;
        }
        return secretsService.encryptForStorage(password, "certstore", storeName, fieldName);
    }

    // ========== Helper Methods (delegating to pesitwizard-common) ==========

    private KeyPair generateKeyPair(int keySize) throws CryptoException {
        return KeystoreUtils.generateKeyPair(keySize);
    }

    private X509Certificate generateSelfSignedCertificate(
            KeyPair keyPair, X500Name subject, Duration validity, boolean isCA)
            throws CryptoException {
        return CertificateUtils.generateSelfSignedCertificate(keyPair, subject, validity, isCA);
    }

    private byte[] createKeystore(
            X509Certificate cert, PrivateKey privateKey, String alias, String password)
            throws CryptoException {
        return KeystoreUtils.createKeystore(cert, privateKey, alias, password);
    }

    private byte[] createKeystoreWithChain(
            PrivateKey privateKey, Certificate[] chain, String alias, String password)
            throws CryptoException {
        return KeystoreUtils.createKeystoreWithChain(privateKey, chain, alias, password);
    }

    private String toPem(Object obj) throws CryptoException {
        return PemUtils.toPem(obj);
    }

    private PKCS10CertificationRequest parseCsr(String csrPem) throws CryptoException {
        return CsrUtils.parseCsr(csrPem);
    }

    private X509Certificate parseCertificate(String certPem) throws CryptoException {
        return PemUtils.parseCertificate(certPem);
    }

    private PrivateKey parsePrivateKey(String keyPem) throws CryptoException {
        return PemUtils.parsePrivateKey(keyPem);
    }

    private String generatePassword() {
        return KeystoreUtils.generateSecurePassword();
    }

    // ========== DTOs ==========

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CertificateRequest {
        private String csrPem;
        private String privateKeyPem;
        private String commonName;
        private CertificatePurpose purpose;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SignedCertificate {
        private String certificatePem;
        private String caCertificatePem;
        private String subjectDn;
        private String serialNumber;
        private Instant expiresAt;
        private String partnerId;
    }
}
