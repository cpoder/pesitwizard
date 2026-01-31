package com.pesitwizard.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validation annotation for safe file paths.
 * Rejects paths that contain traversal sequences or unsafe characters.
 */
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafePathValidator.class)
@Documented
public @interface SafePath {

    String message() default "Invalid or unsafe path";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * If true, allows null values (combine with @NotBlank for required paths).
     */
    boolean allowNull() default true;

    /**
     * If true, allows paths with directory components.
     * If false, only simple filenames are allowed.
     */
    boolean allowDirectoryPath() default true;
}
