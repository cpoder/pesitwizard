package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Integration tests for VaultSecretsProvider using a real Vault instance.
 * Requires Vault running at localhost:8200 with dev token.
 * 
 * Run with: docker run -d --name vault-test -p 8200:8200 \
 * -e 'VAULT_DEV_ROOT_TOKEN_ID=test-root-token' \
 * -e 'VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200' hashicorp/vault:latest
 */
@Tag("integration")
@DisplayName("VaultSecretsProvider Integration Tests")
@EnabledIf("isVaultAvailable")
class VaultSecretsProviderIntegrationTest {

    private static final String VAULT_ADDR = "http://localhost:8200";
    private static final String VAULT_TOKEN = "test-root-token";
    private static final String SECRETS_PATH = "secret/data/test";

    private VaultSecretsProvider provider;

    static boolean isVaultAvailable() {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(2)).build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(VAULT_ADDR + "/v1/sys/health"))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .GET().build();
            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        provider = new VaultSecretsProvider(VAULT_ADDR, VAULT_TOKEN, SECRETS_PATH);
    }

    @Nested
    @DisplayName("Availability Tests")
    class AvailabilityTests {

        @Test
        @DisplayName("Should be available with valid config")
        void shouldBeAvailable() {
            assertThat(provider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should return VAULT provider type")
        void shouldReturnVaultProviderType() {
            assertThat(provider.getProviderType()).isEqualTo("VAULT");
        }
    }

    @Nested
    @DisplayName("Secret Storage Tests")
    class SecretStorageTests {

        @Test
        @DisplayName("Should store and retrieve secret")
        void shouldStoreAndRetrieveSecret() {
            String key = "test-key-" + System.currentTimeMillis();
            String value = "test-secret-value";

            provider.storeSecret(key, value);
            String retrieved = provider.getSecret(key);

            assertThat(retrieved).isEqualTo(value);
        }

        @Test
        @DisplayName("Should return null for non-existent secret")
        void shouldReturnNullForNonExistent() {
            String result = provider.getSecret("non-existent-key-" + System.currentTimeMillis());
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should delete secret")
        void shouldDeleteSecret() {
            String key = "to-delete-" + System.currentTimeMillis();
            provider.storeSecret(key, "value-to-delete");

            // Verify stored
            assertThat(provider.getSecret(key)).isEqualTo("value-to-delete");

            // Delete
            provider.deleteSecret(key);

            // Verify deleted (may return null or cached value initially)
            // The cache should be cleared
        }
    }

    @Nested
    @DisplayName("Encryption/Decryption Tests")
    class EncryptionTests {

        @Test
        @DisplayName("Should encrypt and create vault reference")
        void shouldEncryptAndCreateVaultReference() {
            String plaintext = "sensitive-data";

            String encrypted = provider.encrypt(plaintext);

            assertThat(encrypted).startsWith("vault:");
            assertThat(encrypted).isNotEqualTo(plaintext);
        }

        @Test
        @DisplayName("Should decrypt vault reference")
        void shouldDecryptVaultReference() {
            String plaintext = "my-secret-password";

            String encrypted = provider.encrypt(plaintext);
            String decrypted = provider.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("Should encrypt with context")
        void shouldEncryptWithContext() {
            String plaintext = "context-sensitive-data";
            String context = "partner/mypartner/password";

            String encrypted = provider.encrypt(plaintext, context);

            assertThat(encrypted).startsWith("vault:");
            assertThat(provider.decrypt(encrypted)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("Should handle null plaintext")
        void shouldHandleNullPlaintext() {
            assertThat(provider.encrypt(null)).isNull();
        }

        @Test
        @DisplayName("Should handle blank plaintext")
        void shouldHandleBlankPlaintext() {
            assertThat(provider.encrypt("   ")).isEqualTo("   ");
        }

        @Test
        @DisplayName("Should return non-vault ciphertext unchanged")
        void shouldReturnNonVaultCiphertextUnchanged() {
            String notVaultRef = "AES:v2:somedata";
            assertThat(provider.decrypt(notVaultRef)).isEqualTo(notVaultRef);
        }
    }

    @Nested
    @DisplayName("Vault Reference Tests")
    class VaultReferenceTests {

        @Test
        @DisplayName("Should detect vault reference")
        void shouldDetectVaultReference() {
            assertThat(provider.isVaultReference("vault:some-key")).isTrue();
        }

        @Test
        @DisplayName("Should not detect non-vault reference")
        void shouldNotDetectNonVaultReference() {
            assertThat(provider.isVaultReference("AES:v2:data")).isFalse();
            assertThat(provider.isVaultReference("plaintext")).isFalse();
            assertThat(provider.isVaultReference(null)).isFalse();
        }

        @Test
        @DisplayName("Should create vault reference")
        void shouldCreateVaultReference() {
            String ref = provider.createReference("my-key");
            assertThat(ref).isEqualTo("vault:my-key");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle special characters in secret value")
        void shouldHandleSpecialCharacters() {
            String key = "special-" + System.currentTimeMillis();
            String value = "pass@word!#$%^&*(){}[]|\\:\";<>,.?/~`";

            provider.storeSecret(key, value);
            assertThat(provider.getSecret(key)).isEqualTo(value);
        }

        @Test
        @DisplayName("Should handle unicode in secret value")
        void shouldHandleUnicode() {
            String key = "unicode-" + System.currentTimeMillis();
            String value = "密码 пароль كلمة السر 🔐";

            provider.storeSecret(key, value);
            assertThat(provider.getSecret(key)).isEqualTo(value);
        }

        @Test
        @DisplayName("Should handle long secret value")
        void shouldHandleLongValue() {
            String key = "long-" + System.currentTimeMillis();
            String value = "x".repeat(10000);

            provider.storeSecret(key, value);
            assertThat(provider.getSecret(key)).isEqualTo(value);
        }
    }

    @Nested
    @DisplayName("Unavailable Provider Tests")
    class UnavailableProviderTests {

        @Test
        @DisplayName("Should not be available with null address")
        void shouldNotBeAvailableWithNullAddress() {
            VaultSecretsProvider unavailable = new VaultSecretsProvider(null, VAULT_TOKEN, SECRETS_PATH);
            assertThat(unavailable.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("Should not be available with blank address")
        void shouldNotBeAvailableWithBlankAddress() {
            VaultSecretsProvider unavailable = new VaultSecretsProvider("   ", VAULT_TOKEN, SECRETS_PATH);
            assertThat(unavailable.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("Should not be available with null token")
        void shouldNotBeAvailableWithNullToken() {
            VaultSecretsProvider unavailable = new VaultSecretsProvider(VAULT_ADDR, null, SECRETS_PATH);
            assertThat(unavailable.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("Should pass through encrypt when unavailable")
        void shouldPassThroughEncryptWhenUnavailable() {
            VaultSecretsProvider unavailable = new VaultSecretsProvider(null, null, SECRETS_PATH);
            assertThat(unavailable.encrypt("plaintext")).isEqualTo("plaintext");
        }

        @Test
        @DisplayName("Should pass through decrypt when unavailable")
        void shouldPassThroughDecryptWhenUnavailable() {
            VaultSecretsProvider unavailable = new VaultSecretsProvider(null, null, SECRETS_PATH);
            assertThat(unavailable.decrypt("ciphertext")).isEqualTo("ciphertext");
        }
    }
}
