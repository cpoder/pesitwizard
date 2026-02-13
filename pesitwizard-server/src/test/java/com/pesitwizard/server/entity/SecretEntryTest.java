package com.pesitwizard.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.pesitwizard.server.entity.SecretEntry.SecretScope;
import com.pesitwizard.server.entity.SecretEntry.SecretType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SecretEntry Entity Tests")
class SecretEntryTest {

    @Test
    @DisplayName("should have default values with builder")
    void shouldHaveDefaultValuesWithBuilder() {
        SecretEntry secret = SecretEntry.builder().build();

        assertEquals(SecretType.GENERIC, secret.getSecretType());
        assertEquals(SecretScope.GLOBAL, secret.getScope());
        assertNull(secret.getVersion()); // @Version field starts null before first persist
        assertTrue(secret.getActive());
    }

    @Test
    @DisplayName("isExpired should return false when expiresAt is null")
    void isExpiredShouldReturnFalseWhenExpiresAtIsNull() {
        SecretEntry secret = SecretEntry.builder().build();
        assertFalse(secret.isExpired());
    }

    @Test
    @DisplayName("isExpired should return false when not expired")
    void isExpiredShouldReturnFalseWhenNotExpired() {
        SecretEntry secret =
                SecretEntry.builder().expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        assertFalse(secret.isExpired());
    }

    @Test
    @DisplayName("isExpired should return true when expired")
    void isExpiredShouldReturnTrueWhenExpired() {
        SecretEntry secret =
                SecretEntry.builder().expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        assertTrue(secret.isExpired());
    }

    @Test
    @DisplayName("isValid should return true when active and not expired")
    void isValidShouldReturnTrueWhenActiveAndNotExpired() {
        SecretEntry secret =
                SecretEntry.builder()
                        .active(true)
                        .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                        .build();
        assertTrue(secret.isValid());
    }

    @Test
    @DisplayName("isValid should return false when inactive")
    void isValidShouldReturnFalseWhenInactive() {
        SecretEntry secret = SecretEntry.builder().active(false).build();
        assertFalse(secret.isValid());
    }

    @Test
    @DisplayName("isValid should return false when expired")
    void isValidShouldReturnFalseWhenExpired() {
        SecretEntry secret =
                SecretEntry.builder()
                        .active(true)
                        .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                        .build();
        assertFalse(secret.isValid());
    }

    @Test
    @DisplayName("should have all secret types")
    void shouldHaveAllSecretTypes() {
        assertEquals(8, SecretType.values().length);
        assertNotNull(SecretType.GENERIC);
        assertNotNull(SecretType.PASSWORD);
        assertNotNull(SecretType.API_KEY);
        assertNotNull(SecretType.DATABASE);
        assertNotNull(SecretType.CERTIFICATE);
        assertNotNull(SecretType.ENCRYPTION_KEY);
        assertNotNull(SecretType.TOKEN);
        assertNotNull(SecretType.CONNECTION_STRING);
    }

    @Test
    @DisplayName("should have all secret scopes")
    void shouldHaveAllSecretScopes() {
        assertEquals(3, SecretScope.values().length);
        assertNotNull(SecretScope.GLOBAL);
        assertNotNull(SecretScope.SERVER);
        assertNotNull(SecretScope.PARTNER);
    }

    @Test
    @DisplayName("should store all attributes")
    void shouldStoreAllAttributes() {
        Instant now = Instant.now();

        SecretEntry secret =
                SecretEntry.builder()
                        .id(1L)
                        .name("db-password")
                        .description("Database password")
                        .secretType(SecretType.PASSWORD)
                        .encryptedValue("encrypted_data")
                        .iv("initialization_vector")
                        .scope(SecretScope.SERVER)
                        .partnerId("PARTNER1")
                        .serverId("SERVER1")
                        .version(2L)
                        .active(true)
                        .expiresAt(now.plus(90, ChronoUnit.DAYS))
                        .lastRotatedAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .createdBy("admin")
                        .updatedBy("admin")
                        .build();

        assertEquals(1L, secret.getId());
        assertEquals("db-password", secret.getName());
        assertEquals("Database password", secret.getDescription());
        assertEquals(SecretType.PASSWORD, secret.getSecretType());
        assertEquals("encrypted_data", secret.getEncryptedValue());
        assertEquals("initialization_vector", secret.getIv());
        assertEquals(SecretScope.SERVER, secret.getScope());
        assertEquals("PARTNER1", secret.getPartnerId());
        assertEquals("SERVER1", secret.getServerId());
        assertEquals(2L, secret.getVersion());
        assertTrue(secret.getActive());
        assertEquals(now.plus(90, ChronoUnit.DAYS), secret.getExpiresAt());
        assertEquals(now, secret.getLastRotatedAt());
        assertEquals(now, secret.getCreatedAt());
        assertEquals(now, secret.getUpdatedAt());
        assertEquals("admin", secret.getCreatedBy());
        assertEquals("admin", secret.getUpdatedBy());
    }
}
