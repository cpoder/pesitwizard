package com.vectis.server.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for a logical file (virtual file mapping)
 * Maps PeSIT virtual filenames to local file system paths
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogicalFileConfig {

    /** Logical file identifier (matches PI 12 - Filename) */
    private String id;

    /** Description of this logical file */
    private String description;

    /** Whether this logical file is enabled */
    @Builder.Default
    private boolean enabled = true;

    /** Direction: RECEIVE (for CREATE), SEND (for SELECT), BOTH */
    @Builder.Default
    private Direction direction = Direction.BOTH;

    /** Local directory for received files (for RECEIVE direction) */
    private String receiveDirectory;

    /** Local directory/file for sending (for SEND direction) */
    private String sendDirectory;

    /** Filename pattern for received files (supports placeholders) */
    @Builder.Default
    private String receiveFilenamePattern = "${filename}_${timestamp}";

    /** Whether to overwrite existing files */
    @Builder.Default
    private boolean overwrite = false;

    /** Maximum file size in bytes (0 = unlimited) */
    @Builder.Default
    private long maxFileSize = 0;

    /** Allowed record formats (empty = all) */
    @Builder.Default
    private int[] allowedRecordFormats = {};

    /** File type filter (PI 11) - 0 = any */
    @Builder.Default
    private int fileType = 0;

    public enum Direction {
        RECEIVE, // Only for CREATE (receiving files)
        SEND, // Only for SELECT (sending files)
        BOTH // Both directions
    }

    /**
     * Check if this logical file can receive data (CREATE)
     */
    public boolean canReceive() {
        return direction == Direction.RECEIVE || direction == Direction.BOTH;
    }

    /**
     * Check if this logical file can send data (SELECT)
     */
    public boolean canSend() {
        return direction == Direction.SEND || direction == Direction.BOTH;
    }

    /**
     * Generate the local filename for a received file
     */
    public String generateReceiveFilename(String virtualFilename, int transferId) {
        String pattern = receiveFilenamePattern;
        if (pattern == null || pattern.isEmpty()) {
            pattern = "${filename}_${timestamp}";
        }

        return pattern
                .replace("${filename}", virtualFilename != null ? virtualFilename : "file")
                .replace("${transferId}", String.valueOf(transferId))
                .replace("${timestamp}", String.valueOf(System.currentTimeMillis()))
                .replace("${date}", java.time.LocalDate.now().toString())
                .replace("${time}", java.time.LocalTime.now().toString().replace(":", "-"));
    }
}
