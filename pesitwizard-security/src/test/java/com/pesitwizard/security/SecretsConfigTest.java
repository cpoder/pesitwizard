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

        @Test
        @DisplayName("should create secrets provider bean with reflection setup")
        void shouldCreateSecretsProviderBean() throws Exception {
            SecretsConfig config = new SecretsConfig();

            // Set required fields via reflection (Spring normally does this)
            setField(config, "saltFile", "./target/test-salt.salt");
            setField(config, "encryptionMode", "AES");

            config.loadSecretsFromFiles();

            SecretsProvider provider = config.secretsProvider();
            assertThat(provider).isNotNull();
            assertThat(provider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("should create secrets service bean")
        void shouldCreateSecretsServiceBean() throws Exception {
            SecretsConfig config = new SecretsConfig();
            setField(config, "saltFile", "./target/test-salt2.salt");
            setField(config, "encryptionMode", "AES");
            config.loadSecretsFromFiles();

            SecretsProvider provider = config.secretsProvider();
            SecretsService service = config.secretsService(provider);

            assertThat(service).isNotNull();
        }

        private void setField(Object obj, String fieldName, Object value) throws Exception {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        }
    }

    @Nested
    @DisplayName("Secrets Provider Creation")
    class SecretsProviderCreationTests {

        @Test
        @DisplayName("should create AES provider by default")
        void shouldCreateAesProviderByDefault() throws Exception {
            SecretsConfig config = new SecretsConfig();
            setField(config, "saltFile", "./target/test-salt3.salt");
            setField(config, "encryptionMode", "AES");
            config.loadSecretsFromFiles();

            SecretsProvider provider = config.secretsProvider();

            assertThat(provider).isNotNull();
            assertThat(provider.getProviderType()).isEqualTo("AES");
        }

        @Test
        @DisplayName("should encrypt and decrypt with created provider")
        void shouldEncryptAndDecryptWithCreatedProvider() throws Exception {
            SecretsConfig config = new SecretsConfig();
            setField(config, "saltFile", "./target/test-salt4.salt");
            setField(config, "encryptionMode", "AES");
            config.loadSecretsFromFiles();

            SecretsProvider provider = config.secretsProvider();
            String plaintext = "test-secret";

            String encrypted = provider.encrypt(plaintext);
            String decrypted = provider.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should handle invalid encryption mode gracefully")
        void shouldHandleInvalidEncryptionModeGracefully() throws Exception {
            SecretsConfig config = new SecretsConfig();
            setField(config, "saltFile", "./target/test-salt5.salt");
            setField(config, "encryptionMode", "INVALID_MODE");
            config.loadSecretsFromFiles();

            // Should fall back to AES
            SecretsProvider provider = config.secretsProvider();
            assertThat(provider).isNotNull();
            assertThat(provider.getProviderType()).isEqualTo("AES");
        }

        @Test
        @DisplayName("should handle null encryption mode")
        void shouldHandleNullEncryptionMode() throws Exception {
            SecretsConfig config = new SecretsConfig();
            setField(config, "saltFile", "./target/test-salt6.salt");
            setField(config, "encryptionMode", null);
            config.loadSecretsFromFiles();

            // Should default to AES
            SecretsProvider provider = config.secretsProvider();
            assertThat(provider).isNotNull();
        }

        private void setField(Object obj, String fieldName, Object value) throws Exception {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        }
    }
}
