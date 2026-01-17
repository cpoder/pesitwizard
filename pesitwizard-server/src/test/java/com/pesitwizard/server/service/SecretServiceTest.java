package com.pesitwizard.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.security.SecretsService;
import com.pesitwizard.server.entity.SecretEntry;
import com.pesitwizard.server.entity.SecretEntry.SecretScope;
import com.pesitwizard.server.entity.SecretEntry.SecretType;
import com.pesitwizard.server.repository.SecretRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecretService Tests")
class SecretServiceTest {

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private SecretsService secretsService;

    @InjectMocks
    private SecretService secretService;

    private SecretEntry testSecret;

    @BeforeEach
    void setUp() {
        testSecret = SecretEntry.builder()
                .id(1L)
                .name("test-secret")
                .description("Test secret")
                .secretType(SecretType.PASSWORD)
                .encryptedValue("encrypted")
                .iv("iv123")
                .scope(SecretScope.GLOBAL)
                .version(1)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Secret Creation Tests")
    class CreateSecretTests {

        @Test
        @DisplayName("Should create secret successfully")
        void shouldCreateSecret() {
            when(secretRepository.existsByName("new-secret")).thenReturn(false);
            when(secretRepository.save(any(SecretEntry.class))).thenAnswer(inv -> {
                SecretEntry saved = inv.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            SecretEntry result = secretService.createSecret(
                    "new-secret", "my-password", "Description",
                    SecretType.PASSWORD, SecretScope.GLOBAL, null, null,
                    null, "admin");

            assertNotNull(result);
            assertEquals("new-secret", result.getName());
            assertEquals(SecretType.PASSWORD, result.getSecretType());
            assertNotNull(result.getEncryptedValue());
            assertNotNull(result.getIv());
            verify(secretRepository).save(any(SecretEntry.class));
        }

        @Test
        @DisplayName("Should reject duplicate secret name")
        void shouldRejectDuplicateName() {
            when(secretRepository.existsByName("existing")).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> secretService.createSecret("existing", "value", null,
                    SecretType.PASSWORD, SecretScope.GLOBAL, null, null, null, "admin"));
        }

        @Test
        @DisplayName("Should create secret with expiration")
        void shouldCreateSecretWithExpiration() {
            when(secretRepository.existsByName("expiring")).thenReturn(false);
            when(secretRepository.save(any(SecretEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
            SecretEntry result = secretService.createSecret(
                    "expiring", "value", null,
                    SecretType.API_KEY, SecretScope.GLOBAL, null, null,
                    expiry, "admin");

            assertEquals(expiry, result.getExpiresAt());
        }

        @Test
        @DisplayName("Should create partner-scoped secret")
        void shouldCreatePartnerSecret() {
            when(secretRepository.existsByName("partner-secret")).thenReturn(false);
            when(secretRepository.save(any(SecretEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            SecretEntry result = secretService.createSecret(
                    "partner-secret", "value", null,
                    SecretType.PASSWORD, SecretScope.PARTNER, "PARTNER1", null,
                    null, "admin");

            assertEquals(SecretScope.PARTNER, result.getScope());
            assertEquals("PARTNER1", result.getPartnerId());
        }
    }

    @Nested
    @DisplayName("Secret Retrieval Tests")
    class RetrievalTests {

        @Test
        @DisplayName("Should get secret by name")
        void shouldGetSecretByName() {
            when(secretRepository.findByName("test-secret")).thenReturn(Optional.of(testSecret));

            Optional<SecretEntry> result = secretService.getSecret("test-secret");

            assertTrue(result.isPresent());
            assertEquals("test-secret", result.get().getName());
        }

        @Test
        @DisplayName("Should get secret by ID")
        void shouldGetSecretById() {
            when(secretRepository.findById(1L)).thenReturn(Optional.of(testSecret));

            Optional<SecretEntry> result = secretService.getSecretById(1L);

            assertTrue(result.isPresent());
            assertEquals(1L, result.get().getId());
        }

        @Test
        @DisplayName("Should get all secrets")
        void shouldGetAllSecrets() {
            when(secretRepository.findAll()).thenReturn(List.of(testSecret));

            List<SecretEntry> result = secretService.getAllSecrets();

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should get secrets by type")
        void shouldGetSecretsByType() {
            when(secretRepository.findBySecretTypeOrderByNameAsc(SecretType.PASSWORD))
                    .thenReturn(List.of(testSecret));

            List<SecretEntry> result = secretService.getSecretsByType(SecretType.PASSWORD);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should get secrets by scope")
        void shouldGetSecretsByScope() {
            when(secretRepository.findByScopeOrderByNameAsc(SecretScope.GLOBAL))
                    .thenReturn(List.of(testSecret));

            List<SecretEntry> result = secretService.getSecretsByScope(SecretScope.GLOBAL);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should get secrets for partner")
        void shouldGetSecretsForPartner() {
            when(secretRepository.findActiveSecretsForPartner("PARTNER1"))
                    .thenReturn(List.of(testSecret));

            List<SecretEntry> result = secretService.getSecretsForPartner("PARTNER1");

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should get secrets for server")
        void shouldGetSecretsForServer() {
            when(secretRepository.findActiveSecretsForServer("SERVER1"))
                    .thenReturn(List.of(testSecret));

            List<SecretEntry> result = secretService.getSecretsForServer("SERVER1");

            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("Secret Update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update secret value")
        void shouldUpdateSecretValue() {
            when(secretRepository.findByName("test-secret")).thenReturn(Optional.of(testSecret));
            when(secretRepository.save(any(SecretEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            SecretEntry result = secretService.updateSecretValue("test-secret", "new-value", "admin");

            assertNotNull(result);
            assertEquals(2, result.getVersion());
            assertNotNull(result.getLastRotatedAt());
            verify(secretRepository).save(any(SecretEntry.class));
        }

        @Test
        @DisplayName("Should throw when updating non-existent secret")
        void shouldThrowWhenUpdatingNonExistent() {
            when(secretRepository.findByName("missing")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> secretService.updateSecretValue("missing", "value", "admin"));
        }

        @Test
        @DisplayName("Should update secret metadata")
        void shouldUpdateSecretMetadata() {
            when(secretRepository.findById(1L)).thenReturn(Optional.of(testSecret));
            when(secretRepository.save(any(SecretEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            SecretEntry result = secretService.updateSecretMetadata(
                    1L, "New description", SecretType.API_KEY, false, null, "admin");

            assertEquals("New description", result.getDescription());
            assertEquals(SecretType.API_KEY, result.getSecretType());
            assertFalse(result.getActive());
        }

        @Test
        @DisplayName("Should rotate secret")
        void shouldRotateSecret() {
            when(secretRepository.findByName("test-secret")).thenReturn(Optional.of(testSecret));
            when(secretRepository.save(any(SecretEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            SecretEntry result = secretService.rotateSecret("test-secret", "rotated-value", "admin");

            assertEquals(2, result.getVersion());
            assertNotNull(result.getLastRotatedAt());
        }
    }

    @Nested
    @DisplayName("Secret Deletion Tests")
    class DeletionTests {

        @Test
        @DisplayName("Should delete secret")
        void shouldDeleteSecret() {
            when(secretRepository.findById(1L)).thenReturn(Optional.of(testSecret));

            secretService.deleteSecret(1L);

            verify(secretRepository).delete(testSecret);
        }

        @Test
        @DisplayName("Should throw when deleting non-existent secret")
        void shouldThrowWhenDeletingNonExistent() {
            when(secretRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> secretService.deleteSecret(99L));
        }

        @Test
        @DisplayName("Should deactivate secret")
        void shouldDeactivateSecret() {
            when(secretRepository.findById(1L)).thenReturn(Optional.of(testSecret));
            when(secretRepository.save(any(SecretEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            SecretEntry result = secretService.deactivateSecret(1L);

            assertFalse(result.getActive());
        }
    }

    @Nested
    @DisplayName("Encryption Tests")
    class EncryptionTests {

        @Test
        @DisplayName("Should generate encryption key")
        void shouldGenerateEncryptionKey() {
            String key = secretService.generateEncryptionKey();

            assertNotNull(key);
            assertTrue(key.length() > 20); // Base64 encoded 256-bit key
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should get statistics")
        void shouldGetStatistics() {
            when(secretRepository.count()).thenReturn(10L);
            when(secretRepository.countByActiveTrue()).thenReturn(8L);
            when(secretRepository.findExpiredSecrets()).thenReturn(List.of(testSecret));
            when(secretRepository.countBySecretType(SecretType.PASSWORD)).thenReturn(5L);
            when(secretRepository.countBySecretType(SecretType.API_KEY)).thenReturn(3L);
            when(secretRepository.countBySecretType(SecretType.CERTIFICATE)).thenReturn(2L);

            SecretService.SecretStatistics stats = secretService.getStatistics();

            assertEquals(10L, stats.getTotalSecrets());
            assertEquals(8L, stats.getActiveSecrets());
            assertEquals(1, stats.getExpiredSecrets());
            assertEquals(5L, stats.getPasswordSecrets());
            assertEquals(3L, stats.getApiKeySecrets());
            assertEquals(2L, stats.getCertificateSecrets());
        }
    }
}
