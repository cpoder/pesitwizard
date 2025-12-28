package com.pesitwizard.transport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * TLS/SSL transport implementation for secure PESIT connections.
 * Supports mutual TLS (mTLS) with client certificates.
 */
@Slf4j
public class TlsTransportChannel extends AbstractSocketTransportChannel {

    private final SSLContext sslContext;

    /**
     * Create TLS channel with default trust (system truststore)
     */
    public TlsTransportChannel(String host, int port) {
        super(host, port);
        try {
            this.sslContext = SSLContext.getDefault();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize default SSL context", e);
        }
    }

    /**
     * Create TLS channel with custom truststore only (no client cert)
     */
    public TlsTransportChannel(String host, int port, byte[] truststoreData, String truststorePassword) {
        this(host, port, truststoreData, truststorePassword, null, null);
    }

    /**
     * Create TLS channel with custom truststore and keystore (mutual TLS)
     */
    public TlsTransportChannel(String host, int port,
            byte[] truststoreData, String truststorePassword,
            byte[] keystoreData, String keystorePassword) {
        super(host, port);

        try {
            // Load truststore
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (ByteArrayInputStream bis = new ByteArrayInputStream(truststoreData)) {
                trustStore.load(bis, truststorePassword != null ? truststorePassword.toCharArray() : null);
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            KeyManager[] keyManagers = null;

            // Load keystore for mutual TLS if provided
            if (keystoreData != null && keystoreData.length > 0) {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (ByteArrayInputStream bis = new ByteArrayInputStream(keystoreData)) {
                    keyStore.load(bis, keystorePassword != null ? keystorePassword.toCharArray() : null);
                }

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, keystorePassword != null ? keystorePassword.toCharArray() : null);
                keyManagers = kmf.getKeyManagers();
                log.info("Mutual TLS enabled with keystore ({} bytes)", keystoreData.length);
            }

            this.sslContext = SSLContext.getInstance("TLS");
            this.sslContext.init(keyManagers, tmf.getTrustManagers(), null);

            log.info("TLS context initialized with truststore ({} bytes)", truststoreData.length);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSL context: " + e.getMessage(), e);
        }
    }

    @Override
    protected Socket createSocket() throws IOException {
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, port);

        // Perform TLS handshake
        sslSocket.startHandshake();

        SSLSession session = sslSocket.getSession();
        log.info("TLS connection established: protocol={}, cipher={}",
                session.getProtocol(), session.getCipherSuite());

        return sslSocket;
    }

    @Override
    public boolean isSecure() {
        return true;
    }

    @Override
    public TransportType getTransportType() {
        return TransportType.SSL;
    }

    /**
     * Get the SSL session information
     */
    public SSLSession getSession() {
        return socket instanceof SSLSocket ? ((SSLSocket) socket).getSession() : null;
    }
}
