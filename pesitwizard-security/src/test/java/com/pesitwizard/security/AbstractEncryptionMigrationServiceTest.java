package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for AbstractEncryptionMigrationService.
 */
@DisplayName("AbstractEncryptionMigrationService Tests")
@ExtendWith(MockitoExtension.class)
class AbstractEncryptionMigrationServiceTest {

    @Mock
    private SecretsService secretsService;

    private TestMigrationService migrationService;

    @BeforeEach
    void setUp() {
        migrationService = new TestMigrationService(secretsService);
    }

    @Nested
    @DisplayName("migrateAllToVault")
    class MigrateAllToVaultTests {

        @Test
        @DisplayName("should return failure when Vault is not available")
        void shouldReturnFailureWhenVaultNotAvailable() {
            when(secretsService.isVaultAvailable()).thenReturn(false);

            AbstractEncryptionMigrationService.MigrationResult result = migrationService.migrateAllToVault();

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("Vault is not available");
            assertThat(result.totalMigrated()).isZero();
            assertThat(result.totalSkipped()).isZero();
        }

        @Test
        @DisplayName("should call doMigration when Vault is available")
        void shouldCallDoMigrationWhenVaultAvailable() {
            when(secretsService.isVaultAvailable()).thenReturn(true);

            AbstractEncryptionMigrationService.MigrationResult result = migrationService.migrateAllToVault();

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Migration completed");
        }
    }

    @Nested
    @DisplayName("isVaultRef")
    class IsVaultRefTests {

        @Test
        @DisplayName("should return true for vault: prefix")
        void shouldReturnTrueForVaultPrefix() {
            assertThat(migrationService.testIsVaultRef("vault:secret/data/key")).isTrue();
        }

        @Test
        @DisplayName("should return false for AES: prefix")
        void shouldReturnFalseForAesPrefix() {
            assertThat(migrationService.testIsVaultRef("AES:encrypted")).isFalse();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(migrationService.testIsVaultRef(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for plain text")
        void shouldReturnFalseForPlainText() {
            assertThat(migrationService.testIsVaultRef("plain-password")).isFalse();
        }
    }

    @Nested
    @DisplayName("decryptIfNeeded")
    class DecryptIfNeededTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            String result = migrationService.testDecryptIfNeeded(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should decrypt encrypted value")
        void shouldDecryptEncryptedValue() {
            when(secretsService.isEncrypted("AES:encrypted")).thenReturn(true);
            when(secretsService.decrypt("AES:encrypted")).thenReturn("decrypted");

            String result = migrationService.testDecryptIfNeeded("AES:encrypted");

            assertThat(result).isEqualTo("decrypted");
        }

        @Test
        @DisplayName("should return value as-is if not encrypted")
        void shouldReturnValueAsIsIfNotEncrypted() {
            when(secretsService.isEncrypted("plain")).thenReturn(false);

            String result = migrationService.testDecryptIfNeeded("plain");

            assertThat(result).isEqualTo("plain");
        }
    }

    @Nested
    @DisplayName("migrateToVault")
    class MigrateToVaultTests {

        @Test
        @DisplayName("should store decrypted value in Vault")
        void shouldStoreDecryptedValueInVault() {
            when(secretsService.isEncrypted("AES:encrypted")).thenReturn(true);
            when(secretsService.decrypt("AES:encrypted")).thenReturn("plain-secret");
            when(secretsService.storeInVault("test-key", "plain-secret")).thenReturn("vault:test-key");

            String result = migrationService.testMigrateToVault("test-key", "AES:encrypted");

            assertThat(result).isEqualTo("vault:test-key");
            verify(secretsService).storeInVault("test-key", "plain-secret");
        }
    }

    @Nested
    @DisplayName("MigrationResult Record")
    class MigrationResultTests {

        @Test
        @DisplayName("should create MigrationResult with all fields")
        void shouldCreateMigrationResultWithAllFields() {
            AbstractEncryptionMigrationService.MigrationResult result = new AbstractEncryptionMigrationService.MigrationResult(
                    true, "Success", 5, 2, List.of("detail1", "detail2"));

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Success");
            assertThat(result.totalMigrated()).isEqualTo(5);
            assertThat(result.totalSkipped()).isEqualTo(2);
            assertThat(result.details()).containsExactly("detail1", "detail2");
        }
    }

    @Nested
    @DisplayName("MigrationCount Record")
    class MigrationCountTests {

        @Test
        @DisplayName("should create MigrationCount with fields")
        void shouldCreateMigrationCountWithFields() {
            AbstractEncryptionMigrationService.MigrationCount count = new AbstractEncryptionMigrationService.MigrationCount(
                    10, 3);

            assertThat(count.migrated()).isEqualTo(10);
            assertThat(count.skipped()).isEqualTo(3);
        }
    }

    /**
     * Concrete test implementation of AbstractEncryptionMigrationService.
     */
    private static class TestMigrationService extends AbstractEncryptionMigrationService {

        TestMigrationService(SecretsService secretsService) {
            super(secretsService);
        }

        @Override
        protected MigrationResult doMigration() {
            return new MigrationResult(true, "Migration completed", 0, 0, List.of());
        }

        // Expose protected methods for testing
        boolean testIsVaultRef(String value) {
            return isVaultRef(value);
        }

        String testDecryptIfNeeded(String value) {
            return decryptIfNeeded(value);
        }

        String testMigrateToVault(String key, String value) {
            return migrateToVault(key, value);
        }
    }
}
