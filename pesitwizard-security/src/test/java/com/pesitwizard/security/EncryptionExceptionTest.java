package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for EncryptionException.
 */
@DisplayName("EncryptionException Tests")
class EncryptionExceptionTest {

    @Test
    @DisplayName("should create with message")
    void shouldCreateWithMessage() {
        EncryptionException ex = new EncryptionException("Encryption failed");
        assertThat(ex.getMessage()).isEqualTo("Encryption failed");
    }

    @Test
    @DisplayName("should create with message and cause")
    void shouldCreateWithMessageAndCause() {
        Throwable cause = new RuntimeException("Original error");
        EncryptionException ex = new EncryptionException("Encryption failed", cause);
        assertThat(ex.getMessage()).isEqualTo("Encryption failed");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("should be runtime exception")
    void shouldBeRuntimeException() {
        EncryptionException ex = new EncryptionException("Test");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
