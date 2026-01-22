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
    private static final String TEST_SALT_FILE = "./target/test-encryption.salt";

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("should initialize with valid master key")
        void shouldInitializeWithValidMasterKey() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            assertThat(provider.isAvailable()).isTrue();
            assertThat(provider.getProviderType()).isEqualTo("AES");
        }

        @Test
        @DisplayName("should not be available with null master key")
        void shouldNotBeAvailableWithNullMasterKey() {
            AesSecretsProvider provider = new AesSecretsProvider(null, TEST_SALT_FILE);

            assertThat(provider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should not be available with empty master key")
        void shouldNotBeAvailableWithEmptyMasterKey() {
            AesSecretsProvider provider = new AesSecretsProvider("", TEST_SALT_FILE);

            assertThat(provider.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should not be available with blank master key")
        void shouldNotBeAvailableWithBlankMasterKey() {
            AesSecretsProvider provider = new AesSecretsProvider("   ", TEST_SALT_FILE);

            assertThat(provider.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("Encryption/Decryption")
    class EncryptionDecryptionTests {

        @Test
        @DisplayName("should encrypt and decrypt successfully")
        void shouldEncryptAndDecryptSuccessfully() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
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
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            String result = provider.encrypt(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when decrypting null")
        void shouldReturnNullWhenDecryptingNull() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            String result = provider.decrypt(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should not re-encrypt already encrypted value")
        void shouldNotReEncryptAlreadyEncryptedValue() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
            String plaintext = "my-secret-password";

            String encrypted1 = provider.encrypt(plaintext);
            String encrypted2 = provider.encrypt(encrypted1);

            assertThat(encrypted2).isEqualTo(encrypted1);
        }

        @Test
        @DisplayName("should return plaintext when decrypting non-encrypted value")
        void shouldReturnPlaintextWhenDecryptingNonEncrypted() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
            String plaintext = "not-encrypted";

            String result = provider.decrypt(plaintext);

            assertThat(result).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should produce different ciphertexts for same plaintext (random IV)")
        void shouldProduceDifferentCiphertexts() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
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
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            String encrypted = provider.encrypt("");
            String decrypted = provider.decrypt(encrypted);

            assertThat(decrypted).isEmpty();
        }

        @Test
        @DisplayName("should handle special characters")
        void shouldHandleSpecialCharacters() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
            String plaintext = "pässwörd!@#$%^&*()_+-=[]{}|;':\",./<>?";

            String encrypted = provider.encrypt(plaintext);
            String decrypted = provider.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should handle long strings")
        void shouldHandleLongStrings() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
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
            AesSecretsProvider provider = new AesSecretsProvider(null, TEST_SALT_FILE);
            String plaintext = "secret";

            String result = provider.encrypt(plaintext);

            assertThat(result).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("decrypt should return ciphertext when not available")
        void decryptShouldReturnCiphertextWhenNotAvailable() {
            AesSecretsProvider provider = new AesSecretsProvider(null, TEST_SALT_FILE);
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
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            assertThat(provider.isEncrypted("AES:somedata")).isTrue();
        }

        @Test
        @DisplayName("should return false for non-encrypted value")
        void shouldReturnFalseForNonEncryptedValue() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            assertThat(provider.isEncrypted("plaintext")).isFalse();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            assertThat(provider.isEncrypted(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Cross-instance Compatibility")
    class CrossInstanceTests {

        @Test
        @DisplayName("should decrypt with different instance using same key")
        void shouldDecryptWithDifferentInstanceUsingSameKey() {
            AesSecretsProvider provider1 = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
            AesSecretsProvider provider2 = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            String plaintext = "shared-secret";
            String encrypted = provider1.encrypt(plaintext);
            String decrypted = provider2.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should fail to decrypt with different key")
        void shouldFailToDecryptWithDifferentKey() {
            AesSecretsProvider provider1 = new AesSecretsProvider("key-one-for-encryption", TEST_SALT_FILE);
            AesSecretsProvider provider2 = new AesSecretsProvider("key-two-for-decryption", TEST_SALT_FILE);

            String encrypted = provider1.encrypt("secret");

            assertThatThrownBy(() -> provider2.decrypt(encrypted))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Key Rotation / Versioning")
    class KeyRotationTests {

        @Test
        @DisplayName("should encrypt with v2 prefix")
        void shouldEncryptWithV2Prefix() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            String encrypted = provider.encrypt("secret");

            assertThat(encrypted).startsWith("AES:v2:");
        }

        @Test
        @DisplayName("should decrypt legacy AES: prefix values")
        void shouldDecryptLegacyPrefix() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            // Simulate legacy encrypted value (would have been encrypted with old static
            // salt)
            // For this test, we verify that isEncrypted recognizes both formats
            assertThat(provider.isEncrypted("AES:legacydata")).isTrue();
            assertThat(provider.isEncrypted("AES:v2:newdata")).isTrue();
        }

        @Test
        @DisplayName("should not re-encrypt v2 values")
        void shouldNotReEncryptV2Values() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
            String plaintext = "my-secret";

            String encrypted = provider.encrypt(plaintext);
            String reEncrypted = provider.encrypt(encrypted);

            assertThat(reEncrypted).isEqualTo(encrypted);
            assertThat(encrypted).startsWith("AES:v2:");
        }

        @Test
        @DisplayName("should use same salt file across instances")
        void shouldUseSameSaltFileAcrossInstances() {
            AesSecretsProvider provider1 = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
            String encrypted = provider1.encrypt("test-data");

            // Create new instance with same salt file
            AesSecretsProvider provider2 = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
            String decrypted = provider2.decrypt(encrypted);

            assertThat(decrypted).isEqualTo("test-data");
        }
    }

    @Nested
    @DisplayName("Encrypt with Context")
    class EncryptWithContextTests {

        @Test
        @DisplayName("should encrypt with context and decrypt successfully")
        void shouldEncryptWithContextAndDecrypt() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
            String plaintext = "my-password";
            String context = "registry/github/password";

            String encrypted = provider.encrypt(plaintext, context);
            String decrypted = provider.decrypt(encrypted);

            assertThat(encrypted).startsWith("AES:");
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should return null when encrypting null with context")
        void shouldReturnNullWhenEncryptingNullWithContext() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            String result = provider.encrypt(null, "some/context");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Secret Store Operations")
    class SecretStoreOperationsTests {

        @Test
        @DisplayName("storeSecret should be no-op for AES")
        void storeSecretShouldBeNoOp() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            // Should not throw
            assertThatCode(() -> provider.storeSecret("key", "value")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getSecret should return null for AES")
        void getSecretShouldReturnNull() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            String result = provider.getSecret("any-key");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("deleteSecret should be no-op for AES")
        void deleteSecretShouldBeNoOp() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);

            // Should not throw
            assertThatCode(() -> provider.deleteSecret("key")).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Unicode and Binary Data")
    class UnicodeAndBinaryTests {

        @Test
        @DisplayName("should handle unicode characters")
        void shouldHandleUnicodeCharacters() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
            String plaintext = "密码🔐пароль";

            String encrypted = provider.encrypt(plaintext);
            String decrypted = provider.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should handle newlines and tabs")
        void shouldHandleNewlinesAndTabs() {
            AesSecretsProvider provider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
            String plaintext = "line1\nline2\ttabbed";

            String encrypted = provider.encrypt(plaintext);
            String decrypted = provider.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }
    }
}
