package com.vectis.client.transport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import com.vectis.transport.TransportChannel;
import com.vectis.transport.TransportType;

import lombok.extern.slf4j.Slf4j;

/**
 * TLS/SSL enabled transport channel for secure PeSIT connections
 */
@Slf4j
public class TlsTransportChannel implements TransportChannel {

    private final String host;
    private final int port;
    private final SSLContext sslContext;

    private SSLSocket socket;
    private DataInputStream inputStream;
    private OutputStream outputStream;
    private int receiveTimeout = 60000;

    /**
     * Create TLS channel with default trust (system truststore)
     */
    public TlsTransportChannel(String host, int port) {
        this.host = host;
        this.port = port;
        try {
            this.sslContext = SSLContext.getDefault();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize default SSL context", e);
        }
    }

    /**
     * Create TLS channel with custom truststore
     */
    public TlsTransportChannel(String host, int port, String truststorePath, String truststorePassword) {
        this(host, port, truststorePath, truststorePassword, null, null);
    }

    /**
     * Create TLS channel with custom truststore and keystore (mutual TLS)
     */
    public TlsTransportChannel(String host, int port,
            String truststorePath, String truststorePassword,
            String keystorePath, String keystorePassword) {
        this.host = host;
        this.port = port;

        try {
            // Load truststore
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(truststorePath)) {
                trustStore.load(fis, truststorePassword != null ? truststorePassword.toCharArray() : null);
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            KeyManager[] keyManagers = null;

            // Load keystore for mutual TLS if provided
            if (keystorePath != null) {
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                try (FileInputStream fis = new FileInputStream(keystorePath)) {
                    keyStore.load(fis, keystorePassword != null ? keystorePassword.toCharArray() : null);
                }

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, keystorePassword != null ? keystorePassword.toCharArray() : null);
                keyManagers = kmf.getKeyManagers();
            }

            this.sslContext = SSLContext.getInstance("TLS");
            this.sslContext.init(keyManagers, tmf.getTrustManagers(), null);

            log.info("TLS context initialized with custom truststore: {}", truststorePath);
            if (keystorePath != null) {
                log.info("Mutual TLS enabled with keystore: {}", keystorePath);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSL context", e);
        }
    }

    @Override
    public void connect() throws IOException {
        log.debug("Connecting via TLS to {}:{}", host, port);

        SSLSocketFactory factory = sslContext.getSocketFactory();
        socket = (SSLSocket) factory.createSocket(host, port);
        socket.setSoTimeout(receiveTimeout);

        // Start TLS handshake
        socket.startHandshake();

        SSLSession session = socket.getSession();
        log.info("TLS connection established: protocol={}, cipher={}",
                session.getProtocol(), session.getCipherSuite());

        inputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        outputStream = new BufferedOutputStream(socket.getOutputStream());
    }

    public void disconnect() throws IOException {
        log.debug("Disconnecting TLS channel");
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null;
        inputStream = null;
        outputStream = null;
    }

    @Override
    public void send(byte[] data) throws IOException {
        if (outputStream == null) {
            throw new IOException("Channel not connected");
        }
        outputStream.write(data);
        outputStream.flush();
    }

    @Override
    public byte[] receive() throws IOException {
        if (inputStream == null) {
            throw new IOException("Channel not connected");
        }
        // Read FPDU header (4 bytes: length)
        byte[] header = new byte[4];
        inputStream.readFully(header);
        int length = ((header[0] & 0xFF) << 24) | ((header[1] & 0xFF) << 16) |
                ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);

        // Read FPDU body
        byte[] body = new byte[length];
        inputStream.readFully(body);

        // Combine header and body
        byte[] result = new byte[4 + length];
        System.arraycopy(header, 0, result, 0, 4);
        System.arraycopy(body, 0, result, 4, length);
        return result;
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        disconnect();
    }

    @Override
    public void setReceiveTimeout(int timeoutMs) {
        this.receiveTimeout = timeoutMs;
        if (socket != null) {
            try {
                socket.setSoTimeout(timeoutMs);
            } catch (Exception e) {
                log.warn("Failed to set socket timeout", e);
            }
        }
    }

    @Override
    public String getRemoteAddress() {
        return socket != null ? socket.getRemoteSocketAddress().toString() : host + ":" + port;
    }

    @Override
    public String getLocalAddress() {
        return socket != null ? socket.getLocalSocketAddress().toString() : "unknown";
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
        return socket != null ? socket.getSession() : null;
    }
}
