package com.pesitwizard.fpdu;

/**
 * Exception thrown when FPDU building fails.
 */
public class FpduBuildException extends FpduException {

    private static final long serialVersionUID = 1L;

    public FpduBuildException(String message) {
        super(message);
    }

    public FpduBuildException(String message, Throwable cause) {
        super(message, cause);
    }

    public static FpduBuildException missingMandatoryParameter(Parameter param, FpduType fpduType) {
        return new FpduBuildException(
                String.format("Missing mandatory parameter %s for FPDU %s", param, fpduType));
    }

    public static FpduBuildException parameterEncodingFailed(Parameter param, Throwable cause) {
        return new FpduBuildException(
                String.format("Failed to encode parameter %s", param), cause);
    }
}
