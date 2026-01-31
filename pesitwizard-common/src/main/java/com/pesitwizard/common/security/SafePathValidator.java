package com.pesitwizard.common.security;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for the @SafePath annotation.
 * Checks that paths don't contain traversal sequences or other security issues.
 */
public class SafePathValidator implements ConstraintValidator<SafePath, String> {

    private boolean allowNull;
    private boolean allowDirectoryPath;

    @Override
    public void initialize(SafePath constraintAnnotation) {
        this.allowNull = constraintAnnotation.allowNull();
        this.allowDirectoryPath = constraintAnnotation.allowDirectoryPath();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return allowNull;
        }

        if (value.isBlank()) {
            return false;
        }

        // Check for traversal attempts
        if (PathValidator.containsTraversal(value)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Path contains illegal traversal sequence")
                    .addConstraintViolation();
            return false;
        }

        // Check for null bytes
        if (value.contains("\0")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Path contains illegal null byte")
                    .addConstraintViolation();
            return false;
        }

        // Check length
        if (value.length() > PathValidator.MAX_PATH_LENGTH) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Path exceeds maximum length")
                    .addConstraintViolation();
            return false;
        }

        // Check if directory paths are allowed
        if (!allowDirectoryPath) {
            if (value.contains("/") || value.contains("\\")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Directory paths not allowed, expected filename only")
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
