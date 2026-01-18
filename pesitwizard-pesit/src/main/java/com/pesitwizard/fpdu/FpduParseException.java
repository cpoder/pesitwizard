package com.pesitwizard.fpdu;

/**
 * Exception thrown when FPDU parsing fails due to malformed data.
 */
public class FpduParseException extends FpduException {

    private static final long serialVersionUID = 1L;

    public FpduParseException(String message) {
        super(message);
    }

    public FpduParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public static FpduParseException incompleteBuffer(int expected, int actual) {
        return new FpduParseException(
                String.format("Incomplete buffer: expected %d bytes, got %d", expected, actual));
    }

    public static FpduParseException incompleteParameter(int paramId, int expectedLen, int actualLen) {
        return new FpduParseException(
                String.format("Incomplete parameter PI_%d: expected %d bytes, got %d", paramId, expectedLen,
                        actualLen));
    }

    public static FpduParseException invalidFpduLength(int length) {
        return new FpduParseException(
                String.format("Invalid FPDU length: %d (minimum is 6)", length));
    }
}
