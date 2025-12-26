package com.pesitwizard.server.ssl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.server.entity.CertificateStore;
import com.pesitwizard.server.entity.CertificateStore.StoreFormat;
import com.pesitwizard.server.entity.CertificateStore.StoreType;
import com.pesitwizard.server.repository.CertificateStoreRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("SslContextFactory Tests")
class SslContextFactoryTest {

    @Mock
    private CertificateStoreRepository certificateRepository;

    private SslContextFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SslContextFactory(certificateRepository);
    }

    @Test
    @DisplayName("createDefaultSslContext should throw when no default keystore")
    void createDefaultSslContextShouldThrowWhenNoKeystore() {
        when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.KEYSTORE))
                .thenReturn(Optional.empty());

        SslConfigurationException exception = assertThrows(SslConfigurationException.class,
                () -> factory.createDefaultSslContext());

        assertTrue(exception.getMessage().contains("No default keystore"));
    }

    @Test
    @DisplayName("createSslContext by name should throw when keystore not found")
    void createSslContextByNameShouldThrowWhenKeystoreNotFound() {
        when(certificateRepository.findByNameAndStoreType("missing", StoreType.KEYSTORE))
                .thenReturn(Optional.empty());

        SslConfigurationException exception = assertThrows(SslConfigurationException.class,
                () -> factory.createSslContext("missing", null));

        assertTrue(exception.getMessage().contains("Keystore not found"));
    }

    @Test
    @DisplayName("createSslContext by name should throw when keystore inactive")
    void createSslContextByNameShouldThrowWhenKeystoreInactive() {
        CertificateStore keystore = createStore("keystore1", StoreType.KEYSTORE);
        keystore.setActive(false);
        when(certificateRepository.findByNameAndStoreType("keystore1", StoreType.KEYSTORE))
                .thenReturn(Optional.of(keystore));

        SslConfigurationException exception = assertThrows(SslConfigurationException.class,
                () -> factory.createSslContext("keystore1", null));

        assertTrue(exception.getMessage().contains("not active"));
    }

    @Test
    @DisplayName("createSslContext by name should throw when truststore not found")
    void createSslContextByNameShouldThrowWhenTruststoreNotFound() {
        CertificateStore keystore = createStore("keystore1", StoreType.KEYSTORE);
        when(certificateRepository.findByNameAndStoreType("keystore1", StoreType.KEYSTORE))
                .thenReturn(Optional.of(keystore));
        when(certificateRepository.findByNameAndStoreType("missing", StoreType.TRUSTSTORE))
                .thenReturn(Optional.empty());

        SslConfigurationException exception = assertThrows(SslConfigurationException.class,
                () -> factory.createSslContext("keystore1", "missing"));

        assertTrue(exception.getMessage().contains("Truststore not found"));
    }

    @Test
    @DisplayName("createSslContext by name should throw when truststore inactive")
    void createSslContextByNameShouldThrowWhenTruststoreInactive() {
        CertificateStore keystore = createStore("keystore1", StoreType.KEYSTORE);
        CertificateStore truststore = createStore("truststore1", StoreType.TRUSTSTORE);
        truststore.setActive(false);

        when(certificateRepository.findByNameAndStoreType("keystore1", StoreType.KEYSTORE))
                .thenReturn(Optional.of(keystore));
        when(certificateRepository.findByNameAndStoreType("truststore1", StoreType.TRUSTSTORE))
                .thenReturn(Optional.of(truststore));

        SslConfigurationException exception = assertThrows(SslConfigurationException.class,
                () -> factory.createSslContext("keystore1", "truststore1"));

        assertTrue(exception.getMessage().contains("not active"));
    }

    @Test
    @DisplayName("createPartnerSslContext should throw when no keystore available")
    void createPartnerSslContextShouldThrowWhenNoKeystore() {
        when(certificateRepository.findByPartnerIdAndStoreTypeAndActiveTrue("PARTNER1", StoreType.KEYSTORE))
                .thenReturn(Optional.empty());
        when(certificateRepository.findByStoreTypeAndIsDefaultTrueAndActiveTrue(StoreType.KEYSTORE))
                .thenReturn(Optional.empty());

        SslConfigurationException exception = assertThrows(SslConfigurationException.class,
                () -> factory.createPartnerSslContext("PARTNER1"));

        assertTrue(exception.getMessage().contains("No keystore available"));
    }

    @Test
    @DisplayName("loadKeyStore should throw for unsupported format")
    void loadKeyStoreShouldThrowForUnsupportedFormat() {
        CertificateStore store = createStore("test", StoreType.KEYSTORE);
        store.setFormat(null); // Unsupported format

        assertThrows(Exception.class, () -> factory.loadKeyStore(store));
    }

    @Test
    @DisplayName("validateStore should throw when store data is empty")
    void validateStoreShouldThrowWhenEmpty() {
        CertificateStore store = createStore("test", StoreType.KEYSTORE);
        store.setStoreData(null);

        SslConfigurationException exception = assertThrows(SslConfigurationException.class,
                () -> factory.validateStore(store));

        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("validateStore should throw when store data is zero length")
    void validateStoreShouldThrowWhenZeroLength() {
        CertificateStore store = createStore("test", StoreType.KEYSTORE);
        store.setStoreData(new byte[0]);

        SslConfigurationException exception = assertThrows(SslConfigurationException.class,
                () -> factory.validateStore(store));

        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("createEmptyKeyStore should create JKS store")
    void createEmptyKeyStoreShouldCreateJks() throws Exception {
        byte[] storeData = factory.createEmptyKeyStore(StoreFormat.JKS, "password");

        assertNotNull(storeData);
        assertTrue(storeData.length > 0);
    }

    @Test
    @DisplayName("createEmptyKeyStore should create PKCS12 store")
    void createEmptyKeyStoreShouldCreatePkcs12() throws Exception {
        byte[] storeData = factory.createEmptyKeyStore(StoreFormat.PKCS12, "password");

        assertNotNull(storeData);
        assertTrue(storeData.length > 0);
    }

    @Test
    @DisplayName("createEmptyKeyStore should handle null password")
    void createEmptyKeyStoreShouldHandleNullPassword() throws Exception {
        byte[] storeData = factory.createEmptyKeyStore(StoreFormat.PKCS12, null);

        assertNotNull(storeData);
        assertTrue(storeData.length > 0);
    }

    @Test
    @DisplayName("CertificateInfo isExpired should return true for past date")
    void certificateInfoIsExpiredShouldReturnTrueForPastDate() {
        SslContextFactory.CertificateInfo info = new SslContextFactory.CertificateInfo();
        info.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        assertTrue(info.isExpired());
    }

    @Test
    @DisplayName("CertificateInfo isExpired should return false for future date")
    void certificateInfoIsExpiredShouldReturnFalseForFutureDate() {
        SslContextFactory.CertificateInfo info = new SslContextFactory.CertificateInfo();
        info.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));

        assertFalse(info.isExpired());
    }

    @Test
    @DisplayName("CertificateInfo isExpired should return false when null")
    void certificateInfoIsExpiredShouldReturnFalseWhenNull() {
        SslContextFactory.CertificateInfo info = new SslContextFactory.CertificateInfo();
        info.setExpiresAt(null);

        assertFalse(info.isExpired());
    }

    @Test
    @DisplayName("CertificateInfo isNotYetValid should return true for future date")
    void certificateInfoIsNotYetValidShouldReturnTrueForFutureDate() {
        SslContextFactory.CertificateInfo info = new SslContextFactory.CertificateInfo();
        info.setValidFrom(Instant.now().plus(1, ChronoUnit.DAYS));

        assertTrue(info.isNotYetValid());
    }

    @Test
    @DisplayName("CertificateInfo isNotYetValid should return false for past date")
    void certificateInfoIsNotYetValidShouldReturnFalseForPastDate() {
        SslContextFactory.CertificateInfo info = new SslContextFactory.CertificateInfo();
        info.setValidFrom(Instant.now().minus(1, ChronoUnit.DAYS));

        assertFalse(info.isNotYetValid());
    }

    @Test
    @DisplayName("CertificateInfo should store all fields")
    void certificateInfoShouldStoreAllFields() {
        SslContextFactory.CertificateInfo info = new SslContextFactory.CertificateInfo();
        Instant now = Instant.now();

        info.setAlias("test-alias");
        info.setSubjectDn("CN=Test");
        info.setIssuerDn("CN=Issuer");
        info.setSerialNumber("123456");
        info.setValidFrom(now);
        info.setExpiresAt(now.plus(365, ChronoUnit.DAYS));
        info.setFingerprint("ABC123");
        info.setKeyUsage(List.of("digitalSignature"));
        info.setHasPrivateKey(true);

        assertEquals("test-alias", info.getAlias());
        assertEquals("CN=Test", info.getSubjectDn());
        assertEquals("CN=Issuer", info.getIssuerDn());
        assertEquals("123456", info.getSerialNumber());
        assertEquals(now, info.getValidFrom());
        assertEquals("ABC123", info.getFingerprint());
        assertEquals(1, info.getKeyUsage().size());
        assertTrue(info.isHasPrivateKey());
    }

    @Test
    @DisplayName("SslConfigurationException should store message")
    void sslConfigurationExceptionShouldStoreMessage() {
        SslConfigurationException ex = new SslConfigurationException("Test error");
        assertEquals("Test error", ex.getMessage());
    }

    @Test
    @DisplayName("SslConfigurationException should store cause")
    void sslConfigurationExceptionShouldStoreCause() {
        Exception cause = new RuntimeException("root cause");
        SslConfigurationException ex = new SslConfigurationException("Test error", cause);
        assertEquals("Test error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("createEmptyKeyStore should create PKCS12 keystore")
    void createEmptyKeyStoreShouldCreatePkcs12() throws Exception {
        byte[] data = factory.createEmptyKeyStore(StoreFormat.PKCS12, "password");

        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("createEmptyKeyStore should create JKS keystore")
    void createEmptyKeyStoreShouldCreateJks() throws Exception {
        byte[] data = factory.createEmptyKeyStore(StoreFormat.JKS, "password");

        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("createEmptyKeyStore should work with null password")
    void createEmptyKeyStoreShouldWorkWithNullPassword() throws Exception {
        byte[] data = factory.createEmptyKeyStore(StoreFormat.PKCS12, null);

        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("validateStore should throw for empty store data")
    void validateStoreShouldThrowForEmptyData() {
        CertificateStore store = createStore("empty-store", StoreType.KEYSTORE);
        store.setStoreData(new byte[0]);

        assertThrows(SslConfigurationException.class, () -> factory.validateStore(store));
    }

    @Test
    @DisplayName("validateStore should throw for null store data")
    void validateStoreShouldThrowForNullData() {
        CertificateStore store = createStore("null-store", StoreType.KEYSTORE);
        store.setStoreData(null);

        assertThrows(SslConfigurationException.class, () -> factory.validateStore(store));
    }

    private CertificateStore createStore(String name, StoreType type) {
        CertificateStore store = new CertificateStore();
        store.setName(name);
        store.setStoreType(type);
        store.setFormat(StoreFormat.PKCS12);
        store.setActive(true);
        store.setStoreData(new byte[] { 1, 2, 3 }); // Dummy data
        return store;
    }
}
