package com.pesitwizard.fpdu;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FPDU exception classes.
 */
@DisplayName("FPDU Exception Tests")
class FpduExceptionTest {

    @Nested
    @DisplayName("FpduBuildException")
    class FpduBuildExceptionTests {

        @Test
        @DisplayName("should create with message")
        void shouldCreateWithMessage() {
            FpduBuildException ex = new FpduBuildException("Build failed");
            assertThat(ex.getMessage()).isEqualTo("Build failed");
        }

        @Test
        @DisplayName("should create with message and cause")
        void shouldCreateWithMessageAndCause() {
            Throwable cause = new RuntimeException("Original error");
            FpduBuildException ex = new FpduBuildException("Build failed", cause);
            assertThat(ex.getMessage()).isEqualTo("Build failed");
            assertThat(ex.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("should create for missing mandatory parameter")
        void shouldCreateForMissingMandatoryParameter() {
            FpduBuildException ex = FpduBuildException.missingMandatoryParameter(
                    ParameterIdentifier.PI_02_DIAG, FpduType.CONNECT);
            assertThat(ex.getMessage()).contains("Missing mandatory parameter");
            assertThat(ex.getMessage()).contains("CONNECT");
        }

        @Test
        @DisplayName("should create for parameter encoding failure")
        void shouldCreateForParameterEncodingFailure() {
            Throwable cause = new RuntimeException("Encoding error");
            FpduBuildException ex = FpduBuildException.parameterEncodingFailed(
                    ParameterIdentifier.PI_12_NOM_FICHIER, cause);
            assertThat(ex.getMessage()).contains("Failed to encode parameter");
            assertThat(ex.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    @DisplayName("FpduParseException")
    class FpduParseExceptionTests {

        @Test
        @DisplayName("should create with message")
        void shouldCreateWithMessage() {
            FpduParseException ex = new FpduParseException("Parse failed");
            assertThat(ex.getMessage()).isEqualTo("Parse failed");
        }

        @Test
        @DisplayName("should create with message and cause")
        void shouldCreateWithMessageAndCause() {
            Throwable cause = new RuntimeException("Original error");
            FpduParseException ex = new FpduParseException("Parse failed", cause);
            assertThat(ex.getMessage()).isEqualTo("Parse failed");
            assertThat(ex.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("should create for incomplete buffer")
        void shouldCreateForIncompleteBuffer() {
            FpduParseException ex = FpduParseException.incompleteBuffer(100, 50);
            assertThat(ex.getMessage()).contains("Incomplete buffer");
            assertThat(ex.getMessage()).contains("100");
            assertThat(ex.getMessage()).contains("50");
        }

        @Test
        @DisplayName("should create for incomplete parameter")
        void shouldCreateForIncompleteParameter() {
            FpduParseException ex = FpduParseException.incompleteParameter(12, 20, 10);
            assertThat(ex.getMessage()).contains("Incomplete parameter");
            assertThat(ex.getMessage()).contains("PI_12");
        }

        @Test
        @DisplayName("should create for invalid FPDU length")
        void shouldCreateForInvalidFpduLength() {
            FpduParseException ex = FpduParseException.invalidFpduLength(3);
            assertThat(ex.getMessage()).contains("Invalid FPDU length");
            assertThat(ex.getMessage()).contains("3");
        }
    }

    @Nested
    @DisplayName("FpduException")
    class FpduExceptionBaseTests {

        @Test
        @DisplayName("should create with message")
        void shouldCreateWithMessage() {
            FpduException ex = new FpduException("Base exception");
            assertThat(ex.getMessage()).isEqualTo("Base exception");
        }

        @Test
        @DisplayName("should create with message and cause")
        void shouldCreateWithMessageAndCause() {
            Throwable cause = new RuntimeException("Root cause");
            FpduException ex = new FpduException("Base exception", cause);
            assertThat(ex.getMessage()).isEqualTo("Base exception");
            assertThat(ex.getCause()).isEqualTo(cause);
        }
    }
}
