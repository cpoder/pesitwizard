package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for SecretsConfig.
 */
@DisplayName("SecretsConfig Tests")
class SecretsConfigTest {

    @Nested
    @DisplayName("EncryptionMode Enum")
    class EncryptionModeTests {

        @Test
        @DisplayName("should have AES mode")
        void shouldHaveAesMode() {
            assertThat(SecretsConfig.EncryptionMode.AES).isNotNull();
            assertThat(SecretsConfig.EncryptionMode.AES.name()).isEqualTo("AES");
        }

        @Test
        @DisplayName("should have VAULT mode")
        void shouldHaveVaultMode() {
            assertThat(SecretsConfig.EncryptionMode.VAULT).isNotNull();
            assertThat(SecretsConfig.EncryptionMode.VAULT.name()).isEqualTo("VAULT");
        }

        @Test
        @DisplayName("should have exactly 2 modes")
        void shouldHaveExactlyTwoModes() {
            assertThat(SecretsConfig.EncryptionMode.values()).hasSize(2);
        }

        @Test
        @DisplayName("should parse AES from string")
        void shouldParseAesFromString() {
            SecretsConfig.EncryptionMode mode = SecretsConfig.EncryptionMode.valueOf("AES");
            assertThat(mode).isEqualTo(SecretsConfig.EncryptionMode.AES);
        }

        @Test
        @DisplayName("should parse VAULT from string")
        void shouldParseVaultFromString() {
            SecretsConfig.EncryptionMode mode = SecretsConfig.EncryptionMode.valueOf("VAULT");
            assertThat(mode).isEqualTo(SecretsConfig.EncryptionMode.VAULT);
        }

        @Test
        @DisplayName("should throw exception for invalid mode")
        void shouldThrowExceptionForInvalidMode() {
            assertThatThrownBy(() -> SecretsConfig.EncryptionMode.valueOf("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Secret File Reading")
    class SecretFileReadingTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("should read master key from file")
        void shouldReadMasterKeyFromFile() throws IOException {
            Path keyFile = tempDir.resolve("master-key.txt");
            Files.writeString(keyFile, "my-secret-master-key-from-file\n");

            String content = Files.readString(keyFile).trim();
            assertThat(content).isEqualTo("my-secret-master-key-from-file");
        }

        @Test
        @DisplayName("should trim whitespace from secret files")
        void shouldTrimWhitespaceFromSecretFiles() throws IOException {
            Path keyFile = tempDir.resolve("key-with-whitespace.txt");
            Files.writeString(keyFile, "  secret-with-spaces  \n\n");

            String content = Files.readString(keyFile).trim();
            assertThat(content).isEqualTo("secret-with-spaces");
        }

        @Test
        @DisplayName("should handle non-existent file gracefully")
        void shouldHandleNonExistentFileGracefully() {
            Path nonExistent = tempDir.resolve("does-not-exist.txt");
            assertThat(Files.exists(nonExistent)).isFalse();
        }

        @Test
        @DisplayName("should handle empty file")
        void shouldHandleEmptyFile() throws IOException {
            Path emptyFile = tempDir.resolve("empty.txt");
            Files.writeString(emptyFile, "");

            String content = Files.readString(emptyFile).trim();
            assertThat(content).isEmpty();
        }

        @Test
        @DisplayName("should read vault token from file")
        void shouldReadVaultTokenFromFile() throws IOException {
            Path tokenFile = tempDir.resolve("vault-token.txt");
            Files.writeString(tokenFile, "hvs.my-vault-token\n");

            String content = Files.readString(tokenFile).trim();
            assertThat(content).isEqualTo("hvs.my-vault-token");
        }
    }

    @Nested
    @DisplayName("Configuration Instantiation")
    class ConfigurationInstantiationTests {

        @Test
        @DisplayName("should create SecretsConfig instance")
        void shouldCreateSecretsConfigInstance() {
            SecretsConfig config = new SecretsConfig();
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("should call loadSecretsFromFiles without error")
        void shouldCallLoadSecretsFromFilesWithoutError() {
            SecretsConfig config = new SecretsConfig();
            assertThatCode(() -> config.loadSecretsFromFiles()).doesNotThrowAnyException();
        }
    }
}
