package com.pesitwizard.transport;

import java.io.IOException;

/**
 * Transport layer abstraction for PESIT protocol
 * Supports TCP/IP, SSL/TLS, and potentially X.25
 */
public interface TransportChannel {

    /**
     * Connect to remote endpoint.
     * 
     * @throws IOException if connection fails
     */
    void connect() throws IOException;

    /**
     * Send data over the transport.
     * 
     * @param data the data to send
     * @throws IOException if sending fails
     */
    void send(byte[] data) throws IOException;

    /**
     * Receive data from the transport.
     * 
     * @return received data
     * @throws IOException if receiving fails
     */
    byte[] receive() throws IOException;

    /**
     * Check if transport is connected.
     * 
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Close the transport connection.
     * 
     * @throws IOException if closing fails
     */
    void close() throws IOException;

    /**
     * Get remote address.
     * 
     * @return the remote address
     */
    String getRemoteAddress();

    /**
     * Get local address.
     * 
     * @return the local address
     */
    String getLocalAddress();

    /**
     * Check if transport uses SSL/TLS.
     * 
     * @return true if secure, false otherwise
     */
    boolean isSecure();

    /**
     * Set timeout for receive operations.
     * 
     * @param timeoutMs timeout in milliseconds
     */
    void setReceiveTimeout(int timeoutMs);

    /**
     * Get transport type (TCP, SSL, X25, etc.).
     * 
     * @return the transport type
     */
    TransportType getTransportType();
}
