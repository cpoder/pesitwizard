package com.pesitwizard.client.exception;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }

    @Nested
    @DisplayName("Data Integrity Violation Handling")
    class DataIntegrityViolationTests {

        @Test
        @DisplayName("Should return 409 CONFLICT for unique constraint violation")
        void shouldReturn409ForUniqueConstraint() {
            // Given
            Exception rootCause = new Exception(
                    "Unique index or primary key violation: VALUES ( /* 1 */ 'Production Calendar' )");
            DataIntegrityViolationException ex = new DataIntegrityViolationException("Constraint violation",
                    rootCause);

            // When
            ResponseEntity<ApiError> response = handler.handleDataIntegrityViolation(ex, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(409);
            assertThat(response.getBody().getError()).isEqualTo("CONFLICT");
            assertThat(response.getBody().getMessage()).contains("already exists");
            assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
        }

        @Test
        @DisplayName("Should extract name from unique constraint violation message")
        void shouldExtractNameFromViolationMessage() {
            // Given - H2 database error format
            Exception rootCause = new Exception(
                    "Unique index or primary key violation: \"PUBLIC.UK_NAME_INDEX ON PUBLIC.BUSINESS_CALENDARS(NAME NULLS FIRST) VALUES ( /* 1 */ 'My Calendar' )\"");
            DataIntegrityViolationException ex = new DataIntegrityViolationException("Constraint violation",
                    rootCause);

            // When
            ResponseEntity<ApiError> response = handler.handleDataIntegrityViolation(ex, request);

            // Then
            assertThat(response.getBody().getMessage()).contains("My Calendar");
        }

        @Test
        @DisplayName("Should handle foreign key violation")
        void shouldHandleForeignKeyViolation() {
            // Given
            Exception rootCause = new Exception("foreign key constraint fails");
            DataIntegrityViolationException ex = new DataIntegrityViolationException("FK violation", rootCause);

            // When
            ResponseEntity<ApiError> response = handler.handleDataIntegrityViolation(ex, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().getMessage()).contains("referenced by other resources");
        }

        @Test
        @DisplayName("Should not expose SQL details in error message")
        void shouldNotExposeSqlDetails() {
            // Given
            Exception rootCause = new Exception("Unique index SQL: SELECT * FROM users WHERE id = 1");
            DataIntegrityViolationException ex = new DataIntegrityViolationException("Test", rootCause);

            // When
            ResponseEntity<ApiError> response = handler.handleDataIntegrityViolation(ex, request);

            // Then - should not contain SQL keywords
            assertThat(response.getBody().getMessage()).doesNotContain("SELECT");
            assertThat(response.getBody().getMessage()).doesNotContain("FROM");
        }
    }

    @Nested
    @DisplayName("Entity Not Found Handling")
    class EntityNotFoundTests {

        @Test
        @DisplayName("Should return 404 NOT_FOUND for EntityNotFoundException")
        void shouldReturn404ForEntityNotFound() {
            // Given
            EntityNotFoundException ex = new EntityNotFoundException("Calendar with ID 123 not found");

            // When
            ResponseEntity<ApiError> response = handler.handleEntityNotFound(ex, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(404);
            assertThat(response.getBody().getError()).isEqualTo("NOT_FOUND");
            assertThat(response.getBody().getMessage()).isEqualTo("Calendar with ID 123 not found");
        }
    }

    @Nested
    @DisplayName("Illegal Argument Handling")
    class IllegalArgumentTests {

        @Test
        @DisplayName("Should return 400 BAD_REQUEST for IllegalArgumentException")
        void shouldReturn400ForIllegalArgument() {
            // Given
            IllegalArgumentException ex = new IllegalArgumentException("Invalid parameter value");

            // When
            ResponseEntity<ApiError> response = handler.handleIllegalArgument(ex, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(400);
            assertThat(response.getBody().getError()).isEqualTo("BAD_REQUEST");
            assertThat(response.getBody().getMessage()).isEqualTo("Invalid parameter value");
        }
    }

    @Nested
    @DisplayName("Generic Exception Handling")
    class GenericExceptionTests {

        @Test
        @DisplayName("Should return 500 with generic message for unexpected exceptions")
        void shouldReturn500WithGenericMessage() {
            // Given
            Exception ex = new RuntimeException("Detailed internal error with sensitive data");

            // When
            ResponseEntity<ApiError> response = handler.handleGenericException(ex, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(500);
            assertThat(response.getBody().getError()).isEqualTo("INTERNAL_ERROR");
            // Should NOT expose the actual error message
            assertThat(response.getBody().getMessage()).doesNotContain("sensitive data");
            assertThat(response.getBody().getMessage()).contains("internal error");
        }

        @Test
        @DisplayName("Should not expose stack trace or internal details")
        void shouldNotExposeStackTrace() {
            // Given
            Exception ex = new NullPointerException("at com.internal.SomeClass.method()");

            // When
            ResponseEntity<ApiError> response = handler.handleGenericException(ex, request);

            // Then
            assertThat(response.getBody().getMessage()).doesNotContain("com.internal");
            assertThat(response.getBody().getMessage()).doesNotContain("NullPointerException");
            assertThat(response.getBody().getMessage()).doesNotContain(".java");
        }
    }
}
