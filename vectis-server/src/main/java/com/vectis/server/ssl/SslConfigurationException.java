package com.vectis.server.ssl;

/**
 * Exception thrown when SSL/TLS configuration fails.
 */
public class SslConfigurationException extends Exception {

    public SslConfigurationException(String message) {
        super(message);
    }

    public SslConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
