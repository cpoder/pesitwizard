package com.pesitwizard.exception;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.ParameterValue;

import lombok.Getter;

/**
 * Base exception for all PESIT protocol errors
 */
@Getter
public class PesitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final DiagnosticCode diagnosticCode;
    private final byte[] rawDiagnostic;

    public PesitException(ParameterValue pi2) {
        super(formatDiagnostic(pi2));
        this.diagnosticCode = DiagnosticCode.fromParameterValue(pi2);
        this.rawDiagnostic = pi2.getValue();
    }

    public PesitException(ParameterValue pi2, Throwable cause) {
        super(formatDiagnostic(pi2), cause);
        this.diagnosticCode = DiagnosticCode.fromParameterValue(pi2);
        this.rawDiagnostic = pi2.getValue();
    }

    /**
     * Check if this exception indicates that restart/resume is not supported.
     */
    public boolean isRestartNotSupported() {
        return diagnosticCode != null && diagnosticCode.isRestartNotSupported();
    }

    /**
     * Get the diagnostic code as a formatted string (e.g., "0x02002B").
     */
    public String getDiagnosticCodeHex() {
        if (rawDiagnostic != null && rawDiagnostic.length >= 3) {
            return String.format("0x%02X%02X%02X",
                    rawDiagnostic[0] & 0xFF, rawDiagnostic[1] & 0xFF, rawDiagnostic[2] & 0xFF);
        }
        return "unknown";
    }

    private static String formatDiagnostic(ParameterValue pi2) {
        DiagnosticCode code = DiagnosticCode.fromParameterValue(pi2);
        if (code != null) {
            return code.getMessage();
        }
        // Unknown diagnostic code - format as hex
        byte[] bytes = pi2.getValue();
        if (bytes != null && bytes.length >= 3) {
            return String.format("Unknown PeSIT error: 0x%02X%02X%02X",
                    bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF);
        }
        return "Unknown PeSIT error";
    }
}
