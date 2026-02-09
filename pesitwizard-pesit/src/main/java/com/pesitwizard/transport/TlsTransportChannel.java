package com.pesitwizard.transport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
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
    private boolean hostnameVerification = true;
    private String[] enabledProtocols = new String[] { "TLSv1.3", "TLSv1.2" };

    /**
     * When true (default), uses standard PeSIT framing with 2-byte length prefix
     * (compatible with PeSIT Wizard server).
     * When false, uses Connect:Express TLS framing where the FPDU's internal
     * length field serves as the framing delimiter (no external prefix).
     */
    private boolean standardFraming = true;

    /**
     * Create TLS channel with default trust (system truststore)
     */
    public TlsTransportChannel(String host, int port) {
        super(host, port);
        try {
            this.sslContext = SSLContext.getDefault();
        } catch (java.security.NoSuchAlgorithmException e) {
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

        } catch (java.security.GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize SSL context: " + e.getMessage(), e);
        }
    }

    @Override
    protected Socket createSocket() throws IOException {
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, port);

        // Restrict TLS protocol versions (S3-01)
        sslSocket.setEnabledProtocols(enabledProtocols);
        log.debug("TLS protocols enabled: {}", (Object) sslSocket.getEnabledProtocols());

        // Filter out insecure cipher suites (S3-02)
        String[] ciphers = sslSocket.getEnabledCipherSuites();
        String[] filtered = java.util.Arrays.stream(ciphers)
                .filter(c -> {
                    String u = c.toUpperCase();
                    return !u.contains("_NULL") && !u.contains("_ANON")
                            && !u.contains("EXPORT") && !u.contains("_DES_")
                            && !u.contains("_DES40_") && !u.contains("_3DES_")
                            && !u.contains("DES_CBC") && !u.contains("_RC4");
                })
                .toArray(String[]::new);
        sslSocket.setEnabledCipherSuites(filtered);

        // Configure hostname verification to prevent MITM attacks.
        // When enabled, the server certificate's CN/SAN must match the target hostname.
        if (hostnameVerification) {
            SSLParameters sslParams = sslSocket.getSSLParameters();
            sslParams.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(sslParams);
            log.debug("TLS hostname verification enabled for host: {}", host);
        } else {
            log.warn("TLS hostname verification is DISABLED for {}:{} — vulnerable to MITM attacks", host, port);
        }

        // Perform TLS handshake
        sslSocket.startHandshake();

        SSLSession session = sslSocket.getSession();
        log.info("TLS connection established: protocol={}, cipher={}",
                session.getProtocol(), session.getCipherSuite());

        return sslSocket;
    }

    /**
     * Send data over TLS.
     * <p>
     * In standard framing mode (default), sends with 2-byte length prefix
     * like TCP, compatible with PeSIT Wizard server.
     * In Connect:Express framing mode, sends FPDU directly without prefix
     * (FPDU contains its own length field in the first 2 bytes).
     * </p>
     */
    @Override
    public void send(byte[] data) throws IOException {
        if (standardFraming) {
            super.send(data);
            return;
        }

        if (!isConnected()) {
            throw new IOException("Not connected");
        }

        // Connect:Express TLS: send FPDU directly without additional length prefix
        outputStream.write(data);
        outputStream.flush();

        log.debug("Sent {} bytes to {}:{} (CX framing)", data.length, host, port);
    }

    /**
     * Receive data over TLS.
     * <p>
     * In standard framing mode (default), reads with 2-byte length prefix
     * like TCP, compatible with PeSIT Wizard server.
     * In Connect:Express framing mode, reads the FPDU length from the FPDU's
     * internal header (first 2 bytes).
     * </p>
     */
    @Override
    public byte[] receive() throws IOException {
        if (standardFraming) {
            return super.receive();
        }

        if (!isConnected()) {
            throw new IOException("Not connected");
        }

        // Connect:Express TLS: read FPDU length from FPDU header itself (first 2 bytes)
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

        log.debug("Received {} bytes from {}:{} (CX framing)", length, host, port);
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
     * Set the TLS protocol versions to enable on the socket.
     * Only TLSv1.2 and TLSv1.3 are considered secure.
     *
     * @param protocols array of protocol names (e.g., "TLSv1.3", "TLSv1.2")
     */
    public void setEnabledProtocols(String[] protocols) {
        this.enabledProtocols = protocols;
    }

    /**
     * Set whether TLS hostname verification is enabled.
     * When enabled (default), the server certificate's CN/SAN must match the target hostname.
     * <p>
     * <b>WARNING:</b> Disabling hostname verification makes the connection vulnerable to
     * man-in-the-middle attacks. Only disable for testing or when connecting to servers
     * with self-signed certificates that lack proper CN/SAN entries.
     * </p>
     *
     * @param enabled true to enable hostname verification (default), false to disable
     */
    public void setHostnameVerification(boolean enabled) {
        this.hostnameVerification = enabled;
    }

    /**
     * Check if TLS hostname verification is enabled.
     *
     * @return true if hostname verification is enabled
     */
    public boolean isHostnameVerification() {
        return hostnameVerification;
    }

    /**
     * Set whether to use standard PeSIT framing (2-byte length prefix) or
     * Connect:Express TLS framing (no external prefix).
     * <p>
     * Standard framing (default) is compatible with PeSIT Wizard server.
     * Connect:Express framing is needed for interoperability with CX servers.
     * </p>
     *
     * @param standardFraming true for standard PeSIT framing (default), false for CX framing
     */
    public void setStandardFraming(boolean standardFraming) {
        this.standardFraming = standardFraming;
    }

    /**
     * Check if standard PeSIT framing is enabled.
     *
     * @return true if using standard framing, false if using CX framing
     */
    public boolean isStandardFraming() {
        return standardFraming;
    }

    /**
     * Get the SSL session information
     */
    public SSLSession getSession() {
        return socket instanceof SSLSocket ? ((SSLSocket) socket).getSession() : null;
    }
}
