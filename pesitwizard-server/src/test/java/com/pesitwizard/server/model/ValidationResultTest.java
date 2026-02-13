package com.pesitwizard.server.model;

import static org.junit.jupiter.api.Assertions.*;

import com.pesitwizard.fpdu.DiagnosticCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ValidationResult Tests")
class ValidationResultTest {

    @Test
    @DisplayName("should create successful validation result")
    void shouldCreateSuccessfulResult() {
        ValidationResult result = ValidationResult.ok();

        assertTrue(result.isValid());
        assertEquals(DiagnosticCode.D0_000, result.getDiagCode());
        assertNull(result.getMessage());
    }

    @Test
    @DisplayName("should create error validation result")
    void shouldCreateErrorResult() {
        ValidationResult result =
                ValidationResult.error(DiagnosticCode.D3_301, "Partner not found");

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D3_301, result.getDiagCode());
        assertEquals("Partner not found", result.getMessage());
    }

    @Test
    @DisplayName("should handle different diagnostic codes")
    void shouldHandleDifferentDiagnosticCodes() {
        ValidationResult result1 = ValidationResult.error(DiagnosticCode.D2_205, "File not found");
        ValidationResult result2 = ValidationResult.error(DiagnosticCode.D3_304, "Access denied");
        ValidationResult result3 = ValidationResult.error(DiagnosticCode.D3_311, "Protocol error");

        assertEquals(DiagnosticCode.D2_205, result1.getDiagCode());
        assertEquals(DiagnosticCode.D3_304, result2.getDiagCode());
        assertEquals(DiagnosticCode.D3_311, result3.getDiagCode());
    }

    @Test
    @DisplayName("isValid should return correct boolean")
    void isValidShouldReturnCorrectBoolean() {
        ValidationResult ok = ValidationResult.ok();
        ValidationResult error = ValidationResult.error(DiagnosticCode.D1_100, "Error");

        assertTrue(ok.isValid());
        assertFalse(error.isValid());
    }
}
