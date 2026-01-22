package com.pesitwizard.server.integration;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for mTLS (mutual TLS) socket-level communication.
 * These tests verify actual TLS handshakes between client and server,
 * ensuring proper certificate validation in both directions.
 */
@DisplayName("mTLS Socket Integration Tests")
public class MtlsSocketIntegrationTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MtlsSocketIntegrationTest.class);
    private static final String CERTS_DIR = "src/test/resources/certs";
    private static final String PASSWORD = "changeit";
    private static final int TEST_PORT_BASE = 19000;

    private static byte[] serverKeystoreBytes;
    private static byte[] clientKeystoreBytes;
    private static byte[] caTruststoreBytes;
    private static byte[] untrustedKeystoreBytes;

    private ExecutorService executor;
    private int testPort;
    private static int portCounter = 0;

    @BeforeAll
    static void loadCertificates() throws Exception {
        serverKeystoreBytes = Files.readAllBytes(Path.of(CERTS_DIR, "server-keystore.p12"));
        clientKeystoreBytes = Files.readAllBytes(Path.of(CERTS_DIR, "client-keystore.p12"));
        caTruststoreBytes = Files.readAllBytes(Path.of(CERTS_DIR, "ca-truststore.p12"));
        untrustedKeystoreBytes = Files.readAllBytes(Path.of(CERTS_DIR, "untrusted-keystore.p12"));
        log.info("Loaded test certificates for mTLS socket tests");
    }

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        testPort = TEST_PORT_BASE + (portCounter++ % 1000);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Nested
    @DisplayName("Successful mTLS Communication")
    class SuccessfulMtlsTests {

        @Test
        @Timeout(30)
        @DisplayName("Should establish mTLS connection with valid certificates")
        void shouldEstablishMtlsConnection() throws Exception {
            SSLContext serverContext = createServerSslContext();
            SSLContext clientContext = createClientSslContext();

            CountDownLatch serverReady = new CountDownLatch(1);
            CountDownLatch handshakeComplete = new CountDownLatch(1);
            AtomicBoolean serverSuccess = new AtomicBoolean(false);
            AtomicBoolean clientSuccess = new AtomicBoolean(false);
            AtomicReference<String> clientDn = new AtomicReference<>();

            // Start server
            executor.submit(() -> {
                try (SSLServerSocket serverSocket = createMtlsServerSocket(serverContext, testPort)) {
                    serverReady.countDown();
                    log.info("mTLS server listening on port {}", testPort);

                    try (SSLSocket clientConnection = (SSLSocket) serverSocket.accept()) {
                        clientConnection.startHandshake();

                        // Get client certificate DN
                        var clientCerts = clientConnection.getSession().getPeerCertificates();
                        if (clientCerts.length > 0) {
                            var x509Cert = (java.security.cert.X509Certificate) clientCerts[0];
                            clientDn.set(x509Cert.getSubjectX500Principal().getName());
                            log.info("Server received client cert: {}", clientDn.get());
                        }

                        // Exchange test data
                        DataInputStream in = new DataInputStream(clientConnection.getInputStream());
                        DataOutputStream out = new DataOutputStream(clientConnection.getOutputStream());

                        String received = in.readUTF();
                        log.info("Server received: {}", received);
                        out.writeUTF("HELLO_FROM_SERVER");
                        out.flush();

                        serverSuccess.set("HELLO_FROM_CLIENT".equals(received));
                    }
                } catch (Exception e) {
                    log.error("Server error", e);
                } finally {
                    handshakeComplete.countDown();
                }
            });

            // Wait for server to be ready
            assertTrue(serverReady.await(5, TimeUnit.SECONDS), "Server should start");

            // Connect client
            try (SSLSocket clientSocket = createMtlsClientSocket(clientContext, testPort)) {
                clientSocket.startHandshake();
                log.info("Client handshake complete, protocol: {}", clientSocket.getSession().getProtocol());

                // Verify server certificate
                var serverCerts = clientSocket.getSession().getPeerCertificates();
                assertThat(serverCerts).isNotEmpty();
                var serverCert = (java.security.cert.X509Certificate) serverCerts[0];
                log.info("Client verified server cert: {}", serverCert.getSubjectX500Principal().getName());

                // Exchange test data
                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

                out.writeUTF("HELLO_FROM_CLIENT");
                out.flush();
                String received = in.readUTF();
                log.info("Client received: {}", received);

                clientSuccess.set("HELLO_FROM_SERVER".equals(received));
            }

            assertTrue(handshakeComplete.await(10, TimeUnit.SECONDS), "Handshake should complete");
            assertTrue(serverSuccess.get(), "Server should receive correct message");
            assertTrue(clientSuccess.get(), "Client should receive correct message");
            assertThat(clientDn.get()).contains("test-client");

            log.info("mTLS communication successful");
        }

        @Test
        @Timeout(30)
        @DisplayName("Should use TLS 1.3 or TLS 1.2")
        void shouldUseModernTlsProtocol() throws Exception {
            SSLContext serverContext = createServerSslContext();
            SSLContext clientContext = createClientSslContext();

            CountDownLatch serverReady = new CountDownLatch(1);
            AtomicReference<String> negotiatedProtocol = new AtomicReference<>();

            executor.submit(() -> {
                try (SSLServerSocket serverSocket = createMtlsServerSocket(serverContext, testPort)) {
                    serverReady.countDown();
                    try (SSLSocket clientConnection = (SSLSocket) serverSocket.accept()) {
                        clientConnection.startHandshake();
                        negotiatedProtocol.set(clientConnection.getSession().getProtocol());
                    }
                } catch (Exception e) {
                    log.error("Server error", e);
                }
            });

            assertTrue(serverReady.await(5, TimeUnit.SECONDS));

            try (SSLSocket clientSocket = createMtlsClientSocket(clientContext, testPort)) {
                clientSocket.startHandshake();
            }

            Thread.sleep(500); // Wait for server to record protocol
            assertThat(negotiatedProtocol.get()).isIn("TLSv1.3", "TLSv1.2");
            log.info("Negotiated protocol: {}", negotiatedProtocol.get());
        }
    }

    @Nested
    @DisplayName("mTLS Rejection Scenarios")
    class MtlsRejectionTests {

        @Test
        @Timeout(30)
        @DisplayName("Should reject client with untrusted certificate")
        void shouldRejectUntrustedClient() throws Exception {
            SSLContext serverContext = createServerSslContext();
            SSLContext untrustedClientContext = createUntrustedClientSslContext();

            CountDownLatch serverReady = new CountDownLatch(1);
            AtomicBoolean handshakeFailed = new AtomicBoolean(false);
            AtomicReference<Exception> serverException = new AtomicReference<>();

            executor.submit(() -> {
                try (SSLServerSocket serverSocket = createMtlsServerSocket(serverContext, testPort)) {
                    serverReady.countDown();
                    try (SSLSocket clientConnection = (SSLSocket) serverSocket.accept()) {
                        clientConnection.startHandshake();
                        // Try to read - this should fail if handshake issues occur late
                        clientConnection.getInputStream().read();
                        log.error("Connection should have failed for untrusted client");
                    }
                } catch (SSLHandshakeException e) {
                    handshakeFailed.set(true);
                    serverException.set(e);
                    log.info("Server correctly rejected untrusted client: {}", e.getMessage());
                } catch (IOException e) {
                    // Connection reset or other IO error also indicates rejection
                    handshakeFailed.set(true);
                    serverException.set(e);
                    log.info("Server connection failed (expected): {}", e.getMessage());
                } catch (Exception e) {
                    serverException.set(e);
                    log.error("Unexpected server error", e);
                }
            });

            assertTrue(serverReady.await(5, TimeUnit.SECONDS));

            try (SSLSocket clientSocket = createMtlsClientSocket(untrustedClientContext, testPort)) {
                clientSocket.startHandshake();
                // Try to exchange data - should fail
                clientSocket.getOutputStream().write(1);
                clientSocket.getOutputStream().flush();
                int response = clientSocket.getInputStream().read();
                fail("Connection with untrusted cert should fail, but got response: " + response);
            } catch (SSLHandshakeException e) {
                log.info("Client handshake correctly failed: {}", e.getMessage());
            } catch (IOException e) {
                log.info("Client connection correctly failed: {}", e.getMessage());
            }

            Thread.sleep(500);
            assertTrue(handshakeFailed.get(),
                    "Server should reject untrusted client. Server exception: " + serverException.get());
            log.info("Untrusted client correctly rejected");
        }

        @Test
        @Timeout(30)
        @DisplayName("Should reject client without certificate when mTLS is required")
        void shouldRejectClientWithoutCertificate() throws Exception {
            SSLContext serverContext = createServerSslContext();
            // Create client context without client certificate (only truststore)
            SSLContext noCertClientContext = createClientContextWithoutCert();

            CountDownLatch serverReady = new CountDownLatch(1);
            AtomicBoolean handshakeFailed = new AtomicBoolean(false);
            AtomicReference<Exception> serverException = new AtomicReference<>();

            executor.submit(() -> {
                try (SSLServerSocket serverSocket = createMtlsServerSocket(serverContext, testPort)) {
                    serverReady.countDown();
                    try (SSLSocket clientConnection = (SSLSocket) serverSocket.accept()) {
                        clientConnection.startHandshake();
                        // Try to read - this should fail
                        clientConnection.getInputStream().read();
                        log.error("Connection should have failed for client without cert");
                    }
                } catch (SSLHandshakeException e) {
                    handshakeFailed.set(true);
                    serverException.set(e);
                    log.info("Server correctly rejected client without cert: {}", e.getMessage());
                } catch (IOException e) {
                    handshakeFailed.set(true);
                    serverException.set(e);
                    log.info("Server connection failed (expected): {}", e.getMessage());
                } catch (Exception e) {
                    serverException.set(e);
                    log.error("Unexpected server error", e);
                }
            });

            assertTrue(serverReady.await(5, TimeUnit.SECONDS));

            try (SSLSocket clientSocket = createMtlsClientSocket(noCertClientContext, testPort)) {
                clientSocket.startHandshake();
                // Try to exchange data - should fail
                clientSocket.getOutputStream().write(1);
                clientSocket.getOutputStream().flush();
                int response = clientSocket.getInputStream().read();
                fail("Client without cert should fail, but got response: " + response);
            } catch (IOException e) {
                // Client correctly failed - expected behavior
            }

            Thread.sleep(500);
            assertTrue(handshakeFailed.get(),
                    "Server should reject client without certificate. Server exception: " + serverException.get());
            // Client without certificate correctly rejected
        }
    }

    @Nested
    @DisplayName("Server Certificate Validation")
    class ServerCertValidationTests {

        @Test
        @Timeout(30)
        @DisplayName("Client should reject server with untrusted certificate")
        void clientShouldRejectUntrustedServer() throws Exception {
            // Server uses untrusted (self-signed) cert
            SSLContext untrustedServerContext = createUntrustedServerSslContext();
            SSLContext clientContext = createClientSslContext();

            CountDownLatch serverReady = new CountDownLatch(1);

            executor.submit(() -> {
                try (SSLServerSocket serverSocket = createMtlsServerSocket(untrustedServerContext, testPort)) {
                    serverReady.countDown();
                    try (SSLSocket clientConnection = (SSLSocket) serverSocket.accept()) {
                        clientConnection.startHandshake();
                    }
                } catch (Exception e) {
                    // Server saw handshake failure (expected)
                }
            });

            assertTrue(serverReady.await(5, TimeUnit.SECONDS));

            assertThrows(SSLHandshakeException.class, () -> {
                try (SSLSocket clientSocket = createMtlsClientSocket(clientContext, testPort)) {
                    clientSocket.startHandshake();
                }
            }, "Client should reject untrusted server");

            // Client correctly rejected untrusted server certificate
        }
    }

    // ==================== Helper Methods ====================

    private SSLContext createServerSslContext() throws Exception {
        return createSslContext(serverKeystoreBytes, caTruststoreBytes);
    }

    private SSLContext createClientSslContext() throws Exception {
        return createSslContext(clientKeystoreBytes, caTruststoreBytes);
    }

    private SSLContext createUntrustedClientSslContext() throws Exception {
        return createSslContext(untrustedKeystoreBytes, caTruststoreBytes);
    }

    private SSLContext createUntrustedServerSslContext() throws Exception {
        return createSslContext(untrustedKeystoreBytes, caTruststoreBytes);
    }

    private SSLContext createClientContextWithoutCert() throws Exception {
        // Only truststore, no keystore (no client cert)
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(new java.io.ByteArrayInputStream(caTruststoreBytes), PASSWORD.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        return sslContext;
    }

    private SSLContext createSslContext(byte[] keystoreBytes, byte[] truststoreBytes) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new java.io.ByteArrayInputStream(keystoreBytes), PASSWORD.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, PASSWORD.toCharArray());

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(new java.io.ByteArrayInputStream(truststoreBytes), PASSWORD.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }

    private SSLServerSocket createMtlsServerSocket(SSLContext sslContext, int port) throws IOException {
        SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(port);

        // Require client authentication (mTLS)
        serverSocket.setNeedClientAuth(true);

        // Enable modern protocols only
        serverSocket.setEnabledProtocols(new String[] { "TLSv1.3", "TLSv1.2" });

        serverSocket.setSoTimeout(10000);
        return serverSocket;
    }

    private SSLSocket createMtlsClientSocket(SSLContext sslContext, int port) throws IOException {
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket socket = (SSLSocket) factory.createSocket("localhost", port);

        // Enable modern protocols only
        socket.setEnabledProtocols(new String[] { "TLSv1.3", "TLSv1.2" });

        socket.setSoTimeout(10000);
        return socket;
    }
}
