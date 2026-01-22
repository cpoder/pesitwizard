package com.pesitwizard.server.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PesitIdValidator Tests")
class PesitIdValidatorTest {

    @Nested
    @DisplayName("validate() method")
    class ValidateTests {

        @Test
        @DisplayName("Should return null for valid ID")
        void shouldReturnNullForValidId() {
            assertThat(PesitIdValidator.validate("SERVER01", "Server")).isNull();
            assertThat(PesitIdValidator.validate("PARTNER1", "Partner")).isNull();
            assertThat(PesitIdValidator.validate("A", "File")).isNull();
            assertThat(PesitIdValidator.validate("12345678", "Node")).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should return error for null or empty ID")
        void shouldReturnErrorForNullOrEmptyId(String id) {
            String error = PesitIdValidator.validate(id, "Server");
            assertThat(error).isEqualTo("Server ID cannot be empty");
        }

        @Test
        @DisplayName("Should return error for ID longer than 8 characters")
        void shouldReturnErrorForLongId() {
            String error = PesitIdValidator.validate("TOOLONGID", "Partner");
            assertThat(error).contains("Partner ID must be 8 characters or less");
            assertThat(error).contains("9 chars");
        }

        @ParameterizedTest
        @ValueSource(strings = { "server", "Server1", "SERV_01", "SERV-01", "SERV 01", "SERV.01" })
        @DisplayName("Should return error for invalid characters")
        void shouldReturnErrorForInvalidCharacters(String id) {
            String error = PesitIdValidator.validate(id, "Server");
            assertThat(error).contains("must contain only uppercase letters");
        }
    }

    @Nested
    @DisplayName("isValid() method")
    class IsValidTests {

        @ParameterizedTest
        @ValueSource(strings = { "A", "AB", "ABCDEFGH", "12345678", "A1B2C3D4" })
        @DisplayName("Should return true for valid IDs")
        void shouldReturnTrueForValidIds(String id) {
            assertThat(PesitIdValidator.isValid(id)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should return false for null or empty ID")
        void shouldReturnFalseForNullOrEmptyId(String id) {
            assertThat(PesitIdValidator.isValid(id)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = { "lowercase", "TOOLONGID", "UNDER_SC", "special!" })
        @DisplayName("Should return false for invalid IDs")
        void shouldReturnFalseForInvalidIds(String id) {
            assertThat(PesitIdValidator.isValid(id)).isFalse();
        }
    }

    @Nested
    @DisplayName("validateOrThrow() method")
    class ValidateOrThrowTests {

        @Test
        @DisplayName("Should not throw for valid ID")
        void shouldNotThrowForValidId() {
            assertThatCode(() -> PesitIdValidator.validateOrThrow("VALID01", "Server"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw for null ID")
        void shouldThrowForNullId() {
            assertThatThrownBy(() -> PesitIdValidator.validateOrThrow(null, "Server"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be empty");
        }

        @Test
        @DisplayName("Should throw for empty ID")
        void shouldThrowForEmptyId() {
            assertThatThrownBy(() -> PesitIdValidator.validateOrThrow("", "Partner"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be empty");
        }

        @Test
        @DisplayName("Should throw for ID too long")
        void shouldThrowForIdTooLong() {
            assertThatThrownBy(() -> PesitIdValidator.validateOrThrow("VERYLONGIDENTIFIER", "File"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("8 characters or less");
        }

        @Test
        @DisplayName("Should throw for invalid characters")
        void shouldThrowForInvalidCharacters() {
            assertThatThrownBy(() -> PesitIdValidator.validateOrThrow("invalid", "Node"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("uppercase letters");
        }
    }
}
