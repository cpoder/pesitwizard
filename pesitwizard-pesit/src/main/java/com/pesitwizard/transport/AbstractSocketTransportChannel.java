package com.pesitwizard.transport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for socket-based transport channels. Provides common functionality for TCP
 * and TLS transports.
 */
@Slf4j
public abstract class AbstractSocketTransportChannel implements TransportChannel {

    /** Default socket timeout in milliseconds. */
    protected static final int DEFAULT_TIMEOUT = 60000;

    /**
     * Default maximum FPDU length in bytes (32KB). Limits memory allocation from the 2-byte length
     * prefix.
     */
    public static final int DEFAULT_MAX_FPDU_LENGTH = 32768;

    /** Remote host address. */
    protected final String host;

    /** Remote port number. */
    protected final int port;

    /** Underlying socket connection. */
    protected Socket socket;

    /** Input stream for reading data. */
    protected DataInputStream inputStream;

    /** Output stream for writing data. */
    protected DataOutputStream outputStream;

    /** Receive timeout in milliseconds. */
    protected int receiveTimeout = DEFAULT_TIMEOUT;

    /**
     * Maximum allowed FPDU length in bytes. Prevents memory exhaustion from oversized length
     * prefixes.
     */
    protected int maxFpduLength = DEFAULT_MAX_FPDU_LENGTH;

    /**
     * Construct a new transport channel.
     *
     * @param host the remote host
     * @param port the remote port
     */
    protected AbstractSocketTransportChannel(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Create and configure the socket. Subclasses override for TLS.
     *
     * @return the configured socket
     * @throws IOException if socket creation fails
     */
    protected abstract Socket createSocket() throws IOException;

    @Override
    public void connect() throws IOException {
        if (socket != null && socket.isConnected()) {
            log.warn("Already connected to {}:{}", host, port);
            return;
        }

        log.debug("Connecting to {}:{}", host, port);

        socket = createSocket();
        socket.setSoTimeout(receiveTimeout);
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);

        inputStream = new DataInputStream(socket.getInputStream());
        outputStream = new DataOutputStream(socket.getOutputStream());

        log.info("Connected to {}:{}", host, port);
    }

    @Override
    public void send(byte[] data) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected");
        }

        // Write 2-byte length prefix (PeSIT standard) followed by data
        outputStream.writeShort(data.length);
        outputStream.write(data);
        outputStream.flush();

        log.debug("Sent {} bytes to {}:{}", data.length, host, port);
    }

    @Override
    public byte[] receive() throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected");
        }

        try {
            // Read 2-byte length prefix (PeSIT standard)
            int length = inputStream.readUnsignedShort();
            if (length <= 0) {
                throw new IOException("Invalid FPDU length: " + length);
            }
            if (length > maxFpduLength) {
                throw new IOException(
                        "FPDU length "
                                + length
                                + " exceeds maximum allowed length "
                                + maxFpduLength
                                + " bytes (from "
                                + host
                                + ":"
                                + port
                                + ")");
            }

            // Read data
            byte[] data = new byte[length];
            inputStream.readFully(data);

            log.debug("Received {} bytes from {}:{}", length, host, port);
            return data;

        } catch (SocketTimeoutException e) {
            log.debug("Receive timeout on {}:{}", host, port);
            throw e;
        }
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        log.debug("Closing connection to {}:{}", host, port);

        try {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.warn("Failed to close output stream for {}:{}", host, port, e);
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.warn("Failed to close input stream for {}:{}", host, port, e);
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    log.warn("Failed to close socket for {}:{}", host, port, e);
                }
            }
        } finally {
            outputStream = null;
            inputStream = null;
            socket = null;
        }
    }

    @Override
    public String getRemoteAddress() {
        return socket != null ? socket.getRemoteSocketAddress().toString() : host + ":" + port;
    }

    @Override
    public String getLocalAddress() {
        return socket != null ? socket.getLocalSocketAddress().toString() : "not connected";
    }

    @Override
    public void setReceiveTimeout(int timeoutMs) {
        this.receiveTimeout = timeoutMs;
        if (socket != null) {
            try {
                socket.setSoTimeout(timeoutMs);
            } catch (IOException e) {
                log.warn("Failed to set socket timeout", e);
            }
        }
    }

    /**
     * Set the maximum allowed FPDU length in bytes. FPDUs with a length prefix exceeding this value
     * will be rejected with an IOException. This protects against memory exhaustion from malicious
     * or corrupted length prefixes.
     *
     * @param maxFpduLength maximum FPDU length in bytes (must be positive)
     * @throws IllegalArgumentException if maxFpduLength is not positive
     */
    public void setMaxFpduLength(int maxFpduLength) {
        if (maxFpduLength <= 0) {
            throw new IllegalArgumentException(
                    "maxFpduLength must be positive, got: " + maxFpduLength);
        }
        this.maxFpduLength = maxFpduLength;
    }

    /**
     * Get the maximum allowed FPDU length in bytes.
     *
     * @return the maximum FPDU length
     */
    public int getMaxFpduLength() {
        return maxFpduLength;
    }

    /**
     * Get the remote host.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Get the remote port.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Get the input stream for reading data.
     *
     * @return the input stream
     */
    public DataInputStream getInputStream() {
        return inputStream;
    }
}
