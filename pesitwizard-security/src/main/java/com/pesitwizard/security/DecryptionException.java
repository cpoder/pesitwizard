package com.pesitwizard.security;

/**
 * Exception thrown when decryption operations fail.
 * Does not expose sensitive data in messages.
 */
public class DecryptionException extends RuntimeException {

    public DecryptionException(String message) {
        super(message);
    }

    public DecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
