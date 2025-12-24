package com.pesitwizard.server.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LogicalFileConfig Tests")
class LogicalFileConfigTest {

    @Test
    @DisplayName("should have default values with builder")
    void shouldHaveDefaultValuesWithBuilder() {
        LogicalFileConfig config = LogicalFileConfig.builder().build();

        assertTrue(config.isEnabled());
        assertEquals(LogicalFileConfig.Direction.BOTH, config.getDirection());
        assertEquals("${filename}_${timestamp}", config.getReceiveFilenamePattern());
        assertFalse(config.isOverwrite());
        assertEquals(0, config.getMaxFileSize());
        assertEquals(0, config.getAllowedRecordFormats().length);
        assertEquals(0, config.getFileType());
    }

    @Test
    @DisplayName("canReceive should return true for RECEIVE direction")
    void canReceiveShouldReturnTrueForReceiveDirection() {
        LogicalFileConfig config = LogicalFileConfig.builder()
                .direction(LogicalFileConfig.Direction.RECEIVE)
                .build();

        assertTrue(config.canReceive());
        assertFalse(config.canSend());
    }

    @Test
    @DisplayName("canSend should return true for SEND direction")
    void canSendShouldReturnTrueForSendDirection() {
        LogicalFileConfig config = LogicalFileConfig.builder()
                .direction(LogicalFileConfig.Direction.SEND)
                .build();

        assertTrue(config.canSend());
        assertFalse(config.canReceive());
    }

    @Test
    @DisplayName("canReceive and canSend should return true for BOTH direction")
    void canReceiveAndSendShouldReturnTrueForBothDirection() {
        LogicalFileConfig config = LogicalFileConfig.builder()
                .direction(LogicalFileConfig.Direction.BOTH)
                .build();

        assertTrue(config.canReceive());
        assertTrue(config.canSend());
    }

    @Test
    @DisplayName("should generate receive filename with default pattern")
    void shouldGenerateReceiveFilenameWithDefaultPattern() {
        LogicalFileConfig config = LogicalFileConfig.builder().build();

        String filename = config.generateReceiveFilename("DATA.DAT", 123);

        assertTrue(filename.startsWith("DATA.DAT_"));
        assertTrue(filename.length() > "DATA.DAT_".length());
    }

    @Test
    @DisplayName("should generate receive filename with custom pattern")
    void shouldGenerateReceiveFilenameWithCustomPattern() {
        LogicalFileConfig config = LogicalFileConfig.builder()
                .receiveFilenamePattern("${filename}_TID${transferId}")
                .build();

        String filename = config.generateReceiveFilename("TEST.TXT", 456);

        assertEquals("TEST.TXT_TID456", filename);
    }

    @Test
    @DisplayName("should handle null virtual filename")
    void shouldHandleNullVirtualFilename() {
        LogicalFileConfig config = LogicalFileConfig.builder()
                .receiveFilenamePattern("${filename}_${transferId}")
                .build();

        String filename = config.generateReceiveFilename(null, 789);

        assertEquals("file_789", filename);
    }

    @Test
    @DisplayName("should handle null or empty pattern")
    void shouldHandleNullOrEmptyPattern() {
        LogicalFileConfig config = LogicalFileConfig.builder()
                .receiveFilenamePattern(null)
                .build();

        String filename = config.generateReceiveFilename("DATA", 1);
        assertTrue(filename.startsWith("DATA_"));

        config.setReceiveFilenamePattern("");
        filename = config.generateReceiveFilename("DATA", 1);
        assertTrue(filename.startsWith("DATA_"));
    }

    @Test
    @DisplayName("should replace date placeholder")
    void shouldReplaceDatePlaceholder() {
        LogicalFileConfig config = LogicalFileConfig.builder()
                .receiveFilenamePattern("${filename}_${date}")
                .build();

        String filename = config.generateReceiveFilename("LOG", 1);
        String today = java.time.LocalDate.now().toString();

        assertEquals("LOG_" + today, filename);
    }

    @Test
    @DisplayName("should replace time placeholder")
    void shouldReplaceTimePlaceholder() {
        LogicalFileConfig config = LogicalFileConfig.builder()
                .receiveFilenamePattern("${filename}_${time}")
                .build();

        String filename = config.generateReceiveFilename("LOG", 1);

        assertTrue(filename.startsWith("LOG_"));
        assertTrue(filename.contains("-")); // Time format uses dashes instead of colons
    }

    @Test
    @DisplayName("should store all config attributes")
    void shouldStoreAllConfigAttributes() {
        LogicalFileConfig config = LogicalFileConfig.builder()
                .id("FILE1")
                .description("Test file")
                .enabled(false)
                .direction(LogicalFileConfig.Direction.RECEIVE)
                .receiveDirectory("/data/in")
                .sendDirectory("/data/out")
                .receiveFilenamePattern("${filename}")
                .overwrite(true)
                .maxFileSize(1024000L)
                .allowedRecordFormats(new int[] { 0, 1 })
                .fileType(2)
                .build();

        assertEquals("FILE1", config.getId());
        assertEquals("Test file", config.getDescription());
        assertFalse(config.isEnabled());
        assertEquals(LogicalFileConfig.Direction.RECEIVE, config.getDirection());
        assertEquals("/data/in", config.getReceiveDirectory());
        assertEquals("/data/out", config.getSendDirectory());
        assertEquals("${filename}", config.getReceiveFilenamePattern());
        assertTrue(config.isOverwrite());
        assertEquals(1024000L, config.getMaxFileSize());
        assertEquals(2, config.getAllowedRecordFormats().length);
        assertEquals(2, config.getFileType());
    }
}
