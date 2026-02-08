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

    public static FpduParseException unknownFpduType(int phase, int type) {
        return new FpduParseException(
                String.format("Unknown FPDU type: phase=0x%02X, type=0x%02X", phase, type));
    }

    public static FpduParseException missingMandatoryParameter(FpduType fpduType, Parameter parameter) {
        String paramLabel = (parameter instanceof ParameterGroupIdentifier)
                ? "PGI_" + parameter.getId()
                : "PI_" + parameter.getId();
        return new FpduParseException(
                String.format("Missing mandatory parameter %s (%s) in %s",
                        paramLabel, parameter.getName(), fpduType.getName()));
    }
}
