package com.pesitwizard.server.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler for consistent error responses.
 * Handles validation errors, authentication failures, and other common exceptions.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Standard error response structure.
     */
    public record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            List<FieldValidationError> fieldErrors) {

        public ErrorResponse(int status, String error, String message, String path) {
            this(Instant.now(), status, error, message, path, null);
        }

        public ErrorResponse(int status, String error, String message, String path, List<FieldValidationError> fieldErrors) {
            this(Instant.now(), status, error, message, path, fieldErrors);
        }
    }

    public record FieldValidationError(String field, String message, Object rejectedValue) {}

    /**
     * Handle @Valid validation failures on request body.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldValidationError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldValidationError(
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getRejectedValue()))
                .collect(Collectors.toList());

        String message = fieldErrors.stream()
                .map(e -> e.field() + ": " + e.message())
                .collect(Collectors.joining("; "));

        log.warn("Validation failed for request {}: {}", request.getRequestURI(), message);

        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                message,
                request.getRequestURI(),
                fieldErrors));
    }

    /**
     * Handle @Validated constraint violations on path/query parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<FieldValidationError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldValidationError(
                        extractFieldName(violation),
                        violation.getMessage(),
                        violation.getInvalidValue()))
                .collect(Collectors.toList());

        String message = fieldErrors.stream()
                .map(e -> e.field() + ": " + e.message())
                .collect(Collectors.joining("; "));

        log.warn("Constraint violation for request {}: {}", request.getRequestURI(), message);

        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Constraint Violation",
                message,
                request.getRequestURI(),
                fieldErrors));
    }

    /**
     * Handle missing required request parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        log.warn("Missing parameter for request {}: {}", request.getRequestURI(), ex.getParameterName());

        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Missing Parameter",
                String.format("Required parameter '%s' of type %s is missing",
                        ex.getParameterName(), ex.getParameterType()),
                request.getRequestURI()));
    }

    /**
     * Handle type mismatch in request parameters.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String message = String.format("Parameter '%s' should be of type %s", ex.getName(), requiredType);

        log.warn("Type mismatch for request {}: {}", request.getRequestURI(), message);

        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Type Mismatch",
                message,
                request.getRequestURI()));
    }

    /**
     * Handle malformed JSON in request body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Malformed request body for {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Malformed Request",
                "Request body is not valid JSON or is malformed",
                request.getRequestURI()));
    }

    /**
     * Handle unsupported HTTP method.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        log.warn("Method {} not supported for {}", ex.getMethod(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new ErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Method Not Allowed",
                String.format("HTTP method %s is not supported for this endpoint", ex.getMethod()),
                request.getRequestURI()));
    }

    /**
     * Handle unsupported media type.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        log.warn("Unsupported media type for {}: {}", request.getRequestURI(), ex.getContentType());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ErrorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "Unsupported Media Type",
                String.format("Content type '%s' is not supported", ex.getContentType()),
                request.getRequestURI()));
    }

    /**
     * Handle max upload size exceeded.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        log.warn("Upload size exceeded for {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "Payload Too Large",
                "The uploaded file exceeds the maximum allowed size",
                request.getRequestURI()));
    }

    /**
     * Handle 404 Not Found (no handler).
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoHandlerFoundException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                String.format("No handler found for %s %s", ex.getHttpMethod(), ex.getRequestURL()),
                request.getRequestURI()));
    }

    /**
     * Handle 404 Not Found (no resource - Spring 6.2+).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                String.format("Resource not found: %s", ex.getResourcePath()),
                request.getRequestURI()));
    }

    /**
     * Handle authentication failures.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failed for {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Authentication required or credentials invalid",
                request.getRequestURI()));
    }

    /**
     * Handle bad credentials specifically.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {

        log.warn("Bad credentials for {}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Invalid credentials provided",
                request.getRequestURI()));
    }

    /**
     * Handle access denied (authorization failures).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied for {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "You do not have permission to access this resource",
                request.getRequestURI()));
    }

    /**
     * Handle IllegalArgumentException (bad input).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("Illegal argument for {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI()));
    }

    /**
     * Handle all other unhandled exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception for {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        // Don't expose internal error details in production
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please contact support if this persists.",
                request.getRequestURI()));
    }

    private String extractFieldName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        // Extract just the field name from the path (e.g., "save.dto.name" -> "name")
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
