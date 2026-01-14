package com.pesitwizard.fpdu;

/**
 * Exception thrown when an unknown PeSIT parameter identifier is encountered
 * during FPDU parsing.
 * This indicates a protocol mismatch or an extension not supported by this
 * implementation.
 */
public class UnknownParameterException extends RuntimeException {

    private final int parameterId;
    private final int parameterLength;
    private final String context;

    public UnknownParameterException(int parameterId, int parameterLength, String context) {
        super(String.format("Unknown PeSIT parameter ID %d (0x%02X) with length %d bytes in %s",
                parameterId, parameterId, parameterLength, context));
        this.parameterId = parameterId;
        this.parameterLength = parameterLength;
        this.context = context;
    }

    public int getParameterId() {
        return parameterId;
    }

    public int getParameterLength() {
        return parameterLength;
    }

    public String getContext() {
        return context;
    }
}
