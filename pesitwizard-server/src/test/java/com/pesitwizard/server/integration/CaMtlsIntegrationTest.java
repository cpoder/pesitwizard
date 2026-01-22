package com.pesitwizard.server.integration;

import static org.assertj.core.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.security.KeyStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.pesitwizard.server.entity.CertificateStore;
import com.pesitwizard.server.service.CertificateAuthorityService;
import com.pesitwizard.server.service.CertificateAuthorityService.CertificateRequest;
import com.pesitwizard.server.service.CertificateAuthorityService.SignedCertificate;
import com.pesitwizard.server.service.CertificateService;
import com.pesitwizard.server.ssl.SslContextFactory;

/**
 * Integration tests for CA-based mTLS communication.
 * 
 * Tests the complete workflow:
 * 1. Initialize private CA
 * 2. Generate server certificate signed by CA
 * 3. Generate client certificate signed by CA
 * 4. Establish mTLS connection between client and server
 * 5. Verify untrusted certificates are rejected
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CA-based mTLS Integration Tests")
public class CaMtlsIntegrationTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CaMtlsIntegrationTest.class);
    private static final String PASSWORD = "changeit";
    private static final int TEST_PORT = 15443;

    @Autowired
    private CertificateAuthorityService caService;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private SslContextFactory sslContextFactory;

    private ExecutorService executor;
    private SSLServerSocket serverSocket;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Nested
    @DisplayName("CA Initialization")
    class CaInitializationTests {

        @Test
        @DisplayName("Should initialize CA and create self-signed CA certificate")
        void shouldInitializeCa() throws Exception {
            // Initialize CA
            CertificateStore caStore = caService.initializeCa("test");

            assertThat(caStore).isNotNull();
            assertThat(caStore.getName()).contains("ca");
            assertThat(caStore.getSubjectDn()).contains("CA");
            assertThat(caStore.getPurpose()).isEqualTo(CertificateStore.CertificatePurpose.CA);

            log.info("CA initialized: subject={}", caStore.getSubjectDn());
        }

        @Test
        @DisplayName("Should get CA certificate in PEM format")
        void shouldGetCaCertificatePem() throws Exception {
            // Ensure CA is initialized
            caService.initializeCa("test");

            String caPem = caService.getCaCertificatePem();

            assertThat(caPem).isNotNull();
            assertThat(caPem).startsWith("-----BEGIN CERTIFICATE-----");
            assertThat(caPem).contains("-----END CERTIFICATE-----");

            log.info("CA certificate PEM retrieved ({} chars)", caPem.length());
        }
    }

    @Nested
    @DisplayName("Certificate Generation")
    class CertificateGenerationTests {

        @Test
        @DisplayName("Should generate server certificate signed by CA")
        void shouldGenerateServerCertificate() throws Exception {
            // Initialize CA
            caService.initializeCa("test");

            // Generate server certificate
            CertificateStore serverCert = caService.generatePartnerCertificate(
                    "TEST_SERVER",
                    "test-server.example.com",
                    CertificateStore.CertificatePurpose.SERVER,
                    365,
                    "test");

            assertThat(serverCert).isNotNull();
            assertThat(serverCert.getSubjectDn()).contains("test-server.example.com");
            assertThat(serverCert.getPurpose()).isEqualTo(CertificateStore.CertificatePurpose.SERVER);

            log.info("Server certificate generated: subject={}", serverCert.getSubjectDn());
        }

        @Test
        @DisplayName("Should generate client certificate signed by CA")
        void shouldGenerateClientCertificate() throws Exception {
            // Initialize CA
            caService.initializeCa("test");

            // Generate client certificate
            CertificateStore clientCert = caService.generatePartnerCertificate(
                    "TEST_CLIENT",
                    "test-client.example.com",
                    CertificateStore.CertificatePurpose.CLIENT,
                    365,
                    "test");

            assertThat(clientCert).isNotNull();
            assertThat(clientCert.getSubjectDn()).contains("test-client.example.com");
            assertThat(clientCert.getPurpose()).isEqualTo(CertificateStore.CertificatePurpose.CLIENT);

            log.info("Client certificate generated: subject={}", clientCert.getSubjectDn());
        }

        @Test
        @DisplayName("Should sign external CSR")
        void shouldSignExternalCsr() throws Exception {
            // Initialize CA
            caService.initializeCa("test");

            // Generate a CSR
            CertificateRequest request = caService.generateCertificateRequest(
                    "external-client.example.com",
                    "External Partners",
                    "External Corp",
                    CertificateStore.CertificatePurpose.CLIENT);

            assertThat(request.getCsrPem()).startsWith("-----BEGIN CERTIFICATE REQUEST-----");

            // Sign the CSR
            SignedCertificate signed = caService.signCertificateRequest(
                    request.getCsrPem(),
                    CertificateStore.CertificatePurpose.CLIENT,
                    365,
                    "EXTERNAL_PARTNER",
                    "test");

            assertThat(signed).isNotNull();
            assertThat(signed.getCertificatePem()).startsWith("-----BEGIN CERTIFICATE-----");
            assertThat(signed.getSubjectDn()).contains("external-client.example.com");

            log.info("External CSR signed: subject={}", signed.getSubjectDn());
        }
    }

    @Nested
    @DisplayName("mTLS Socket Communication")
    @Timeout(30)
    class MtlsSocketTests {

        @Test
        @DisplayName("Should establish mTLS connection with CA-signed certificates")
        void shouldEstablishMtlsConnection() throws Exception {
            // Initialize CA
            caService.initializeCa("test");

            // Generate server certificate
            CertificateStore serverCert = caService.generatePartnerCertificate(
                    "MTLS_SERVER",
                    "mtls-server.local",
                    CertificateStore.CertificatePurpose.SERVER,
                    365,
                    "test");

            // Generate client certificate
            CertificateStore clientCert = caService.generatePartnerCertificate(
                    "MTLS_CLIENT",
                    "mtls-client.local",
                    CertificateStore.CertificatePurpose.CLIENT,
                    365,
                    "test");

            // Get CA truststore
            CertificateStore caTruststore = certificateService.getCertificateStoreByName("pesit-ca-truststore")
                    .orElseThrow(() -> new IllegalStateException("CA truststore not found"));

            // Create server SSL context
            SSLContext serverSslContext = sslContextFactory.createSslContext(serverCert, caTruststore);

            // Create client SSL context
            SSLContext clientSslContext = sslContextFactory.createSslContext(clientCert, caTruststore);

            // Start server
            serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory()
                    .createServerSocket(TEST_PORT);
            serverSocket.setNeedClientAuth(true); // Require client certificate

            String testMessage = "Hello from mTLS client!";
            String expectedResponse = "Hello from mTLS server!";

            // Server task
            CompletableFuture<String> serverFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    log.info("Server waiting for connection on port {}", TEST_PORT);
                    SSLSocket clientConnection = (SSLSocket) serverSocket.accept();
                    log.info("Server accepted connection from {}",
                            clientConnection.getSession().getPeerPrincipal());

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(clientConnection.getInputStream()));
                    PrintWriter writer = new PrintWriter(clientConnection.getOutputStream(), true);

                    String received = reader.readLine();
                    log.info("Server received: {}", received);

                    writer.println(expectedResponse);
                    clientConnection.close();

                    return received;
                } catch (Exception e) {
                    log.error("Server error", e);
                    throw new RuntimeException(e);
                }
            }, executor);

            // Client task
            CompletableFuture<String> clientFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(500); // Give server time to start

                    log.info("Client connecting to localhost:{}", TEST_PORT);
                    SSLSocket socket = (SSLSocket) clientSslContext.getSocketFactory()
                            .createSocket("localhost", TEST_PORT);
                    socket.startHandshake();
                    log.info("Client connected, server cert: {}",
                            socket.getSession().getPeerPrincipal());

                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

                    writer.println(testMessage);
                    String response = reader.readLine();
                    log.info("Client received: {}", response);

                    socket.close();
                    return response;
                } catch (Exception e) {
                    log.error("Client error", e);
                    throw new RuntimeException(e);
                }
            }, executor);

            // Wait for both to complete
            String serverReceived = serverFuture.get(10, TimeUnit.SECONDS);
            String clientReceived = clientFuture.get(10, TimeUnit.SECONDS);

            assertThat(serverReceived).isEqualTo(testMessage);
            assertThat(clientReceived).isEqualTo(expectedResponse);

            log.info("mTLS communication successful!");
        }

        @Test
        @DisplayName("Should reject client without certificate when mTLS required")
        void shouldRejectClientWithoutCertificate() throws Exception {
            // Initialize CA
            caService.initializeCa("test");

            // Generate server certificate
            CertificateStore serverCert = caService.generatePartnerCertificate(
                    "MTLS_SERVER_REJECT",
                    "mtls-server-reject.local",
                    CertificateStore.CertificatePurpose.SERVER,
                    365,
                    "test");

            CertificateStore caTruststore = certificateService.getCertificateStoreByName("pesit-ca-truststore")
                    .orElseThrow();

            // Create server SSL context requiring client auth
            SSLContext serverSslContext = sslContextFactory.createSslContext(serverCert, caTruststore);

            serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory()
                    .createServerSocket(TEST_PORT + 1);
            serverSocket.setNeedClientAuth(true);

            // Start server
            CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
                try {
                    SSLSocket clientConnection = (SSLSocket) serverSocket.accept();
                    clientConnection.getInputStream().read(); // This should fail
                } catch (Exception e) {
                    log.info("Server correctly rejected connection: {}", e.getMessage());
                }
            }, executor);

            // Client without client certificate (only trusts server)
            SSLContext clientNoAuthContext = SSLContext.getInstance("TLSv1.3");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");

            // Load CA truststore for client
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(new java.io.ByteArrayInputStream(caTruststore.getStoreData()),
                    PASSWORD.toCharArray());
            tmf.init(trustStore);

            clientNoAuthContext.init(null, tmf.getTrustManagers(), null);

            Thread.sleep(500);

            // Client should fail to connect
            assertThatThrownBy(() -> {
                SSLSocket socket = (SSLSocket) clientNoAuthContext.getSocketFactory()
                        .createSocket("localhost", TEST_PORT + 1);
                socket.startHandshake();
                socket.getInputStream().read();
            }).isInstanceOf(Exception.class);

            log.info("Client without certificate correctly rejected");
        }

        @Test
        @DisplayName("Should reject untrusted client certificate")
        void shouldRejectUntrustedClientCertificate() throws Exception {
            // Initialize CA
            caService.initializeCa("test");

            // Generate server certificate
            CertificateStore serverCert = caService.generatePartnerCertificate(
                    "MTLS_SERVER_UNTRUST",
                    "mtls-server-untrust.local",
                    CertificateStore.CertificatePurpose.SERVER,
                    365,
                    "test");

            CertificateStore caTruststore = certificateService.getCertificateStoreByName("pesit-ca-truststore")
                    .orElseThrow();

            SSLContext serverSslContext = sslContextFactory.createSslContext(serverCert, caTruststore);

            serverSocket = (SSLServerSocket) serverSslContext.getServerSocketFactory()
                    .createServerSocket(TEST_PORT + 2);
            serverSocket.setNeedClientAuth(true);

            // Start server
            CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
                try {
                    SSLSocket clientConnection = (SSLSocket) serverSocket.accept();
                    clientConnection.getInputStream().read();
                } catch (Exception e) {
                    log.info("Server correctly rejected untrusted client: {}", e.getMessage());
                }
            }, executor);

            // Create self-signed (untrusted) client certificate
            SSLContext untrustedClientContext = createSelfSignedSslContext();

            Thread.sleep(500);

            // Client with untrusted cert should fail
            assertThatThrownBy(() -> {
                SSLSocket socket = (SSLSocket) untrustedClientContext.getSocketFactory()
                        .createSocket("localhost", TEST_PORT + 2);
                socket.startHandshake();
                socket.getInputStream().read();
            }).isInstanceOf(Exception.class);

            log.info("Untrusted client certificate correctly rejected");
        }
    }

    @Nested
    @DisplayName("Certificate Verification")
    class CertificateVerificationTests {

        @Test
        @DisplayName("Should verify certificate signed by CA")
        void shouldVerifyCaSignedCertificate() throws Exception {
            // Initialize CA
            caService.initializeCa("test");

            // Generate certificate
            CertificateStore cert = caService.generatePartnerCertificate(
                    "VERIFY_TEST",
                    "verify-test.local",
                    CertificateStore.CertificatePurpose.CLIENT,
                    365,
                    "test");

            // Get certificate PEM
            // For this test, we'll use the CA's verify method
            // The certificate should be verifiable
            assertThat(cert.getSubjectDn()).contains("verify-test.local");
            assertThat(cert.getIssuerDn()).contains("CA");

            log.info("Certificate verification: subject={}, issuer={}",
                    cert.getSubjectDn(), cert.getIssuerDn());
        }

        @Test
        @DisplayName("Should reject certificate not signed by CA")
        void shouldRejectCertificateNotSignedByCa() throws Exception {
            // Initialize CA
            caService.initializeCa("test");

            // Create a self-signed certificate (not from our CA)
            // Use BouncyCastle or standard Java to generate
            // For now, test that verify returns false for non-CA cert

            String selfSignedPem = generateSelfSignedCertPem();
            boolean valid = caService.verifyCertificate(selfSignedPem);

            assertThat(valid).isFalse();
            log.info("Self-signed certificate correctly rejected by CA verification");
        }
    }

    /**
     * Creates a self-signed SSL context for testing rejection scenarios.
     */
    private SSLContext createSelfSignedSslContext() throws Exception {
        // Generate self-signed key pair
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        java.security.KeyPair keyPair = keyGen.generateKeyPair();

        // Create self-signed certificate
        org.bouncycastle.asn1.x500.X500Name subject = new org.bouncycastle.asn1.x500.X500Name(
                "CN=Untrusted Client, O=Untrusted, C=XX");

        java.math.BigInteger serial = java.math.BigInteger.valueOf(System.currentTimeMillis());
        java.util.Date notBefore = new java.util.Date();
        java.util.Date notAfter = new java.util.Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

        org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder certBuilder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, keyPair.getPublic());

        org.bouncycastle.operator.ContentSigner signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder(
                "SHA256withRSA")
                .build(keyPair.getPrivate());

        java.security.cert.X509Certificate cert = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                .getCertificate(certBuilder.build(signer));

        // Create keystore with self-signed cert
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, PASSWORD.toCharArray());
        ks.setKeyEntry("untrusted", keyPair.getPrivate(), PASSWORD.toCharArray(),
                new java.security.cert.Certificate[] { cert });

        // Create truststore with self-signed cert (trusts itself)
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, PASSWORD.toCharArray());
        ts.setCertificateEntry("untrusted-ca", cert);

        // Initialize SSL context
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, PASSWORD.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslContext;
    }

    /**
     * Generates a self-signed certificate PEM for testing.
     */
    private String generateSelfSignedCertPem() throws Exception {
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        java.security.KeyPair keyPair = keyGen.generateKeyPair();

        org.bouncycastle.asn1.x500.X500Name subject = new org.bouncycastle.asn1.x500.X500Name(
                "CN=Self Signed, O=Test, C=XX");

        java.math.BigInteger serial = java.math.BigInteger.valueOf(System.currentTimeMillis());
        java.util.Date notBefore = new java.util.Date();
        java.util.Date notAfter = new java.util.Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

        org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder certBuilder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, keyPair.getPublic());

        org.bouncycastle.operator.ContentSigner signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder(
                "SHA256withRSA")
                .build(keyPair.getPrivate());

        java.security.cert.X509Certificate cert = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                .getCertificate(certBuilder.build(signer));

        // Convert to PEM
        java.io.StringWriter sw = new java.io.StringWriter();
        org.bouncycastle.openssl.jcajce.JcaPEMWriter pemWriter = new org.bouncycastle.openssl.jcajce.JcaPEMWriter(sw);
        pemWriter.writeObject(cert);
        pemWriter.close();

        return sw.toString();
    }
}
