package com.pesitwizard.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ApiKey Entity Tests")
class ApiKeyTest {

    @Test
    @DisplayName("should have default values with builder")
    void shouldHaveDefaultValuesWithBuilder() {
        ApiKey key = ApiKey.builder().build();

        assertNotNull(key.getRoles());
        assertTrue(key.getRoles().isEmpty());
        assertTrue(key.getActive());
    }

    @Test
    @DisplayName("isExpired should return false when expiresAt is null")
    void isExpiredShouldReturnFalseWhenExpiresAtIsNull() {
        ApiKey key = ApiKey.builder().build();
        assertFalse(key.isExpired());
    }

    @Test
    @DisplayName("isExpired should return false when not expired")
    void isExpiredShouldReturnFalseWhenNotExpired() {
        ApiKey key = ApiKey.builder()
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        assertFalse(key.isExpired());
    }

    @Test
    @DisplayName("isExpired should return true when expired")
    void isExpiredShouldReturnTrueWhenExpired() {
        ApiKey key = ApiKey.builder()
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        assertTrue(key.isExpired());
    }

    @Test
    @DisplayName("isValid should return true when active and not expired")
    void isValidShouldReturnTrueWhenActiveAndNotExpired() {
        ApiKey key = ApiKey.builder()
                .active(true)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        assertTrue(key.isValid());
    }

    @Test
    @DisplayName("isValid should return false when inactive")
    void isValidShouldReturnFalseWhenInactive() {
        ApiKey key = ApiKey.builder()
                .active(false)
                .build();
        assertFalse(key.isValid());
    }

    @Test
    @DisplayName("isValid should return false when expired")
    void isValidShouldReturnFalseWhenExpired() {
        ApiKey key = ApiKey.builder()
                .active(true)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        assertFalse(key.isValid());
    }

    @Test
    @DisplayName("isIpAllowed should return true when no restrictions")
    void isIpAllowedShouldReturnTrueWhenNoRestrictions() {
        ApiKey key = ApiKey.builder().build();
        assertTrue(key.isIpAllowed("192.168.1.1"));
    }

    @Test
    @DisplayName("isIpAllowed should return true when allowedIps is blank")
    void isIpAllowedShouldReturnTrueWhenAllowedIpsIsBlank() {
        ApiKey key = ApiKey.builder().allowedIps("   ").build();
        assertTrue(key.isIpAllowed("192.168.1.1"));
    }

    @Test
    @DisplayName("isIpAllowed should return true for exact match")
    void isIpAllowedShouldReturnTrueForExactMatch() {
        ApiKey key = ApiKey.builder()
                .allowedIps("192.168.1.1,10.0.0.1")
                .build();
        assertTrue(key.isIpAllowed("192.168.1.1"));
        assertTrue(key.isIpAllowed("10.0.0.1"));
    }

    @Test
    @DisplayName("isIpAllowed should return false for non-matching IP")
    void isIpAllowedShouldReturnFalseForNonMatchingIp() {
        ApiKey key = ApiKey.builder()
                .allowedIps("192.168.1.1")
                .build();
        assertFalse(key.isIpAllowed("192.168.1.2"));
    }

    @Test
    @DisplayName("isIpAllowed should return true for wildcard")
    void isIpAllowedShouldReturnTrueForWildcard() {
        ApiKey key = ApiKey.builder()
                .allowedIps("*")
                .build();
        assertTrue(key.isIpAllowed("192.168.1.1"));
    }

    @Test
    @DisplayName("isIpAllowed should support CIDR notation")
    void isIpAllowedShouldSupportCidrNotation() {
        ApiKey key = ApiKey.builder()
                .allowedIps("192.168.1.0/24")
                .build();
        assertTrue(key.isIpAllowed("192.168.1.100"));
        assertTrue(key.isIpAllowed("192.168.1.1"));
        assertFalse(key.isIpAllowed("192.168.2.1"));
    }

    @Test
    @DisplayName("isIpAllowed should handle invalid CIDR gracefully")
    void isIpAllowedShouldHandleInvalidCidrGracefully() {
        ApiKey key = ApiKey.builder()
                .allowedIps("invalid/cidr")
                .build();
        assertFalse(key.isIpAllowed("192.168.1.1"));
    }

    @Test
    @DisplayName("should store all attributes")
    void shouldStoreAllAttributes() {
        Instant now = Instant.now();
        List<String> roles = List.of("ROLE_API", "ROLE_USER");

        ApiKey key = ApiKey.builder()
                .id(1L)
                .name("test-key")
                .description("Test API key")
                .keyHash("abc123hash")
                .keyPrefix("pk_test_")
                .roles(roles)
                .active(true)
                .expiresAt(now.plus(30, ChronoUnit.DAYS))
                .lastUsedAt(now)
                .allowedIps("192.168.1.0/24")
                .rateLimit(100)
                .partnerId("PARTNER1")
                .createdAt(now)
                .updatedAt(now)
                .createdBy("admin")
                .build();

        assertEquals(1L, key.getId());
        assertEquals("test-key", key.getName());
        assertEquals("Test API key", key.getDescription());
        assertEquals("abc123hash", key.getKeyHash());
        assertEquals("pk_test_", key.getKeyPrefix());
        assertEquals(roles, key.getRoles());
        assertTrue(key.getActive());
        assertEquals(now.plus(30, ChronoUnit.DAYS), key.getExpiresAt());
        assertEquals(now, key.getLastUsedAt());
        assertEquals("192.168.1.0/24", key.getAllowedIps());
        assertEquals(100, key.getRateLimit());
        assertEquals("PARTNER1", key.getPartnerId());
        assertEquals(now, key.getCreatedAt());
        assertEquals(now, key.getUpdatedAt());
        assertEquals("admin", key.getCreatedBy());
    }
}
