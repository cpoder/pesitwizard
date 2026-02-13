package com.pesitwizard.server.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PartnerConfig Tests")
class PartnerConfigTest {

    private PartnerConfig config;

    @BeforeEach
    void setUp() {
        config = new PartnerConfig();
    }

    @Test
    @DisplayName("should have default values")
    void shouldHaveDefaultValues() {
        assertEquals("", config.getPassword());
        assertTrue(config.isEnabled());
        assertEquals(PartnerConfig.AccessType.BOTH, config.getAccessType());
        assertEquals(10, config.getMaxConnections());
        assertEquals(0, config.getAllowedFiles().length);
    }

    @Test
    @DisplayName("canWrite should return true for WRITE access")
    void canWriteShouldReturnTrueForWriteAccess() {
        config.setAccessType(PartnerConfig.AccessType.WRITE);
        assertTrue(config.canWrite());
        assertFalse(config.canRead());
    }

    @Test
    @DisplayName("canRead should return true for READ access")
    void canReadShouldReturnTrueForReadAccess() {
        config.setAccessType(PartnerConfig.AccessType.READ);
        assertTrue(config.canRead());
        assertFalse(config.canWrite());
    }

    @Test
    @DisplayName("canRead and canWrite should return true for BOTH access")
    void canReadAndWriteShouldReturnTrueForBothAccess() {
        config.setAccessType(PartnerConfig.AccessType.BOTH);
        assertTrue(config.canRead());
        assertTrue(config.canWrite());
    }

    @Test
    @DisplayName("canAccessFile should allow all files when no restrictions")
    void canAccessFileShouldAllowAllWhenNoRestrictions() {
        assertTrue(config.canAccessFile("ANY_FILE.dat"));
        assertTrue(config.canAccessFile("another.txt"));
    }

    @Test
    @DisplayName("canAccessFile should allow all files when allowedFiles is null")
    void canAccessFileShouldAllowAllWhenNull() {
        config.setAllowedFiles(null);
        assertTrue(config.canAccessFile("ANY_FILE.dat"));
    }

    @Test
    @DisplayName("canAccessFile should match exact filename")
    void canAccessFileShouldMatchExactFilename() {
        config.setAllowedFiles(new String[] {"FILE1.dat", "FILE2.txt"});
        assertTrue(config.canAccessFile("FILE1.dat"));
        assertTrue(config.canAccessFile("FILE2.txt"));
        assertFalse(config.canAccessFile("FILE3.dat"));
    }

    @Test
    @DisplayName("canAccessFile should match glob patterns")
    void canAccessFileShouldMatchGlobPatterns() {
        config.setAllowedFiles(new String[] {"DATA_*.dat", "LOG_*"});
        assertTrue(config.canAccessFile("DATA_001.dat"));
        assertTrue(config.canAccessFile("DATA_test.dat"));
        assertTrue(config.canAccessFile("LOG_2024"));
        assertFalse(config.canAccessFile("OTHER.dat"));
    }

    @Test
    @DisplayName("canAccessFile should handle empty patterns")
    void canAccessFileShouldHandleEmptyPatterns() {
        config.setAllowedFiles(new String[] {"", "  ", "VALID.dat"});
        assertTrue(config.canAccessFile("VALID.dat"));
        assertFalse(config.canAccessFile("INVALID.dat"));
    }

    @Test
    @DisplayName("canAccessFile should handle null patterns in array")
    void canAccessFileShouldHandleNullPatternsInArray() {
        config.setAllowedFiles(new String[] {null, "VALID.dat", null});
        assertTrue(config.canAccessFile("VALID.dat"));
        assertFalse(config.canAccessFile("INVALID.dat"));
    }

    @Test
    @DisplayName("should store partner attributes")
    void shouldStorePartnerAttributes() {
        config.setId("PARTNER1");
        config.setDescription("Test partner");
        config.setPassword("secret123");
        config.setEnabled(false);
        config.setMaxConnections(5);

        assertEquals("PARTNER1", config.getId());
        assertEquals("Test partner", config.getDescription());
        assertEquals("secret123", config.getPassword());
        assertFalse(config.isEnabled());
        assertEquals(5, config.getMaxConnections());
    }
}
