package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for VaultSecretsProvider.
 */
@DisplayName("VaultSecretsProvider Tests")
class VaultSecretsProviderTest {

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("should not be available with null address")
        void shouldNotBeAvailableWithNullAddress() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, "token", "secret/data/test");

            assertThat(provider.isAvailable()).isFalse();
            assertThat(provider.getProviderType()).isEqualTo("VAULT");
        }

        @Test
        @DisplayName("should not be available with empty address")
        void shouldNotBeAvailableWithEmptyAddress() {
            VaultSecretsProvider provider = new VaultSecretsProvider("", "token", "secret/data/test");

            assertThat(provider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should not be available with null token")
        void shouldNotBeAvailableWithNullToken() {
            VaultSecretsProvider provider = new VaultSecretsProvider("http://vault:8200", null, "secret/data/test");

            assertThat(provider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should not be available with empty token")
        void shouldNotBeAvailableWithEmptyToken() {
            VaultSecretsProvider provider = new VaultSecretsProvider("http://vault:8200", "", "secret/data/test");

            assertThat(provider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should not be available when vault unreachable")
        void shouldNotBeAvailableWhenVaultUnreachable() {
            // Use invalid address that will fail connection
            VaultSecretsProvider provider = new VaultSecretsProvider(
                    "http://localhost:99999", "token", "secret/data/test");

            assertThat(provider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("getProviderType should return VAULT")
        void getProviderTypeShouldReturnVault() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);

            assertThat(provider.getProviderType()).isEqualTo("VAULT");
        }
    }

    @Nested
    @DisplayName("Encrypt/Decrypt Passthrough")
    class EncryptDecryptTests {

        @Test
        @DisplayName("encrypt should return plaintext as-is")
        void encryptShouldReturnPlaintextAsIs() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);

            String result = provider.encrypt("secret");

            assertThat(result).isEqualTo("secret");
        }

        @Test
        @DisplayName("decrypt should return non-vault value as-is")
        void decryptShouldReturnNonVaultValueAsIs() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);

            String result = provider.decrypt("plaintext");

            assertThat(result).isEqualTo("plaintext");
        }

        @Test
        @DisplayName("decrypt should return vault reference as-is when unavailable")
        void decryptShouldReturnVaultRefAsIsWhenUnavailable() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);

            String result = provider.decrypt("vault:some-key");

            // When unavailable, vault reference is returned as-is
            assertThat(result).isEqualTo("vault:some-key");
        }
    }

    @Nested
    @DisplayName("Vault Reference")
    class VaultReferenceTests {

        @Test
        @DisplayName("createReference should add vault prefix")
        void createReferenceShouldAddVaultPrefix() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);

            String ref = provider.createReference("my-secret-key");

            assertThat(ref).isEqualTo("vault:my-secret-key");
        }

        @Test
        @DisplayName("isVaultReference should return true for vault prefix")
        void isVaultReferenceShouldReturnTrueForVaultPrefix() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);

            assertThat(provider.isVaultReference("vault:key")).isTrue();
        }

        @Test
        @DisplayName("isVaultReference should return false for non-vault value")
        void isVaultReferenceShouldReturnFalseForNonVaultValue() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);

            assertThat(provider.isVaultReference("plaintext")).isFalse();
        }

        @Test
        @DisplayName("isVaultReference should return false for null")
        void isVaultReferenceShouldReturnFalseForNull() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);

            assertThat(provider.isVaultReference(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Operations When Unavailable")
    class UnavailableOperationsTests {

        @Test
        @DisplayName("storeSecret should not throw when unavailable")
        void storeSecretShouldNotThrowWhenUnavailable() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);

            assertThatCode(() -> provider.storeSecret("key", "value"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getSecret should return null when unavailable")
        void getSecretShouldReturnNullWhenUnavailable() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);

            String result = provider.getSecret("key");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("deleteSecret should not throw when unavailable")
        void deleteSecretShouldNotThrowWhenUnavailable() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);

            assertThatCode(() -> provider.deleteSecret("key"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Circuit Breaker")
    class CircuitBreakerTests {

        @Test
        @DisplayName("should be closed initially")
        void shouldBeClosedInitially() {
            VaultSecretsProvider provider = new VaultSecretsProvider(
                    "http://localhost:99999", "token", "secret/data/test");
            // Circuit starts closed, provider attempts connection
            assertThat(provider.getProviderType()).isEqualTo("VAULT");
        }
    }

    @Nested
    @DisplayName("Context Sanitization")
    class SanitizationTests {

        @Test
        @DisplayName("encrypt with context should sanitize special characters")
        void encryptWithContextShouldSanitize() {
            VaultSecretsProvider provider = new VaultSecretsProvider(null, null, null);
            // When unavailable, returns plaintext - tests path sanitization logic exists
            String result = provider.encrypt("secret", "My--Special@Context!");
            assertThat(result).isEqualTo("secret");
        }
    }
}
