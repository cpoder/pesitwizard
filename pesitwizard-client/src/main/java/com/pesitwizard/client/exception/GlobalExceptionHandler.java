package com.pesitwizard.client.exception;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler for the REST API.
 * Provides consistent error responses without exposing internal details.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle database constraint violations (e.g., duplicate names, foreign key violations)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.error("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMessage());

        // Extract meaningful message from the exception
        String message = extractConstraintViolationMessage(ex);

        ApiError error = ApiError.builder()
                .status(HttpStatus.CONFLICT.value())
                .error("CONFLICT")
                .message(message)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle validation errors from @Valid annotations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.warn("Validation error on {}: {}", request.getRequestURI(), ex.getMessage());

        BindingResult result = ex.getBindingResult();

        var fieldErrors = result.getFieldErrors().stream()
                .map(fe -> ApiError.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .collect(Collectors.toList());

        String message = fieldErrors.isEmpty()
                ? "Validation failed"
                : "Validation failed: " + fieldErrors.stream()
                        .map(fe -> fe.getField() + " " + fe.getMessage())
                        .collect(Collectors.joining(", "));

        ApiError error = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("VALIDATION_ERROR")
                .message(message)
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle entity not found errors
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFound(
            EntityNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Entity not found on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiError error = ApiError.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .error("NOT_FOUND")
                .message(ex.getMessage() != null ? ex.getMessage() : "Resource not found")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle illegal argument exceptions (business logic errors)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiError error = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("BAD_REQUEST")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle type mismatch errors (e.g., wrong enum value)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        log.warn("Type mismatch on {}: {}", request.getRequestURI(), ex.getMessage());

        String message = String.format("Invalid value '%s' for parameter '%s'",
                ex.getValue(), ex.getName());

        ApiError error = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("TYPE_MISMATCH")
                .message(message)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle malformed JSON requests
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("Malformed JSON on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiError error = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("MALFORMED_REQUEST")
                .message("Invalid JSON format")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle all other unexpected exceptions.
     * Logs the full stack trace but only returns a generic message to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ApiError error = ApiError.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("INTERNAL_ERROR")
                .message("An internal error occurred. Please try again later.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Extract a user-friendly message from a DataIntegrityViolationException.
     * Looks for common constraint patterns.
     */
    private String extractConstraintViolationMessage(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();

        if (message == null) {
            return "A data integrity violation occurred";
        }

        // Handle unique constraint violations
        if (message.contains("Unique index") || message.contains("unique constraint") ||
                message.toLowerCase().contains("duplicate")) {

            // Try to extract the value that caused the conflict
            // Pattern: VALUES ( /* 1 */ 'Production Calendar' )
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "VALUES\\s*\\([^)]*'([^']+)'");
            java.util.regex.Matcher matcher = pattern.matcher(message);

            if (matcher.find()) {
                String value = matcher.group(1);
                // Try to determine the field name
                if (message.toLowerCase().contains("name")) {
                    return String.format("A resource with name '%s' already exists", value);
                }
                return String.format("A resource with value '%s' already exists", value);
            }

            // Generic unique constraint message
            if (message.toLowerCase().contains("name")) {
                return "A resource with this name already exists";
            }
            return "A resource with this identifier already exists";
        }

        // Handle foreign key violations
        if (message.contains("foreign key") || message.contains("FOREIGN KEY")) {
            return "Cannot delete or modify this resource because it is referenced by other resources";
        }

        // Handle not null violations
        if (message.contains("null value") || message.contains("NOT NULL")) {
            return "A required field is missing";
        }

        // Default message
        return "A data integrity violation occurred";
    }
}
