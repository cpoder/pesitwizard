package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompositeSecretsProvider")
class CompositeSecretsProviderTest {

    private static final String TEST_SALT_FILE = "./target/test-encryption.salt";
    private static final String VALID_MASTER_KEY = "test-master-key-for-unit-tests-32";

    @Mock
    private SecretsProvider mockPrimaryProvider;
    private AesSecretsProvider aesProvider;
    private CompositeSecretsProvider compositeProvider;

    @BeforeEach
    void setUp() {
        aesProvider = new AesSecretsProvider(VALID_MASTER_KEY, TEST_SALT_FILE);
        when(mockPrimaryProvider.getProviderType()).thenReturn("VAULT");
        compositeProvider = new CompositeSecretsProvider(mockPrimaryProvider, aesProvider);
    }

    @Test
    @DisplayName("should delegate encrypt to primary provider")
    void shouldDelegateEncryptToPrimary() {
        when(mockPrimaryProvider.encrypt("secret")).thenReturn("vault:encrypted");
        assertThat(compositeProvider.encrypt("secret")).isEqualTo("vault:encrypted");
    }

    @Test
    @DisplayName("should delegate encrypt with context to primary")
    void shouldDelegateEncryptWithContext() {
        when(mockPrimaryProvider.encrypt("secret", "ctx")).thenReturn("vault:ctx");
        assertThat(compositeProvider.encrypt("secret", "ctx")).isEqualTo("vault:ctx");
    }

    @Test
    @DisplayName("should decrypt AES values with AES provider")
    void shouldDecryptAesWithAesProvider() {
        String encrypted = aesProvider.encrypt("my-secret");
        assertThat(compositeProvider.decrypt(encrypted)).isEqualTo("my-secret");
        verify(mockPrimaryProvider, never()).decrypt(anyString());
    }

    @Test
    @DisplayName("should decrypt vault values with primary provider")
    void shouldDecryptVaultWithPrimary() {
        when(mockPrimaryProvider.decrypt("vault:key")).thenReturn("value");
        assertThat(compositeProvider.decrypt("vault:key")).isEqualTo("value");
    }

    @Test
    @DisplayName("should return unencrypted values as-is")
    void shouldReturnPlainAsIs() {
        assertThat(compositeProvider.decrypt("plain")).isEqualTo("plain");
    }

    @Test
    @DisplayName("should handle null")
    void shouldHandleNull() {
        assertThat(compositeProvider.decrypt(null)).isNull();
    }
}
