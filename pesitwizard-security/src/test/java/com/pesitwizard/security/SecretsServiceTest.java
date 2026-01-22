package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for SecretsService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecretsService Tests")
class SecretsServiceTest {

    @Mock
    private SecretsProvider secretsProvider;

    private SecretsService secretsService;

    @BeforeEach
    void setUp() {
        secretsService = new SecretsService(secretsProvider);
    }

    @Nested
    @DisplayName("encryptForStorage")
    class EncryptForStorageTests {

        @Test
        @DisplayName("should delegate to provider encrypt")
        void shouldDelegateToProviderEncrypt() {
            when(secretsProvider.encrypt("plaintext")).thenReturn("AES:encrypted");

            String result = secretsService.encryptForStorage("plaintext");

            assertThat(result).isEqualTo("AES:encrypted");
            verify(secretsProvider).encrypt("plaintext");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            String result = secretsService.encryptForStorage(null);

            assertThat(result).isNull();
            verify(secretsProvider, never()).encrypt(any());
        }

        @Test
        @DisplayName("should return blank for blank input")
        void shouldReturnBlankForBlankInput() {
            String result = secretsService.encryptForStorage("   ");

            assertThat(result).isEqualTo("   ");
            verify(secretsProvider, never()).encrypt(any());
        }
    }

    @Nested
    @DisplayName("decryptFromStorage")
    class DecryptFromStorageTests {

        @Test
        @DisplayName("should delegate to provider decrypt")
        void shouldDelegateToProviderDecrypt() {
            when(secretsProvider.decrypt("AES:encrypted")).thenReturn("plaintext");

            String result = secretsService.decryptFromStorage("AES:encrypted");

            assertThat(result).isEqualTo("plaintext");
            verify(secretsProvider).decrypt("AES:encrypted");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            String result = secretsService.decryptFromStorage(null);

            assertThat(result).isNull();
            verify(secretsProvider, never()).decrypt(any());
        }

        @Test
        @DisplayName("should return blank for blank input")
        void shouldReturnBlankForBlankInput() {
            String result = secretsService.decryptFromStorage("  ");

            assertThat(result).isEqualTo("  ");
            verify(secretsProvider, never()).decrypt(any());
        }
    }

    @Nested
    @DisplayName("getEncryptionMode")
    class GetEncryptionModeTests {

        @Test
        @DisplayName("should return provider type")
        void shouldReturnProviderType() {
            when(secretsProvider.getProviderType()).thenReturn("AES");

            String result = secretsService.getEncryptionMode();

            assertThat(result).isEqualTo("AES");
        }
    }

    @Nested
    @DisplayName("isEncryptionEnabled")
    class IsEncryptionEnabledTests {

        @Test
        @DisplayName("should return true when provider available and not NONE")
        void shouldReturnTrueWhenAvailableAndNotNone() {
            when(secretsProvider.isAvailable()).thenReturn(true);
            when(secretsProvider.getProviderType()).thenReturn("AES");

            boolean result = secretsService.isEncryptionEnabled();

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when provider type is NONE")
        void shouldReturnFalseWhenTypeIsNone() {
            when(secretsProvider.isAvailable()).thenReturn(true);
            when(secretsProvider.getProviderType()).thenReturn("NONE");

            boolean result = secretsService.isEncryptionEnabled();

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when provider not available")
        void shouldReturnFalseWhenNotAvailable() {
            when(secretsProvider.isAvailable()).thenReturn(false);

            boolean result = secretsService.isEncryptionEnabled();

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getStatus")
    class GetStatusTests {

        @Test
        @DisplayName("should return status with AES provider")
        void shouldReturnStatusWithAesProvider() {
            when(secretsProvider.getProviderType()).thenReturn("AES");
            when(secretsProvider.isAvailable()).thenReturn(true);

            var status = secretsService.getStatus();

            assertThat(status.providerType()).isEqualTo("AES");
            assertThat(status.available()).isTrue();
            assertThat(status.message()).contains("AES-256-GCM");
        }

        @Test
        @DisplayName("should return warning status for NONE provider")
        void shouldReturnWarningStatusForNoneProvider() {
            when(secretsProvider.getProviderType()).thenReturn("NONE");
            when(secretsProvider.isAvailable()).thenReturn(true);

            var status = secretsService.getStatus();

            assertThat(status.providerType()).isEqualTo("NONE");
            assertThat(status.message()).contains("plaintext");
        }

        @Test
        @DisplayName("should return status with Vault provider")
        void shouldReturnStatusWithVaultProvider() {
            when(secretsProvider.getProviderType()).thenReturn("VAULT");
            when(secretsProvider.isAvailable()).thenReturn(true);

            var status = secretsService.getStatus();

            assertThat(status.providerType()).isEqualTo("VAULT");
            assertThat(status.message()).contains("Vault");
        }
    }

    @Nested
    @DisplayName("External Secret Operations")
    class ExternalSecretOperationsTests {

        @Test
        @DisplayName("storeSecret should delegate to provider")
        void storeSecretShouldDelegateToProvider() {
            secretsService.storeSecret("key", "value");

            verify(secretsProvider).storeSecret("key", "value");
        }

        @Test
        @DisplayName("getSecret should delegate to provider")
        void getSecretShouldDelegateToProvider() {
            when(secretsProvider.getSecret("key")).thenReturn("value");

            String result = secretsService.getSecret("key");

            assertThat(result).isEqualTo("value");
            verify(secretsProvider).getSecret("key");
        }

        @Test
        @DisplayName("deleteSecret should delegate to provider")
        void deleteSecretShouldDelegateToProvider() {
            secretsService.deleteSecret("key");

            verify(secretsProvider).deleteSecret("key");
        }
    }

    @Nested
    @DisplayName("isEncrypted")
    class IsEncryptedTests {

        @Test
        @DisplayName("should return true for AES encrypted value")
        void shouldReturnTrueForAesEncrypted() {
            assertThat(secretsService.isEncrypted("AES:someencryptedvalue")).isTrue();
        }

        @Test
        @DisplayName("should return true for vault encrypted value")
        void shouldReturnTrueForVaultEncrypted() {
            assertThat(secretsService.isEncrypted("vault:some/path")).isTrue();
        }

        @Test
        @DisplayName("should return true for ENC encrypted value")
        void shouldReturnTrueForEncEncrypted() {
            assertThat(secretsService.isEncrypted("ENC:somevalue")).isTrue();
        }

        @Test
        @DisplayName("should return false for plaintext")
        void shouldReturnFalseForPlaintext() {
            assertThat(secretsService.isEncrypted("plaintext")).isFalse();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(secretsService.isEncrypted(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("encryptForStorage with context")
    class EncryptForStorageWithContextTests {

        @Test
        @DisplayName("should delegate to provider with context path")
        void shouldDelegateToProviderWithContextPath() {
            when(secretsProvider.encrypt(eq("password123"), eq("registry/github/password")))
                    .thenReturn("vault:registry/github/password");

            String result = secretsService.encryptForStorage("password123", "registry", "github", "password");

            assertThat(result).isEqualTo("vault:registry/github/password");
            verify(secretsProvider).encrypt("password123", "registry/github/password");
        }

        @Test
        @DisplayName("should return null for null plaintext with context")
        void shouldReturnNullForNullPlaintextWithContext() {
            String result = secretsService.encryptForStorage(null, "registry", "github", "password");

            assertThat(result).isNull();
            verify(secretsProvider, never()).encrypt(any(), any());
        }

        @Test
        @DisplayName("should return blank for blank plaintext with context")
        void shouldReturnBlankForBlankPlaintextWithContext() {
            String result = secretsService.encryptForStorage("  ", "registry", "github", "password");

            assertThat(result).isEqualTo("  ");
            verify(secretsProvider, never()).encrypt(any(), any());
        }
    }

    @Nested
    @DisplayName("isVaultAvailable")
    class IsVaultAvailableTests {

        @Test
        @DisplayName("should return true when provider is VAULT and available")
        void shouldReturnTrueWhenVaultAndAvailable() {
            when(secretsProvider.getProviderType()).thenReturn("VAULT");
            when(secretsProvider.isAvailable()).thenReturn(true);

            assertThat(secretsService.isVaultAvailable()).isTrue();
        }

        @Test
        @DisplayName("should return false when provider is AES")
        void shouldReturnFalseWhenAes() {
            when(secretsProvider.getProviderType()).thenReturn("AES");

            assertThat(secretsService.isVaultAvailable()).isFalse();
        }

        @Test
        @DisplayName("should return false when Vault not available")
        void shouldReturnFalseWhenVaultNotAvailable() {
            when(secretsProvider.getProviderType()).thenReturn("VAULT");
            when(secretsProvider.isAvailable()).thenReturn(false);

            assertThat(secretsService.isVaultAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("storeInVault")
    class StoreInVaultTests {

        @Test
        @DisplayName("should store and return vault reference")
        void shouldStoreAndReturnVaultReference() {
            when(secretsProvider.getProviderType()).thenReturn("VAULT");
            when(secretsProvider.isAvailable()).thenReturn(true);

            String result = secretsService.storeInVault("mykey", "mysecret");

            assertThat(result).isEqualTo("vault:mykey");
            verify(secretsProvider).storeSecret("mykey", "mysecret");
        }

        @Test
        @DisplayName("should throw when Vault not available")
        void shouldThrowWhenVaultNotAvailable() {
            when(secretsProvider.getProviderType()).thenReturn("AES");

            assertThatThrownBy(() -> secretsService.storeInVault("key", "value"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Vault is not available");
        }
    }

    @Nested
    @DisplayName("encrypt and decrypt aliases")
    class EncryptDecryptAliasesTests {

        @Test
        @DisplayName("encrypt should delegate to encryptForStorage")
        void encryptShouldDelegateToEncryptForStorage() {
            when(secretsProvider.encrypt("plain")).thenReturn("AES:encrypted");

            String result = secretsService.encrypt("plain");

            assertThat(result).isEqualTo("AES:encrypted");
        }

        @Test
        @DisplayName("decrypt should delegate to decryptFromStorage")
        void decryptShouldDelegateToDecryptFromStorage() {
            when(secretsProvider.decrypt("AES:encrypted")).thenReturn("plain");

            String result = secretsService.decrypt("AES:encrypted");

            assertThat(result).isEqualTo("plain");
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailableTests {

        @Test
        @DisplayName("should delegate to provider isAvailable")
        void shouldDelegateToProviderIsAvailable() {
            when(secretsProvider.isAvailable()).thenReturn(true);

            assertThat(secretsService.isAvailable()).isTrue();
            verify(secretsProvider).isAvailable();
        }
    }

    @Nested
    @DisplayName("getStatus edge cases")
    class GetStatusEdgeCasesTests {

        @Test
        @DisplayName("should return error status when not available")
        void shouldReturnErrorStatusWhenNotAvailable() {
            when(secretsProvider.getProviderType()).thenReturn("AES");
            when(secretsProvider.isAvailable()).thenReturn(false);

            var status = secretsService.getStatus();

            assertThat(status.available()).isFalse();
            assertThat(status.message()).contains("not available");
        }
    }
}
