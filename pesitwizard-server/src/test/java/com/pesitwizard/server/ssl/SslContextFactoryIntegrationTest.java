package com.pesitwizard.server.ssl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.server.config.SslProperties;
import com.pesitwizard.server.entity.CertificateStore;
import com.pesitwizard.server.entity.CertificateStore.StoreFormat;
import com.pesitwizard.server.entity.CertificateStore.StoreType;
import com.pesitwizard.server.repository.CertificateStoreRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("SslContextFactory Integration Tests")
class SslContextFactoryIntegrationTest {

    @Mock
    private CertificateStoreRepository certificateRepository;

    @Mock
    private SslProperties sslProperties;

    private SslContextFactory sslContextFactory;

    private byte[] testKeystoreData;
    private byte[] testTruststoreData;
    private static final String TEST_PASSWORD = "testpassword";

    @BeforeEach
    void setUp() throws Exception {
        sslContextFactory = new SslContextFactory(certificateRepository, sslProperties);

        // Generate test keystore with self-signed certificate
        testKeystoreData = generateTestKeystore();
        testTruststoreData = generateTestTruststore();
    }

    private byte[] generateTestKeystore() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        X500Name issuer = new X500Name("CN=Test CA, O=Test, C=FR");
        X500Name subject = new X500Name("CN=Test Server, O=Test, C=FR");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        Date notAfter = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, keyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(keyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certHolder);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("server", keyPair.getPrivate(), TEST_PASSWORD.toCharArray(),
                new X509Certificate[] { cert });

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, TEST_PASSWORD.toCharArray());
        return baos.toByteArray();
    }

    private byte[] generateTestTruststore() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        X500Name issuer = new X500Name("CN=Test CA, O=Test, C=FR");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        Date notAfter = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, issuer, keyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(keyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certHolder);

        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry("ca", cert);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ts.store(baos, TEST_PASSWORD.toCharArray());
        return baos.toByteArray();
    }

    @Nested
    @DisplayName("SSL Context Creation")
    class SslContextCreationTests {

        @Test
        @DisplayName("should create SSL context from keystore only")
        void shouldCreateSslContextFromKeystoreOnly() throws Exception {
            CertificateStore keystore = createTestKeystore();

            SSLContext sslContext = sslContextFactory.createSslContext(keystore, null);

            assertThat(sslContext).isNotNull();
            assertThat(sslContext.getProtocol()).isIn("TLSv1.3", "TLSv1.2");
        }

        @Test
        @DisplayName("should create SSL context with keystore and truststore")
        void shouldCreateSslContextWithBothStores() throws Exception {
            CertificateStore keystore = createTestKeystore();
            CertificateStore truststore = createTestTruststore();

            SSLContext sslContext = sslContextFactory.createSslContext(keystore, truststore);

            assertThat(sslContext).isNotNull();
        }

        @Test
        @DisplayName("should create default SSL context")
        void shouldCreateDefaultSslContext() throws Exception {
            CertificateStore keystore = createTestKeystore();
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.KEYSTORE))
                    .thenReturn(Optional.of(keystore));
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.TRUSTSTORE))
                    .thenReturn(Optional.empty());

            SSLContext sslContext = sslContextFactory.createDefaultSslContext();

            assertThat(sslContext).isNotNull();
        }

        @Test
        @DisplayName("should throw when no default keystore")
        void shouldThrowWhenNoDefaultKeystore() {
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.KEYSTORE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> sslContextFactory.createDefaultSslContext())
                    .isInstanceOf(SslConfigurationException.class)
                    .hasMessageContaining("No default keystore");
        }
    }

    @Nested
    @DisplayName("Named Store Context")
    class NamedStoreContextTests {

        @Test
        @DisplayName("should create context with named keystore")
        void shouldCreateContextWithNamedKeystore() throws Exception {
            CertificateStore keystore = createTestKeystore();
            when(sslProperties.getKeystoreData()).thenReturn(null);
            when(certificateRepository.findByNameAndStoreType("my-keystore", StoreType.KEYSTORE))
                    .thenReturn(Optional.of(keystore));

            SSLContext context = sslContextFactory.createSslContext("my-keystore", null);

            assertThat(context).isNotNull();
        }

        @Test
        @DisplayName("should throw when keystore not found")
        void shouldThrowWhenKeystoreNotFound() {
            when(sslProperties.getKeystoreData()).thenReturn(null);
            when(certificateRepository.findByNameAndStoreType("missing", StoreType.KEYSTORE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> sslContextFactory.createSslContext("missing", null))
                    .isInstanceOf(SslConfigurationException.class)
                    .hasMessageContaining("Keystore not found");
        }

        @Test
        @DisplayName("should throw when keystore is inactive")
        void shouldThrowWhenKeystoreInactive() {
            CertificateStore inactiveStore = createTestKeystore();
            inactiveStore.setActive(false);
            when(sslProperties.getKeystoreData()).thenReturn(null);
            when(certificateRepository.findByNameAndStoreType("inactive", StoreType.KEYSTORE))
                    .thenReturn(Optional.of(inactiveStore));

            assertThatThrownBy(() -> sslContextFactory.createSslContext("inactive", null))
                    .isInstanceOf(SslConfigurationException.class)
                    .hasMessageContaining("not active");
        }

        @Test
        @DisplayName("should throw when truststore not found")
        void shouldThrowWhenTruststoreNotFound() {
            CertificateStore keystore = createTestKeystore();
            when(sslProperties.getKeystoreData()).thenReturn(null);
            when(certificateRepository.findByNameAndStoreType("ks", StoreType.KEYSTORE))
                    .thenReturn(Optional.of(keystore));
            when(certificateRepository.findByNameAndStoreType("missing-ts", StoreType.TRUSTSTORE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> sslContextFactory.createSslContext("ks", "missing-ts"))
                    .isInstanceOf(SslConfigurationException.class)
                    .hasMessageContaining("Truststore not found");
        }

        @Test
        @DisplayName("should throw when truststore is inactive")
        void shouldThrowWhenTruststoreInactive() {
            CertificateStore keystore = createTestKeystore();
            CertificateStore truststore = createTestTruststore();
            truststore.setActive(false);
            when(sslProperties.getKeystoreData()).thenReturn(null);
            when(certificateRepository.findByNameAndStoreType("ks", StoreType.KEYSTORE))
                    .thenReturn(Optional.of(keystore));
            when(certificateRepository.findByNameAndStoreType("ts", StoreType.TRUSTSTORE))
                    .thenReturn(Optional.of(truststore));

            assertThatThrownBy(() -> sslContextFactory.createSslContext("ks", "ts"))
                    .isInstanceOf(SslConfigurationException.class)
                    .hasMessageContaining("not active");
        }
    }

    @Nested
    @DisplayName("Partner SSL Context")
    class PartnerSslContextTests {

        @Test
        @DisplayName("should create partner SSL context with partner-specific store")
        void shouldCreatePartnerSslContextWithPartnerStore() throws Exception {
            CertificateStore partnerKeystore = createTestKeystore();
            partnerKeystore.setPartnerId("PARTNER1");
            when(certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue("PARTNER1", StoreType.KEYSTORE))
                    .thenReturn(Optional.of(partnerKeystore));
            when(certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue("PARTNER1", StoreType.TRUSTSTORE))
                    .thenReturn(Optional.empty());
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.TRUSTSTORE))
                    .thenReturn(Optional.empty());

            SSLContext context = sslContextFactory.createPartnerSslContext("PARTNER1");

            assertThat(context).isNotNull();
        }

        @Test
        @DisplayName("should fallback to default keystore for partner")
        void shouldFallbackToDefaultKeystoreForPartner() throws Exception {
            CertificateStore defaultKeystore = createTestKeystore();
            when(certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue("PARTNER2", StoreType.KEYSTORE))
                    .thenReturn(Optional.empty());
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.KEYSTORE))
                    .thenReturn(Optional.of(defaultKeystore));
            when(certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue("PARTNER2", StoreType.TRUSTSTORE))
                    .thenReturn(Optional.empty());
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.TRUSTSTORE))
                    .thenReturn(Optional.empty());

            SSLContext context = sslContextFactory.createPartnerSslContext("PARTNER2");

            assertThat(context).isNotNull();
        }

        @Test
        @DisplayName("should throw when no keystore available for partner")
        void shouldThrowWhenNoKeystoreForPartner() {
            when(certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue("PARTNER3", StoreType.KEYSTORE))
                    .thenReturn(Optional.empty());
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.KEYSTORE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> sslContextFactory.createPartnerSslContext("PARTNER3"))
                    .isInstanceOf(SslConfigurationException.class)
                    .hasMessageContaining("No keystore available for partner");
        }
    }

    @Nested
    @DisplayName("Socket Factory Creation")
    class SocketFactoryTests {

        @Test
        @DisplayName("should create server socket factory")
        void shouldCreateServerSocketFactory() throws Exception {
            CertificateStore keystore = createTestKeystore();
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.KEYSTORE))
                    .thenReturn(Optional.of(keystore));
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.TRUSTSTORE))
                    .thenReturn(Optional.empty());

            SSLServerSocketFactory factory = sslContextFactory.createServerSocketFactory();

            assertThat(factory).isNotNull();
        }

        @Test
        @DisplayName("should create socket factory")
        void shouldCreateSocketFactory() throws Exception {
            CertificateStore keystore = createTestKeystore();
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.KEYSTORE))
                    .thenReturn(Optional.of(keystore));
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.TRUSTSTORE))
                    .thenReturn(Optional.empty());

            SSLSocketFactory factory = sslContextFactory.createSocketFactory();

            assertThat(factory).isNotNull();
        }

        @Test
        @DisplayName("should create partner socket factory")
        void shouldCreatePartnerSocketFactory() throws Exception {
            CertificateStore keystore = createTestKeystore();
            when(certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue("P1", StoreType.KEYSTORE))
                    .thenReturn(Optional.of(keystore));
            when(certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue("P1", StoreType.TRUSTSTORE))
                    .thenReturn(Optional.empty());
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.TRUSTSTORE))
                    .thenReturn(Optional.empty());

            SSLSocketFactory factory = sslContextFactory.createPartnerSocketFactory("P1");

            assertThat(factory).isNotNull();
        }

        @Test
        @DisplayName("should create server socket factory with named stores")
        void shouldCreateServerSocketFactoryWithNamedStores() throws Exception {
            CertificateStore keystore = createTestKeystore();
            CertificateStore truststore = createTestTruststore();
            when(sslProperties.getKeystoreData()).thenReturn(null);
            when(certificateRepository.findByNameAndStoreType("ks", StoreType.KEYSTORE))
                    .thenReturn(Optional.of(keystore));
            when(certificateRepository.findByNameAndStoreType("ts", StoreType.TRUSTSTORE))
                    .thenReturn(Optional.of(truststore));

            SSLServerSocketFactory factory = sslContextFactory.createServerSocketFactory("ks", "ts");

            assertThat(factory).isNotNull();
        }
    }

    @Nested
    @DisplayName("Environment Variable SSL Context")
    class EnvVarSslContextTests {

        @Test
        @DisplayName("should create SSL context from env vars")
        void shouldCreateSslContextFromEnvVars() throws Exception {
            String encodedKeystore = Base64.getEncoder().encodeToString(testKeystoreData);
            when(sslProperties.getKeystoreData()).thenReturn(encodedKeystore);
            when(sslProperties.getKeystorePassword()).thenReturn(TEST_PASSWORD);
            when(sslProperties.getCaCertPem()).thenReturn(null);

            SSLContext context = sslContextFactory.createSslContextFromEnvVars();

            assertThat(context).isNotNull();
        }

        @Test
        @DisplayName("should throw when no keystore data in env vars")
        void shouldThrowWhenNoKeystoreDataInEnvVars() {
            when(sslProperties.getKeystoreData()).thenReturn(null);

            assertThatThrownBy(() -> sslContextFactory.createSslContextFromEnvVars())
                    .isInstanceOf(SslConfigurationException.class)
                    .hasMessageContaining("No keystore data");
        }

        @Test
        @DisplayName("should throw when empty keystore data in env vars")
        void shouldThrowWhenEmptyKeystoreDataInEnvVars() {
            when(sslProperties.getKeystoreData()).thenReturn("");

            assertThatThrownBy(() -> sslContextFactory.createSslContextFromEnvVars())
                    .isInstanceOf(SslConfigurationException.class)
                    .hasMessageContaining("No keystore data");
        }

        @Test
        @DisplayName("should use env vars over named stores")
        void shouldUseEnvVarsOverNamedStores() throws Exception {
            String encodedKeystore = Base64.getEncoder().encodeToString(testKeystoreData);
            when(sslProperties.getKeystoreData()).thenReturn(encodedKeystore);
            when(sslProperties.getKeystorePassword()).thenReturn(TEST_PASSWORD);
            when(sslProperties.getCaCertPem()).thenReturn(null);

            SSLContext context = sslContextFactory.createSslContext("ignored", "ignored");

            assertThat(context).isNotNull();
            // Should not call repository
            verify(certificateRepository, never()).findByNameAndStoreType(any(), any());
        }
    }

    @Nested
    @DisplayName("KeyStore Loading")
    class KeyStoreLoadingTests {

        @Test
        @DisplayName("should load PKCS12 keystore")
        void shouldLoadPkcs12Keystore() throws Exception {
            CertificateStore store = createTestKeystore();
            store.setFormat(StoreFormat.PKCS12);

            KeyStore ks = sslContextFactory.loadKeyStore(store);

            assertThat(ks).isNotNull();
            assertThat(ks.size()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should load JKS keystore")
        void shouldLoadJksKeystore() throws Exception {
            // Generate JKS keystore
            KeyStore jks = KeyStore.getInstance("JKS");
            jks.load(null, null);

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            X500Name issuer = new X500Name("CN=Test, O=Test, C=FR");
            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
            Date notBefore = new Date();
            Date notAfter = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L);

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    issuer, serial, notBefore, notAfter, issuer, keyPair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

            jks.setKeyEntry("key", keyPair.getPrivate(), TEST_PASSWORD.toCharArray(), new X509Certificate[] { cert });

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            jks.store(baos, TEST_PASSWORD.toCharArray());

            CertificateStore store = CertificateStore.builder()
                    .name("jks-store")
                    .storeType(StoreType.KEYSTORE)
                    .format(StoreFormat.JKS)
                    .storeData(baos.toByteArray())
                    .storePassword(TEST_PASSWORD)
                    .active(true)
                    .build();

            KeyStore loaded = sslContextFactory.loadKeyStore(store);

            assertThat(loaded).isNotNull();
            assertThat(loaded.size()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Certificate Info Extraction")
    class CertificateInfoTests {

        @Test
        @DisplayName("should extract certificate info")
        void shouldExtractCertificateInfo() throws Exception {
            CertificateStore store = createTestKeystore();

            SslContextFactory.CertificateInfo info = sslContextFactory.extractCertificateInfo(store);

            assertThat(info).isNotNull();
            assertThat(info.getSubjectDn()).contains("CN=Test Server");
            assertThat(info.getIssuerDn()).contains("CN=Test CA");
            assertThat(info.getFingerprint()).isNotEmpty();
            assertThat(info.isHasPrivateKey()).isTrue();
        }

        @Test
        @DisplayName("should extract certificate info from truststore")
        void shouldExtractCertificateInfoFromTruststore() throws Exception {
            CertificateStore store = createTestTruststore();

            SslContextFactory.CertificateInfo info = sslContextFactory.extractCertificateInfo(store);

            assertThat(info).isNotNull();
            assertThat(info.getSubjectDn()).contains("CN=Test CA");
            assertThat(info.isHasPrivateKey()).isFalse();
        }
    }

    private CertificateStore createTestKeystore() {
        return CertificateStore.builder()
                .id(1L)
                .name("test-keystore")
                .storeType(StoreType.KEYSTORE)
                .format(StoreFormat.PKCS12)
                .storeData(testKeystoreData)
                .storePassword(TEST_PASSWORD)
                .keyPassword(TEST_PASSWORD)
                .active(true)
                .build();
    }

    private CertificateStore createTestTruststore() {
        return CertificateStore.builder()
                .id(2L)
                .name("test-truststore")
                .storeType(StoreType.TRUSTSTORE)
                .format(StoreFormat.PKCS12)
                .storeData(testTruststoreData)
                .storePassword(TEST_PASSWORD)
                .active(true)
                .build();
    }
}
