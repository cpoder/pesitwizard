package com.pesitwizard.connector;

/** Exception thrown by storage connectors. */
public class ConnectorException extends Exception {

    private final ErrorCode errorCode;

    public ConnectorException(String message) {
        super(message);
        this.errorCode = ErrorCode.UNKNOWN;
    }

    public ConnectorException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.UNKNOWN;
    }

    public ConnectorException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ConnectorException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Standard error codes for connector operations */
    public enum ErrorCode {
        UNKNOWN,
        CONNECTION_FAILED,
        AUTHENTICATION_FAILED,
        FILE_NOT_FOUND,
        PERMISSION_DENIED,
        ALREADY_EXISTS,
        DISK_FULL,
        TIMEOUT,
        INVALID_PATH,
        INVALID_CONFIG,
        NOT_SUPPORTED
    }
}
