package com.pesitwizard.server.handler;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pesitwizard.fpdu.DiagnosticCode;

/**
 * Unit tests for DataTransferHandler.DataTransferException.
 */
@DisplayName("DataTransferException Tests")
class DataTransferExceptionTest {

    @Test
    @DisplayName("should create with diagnostic code and message")
    void shouldCreateWithDiagnosticCodeAndMessage() {
        DataTransferHandler.DataTransferException ex = new DataTransferHandler.DataTransferException(
                DiagnosticCode.D0_000, "Success");

        assertThat(ex.getMessage()).isEqualTo("Success");
        assertThat(ex.getDiagnosticCode()).isEqualTo(DiagnosticCode.D0_000);
    }

    @Test
    @DisplayName("should be IOException")
    void shouldBeIOException() {
        DataTransferHandler.DataTransferException ex = new DataTransferHandler.DataTransferException(
                DiagnosticCode.D3_300, "Congestion");

        assertThat(ex).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("should handle different diagnostic codes")
    void shouldHandleDifferentDiagnosticCodes() {
        DataTransferHandler.DataTransferException ex1 = new DataTransferHandler.DataTransferException(
                DiagnosticCode.D3_301, "Unknown ID");
        DataTransferHandler.DataTransferException ex2 = new DataTransferHandler.DataTransferException(
                DiagnosticCode.D3_300, "Congestion");

        assertThat(ex1.getDiagnosticCode()).isEqualTo(DiagnosticCode.D3_301);
        assertThat(ex2.getDiagnosticCode()).isEqualTo(DiagnosticCode.D3_300);
    }
}
