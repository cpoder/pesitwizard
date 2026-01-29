package com.pesitwizard.client.exception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Builder;
import lombok.Data;

/**
 * Standard API error response format.
 * Returns consistent error information without exposing internal details.
 */
@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class ApiError {

    /**
     * Timestamp when the error occurred
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * HTTP status code
     */
    private int status;

    /**
     * Short error type (e.g., "CONFLICT", "VALIDATION_ERROR", "NOT_FOUND")
     */
    private String error;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * Request path that caused the error
     */
    private String path;

    /**
     * Detailed validation errors (for 400 Bad Request)
     */
    @Builder.Default
    private List<FieldError> fieldErrors = new ArrayList<>();

    @Data
    @Builder
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}
