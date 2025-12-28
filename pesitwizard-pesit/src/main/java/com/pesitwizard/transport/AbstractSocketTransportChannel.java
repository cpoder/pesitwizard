package com.pesitwizard.transport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for socket-based transport channels.
 * Provides common functionality for TCP and TLS transports.
 */
@Slf4j
public abstract class AbstractSocketTransportChannel implements TransportChannel {

    protected static final int DEFAULT_TIMEOUT = 60000; // 60 seconds

    protected final String host;
    protected final int port;
    protected Socket socket;
    protected DataInputStream inputStream;
    protected DataOutputStream outputStream;
    protected int receiveTimeout = DEFAULT_TIMEOUT;

    protected AbstractSocketTransportChannel(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Create and configure the socket. Subclasses override for TLS.
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
                outputStream.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        } finally {
            socket = null;
            inputStream = null;
            outputStream = null;
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

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
