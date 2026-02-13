package com.pesitwizard.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.security.SecretsService;
import com.pesitwizard.server.entity.CertificateStore;
import com.pesitwizard.server.entity.CertificateStore.CertificatePurpose;
import com.pesitwizard.server.entity.CertificateStore.StoreFormat;
import com.pesitwizard.server.entity.CertificateStore.StoreType;
import com.pesitwizard.server.repository.CertificateStoreRepository;
import com.pesitwizard.server.ssl.SslConfigurationException;
import com.pesitwizard.server.ssl.SslContextFactory;
import com.pesitwizard.server.ssl.SslContextFactory.CertificateInfo;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("CertificateService Tests")
class CertificateServiceTest {

    @Mock private CertificateStoreRepository certificateRepository;

    @Mock private SslContextFactory sslContextFactory;

    @Mock private SecretsService secretsService;

    @InjectMocks private CertificateService certificateService;

    private CertificateStore testKeystore;
    private CertificateStore testTruststore;
    private byte[] testStoreData;

    @BeforeEach
    void setUp() {
        testStoreData = "test-store-data".getBytes();

        // SecretsService: encrypt returns an encrypted marker, isEncrypted detects it
        lenient()
                .when(secretsService.isEncrypted(any()))
                .thenAnswer(
                        inv -> {
                            String val = inv.getArgument(0);
                            return val != null
                                    && (val.startsWith("AES:")
                                            || val.startsWith("vault:")
                                            || val.startsWith("ENC:"));
                        });
        lenient()
                .when(
                        secretsService.encryptForStorage(
                                anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "ENC:" + inv.getArgument(0));

        testKeystore =
                CertificateStore.builder()
                        .id(1L)
                        .name("test-keystore")
                        .description("Test keystore")
                        .storeType(StoreType.KEYSTORE)
                        .format(StoreFormat.PKCS12)
                        .storeData(testStoreData)
                        .storePassword("changeit")
                        .keyPassword("keypass")
                        .keyAlias("server")
                        .purpose(CertificatePurpose.SERVER)
                        .isDefault(true)
                        .active(true)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

        testTruststore =
                CertificateStore.builder()
                        .id(2L)
                        .name("test-truststore")
                        .description("Test truststore")
                        .storeType(StoreType.TRUSTSTORE)
                        .format(StoreFormat.PKCS12)
                        .storeData(testStoreData)
                        .storePassword("changeit")
                        .purpose(CertificatePurpose.CA)
                        .isDefault(false)
                        .active(true)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
    }

    @Nested
    @DisplayName("Certificate Store CRUD Tests")
    class CrudTests {

        @Test
        @DisplayName("Should create certificate store")
        void shouldCreateCertificateStore() throws SslConfigurationException {
            when(certificateRepository.existsByNameAndStoreType("new-store", StoreType.KEYSTORE))
                    .thenReturn(false);
            when(certificateRepository.save(any(CertificateStore.class)))
                    .thenAnswer(
                            inv -> {
                                CertificateStore saved = inv.getArgument(0);
                                saved.setId(3L);
                                return saved;
                            });

            CertificateInfo mockInfo = mock(CertificateInfo.class);
            when(mockInfo.getSubjectDn()).thenReturn("CN=Test");
            when(mockInfo.getIssuerDn()).thenReturn("CN=CA");
            when(mockInfo.getSerialNumber()).thenReturn("12345");
            when(mockInfo.getValidFrom()).thenReturn(Instant.now());
            when(mockInfo.getExpiresAt()).thenReturn(Instant.now().plus(365, ChronoUnit.DAYS));
            when(mockInfo.getFingerprint()).thenReturn("ABC123");
            when(sslContextFactory.extractCertificateInfo(any(CertificateStore.class)))
                    .thenReturn(mockInfo);

            CertificateStore result =
                    certificateService.createCertificateStore(
                            "new-store",
                            "New store",
                            StoreType.KEYSTORE,
                            StoreFormat.PKCS12,
                            testStoreData,
                            "password",
                            "keypass",
                            "server",
                            CertificatePurpose.SERVER,
                            null,
                            false,
                            "admin");

            assertNotNull(result);
            assertEquals("new-store", result.getName());
            assertEquals(StoreType.KEYSTORE, result.getStoreType());
            verify(certificateRepository).save(any(CertificateStore.class));
        }

        @Test
        @DisplayName("Should reject duplicate store name")
        void shouldRejectDuplicateName() {
            when(certificateRepository.existsByNameAndStoreType("existing", StoreType.KEYSTORE))
                    .thenReturn(true);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            certificateService.createCertificateStore(
                                    "existing",
                                    null,
                                    StoreType.KEYSTORE,
                                    StoreFormat.PKCS12,
                                    testStoreData,
                                    "pass",
                                    null,
                                    null,
                                    CertificatePurpose.SERVER,
                                    null,
                                    false,
                                    "admin"));
        }

        @Test
        @DisplayName("Should get certificate store by ID")
        void shouldGetById() {
            when(certificateRepository.findById(1L)).thenReturn(Optional.of(testKeystore));

            Optional<CertificateStore> result = certificateService.getCertificateStore(1L);

            assertTrue(result.isPresent());
            assertEquals("test-keystore", result.get().getName());
        }

        @Test
        @DisplayName("Should get certificate store by name")
        void shouldGetByName() {
            when(certificateRepository.findByName("test-keystore"))
                    .thenReturn(Optional.of(testKeystore));

            Optional<CertificateStore> result =
                    certificateService.getCertificateStoreByName("test-keystore");

            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Should get all certificate stores")
        void shouldGetAll() {
            when(certificateRepository.findAll()).thenReturn(List.of(testKeystore, testTruststore));

            List<CertificateStore> result = certificateService.getAllCertificateStores();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should get stores by type")
        void shouldGetByType() {
            when(certificateRepository.findByStoreTypeOrderByNameAsc(StoreType.KEYSTORE))
                    .thenReturn(List.of(testKeystore));

            List<CertificateStore> result =
                    certificateService.getCertificateStoresByType(StoreType.KEYSTORE);

            assertEquals(1, result.size());
            assertEquals(StoreType.KEYSTORE, result.get(0).getStoreType());
        }

        @Test
        @DisplayName("Should get active stores by type")
        void shouldGetActiveByType() {
            when(certificateRepository.findByStoreTypeAndActiveOrderByNameAsc(
                            StoreType.KEYSTORE, true))
                    .thenReturn(List.of(testKeystore));

            List<CertificateStore> result =
                    certificateService.getActiveCertificateStoresByType(StoreType.KEYSTORE);

            assertEquals(1, result.size());
            assertTrue(result.get(0).getActive());
        }

        @Test
        @DisplayName("Should update certificate store description")
        void shouldUpdateDescription() throws SslConfigurationException {
            when(certificateRepository.findById(1L)).thenReturn(Optional.of(testKeystore));
            when(certificateRepository.save(any(CertificateStore.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CertificateStore result =
                    certificateService.updateCertificateStore(
                            1L, "Updated description", null, null, null, null, null, null, "admin");

            assertEquals("Updated description", result.getDescription());
        }

        @Test
        @DisplayName("Should throw when updating non-existent store")
        void shouldThrowOnUpdateNonExistent() {
            when(certificateRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            certificateService.updateCertificateStore(
                                    99L, null, null, null, null, null, null, null, null));
        }

        @Test
        @DisplayName("Should delete certificate store")
        void shouldDelete() {
            when(certificateRepository.findById(1L)).thenReturn(Optional.of(testKeystore));

            certificateService.deleteCertificateStore(1L);

            verify(certificateRepository).delete(testKeystore);
        }

        @Test
        @DisplayName("Should throw when deleting non-existent store")
        void shouldThrowOnDeleteNonExistent() {
            when(certificateRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(
                    IllegalArgumentException.class,
                    () -> certificateService.deleteCertificateStore(99L));
        }
    }

    @Nested
    @DisplayName("Default Store Tests")
    class DefaultStoreTests {

        @Test
        @DisplayName("Should set store as default")
        void shouldSetAsDefault() {
            when(certificateRepository.findById(2L)).thenReturn(Optional.of(testTruststore));
            when(certificateRepository.findByStoreTypeAndActiveOrderByNameAsc(
                            StoreType.TRUSTSTORE, true))
                    .thenReturn(List.of());
            when(certificateRepository.save(any(CertificateStore.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CertificateStore result = certificateService.setAsDefault(2L);

            assertTrue(result.getIsDefault());
        }

        @Test
        @DisplayName("Should clear previous default when setting new default")
        void shouldClearPreviousDefault() {
            CertificateStore previousDefault =
                    CertificateStore.builder()
                            .id(3L)
                            .storeType(StoreType.TRUSTSTORE)
                            .format(StoreFormat.PKCS12)
                            .storeData(testStoreData)
                            .isDefault(true)
                            .active(true)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

            when(certificateRepository.findById(2L)).thenReturn(Optional.of(testTruststore));
            when(certificateRepository.findByStoreTypeAndActiveOrderByNameAsc(
                            StoreType.TRUSTSTORE, true))
                    .thenReturn(List.of(previousDefault));
            when(certificateRepository.save(any(CertificateStore.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            certificateService.setAsDefault(2L);

            assertFalse(previousDefault.getIsDefault());
            verify(certificateRepository, times(2)).save(any(CertificateStore.class));
        }

        @Test
        @DisplayName("Should get default keystore")
        void shouldGetDefaultKeystore() {
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(
                            StoreType.KEYSTORE))
                    .thenReturn(Optional.of(testKeystore));

            Optional<CertificateStore> result =
                    certificateService.getDefaultCertificateStore(StoreType.KEYSTORE);

            assertTrue(result.isPresent());
            assertEquals(StoreType.KEYSTORE, result.get().getStoreType());
        }

        @Test
        @DisplayName("Should get default truststore")
        void shouldGetDefaultTruststore() {
            when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(
                            StoreType.TRUSTSTORE))
                    .thenReturn(Optional.of(testTruststore));

            Optional<CertificateStore> result =
                    certificateService.getDefaultCertificateStore(StoreType.TRUSTSTORE);

            assertTrue(result.isPresent());
            assertEquals(StoreType.TRUSTSTORE, result.get().getStoreType());
        }

        @Test
        @DisplayName("Should throw when setting non-existent store as default")
        void shouldThrowOnSetNonExistentAsDefault() {
            when(certificateRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(
                    IllegalArgumentException.class, () -> certificateService.setAsDefault(99L));
        }
    }

    @Nested
    @DisplayName("Certificate Info Tests")
    class CertificateInfoTests {

        @Test
        @DisplayName("Should handle non-existent store for info")
        void shouldHandleNonExistentForInfo() {
            when(certificateRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(
                    IllegalArgumentException.class,
                    () -> certificateService.getCertificateInfo(99L));
        }

        @Test
        @DisplayName("Should get certificate info")
        void shouldGetCertificateInfo() throws SslConfigurationException {
            when(certificateRepository.findById(1L)).thenReturn(Optional.of(testKeystore));
            CertificateInfo mockInfo = mock(CertificateInfo.class);
            when(mockInfo.getSubjectDn()).thenReturn("CN=Test");
            when(sslContextFactory.extractCertificateInfo(testKeystore)).thenReturn(mockInfo);

            CertificateInfo result = certificateService.getCertificateInfo(1L);

            assertNotNull(result);
            assertEquals("CN=Test", result.getSubjectDn());
        }

        @Test
        @DisplayName("Should validate certificate store")
        void shouldValidateCertificateStore() throws SslConfigurationException {
            when(certificateRepository.findById(1L)).thenReturn(Optional.of(testKeystore));

            assertDoesNotThrow(() -> certificateService.validateCertificateStore(1L));
            verify(sslContextFactory).validateStore(testKeystore);
        }
    }

    @Nested
    @DisplayName("Expiration Check Tests")
    class ExpirationCheckTests {

        @Test
        @DisplayName("Should find expiring certificates")
        void shouldFindExpiringCertificates() {
            testKeystore.setExpiresAt(Instant.now().plus(15, ChronoUnit.DAYS));
            when(certificateRepository.findExpiringBefore(any(Instant.class)))
                    .thenReturn(List.of(testKeystore));

            List<CertificateStore> result = certificateService.getExpiringCertificates(30);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should check expiring certificates scheduled task")
        void shouldCheckExpiringCertificates() {
            when(certificateRepository.findExpiringBefore(any(Instant.class)))
                    .thenReturn(List.of());

            // Should not throw
            assertDoesNotThrow(() -> certificateService.checkExpiringCertificates());
        }

        @Test
        @DisplayName("Should get expired certificates")
        void shouldGetExpiredCertificates() {
            testKeystore.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
            when(certificateRepository.findExpired()).thenReturn(List.of(testKeystore));

            List<CertificateStore> result = certificateService.getExpiredCertificates();

            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("Activation Tests")
    class ActivationTests {

        @Test
        @DisplayName("Should activate store")
        void shouldActivateStore() {
            testKeystore.setActive(false);
            when(certificateRepository.findById(1L)).thenReturn(Optional.of(testKeystore));
            when(certificateRepository.save(any(CertificateStore.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CertificateStore result = certificateService.activate(1L);

            assertTrue(result.getActive());
        }

        @Test
        @DisplayName("Should deactivate store")
        void shouldDeactivateStore() {
            when(certificateRepository.findById(1L)).thenReturn(Optional.of(testKeystore));
            when(certificateRepository.save(any(CertificateStore.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CertificateStore result = certificateService.deactivate(1L);

            assertFalse(result.getActive());
        }

        @Test
        @DisplayName("Should throw when activating non-existent store")
        void shouldThrowOnActivateNonExistent() {
            when(certificateRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> certificateService.activate(99L));
        }

        @Test
        @DisplayName("Should throw when deactivating non-existent store")
        void shouldThrowOnDeactivateNonExistent() {
            when(certificateRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> certificateService.deactivate(99L));
        }
    }

    @Nested
    @DisplayName("Partner Certificate Tests")
    class PartnerCertificateTests {

        @Test
        @DisplayName("Should get partner certificates")
        void shouldGetPartnerCertificates() {
            testKeystore.setPartnerId("partner1");
            when(certificateRepository.findByPartnerIdAndActiveOrderByNameAsc("partner1", true))
                    .thenReturn(List.of(testKeystore));

            List<CertificateStore> result = certificateService.getPartnerCertificates("partner1");

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should get partner keystore")
        void shouldGetPartnerKeystore() {
            testKeystore.setPartnerId("partner1");
            when(certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue(
                            "partner1", StoreType.KEYSTORE))
                    .thenReturn(Optional.of(testKeystore));

            Optional<CertificateStore> result = certificateService.getPartnerKeystore("partner1");

            assertTrue(result.isPresent());
            assertEquals(StoreType.KEYSTORE, result.get().getStoreType());
        }

        @Test
        @DisplayName("Should get partner truststore")
        void shouldGetPartnerTruststore() {
            testTruststore.setPartnerId("partner1");
            when(certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue(
                            "partner1", StoreType.TRUSTSTORE))
                    .thenReturn(Optional.of(testTruststore));

            Optional<CertificateStore> result = certificateService.getPartnerTruststore("partner1");

            assertTrue(result.isPresent());
            assertEquals(StoreType.TRUSTSTORE, result.get().getStoreType());
        }
    }

    @Nested
    @DisplayName("Empty Store Creation Tests")
    class EmptyStoreCreationTests {

        @Test
        @DisplayName("Should create empty keystore")
        void shouldCreateEmptyKeystore() throws SslConfigurationException {
            when(certificateRepository.existsByNameAndStoreType(
                            "empty-keystore", StoreType.KEYSTORE))
                    .thenReturn(false);
            when(sslContextFactory.createEmptyKeyStore(StoreFormat.PKCS12, "password"))
                    .thenReturn(testStoreData);
            when(certificateRepository.save(any(CertificateStore.class)))
                    .thenAnswer(
                            inv -> {
                                CertificateStore saved = inv.getArgument(0);
                                saved.setId(10L);
                                return saved;
                            });

            CertificateStore result =
                    certificateService.createEmptyKeystore(
                            "empty-keystore",
                            "Empty keystore",
                            StoreFormat.PKCS12,
                            "password",
                            CertificatePurpose.SERVER,
                            null,
                            false,
                            "admin");

            assertNotNull(result);
            assertEquals("empty-keystore", result.getName());
            assertEquals(StoreType.KEYSTORE, result.getStoreType());
        }

        @Test
        @DisplayName("Should reject duplicate empty keystore name")
        void shouldRejectDuplicateEmptyKeystoreName() {
            when(certificateRepository.existsByNameAndStoreType("existing", StoreType.KEYSTORE))
                    .thenReturn(true);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            certificateService.createEmptyKeystore(
                                    "existing",
                                    null,
                                    StoreFormat.PKCS12,
                                    "password",
                                    CertificatePurpose.SERVER,
                                    null,
                                    false,
                                    "admin"));
        }

        @Test
        @DisplayName("Should create empty truststore")
        void shouldCreateEmptyTruststore() throws SslConfigurationException {
            when(certificateRepository.existsByNameAndStoreType(
                            "empty-truststore", StoreType.TRUSTSTORE))
                    .thenReturn(false);
            when(sslContextFactory.createEmptyKeyStore(StoreFormat.PKCS12, "password"))
                    .thenReturn(testStoreData);
            when(certificateRepository.save(any(CertificateStore.class)))
                    .thenAnswer(
                            inv -> {
                                CertificateStore saved = inv.getArgument(0);
                                saved.setId(11L);
                                return saved;
                            });

            CertificateStore result =
                    certificateService.createEmptyTruststore(
                            "empty-truststore",
                            "Empty truststore",
                            StoreFormat.PKCS12,
                            "password",
                            null,
                            false,
                            "admin");

            assertNotNull(result);
            assertEquals("empty-truststore", result.getName());
            assertEquals(StoreType.TRUSTSTORE, result.getStoreType());
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should get certificate statistics")
        void shouldGetStatistics() {
            when(certificateRepository.countByStoreTypeAndActive(StoreType.KEYSTORE, true))
                    .thenReturn(5L);
            when(certificateRepository.countByStoreTypeAndActive(StoreType.TRUSTSTORE, true))
                    .thenReturn(3L);
            when(certificateRepository.findExpired()).thenReturn(List.of());
            when(certificateRepository.findExpiringBefore(any(Instant.class)))
                    .thenReturn(List.of());

            CertificateService.CertificateStatistics result = certificateService.getStatistics();

            assertNotNull(result);
            assertEquals(5L, result.getTotalKeystores());
            assertEquals(3L, result.getTotalTruststores());
            assertEquals(0, result.getExpiredCount());
        }
    }
}
