package com.pesitwizard.server.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.pesitwizard.security.AbstractEncryptionMigrationService.MigrationResult;
import com.pesitwizard.security.SecretsService;
import com.pesitwizard.server.entity.CertificateStore;
import com.pesitwizard.server.entity.Partner;
import com.pesitwizard.server.repository.CertificateStoreRepository;
import com.pesitwizard.server.repository.PartnerRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EncryptionMigrationService Tests")
class EncryptionMigrationServiceTest {

    @Mock
    private SecretsService secretsService;

    @Mock
    private PartnerRepository partnerRepository;

    @Mock
    private CertificateStoreRepository certificateStoreRepository;

    private EncryptionMigrationService migrationService;

    @BeforeEach
    void setUp() {
        migrationService = new EncryptionMigrationService(
                secretsService, partnerRepository, certificateStoreRepository);
    }

    @Nested
    @DisplayName("Migration Tests")
    class MigrationTests {

        @Test
        @DisplayName("Should migrate partner passwords to Vault")
        void shouldMigratePartnerPasswords() {
            Partner partner = new Partner();
            partner.setId("partner-1");
            partner.setPassword("AES:v2:encrypted-password");

            when(partnerRepository.findAll()).thenReturn(List.of(partner));
            when(certificateStoreRepository.findAll()).thenReturn(List.of());
            when(secretsService.isVaultAvailable()).thenReturn(true);
            when(secretsService.decrypt(anyString())).thenReturn("decrypted-password");
            when(secretsService.encryptForStorage(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("vault:partner-1-password");

            MigrationResult result = migrationService.migrateAllToVault();

            assertThat(result.success()).isTrue();
            verify(partnerRepository).save(any(Partner.class));
        }

        @Test
        @DisplayName("Should skip already migrated partners")
        void shouldSkipAlreadyMigratedPartners() {
            Partner partner = new Partner();
            partner.setId("partner-1");
            partner.setPassword("vault:already-migrated");

            when(partnerRepository.findAll()).thenReturn(List.of(partner));
            when(certificateStoreRepository.findAll()).thenReturn(List.of());
            when(secretsService.isVaultAvailable()).thenReturn(true);

            MigrationResult result = migrationService.migrateAllToVault();

            assertThat(result.success()).isTrue();
            verify(partnerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should migrate certificate store passwords")
        void shouldMigrateCertificateStorePasswords() {
            CertificateStore certStore = new CertificateStore();
            certStore.setId(1L);
            certStore.setStorePassword("AES:v2:store-pass");
            certStore.setKeyPassword("AES:v2:key-pass");

            when(partnerRepository.findAll()).thenReturn(List.of());
            when(certificateStoreRepository.findAll()).thenReturn(List.of(certStore));
            when(secretsService.isVaultAvailable()).thenReturn(true);
            when(secretsService.decrypt(anyString())).thenReturn("decrypted");
            when(secretsService.encryptForStorage(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("vault:migrated");

            MigrationResult result = migrationService.migrateAllToVault();

            assertThat(result.success()).isTrue();
            verify(certificateStoreRepository).save(any(CertificateStore.class));
        }

        @Test
        @DisplayName("Should fail migration when Vault is not available")
        void shouldFailWhenVaultNotAvailable() {
            when(secretsService.isVaultAvailable()).thenReturn(false);

            MigrationResult result = migrationService.migrateAllToVault();

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("not available");
        }

        @Test
        @DisplayName("Should handle empty repositories")
        void shouldHandleEmptyRepositories() {
            when(partnerRepository.findAll()).thenReturn(List.of());
            when(certificateStoreRepository.findAll()).thenReturn(List.of());
            when(secretsService.isVaultAvailable()).thenReturn(true);

            MigrationResult result = migrationService.migrateAllToVault();

            assertThat(result.success()).isTrue();
            assertThat(result.totalMigrated()).isZero();
        }

        @Test
        @DisplayName("Should handle partner with null password")
        void shouldHandlePartnerWithNullPassword() {
            Partner partner = new Partner();
            partner.setId("partner-1");
            partner.setPassword(null);

            when(partnerRepository.findAll()).thenReturn(List.of(partner));
            when(certificateStoreRepository.findAll()).thenReturn(List.of());
            when(secretsService.isVaultAvailable()).thenReturn(true);

            MigrationResult result = migrationService.migrateAllToVault();

            assertThat(result.success()).isTrue();
            verify(partnerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle certificate store with partial passwords")
        void shouldHandleCertStoreWithPartialPasswords() {
            CertificateStore certStore = new CertificateStore();
            certStore.setId(1L);
            certStore.setStorePassword("AES:v2:store-pass");
            certStore.setKeyPassword(null);

            when(partnerRepository.findAll()).thenReturn(List.of());
            when(certificateStoreRepository.findAll()).thenReturn(List.of(certStore));
            when(secretsService.isVaultAvailable()).thenReturn(true);
            when(secretsService.decrypt(anyString())).thenReturn("decrypted");
            when(secretsService.encryptForStorage(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("vault:migrated");

            MigrationResult result = migrationService.migrateAllToVault();

            assertThat(result.success()).isTrue();
            verify(certificateStoreRepository).save(any(CertificateStore.class));
        }
    }

    @Nested
    @DisplayName("MigrationResult Tests")
    class MigrationResultTests {

        @Test
        @DisplayName("Should create migration result with all fields")
        void shouldCreateMigrationResultWithAllFields() {
            MigrationResult result = new MigrationResult(
                    true, "Success", 5, 2, List.of("Detail 1", "Detail 2"));

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Success");
            assertThat(result.totalMigrated()).isEqualTo(5);
            assertThat(result.totalSkipped()).isEqualTo(2);
            assertThat(result.details()).hasSize(2);
        }
    }
}
