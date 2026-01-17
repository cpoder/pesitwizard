package com.pesitwizard.security;

/**
 * Exception thrown when encryption operations fail.
 * Does not expose sensitive data in messages.
 */
public class EncryptionException extends RuntimeException {

    public EncryptionException(String message) {
        super(message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
