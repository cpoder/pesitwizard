package com.pesitwizard.common.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SafePathValidatorTest {

    private SafePathValidator validator;

    @Mock private ConstraintValidatorContext context;

    @Mock private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @BeforeEach
    void setUp() {
        validator = new SafePathValidator();
        lenient()
                .when(context.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(violationBuilder);
    }

    private SafePath annotation(boolean allowNull, boolean allowDirectoryPath) {
        SafePath ann = mock(SafePath.class);
        when(ann.allowNull()).thenReturn(allowNull);
        when(ann.allowDirectoryPath()).thenReturn(allowDirectoryPath);
        return ann;
    }

    @Nested
    class NullHandling {

        @Test
        void null_allowNullTrue_returnsTrue() {
            validator.initialize(annotation(true, true));
            assertThat(validator.isValid(null, context)).isTrue();
        }

        @Test
        void null_allowNullFalse_returnsFalse() {
            validator.initialize(annotation(false, true));
            assertThat(validator.isValid(null, context)).isFalse();
        }
    }

    @Nested
    class BasicValidation {

        @BeforeEach
        void init() {
            validator.initialize(annotation(false, true));
        }

        @Test
        void blank_returnsFalse() {
            assertThat(validator.isValid("   ", context)).isFalse();
        }

        @Test
        void validFilename_returnsTrue() {
            assertThat(validator.isValid("report.pdf", context)).isTrue();
        }

        @Test
        void validDirectoryPath_returnsTrue() {
            assertThat(validator.isValid("data/files/report.pdf", context)).isTrue();
        }
    }

    @Nested
    class TraversalDetection {

        @BeforeEach
        void init() {
            validator.initialize(annotation(false, true));
        }

        @Test
        void traversalPath_returnsFalse() {
            assertThat(validator.isValid("../etc/passwd", context)).isFalse();
            verify(context).disableDefaultConstraintViolation();
            verify(context)
                    .buildConstraintViolationWithTemplate(
                            "Path contains illegal traversal sequence");
        }
    }

    @Nested
    class NullByteDetection {

        @BeforeEach
        void init() {
            validator.initialize(annotation(false, true));
        }

        @Test
        void nullByte_returnsFalse() {
            // Null bytes are caught by the traversal check (PathValidator pattern matches \x00)
            assertThat(validator.isValid("file\0.txt", context)).isFalse();
            verify(context).disableDefaultConstraintViolation();
        }
    }

    @Nested
    class LengthValidation {

        @BeforeEach
        void init() {
            validator.initialize(annotation(false, true));
        }

        @Test
        void exceedsMaxPathLength_returnsFalse() {
            String longPath = "a".repeat(PathValidator.MAX_PATH_LENGTH + 1);
            assertThat(validator.isValid(longPath, context)).isFalse();
            verify(context).disableDefaultConstraintViolation();
            verify(context).buildConstraintViolationWithTemplate("Path exceeds maximum length");
        }
    }

    @Nested
    class DirectoryPathHandling {

        @Test
        void directoryPath_notAllowed_returnsFalse() {
            validator.initialize(annotation(false, false));
            assertThat(validator.isValid("data/file.txt", context)).isFalse();
            verify(context)
                    .buildConstraintViolationWithTemplate(
                            "Directory paths not allowed, expected filename only");
        }

        @Test
        void directoryPath_allowed_returnsTrue() {
            validator.initialize(annotation(false, true));
            assertThat(validator.isValid("data/file.txt", context)).isTrue();
        }

        @Test
        void simpleFilename_notAllowed_returnsTrue() {
            validator.initialize(annotation(false, false));
            assertThat(validator.isValid("file.txt", context)).isTrue();
        }
    }
}
