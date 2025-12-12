package com.vectis.server.model;

import com.vectis.fpdu.DiagnosticCode;

import lombok.Getter;

/**
 * Result of a validation operation
 */
@Getter
public class ValidationResult {

    private final boolean valid;
    private final DiagnosticCode diagCode;
    private final String message;

    private ValidationResult(boolean valid, DiagnosticCode diagCode, String message) {
        this.valid = valid;
        this.diagCode = diagCode;
        this.message = message;
    }

    /**
     * Create a successful validation result
     */
    public static ValidationResult ok() {
        return new ValidationResult(true, DiagnosticCode.D0_000, null);
    }

    /**
     * Create a failed validation result
     */
    public static ValidationResult error(DiagnosticCode diagCode, String message) {
        return new ValidationResult(false, diagCode, message);
    }

    /**
     * Check if validation passed
     */
    public boolean isValid() {
        return valid;
    }
}
