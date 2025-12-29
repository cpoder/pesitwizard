package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for AesSecretsProvider.
 */
@DisplayName("AesSecretsProvider Tests")
class AesSecretsProviderTest {

    private static final String VALID_MASTER_KEY = "test-master-key-for-unit-tests-32";

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("should initialize with valid master key")
        void shouldInitializeWithValidMasterKey() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);

            assertThat(provider.isAvailable()).isTrue();
            assertThat(provider.getProviderType()).isEqualTo("AES");
        }

        @Test
        @DisplayName("should not be available with null master key")
        void shouldNotBeAvailableWithNullMasterKey() {
            AesSecretsProvider provider = new AesSecretsProvider(null);

            assertThat(provider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should not be available with empty master key")
        void shouldNotBeAvailableWithEmptyMasterKey() {
            AesSecretsProvider provider = new AesSecretsProvider("");

            assertThat(provider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should not be available with blank master key")
        void shouldNotBeAvailableWithBlankMasterKey() {
            AesSecretsProvider provider = new AesSecretsProvider("   ");

            assertThat(provider.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("Encryption/Decryption")
    class EncryptionDecryptionTests {

        @Test
        @DisplayName("should encrypt and decrypt successfully")
        void shouldEncryptAndDecryptSuccessfully() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);
            String plaintext = "my-secret-password";

            String encrypted = provider.encrypt(plaintext);
            String decrypted = provider.decrypt(encrypted);

            assertThat(encrypted).isNotEqualTo(plaintext);
            assertThat(encrypted).startsWith("AES:");
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should return null when encrypting null")
        void shouldReturnNullWhenEncryptingNull() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);

            String result = provider.encrypt(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when decrypting null")
        void shouldReturnNullWhenDecryptingNull() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);

            String result = provider.decrypt(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should not re-encrypt already encrypted value")
        void shouldNotReEncryptAlreadyEncryptedValue() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);
            String plaintext = "my-secret-password";

            String encrypted1 = provider.encrypt(plaintext);
            String encrypted2 = provider.encrypt(encrypted1);

            assertThat(encrypted2).isEqualTo(encrypted1);
        }

        @Test
        @DisplayName("should return plaintext when decrypting non-encrypted value")
        void shouldReturnPlaintextWhenDecryptingNonEncrypted() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);
            String plaintext = "not-encrypted";

            String result = provider.decrypt(plaintext);

            assertThat(result).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should produce different ciphertexts for same plaintext (random IV)")
        void shouldProduceDifferentCiphertexts() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);
            String plaintext = "my-secret-password";

            String encrypted1 = provider.encrypt(plaintext);
            String encrypted2 = provider.encrypt(plaintext);

            assertThat(encrypted1).isNotEqualTo(encrypted2);
            assertThat(provider.decrypt(encrypted1)).isEqualTo(plaintext);
            assertThat(provider.decrypt(encrypted2)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmptyString() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);

            String encrypted = provider.encrypt("");
            String decrypted = provider.decrypt(encrypted);

            assertThat(decrypted).isEmpty();
        }

        @Test
        @DisplayName("should handle special characters")
        void shouldHandleSpecialCharacters() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);
            String plaintext = "pässwörd!@#$%^&*()_+-=[]{}|;':\",./<>?";

            String encrypted = provider.encrypt(plaintext);
            String decrypted = provider.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should handle long strings")
        void shouldHandleLongStrings() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);
            String plaintext = "a".repeat(10000);

            String encrypted = provider.encrypt(plaintext);
            String decrypted = provider.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }
    }

    @Nested
    @DisplayName("Unavailable Provider")
    class UnavailableProviderTests {

        @Test
        @DisplayName("encrypt should return plaintext when not available")
        void encryptShouldReturnPlaintextWhenNotAvailable() {
            AesSecretsProvider provider = new AesSecretsProvider(null);
            String plaintext = "secret";

            String result = provider.encrypt(plaintext);

            assertThat(result).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("decrypt should return ciphertext when not available")
        void decryptShouldReturnCiphertextWhenNotAvailable() {
            AesSecretsProvider provider = new AesSecretsProvider(null);
            String ciphertext = "AES:someencrypteddata";

            String result = provider.decrypt(ciphertext);

            assertThat(result).isEqualTo(ciphertext);
        }
    }

    @Nested
    @DisplayName("isEncrypted Helper")
    class IsEncryptedTests {

        @Test
        @DisplayName("should return true for encrypted value")
        void shouldReturnTrueForEncryptedValue() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);

            assertThat(provider.isEncrypted("AES:somedata")).isTrue();
        }

        @Test
        @DisplayName("should return false for non-encrypted value")
        void shouldReturnFalseForNonEncryptedValue() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);

            assertThat(provider.isEncrypted("plaintext")).isFalse();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY);

            assertThat(provider.isEncrypted(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Cross-instance Compatibility")
    class CrossInstanceTests {

        @Test
        @DisplayName("should decrypt with different instance using same key")
        void shouldDecryptWithDifferentInstanceUsingSameKey() {
            AesSecretsProvider provider1 = new AesSecretsProvider(VALID_MASTER_KEY);
            AesSecretsProvider provider2 = new AesSecretsProvider(VALID_MASTER_KEY);

            String plaintext = "shared-secret";
            String encrypted = provider1.encrypt(plaintext);
            String decrypted = provider2.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should fail to decrypt with different key")
        void shouldFailToDecryptWithDifferentKey() {
            AesSecretsProvider provider1 = new AesSecretsProvider("key-one-for-encryption");
            AesSecretsProvider provider2 = new AesSecretsProvider("key-two-for-decryption");

            String encrypted = provider1.encrypt("secret");

            assertThatThrownBy(() -> provider2.decrypt(encrypted))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
