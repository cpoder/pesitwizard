package com.pesitwizard.fpdu;

/**
 * Base exception for all FPDU-related technical errors.
 * This is separate from PesitException which represents protocol-level
 * errors with DiagnosticCode (PI_02).
 * 
 * Hierarchy:
 * - FpduException (this class) - base for technical FPDU errors
 * - FpduParseException - parsing errors (malformed data)
 * - FpduBuildException - building errors (missing params, encoding)
 * - UnknownParameterException - unknown parameter ID
 */
public class FpduException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FpduException(String message) {
        super(message);
    }

    public FpduException(String message, Throwable cause) {
        super(message, cause);
    }
}
