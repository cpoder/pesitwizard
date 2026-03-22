package com.pesitwizard.common.crypto;

import static org.assertj.core.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CryptoUtilsTest {

    private static KeyPair keyPair;
    private static X500Name subject;

    @BeforeAll
    static void setUp() throws CryptoException {
        keyPair = KeystoreUtils.generateKeyPair(2048);
        subject = new X500Name("CN=Test,O=PeSIT Wizard,C=FR");
    }

    @Nested
    class CertificateUtilsTests {

        @Test
        void generateSelfSignedCertificate_caCert_hasKeyCertSign() throws Exception {
            X509Certificate cert =
                    CertificateUtils.generateSelfSignedCertificate(
                            keyPair, subject, Duration.ofDays(365), true);

            assertThat(cert).isNotNull();
            assertThat(cert.getBasicConstraints()).isGreaterThanOrEqualTo(0); // is CA
            boolean[] keyUsage = cert.getKeyUsage();
            assertThat(keyUsage).isNotNull();
            assertThat(keyUsage[5]).isTrue(); // keyCertSign
        }

        @Test
        void generateSelfSignedCertificate_nonCaCert_noKeyCertSign() throws Exception {
            X509Certificate cert =
                    CertificateUtils.generateSelfSignedCertificate(
                            keyPair, subject, Duration.ofDays(365), false);

            assertThat(cert).isNotNull();
            assertThat(cert.getBasicConstraints()).isEqualTo(-1); // not CA
        }

        @Test
        void generateSelfSignedCertificate_subjectAndValidityCorrect() throws Exception {
            X509Certificate cert =
                    CertificateUtils.generateSelfSignedCertificate(
                            keyPair, subject, Duration.ofDays(30), false);

            assertThat(cert.getSubjectX500Principal().getName()).contains("CN=Test");
            assertThat(cert.getIssuerX500Principal().getName()).contains("CN=Test"); // self-signed
            assertThat(cert.getSerialNumber()).isPositive();
        }

        @Test
        void buildSignedCertificate_serverPurpose_hasSanAndServerAuth() throws Exception {
            KeyPair caKeyPair = KeystoreUtils.generateKeyPair(2048);
            X500Name caSubject = new X500Name("CN=TestCA,O=PeSIT Wizard,C=FR");
            X509Certificate caCert =
                    CertificateUtils.generateSelfSignedCertificate(
                            caKeyPair, caSubject, Duration.ofDays(365), true);

            KeyPair serverKeyPair = KeystoreUtils.generateKeyPair(2048);
            X500Name serverSubject = new X500Name("CN=server.example.com,O=PeSIT Wizard,C=FR");

            X509Certificate serverCert =
                    CertificateUtils.buildSignedCertificate(
                            serverSubject,
                            serverKeyPair.getPublic(),
                            caCert,
                            caKeyPair.getPrivate(),
                            365,
                            "SERVER");

            assertThat(serverCert).isNotNull();
            assertThat(serverCert.getBasicConstraints()).isEqualTo(-1); // not CA
            assertThat(serverCert.getIssuerX500Principal().getName()).contains("CN=TestCA");

            // Check SAN
            assertThat(serverCert.getSubjectAlternativeNames()).isNotNull();

            // Check serverAuth EKU
            assertThat(serverCert.getExtendedKeyUsage()).contains("1.3.6.1.5.5.7.3.1");
        }

        @Test
        void buildSignedCertificate_clientPurpose_hasClientAuth() throws Exception {
            KeyPair caKeyPair = KeystoreUtils.generateKeyPair(2048);
            X500Name caSubject = new X500Name("CN=TestCA,O=PeSIT Wizard,C=FR");
            X509Certificate caCert =
                    CertificateUtils.generateSelfSignedCertificate(
                            caKeyPair, caSubject, Duration.ofDays(365), true);

            KeyPair clientKeyPair = KeystoreUtils.generateKeyPair(2048);
            X500Name clientSubject = new X500Name("CN=client,O=PeSIT Wizard,C=FR");

            X509Certificate clientCert =
                    CertificateUtils.buildSignedCertificate(
                            clientSubject,
                            clientKeyPair.getPublic(),
                            caCert,
                            caKeyPair.getPrivate(),
                            365,
                            "CLIENT");

            assertThat(clientCert).isNotNull();
            // Check clientAuth EKU
            assertThat(clientCert.getExtendedKeyUsage()).contains("1.3.6.1.5.5.7.3.2");
            // No SAN for client certs
            assertThat(clientCert.getSubjectAlternativeNames()).isNull();
        }

        @Test
        void extractCn_withCn_returnsCn() {
            X500Name name = new X500Name("CN=MyHost,O=Org,C=FR");
            assertThat(CertificateUtils.extractCn(name)).isEqualTo("MyHost");
        }

        @Test
        void extractCn_withoutCn_returnsNull() {
            X500Name name = new X500Name("O=Org,C=FR");
            assertThat(CertificateUtils.extractCn(name)).isNull();
        }

        @Test
        void calculateFingerprint_returnsSha256Hex() throws Exception {
            X509Certificate cert =
                    CertificateUtils.generateSelfSignedCertificate(
                            keyPair, subject, Duration.ofDays(365), false);

            String fingerprint = CertificateUtils.calculateFingerprint(cert);
            assertThat(fingerprint).isNotNull();
            assertThat(fingerprint).matches("([0-9A-F]{2}:){31}[0-9A-F]{2}");
        }
    }

    @Nested
    class KeystoreUtilsTests {

        @Test
        void generateKeyPair_returnsValidRsaPair() throws CryptoException {
            KeyPair kp = KeystoreUtils.generateKeyPair(2048);
            assertThat(kp).isNotNull();
            assertThat(kp.getPublic().getAlgorithm()).isEqualTo("RSA");
            assertThat(kp.getPrivate().getAlgorithm()).isEqualTo("RSA");
        }

        @Test
        void createKeystore_roundtrip() throws Exception {
            X509Certificate cert =
                    CertificateUtils.generateSelfSignedCertificate(
                            keyPair, subject, Duration.ofDays(365), false);
            String password = "testpass";

            byte[] ksBytes =
                    KeystoreUtils.createKeystore(cert, keyPair.getPrivate(), "mykey", password);
            assertThat(ksBytes).isNotEmpty();

            KeyStore ks = KeystoreUtils.loadKeystore(ksBytes, password);
            assertThat(ks.containsAlias("mykey")).isTrue();
            assertThat(ks.getCertificate("mykey")).isEqualTo(cert);
            assertThat(ks.getKey("mykey", password.toCharArray())).isNotNull();
        }

        @Test
        void createKeystoreWithChain_roundtrip() throws Exception {
            KeyPair caKeyPair = KeystoreUtils.generateKeyPair(2048);
            X500Name caSubject = new X500Name("CN=CA,O=Test,C=FR");
            X509Certificate caCert =
                    CertificateUtils.generateSelfSignedCertificate(
                            caKeyPair, caSubject, Duration.ofDays(365), true);

            KeyPair leafKeyPair = KeystoreUtils.generateKeyPair(2048);
            X500Name leafSubject = new X500Name("CN=leaf,O=Test,C=FR");
            X509Certificate leafCert =
                    CertificateUtils.buildSignedCertificate(
                            leafSubject,
                            leafKeyPair.getPublic(),
                            caCert,
                            caKeyPair.getPrivate(),
                            365,
                            "SERVER");

            java.security.cert.Certificate[] chain = {leafCert, caCert};
            byte[] ksBytes =
                    KeystoreUtils.createKeystoreWithChain(
                            leafKeyPair.getPrivate(), chain, "server", "pass");

            KeyStore ks = KeystoreUtils.loadKeystore(ksBytes, "pass");
            java.security.cert.Certificate[] loadedChain = ks.getCertificateChain("server");
            assertThat(loadedChain).hasSize(2);
        }

        @Test
        void createTruststore_roundtrip() throws Exception {
            X509Certificate cert =
                    CertificateUtils.generateSelfSignedCertificate(
                            keyPair, subject, Duration.ofDays(365), false);

            byte[] tsBytes = KeystoreUtils.createTruststore("ca", cert, "trustpass");
            assertThat(tsBytes).isNotEmpty();

            KeyStore ts = KeystoreUtils.loadKeystore(tsBytes, "trustpass");
            assertThat(ts.containsAlias("ca")).isTrue();
            assertThat(ts.getCertificate("ca")).isEqualTo(cert);
        }

        @Test
        void loadKeystore_invalidData_throwsCryptoException() {
            assertThatThrownBy(() -> KeystoreUtils.loadKeystore(new byte[] {1, 2, 3}, "pass"))
                    .isInstanceOf(CryptoException.class);
        }

        @Test
        void loadKeystore_wrongPassword_throwsCryptoException() throws Exception {
            X509Certificate cert =
                    CertificateUtils.generateSelfSignedCertificate(
                            keyPair, subject, Duration.ofDays(365), false);
            byte[] ksBytes =
                    KeystoreUtils.createKeystore(cert, keyPair.getPrivate(), "k", "correct");

            assertThatThrownBy(() -> KeystoreUtils.loadKeystore(ksBytes, "wrong"))
                    .isInstanceOf(CryptoException.class);
        }

        @Test
        void generateSecurePassword_returnsNonNullUniqueValues() {
            String p1 = KeystoreUtils.generateSecurePassword();
            String p2 = KeystoreUtils.generateSecurePassword();
            assertThat(p1).isNotNull().isNotEmpty();
            assertThat(p2).isNotNull().isNotEmpty();
            assertThat(p1).isNotEqualTo(p2);
        }
    }

    @Nested
    class PemUtilsTests {

        @Test
        void toPem_cert_roundtrip() throws Exception {
            X509Certificate cert =
                    CertificateUtils.generateSelfSignedCertificate(
                            keyPair, subject, Duration.ofDays(365), false);

            String pem = PemUtils.toPem(cert);
            assertThat(pem).startsWith("-----BEGIN CERTIFICATE-----");

            X509Certificate parsed = PemUtils.parseCertificate(pem);
            assertThat(parsed).isEqualTo(cert);
        }

        @Test
        void toPem_privateKey_returnsPemString() throws Exception {
            String pem = PemUtils.toPem(keyPair.getPrivate());
            assertThat(pem).contains("-----BEGIN");
            assertThat(pem).contains("PRIVATE KEY-----");
        }

        @Test
        void parseCertificate_invalidPem_throwsCryptoException() {
            assertThatThrownBy(() -> PemUtils.parseCertificate("not a cert"))
                    .isInstanceOf(CryptoException.class);
        }

        @Test
        void parsePrivateKey_pkcs8Pem_returnsKey() throws Exception {
            String pem = PemUtils.toPem(keyPair.getPrivate());
            PrivateKey parsed = PemUtils.parsePrivateKey(pem);
            assertThat(parsed).isNotNull();
            assertThat(parsed.getAlgorithm()).isEqualTo("RSA");
        }

        @Test
        void parsePrivateKey_invalidPem_throwsException() {
            assertThatThrownBy(() -> PemUtils.parsePrivateKey("not a key"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    class CsrUtilsTests {

        @Test
        void generateCsr_validOutput() throws Exception {
            String csrPem = CsrUtils.generateCsr(keyPair, "CN=Test,O=PeSIT Wizard,C=FR");
            assertThat(csrPem).startsWith("-----BEGIN CERTIFICATE REQUEST-----");
        }

        @Test
        void generateCsr_subjectDnPreserved() throws Exception {
            String csrPem = CsrUtils.generateCsr(keyPair, "CN=MyHost,O=Org,C=US");
            PKCS10CertificationRequest csr = CsrUtils.parseCsr(csrPem);
            assertThat(csr.getSubject().toString()).contains("CN=MyHost");
        }

        @Test
        void parseCsr_roundtrip() throws Exception {
            String csrPem = CsrUtils.generateCsr(keyPair, "CN=Round,O=Trip,C=FR");
            PKCS10CertificationRequest csr = CsrUtils.parseCsr(csrPem);
            assertThat(csr).isNotNull();
            assertThat(csr.getSubject().toString()).contains("CN=Round");
        }

        @Test
        void parseCsr_invalidInput_throwsCryptoException() {
            assertThatThrownBy(() -> CsrUtils.parseCsr("not a CSR"))
                    .isInstanceOf(CryptoException.class);
        }
    }

    @Nested
    class CryptoExceptionTests {

        @Test
        void constructorWithMessage() {
            CryptoException ex = new CryptoException("test error");
            assertThat(ex.getMessage()).isEqualTo("test error");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        void constructorWithMessageAndCause() {
            RuntimeException cause = new RuntimeException("root cause");
            CryptoException ex = new CryptoException("wrapped", cause);
            assertThat(ex.getMessage()).isEqualTo("wrapped");
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }
}
