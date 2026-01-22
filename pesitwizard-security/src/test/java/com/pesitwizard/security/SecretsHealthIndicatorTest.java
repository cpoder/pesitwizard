package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SecretsHealthIndicator.
 */
@DisplayName("SecretsHealthIndicator Tests")
class SecretsHealthIndicatorTest {

    @Nested
    @DisplayName("Health Check with AesSecretsProvider")
    class AesProviderTests {

        @Test
        @DisplayName("should return UP when AES provider is available")
        void shouldReturnUpWhenAesProviderAvailable() {
            AesSecretsProvider aesProvider = new AesSecretsProvider("test-key-32-chars-for-testing!!",
                    "./target/test.salt");
            SecretsHealthIndicator indicator = new SecretsHealthIndicator(aesProvider);

            SecretsHealthIndicator.HealthStatus status = indicator.health();

            assertThat(status.status()).isEqualTo("UP");
            assertThat(status.details()).containsEntry("provider", "AES");
            assertThat(status.details()).containsEntry("available", true);
            assertThat(status.details()).containsEntry("encryptionAlgorithm", "AES-256-GCM");
            assertThat(status.details()).containsEntry("keyDerivation", "PBKDF2-HMAC-SHA256");
        }

        @Test
        @DisplayName("should return DOWN when AES provider is not available")
        void shouldReturnDownWhenAesProviderNotAvailable() {
            AesSecretsProvider aesProvider = new AesSecretsProvider(null, "./target/test.salt");
            SecretsHealthIndicator indicator = new SecretsHealthIndicator(aesProvider);

            SecretsHealthIndicator.HealthStatus status = indicator.health();

            assertThat(status.status()).isEqualTo("DOWN");
            assertThat(status.details()).containsEntry("available", false);
        }
    }

    @Nested
    @DisplayName("Health Check with VaultSecretsProvider")
    class VaultProviderTests {

        @Test
        @DisplayName("should return DOWN when Vault is not configured")
        void shouldReturnDownWhenVaultNotConfigured() {
            VaultSecretsProvider vaultProvider = new VaultSecretsProvider(null, null, null);
            SecretsHealthIndicator indicator = new SecretsHealthIndicator(vaultProvider);

            SecretsHealthIndicator.HealthStatus status = indicator.health();

            assertThat(status.status()).isEqualTo("DOWN");
            assertThat(status.details()).containsEntry("provider", "VAULT");
            assertThat(status.details()).containsEntry("vaultConnected", false);
            assertThat(status.details()).containsEntry("cacheEnabled", true);
            assertThat(status.details()).containsEntry("circuitBreakerEnabled", true);
        }
    }

    @Nested
    @DisplayName("Health Check with CompositeSecretsProvider")
    class CompositeProviderTests {

        @Test
        @DisplayName("should return DOWN when composite provider primary is unavailable")
        void shouldReturnDownWhenCompositePrimaryUnavailable() {
            AesSecretsProvider aesProvider = new AesSecretsProvider("test-key-32-chars-for-testing!!",
                    "./target/test.salt");
            VaultSecretsProvider vaultProvider = new VaultSecretsProvider(null, null, null);
            CompositeSecretsProvider compositeProvider = new CompositeSecretsProvider(vaultProvider, aesProvider);
            SecretsHealthIndicator indicator = new SecretsHealthIndicator(compositeProvider);

            SecretsHealthIndicator.HealthStatus status = indicator.health();

            // CompositeSecretsProvider.isAvailable() returns primaryProvider.isAvailable()
            assertThat(status.status()).isEqualTo("DOWN");
            assertThat(status.details()).containsEntry("mode", "composite");
            assertThat(status.details()).containsEntry("migrationSupported", true);
        }

        @Test
        @DisplayName("should return UP when composite provider primary is AES and available")
        void shouldReturnUpWhenCompositePrimaryIsAesAndAvailable() {
            AesSecretsProvider aesProvider = new AesSecretsProvider("test-key-32-chars-for-testing!!",
                    "./target/test.salt");
            AesSecretsProvider fallbackAes = new AesSecretsProvider("fallback-key-32-chars-testing!!",
                    "./target/test2.salt");
            CompositeSecretsProvider compositeProvider = new CompositeSecretsProvider(aesProvider, fallbackAes);
            SecretsHealthIndicator indicator = new SecretsHealthIndicator(compositeProvider);

            SecretsHealthIndicator.HealthStatus status = indicator.health();

            assertThat(status.status()).isEqualTo("UP");
            assertThat(status.details()).containsEntry("mode", "composite");
            assertThat(status.details()).containsEntry("migrationSupported", true);
        }
    }

    @Nested
    @DisplayName("Health Check with Mock Provider")
    class MockProviderTests {

        @Test
        @DisplayName("should use provider type from getProviderType()")
        void shouldUseProviderTypeFromMethod() {
            SecretsProvider mockProvider = mock(SecretsProvider.class);
            when(mockProvider.getProviderType()).thenReturn("CUSTOM");
            when(mockProvider.isAvailable()).thenReturn(true);

            SecretsHealthIndicator indicator = new SecretsHealthIndicator(mockProvider);
            SecretsHealthIndicator.HealthStatus status = indicator.health();

            assertThat(status.status()).isEqualTo("UP");
            assertThat(status.details()).containsEntry("provider", "CUSTOM");
            assertThat(status.details()).containsEntry("available", true);
        }

        @Test
        @DisplayName("should return DOWN when mock provider is unavailable")
        void shouldReturnDownWhenMockProviderUnavailable() {
            SecretsProvider mockProvider = mock(SecretsProvider.class);
            when(mockProvider.getProviderType()).thenReturn("MOCK");
            when(mockProvider.isAvailable()).thenReturn(false);

            SecretsHealthIndicator indicator = new SecretsHealthIndicator(mockProvider);
            SecretsHealthIndicator.HealthStatus status = indicator.health();

            assertThat(status.status()).isEqualTo("DOWN");
            assertThat(status.details()).containsEntry("available", false);
        }
    }
}
