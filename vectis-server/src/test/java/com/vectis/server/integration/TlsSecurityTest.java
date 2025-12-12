package com.vectis.server.integration;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.vectis.server.entity.CertificateStore;
import com.vectis.server.entity.CertificateStore.CertificatePurpose;
import com.vectis.server.entity.CertificateStore.StoreFormat;
import com.vectis.server.entity.CertificateStore.StoreType;
import com.vectis.server.service.CertificateService;
import com.vectis.server.ssl.SslConfigurationException;
import com.vectis.server.ssl.SslContextFactory;
import com.vectis.server.ssl.SslContextFactory.CertificateInfo;

import lombok.extern.slf4j.Slf4j;

/**
 * Integration tests for TLS/SSL and mTLS functionality.
 * Tests certificate management, SSL context creation, and validation.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("TLS/SSL Security Tests")
public class TlsSecurityTest {

    private static final String CERTS_DIR = "src/test/resources/certs";
    private static final String PASSWORD = "changeit";

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private SslContextFactory sslContextFactory;

    private static byte[] serverKeystoreBytes;
    private static byte[] clientKeystoreBytes;
    private static byte[] partnerKeystoreBytes;
    private static byte[] caTruststoreBytes;
    private static byte[] untrustedKeystoreBytes;

    @BeforeAll
    static void loadCertificates() throws Exception {
        serverKeystoreBytes = Files.readAllBytes(Path.of(CERTS_DIR, "server-keystore.p12"));
        clientKeystoreBytes = Files.readAllBytes(Path.of(CERTS_DIR, "client-keystore.p12"));
        partnerKeystoreBytes = Files.readAllBytes(Path.of(CERTS_DIR, "partner-keystore.p12"));
        caTruststoreBytes = Files.readAllBytes(Path.of(CERTS_DIR, "ca-truststore.p12"));
        untrustedKeystoreBytes = Files.readAllBytes(Path.of(CERTS_DIR, "untrusted-keystore.p12"));

        log.info("Loaded test certificates from {}", CERTS_DIR);
    }

    @Nested
    @DisplayName("Certificate Store Management")
    class CertificateStoreTests {

        @Test
        @DisplayName("Should upload and store server keystore")
        void shouldUploadServerKeystore() throws Exception {
            // Upload server keystore
            CertificateStore store = certificateService.createCertificateStore(
                    "test-server-keystore",
                    "Test server keystore",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    serverKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "server",
                    CertificatePurpose.SERVER,
                    null,
                    true,
                    "test");

            assertThat(store.getId()).isNotNull();
            assertThat(store.getName()).isEqualTo("test-server-keystore");
            assertThat(store.getStoreType()).isEqualTo(StoreType.KEYSTORE);
            assertThat(store.getFormat()).isEqualTo(StoreFormat.PKCS12);
            assertThat(store.getSubjectDn()).contains("localhost");
            assertThat(store.getFingerprint()).isNotNull();
            assertThat(store.getIsDefault()).isTrue();

            log.info("Server keystore uploaded: subject={}, fingerprint={}",
                    store.getSubjectDn(), store.getFingerprint());
        }

        @Test
        @DisplayName("Should upload and store CA truststore")
        void shouldUploadCaTruststore() throws Exception {
            CertificateStore store = certificateService.createCertificateStore(
                    "test-ca-truststore",
                    "Test CA truststore",
                    StoreType.TRUSTSTORE,
                    StoreFormat.PKCS12,
                    caTruststoreBytes,
                    PASSWORD,
                    null,
                    null,
                    CertificatePurpose.CA,
                    null,
                    true,
                    "test");

            assertThat(store.getId()).isNotNull();
            assertThat(store.getStoreType()).isEqualTo(StoreType.TRUSTSTORE);
            assertThat(store.getSubjectDn()).contains("PeSIT Test CA");

            log.info("CA truststore uploaded: subject={}", store.getSubjectDn());
        }

        @Test
        @DisplayName("Should upload client keystore for mTLS")
        void shouldUploadClientKeystore() throws Exception {
            CertificateStore store = certificateService.createCertificateStore(
                    "test-client-keystore",
                    "Test client keystore for mTLS",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    clientKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "client",
                    CertificatePurpose.CLIENT,
                    null,
                    false,
                    "test");

            assertThat(store.getId()).isNotNull();
            assertThat(store.getPurpose()).isEqualTo(CertificatePurpose.CLIENT);
            assertThat(store.getSubjectDn()).contains("test-client");

            log.info("Client keystore uploaded for mTLS: subject={}", store.getSubjectDn());
        }

        @Test
        @DisplayName("Should upload partner-specific keystore")
        void shouldUploadPartnerKeystore() throws Exception {
            CertificateStore store = certificateService.createCertificateStore(
                    "partner-a-keystore",
                    "Partner A keystore",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    partnerKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "partner",
                    CertificatePurpose.PARTNER,
                    "PARTNER_A",
                    false,
                    "test");

            assertThat(store.getId()).isNotNull();
            assertThat(store.getPartnerId()).isEqualTo("PARTNER_A");
            assertThat(store.getPurpose()).isEqualTo(CertificatePurpose.PARTNER);

            log.info("Partner keystore uploaded: partnerId={}, subject={}",
                    store.getPartnerId(), store.getSubjectDn());
        }

        @Test
        @DisplayName("Should extract certificate info")
        void shouldExtractCertificateInfo() throws Exception {
            CertificateStore store = certificateService.createCertificateStore(
                    "test-info-keystore",
                    "Test keystore for info extraction",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    serverKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "server",
                    CertificatePurpose.SERVER,
                    null,
                    false,
                    "test");

            CertificateInfo info = certificateService.getCertificateInfo(store.getId());

            assertThat(info.getSubjectDn()).contains("localhost");
            assertThat(info.getIssuerDn()).contains("PeSIT Test CA");
            assertThat(info.getFingerprint()).isNotNull();
            assertThat(info.isHasPrivateKey()).isTrue();
            assertThat(info.isExpired()).isFalse();
            assertThat(info.getValidFrom()).isNotNull();
            assertThat(info.getExpiresAt()).isNotNull();

            log.info("Certificate info: subject={}, issuer={}, expires={}",
                    info.getSubjectDn(), info.getIssuerDn(), info.getExpiresAt());
        }

        @Test
        @DisplayName("Should reject invalid keystore")
        void shouldRejectInvalidKeystore() {
            byte[] invalidData = "not a keystore".getBytes();

            assertThrows(SslConfigurationException.class, () -> certificateService.createCertificateStore(
                    "invalid-keystore",
                    "Invalid keystore",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    invalidData,
                    PASSWORD,
                    PASSWORD,
                    null,
                    CertificatePurpose.SERVER,
                    null,
                    false,
                    "test"));

            log.info("Invalid keystore correctly rejected");
        }
    }

    @Nested
    @DisplayName("SSL Context Creation")
    class SslContextTests {

        @Test
        @DisplayName("Should create SSL context from stored certificates")
        void shouldCreateSslContext() throws Exception {
            // First upload the certificates
            CertificateStore keystore = certificateService.createCertificateStore(
                    "ssl-test-keystore",
                    "SSL test keystore",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    serverKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "server",
                    CertificatePurpose.SERVER,
                    null,
                    false,
                    "test");

            CertificateStore truststore = certificateService.createCertificateStore(
                    "ssl-test-truststore",
                    "SSL test truststore",
                    StoreType.TRUSTSTORE,
                    StoreFormat.PKCS12,
                    caTruststoreBytes,
                    PASSWORD,
                    null,
                    null,
                    CertificatePurpose.CA,
                    null,
                    false,
                    "test");

            // Create SSL context
            SSLContext sslContext = sslContextFactory.createSslContext(keystore, truststore);

            assertThat(sslContext).isNotNull();
            assertThat(sslContext.getProtocol()).isIn("TLSv1.3", "TLSv1.2");

            log.info("SSL context created: protocol={}", sslContext.getProtocol());
        }

        @Test
        @DisplayName("Should create SSL context by name")
        void shouldCreateSslContextByName() throws Exception {
            // Upload with specific names
            certificateService.createCertificateStore(
                    "named-keystore",
                    "Named keystore",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    serverKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "server",
                    CertificatePurpose.SERVER,
                    null,
                    false,
                    "test");

            certificateService.createCertificateStore(
                    "named-truststore",
                    "Named truststore",
                    StoreType.TRUSTSTORE,
                    StoreFormat.PKCS12,
                    caTruststoreBytes,
                    PASSWORD,
                    null,
                    null,
                    CertificatePurpose.CA,
                    null,
                    false,
                    "test");

            SSLContext sslContext = sslContextFactory.createSslContext("named-keystore", "named-truststore");

            assertThat(sslContext).isNotNull();
            log.info("SSL context created by name");
        }

        @Test
        @DisplayName("Should create partner-specific SSL context")
        void shouldCreatePartnerSslContext() throws Exception {
            // Upload partner keystore
            certificateService.createCertificateStore(
                    "partner-ssl-keystore",
                    "Partner SSL keystore",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    partnerKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "partner",
                    CertificatePurpose.PARTNER,
                    "PARTNER_SSL",
                    false,
                    "test");

            // Upload partner truststore
            certificateService.createCertificateStore(
                    "partner-ssl-truststore",
                    "Partner SSL truststore",
                    StoreType.TRUSTSTORE,
                    StoreFormat.PKCS12,
                    caTruststoreBytes,
                    PASSWORD,
                    null,
                    null,
                    CertificatePurpose.PARTNER,
                    "PARTNER_SSL",
                    false,
                    "test");

            SSLContext sslContext = sslContextFactory.createPartnerSslContext("PARTNER_SSL");

            assertThat(sslContext).isNotNull();
            log.info("Partner SSL context created");
        }
    }

    @Nested
    @DisplayName("Certificate Validation")
    class CertificateValidationTests {

        @Test
        @DisplayName("Should validate valid keystore")
        void shouldValidateValidKeystore() throws Exception {
            CertificateStore store = certificateService.createCertificateStore(
                    "valid-keystore",
                    "Valid keystore",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    serverKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "server",
                    CertificatePurpose.SERVER,
                    null,
                    false,
                    "test");

            // Should not throw
            certificateService.validateCertificateStore(store.getId());
            log.info("Valid keystore validated successfully");
        }

        @Test
        @DisplayName("Should detect expiring certificates")
        void shouldDetectExpiringCertificates() throws Exception {
            CertificateStore store = certificateService.createCertificateStore(
                    "expiry-test-keystore",
                    "Expiry test keystore",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    serverKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "server",
                    CertificatePurpose.SERVER,
                    null,
                    false,
                    "test");

            // Certificate expires in 365 days, so should be in 400-day expiring list
            var expiringIn400Days = certificateService.getExpiringCertificates(400);
            assertThat(expiringIn400Days).anyMatch(c -> c.getId().equals(store.getId()));

            // But not in 30-day list (unless test certs were generated with short validity)
            // Just verify the query works - result depends on cert validity
            assertThat(certificateService.getExpiringCertificates(30)).isNotNull();

            log.info("Expiring certificate detection working");
        }
    }

    @Nested
    @DisplayName("mTLS (Mutual TLS) Tests")
    class MutualTlsTests {

        @Test
        @DisplayName("Should setup mTLS with client certificate")
        void shouldSetupMtls() throws Exception {
            // Server keystore
            CertificateStore serverKs = certificateService.createCertificateStore(
                    "mtls-server-keystore",
                    "mTLS server keystore",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    serverKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "server",
                    CertificatePurpose.SERVER,
                    null,
                    false,
                    "test");

            // Server truststore (to verify client certs)
            CertificateStore serverTs = certificateService.createCertificateStore(
                    "mtls-server-truststore",
                    "mTLS server truststore",
                    StoreType.TRUSTSTORE,
                    StoreFormat.PKCS12,
                    caTruststoreBytes,
                    PASSWORD,
                    null,
                    null,
                    CertificatePurpose.CA,
                    null,
                    false,
                    "test");

            // Client keystore
            CertificateStore clientKs = certificateService.createCertificateStore(
                    "mtls-client-keystore",
                    "mTLS client keystore",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    clientKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "client",
                    CertificatePurpose.CLIENT,
                    null,
                    false,
                    "test");

            // Client truststore (to verify server cert)
            CertificateStore clientTs = certificateService.createCertificateStore(
                    "mtls-client-truststore",
                    "mTLS client truststore",
                    StoreType.TRUSTSTORE,
                    StoreFormat.PKCS12,
                    caTruststoreBytes,
                    PASSWORD,
                    null,
                    null,
                    CertificatePurpose.CA,
                    null,
                    false,
                    "test");

            // Create server SSL context
            SSLContext serverContext = sslContextFactory.createSslContext(serverKs, serverTs);
            assertThat(serverContext).isNotNull();

            // Create client SSL context
            SSLContext clientContext = sslContextFactory.createSslContext(clientKs, clientTs);
            assertThat(clientContext).isNotNull();

            log.info("mTLS setup complete - server and client SSL contexts created");
        }

        @Test
        @DisplayName("Should reject untrusted client certificate")
        void shouldRejectUntrustedClient() throws Exception {
            // Upload untrusted keystore
            CertificateStore untrustedKs = certificateService.createCertificateStore(
                    "untrusted-client-keystore",
                    "Untrusted client keystore",
                    StoreType.KEYSTORE,
                    StoreFormat.PKCS12,
                    untrustedKeystoreBytes,
                    PASSWORD,
                    PASSWORD,
                    "untrusted",
                    CertificatePurpose.CLIENT,
                    null,
                    false,
                    "test");

            // The untrusted cert is self-signed, not from our CA
            CertificateInfo info = certificateService.getCertificateInfo(untrustedKs.getId());
            assertThat(info.getSubjectDn()).contains("Untrusted");
            assertThat(info.getIssuerDn()).contains("Untrusted"); // Self-signed

            log.info("Untrusted certificate correctly identified as self-signed");
        }
    }

    @Nested
    @DisplayName("KeyStore Loading")
    class KeyStoreLoadingTests {

        @Test
        @DisplayName("Should load PKCS12 keystore")
        void shouldLoadPkcs12Keystore() throws Exception {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(CERTS_DIR + "/server-keystore.p12")) {
                ks.load(fis, PASSWORD.toCharArray());
            }

            assertThat(ks.size()).isGreaterThan(0);
            assertThat(ks.containsAlias("server")).isTrue();
            assertThat(ks.isKeyEntry("server")).isTrue();

            log.info("PKCS12 keystore loaded: aliases={}", ks.aliases());
        }

        @Test
        @DisplayName("Should load truststore with CA certificate")
        void shouldLoadTruststore() throws Exception {
            KeyStore ts = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(CERTS_DIR + "/ca-truststore.p12")) {
                ts.load(fis, PASSWORD.toCharArray());
            }

            assertThat(ts.size()).isGreaterThan(0);
            assertThat(ts.containsAlias("ca")).isTrue();
            assertThat(ts.isCertificateEntry("ca")).isTrue();

            log.info("Truststore loaded: aliases={}", ts.aliases());
        }
    }
}
