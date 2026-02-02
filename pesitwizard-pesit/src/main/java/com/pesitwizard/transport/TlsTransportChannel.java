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

        // Configure TLS protocols - use TLSv1.2 for compatibility with older servers
        // CX (Connect:Express) may not support TLSv1.3
        String tlsProtocol = System.getProperty("pesit.tls.protocol", "TLSv1.2");
        sslSocket.setEnabledProtocols(new String[] { tlsProtocol });
        log.debug("TLS protocols enabled: {}", (Object) sslSocket.getEnabledProtocols());

        // Perform TLS handshake
        sslSocket.startHandshake();

        SSLSession session = sslSocket.getSession();
        log.info("TLS connection established: protocol={}, cipher={}",
                session.getProtocol(), session.getCipherSuite());

        return sslSocket;
    }

    /**
     * Send data over TLS without the 2-byte length prefix.
     * <p>
     * Connect:Express TLS protocol differs from plain TCP:
     * - TCP: expects [2-byte length prefix] + [FPDU data]
     * - TLS: expects just [FPDU data] (FPDU contains its own length field)
     * </p>
     */
    @Override
    public void send(byte[] data) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected");
        }

        // For TLS, send FPDU directly without additional length prefix
        // The FPDU already contains its own length field in the first 2 bytes
        outputStream.write(data);
        outputStream.flush();

        log.debug("Sent {} bytes to {}:{}", data.length, host, port);
    }

    /**
     * Receive data over TLS without expecting a 2-byte length prefix.
     * <p>
     * Connect:Express TLS protocol differs from plain TCP:
     * - TCP: returns [2-byte length prefix] + [FPDU data]
     * - TLS: returns just [FPDU data] (length is in FPDU's first 2 bytes)
     * </p>
     */
    @Override
    public byte[] receive() throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected");
        }

        // For TLS, read the FPDU length from the FPDU header itself (first 2 bytes)
        int length = inputStream.readUnsignedShort();
        if (length <= 0) {
            throw new IOException("Invalid FPDU length: " + length);
        }

        // Read remaining data (length includes the 2-byte length field)
        byte[] data = new byte[length];
        // Put the length back as first 2 bytes
        data[0] = (byte) ((length >> 8) & 0xFF);
        data[1] = (byte) (length & 0xFF);
        // Read the rest
        inputStream.readFully(data, 2, length - 2);

        log.debug("Received {} bytes from {}:{}", length, host, port);
        return data;
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
