package com.pesitwizard.server.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a virtual file (logical file mapping).
 * Maps PeSIT virtual filenames to local file system paths.
 */
@Entity
@Table(name = "virtual_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualFile {

    /**
     * Virtual file identifier - matches PI 12 (Filename)
     * Can include wildcards like FILE* or DATA_*
     */
    @Id
    @Column(length = 128)
    private String id;

    /**
     * Description of this virtual file
     */
    private String description;

    /**
     * Whether this virtual file is enabled
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Direction: RECEIVE (for CREATE), SEND (for SELECT), BOTH
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Direction direction = Direction.BOTH;

    /**
     * Local directory for received files (for RECEIVE direction)
     */
    private String receiveDirectory;

    /**
     * Local directory/file for sending (for SEND direction)
     */
    private String sendDirectory;

    /**
     * Filename pattern for received files (supports placeholders)
     * Placeholders: ${virtualFile}, ${timestamp}, ${date}, ${time}, ${transferId}
     */
    @Builder.Default
    private String receiveFilenamePattern = "${virtualFile}_${timestamp}";

    /**
     * Whether to overwrite existing files
     */
    @Builder.Default
    private boolean overwrite = false;

    /**
     * Maximum file size in bytes (0 = unlimited)
     */
    @Builder.Default
    private long maxFileSize = 0;

    /**
     * File type filter (PI 11) - 0 = any
     */
    @Builder.Default
    private int fileType = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Direction {
        RECEIVE, // Only for CREATE (receiving files)
        SEND, // Only for SELECT (sending files)
        BOTH // Both directions
    }

    /**
     * Check if this virtual file can receive data (CREATE)
     */
    public boolean canReceive() {
        return direction == Direction.RECEIVE || direction == Direction.BOTH;
    }

    /**
     * Check if this virtual file can send data (SELECT)
     */
    public boolean canSend() {
        return direction == Direction.SEND || direction == Direction.BOTH;
    }

    /**
     * Check if a filename matches this virtual file pattern
     */
    public boolean matches(String filename) {
        if (filename == null)
            return false;
        if (id.equals(filename))
            return true;
        // Support wildcards
        String pattern = id.replace("*", ".*");
        return filename.matches(pattern);
    }

    /**
     * Generate the local filename for a received file
     */
    public String generateReceiveFilename(String virtualFilename, long transferId) {
        String pattern = receiveFilenamePattern;
        if (pattern == null || pattern.isEmpty()) {
            pattern = "${virtualFile}_${timestamp}";
        }

        return pattern
                .replace("${virtualFile}", virtualFilename != null ? virtualFilename : "file")
                .replace("${transferId}", String.valueOf(transferId))
                .replace("${timestamp}", String.valueOf(System.currentTimeMillis()))
                .replace("${date}", java.time.LocalDate.now().toString())
                .replace("${time}", java.time.LocalTime.now().toString().replace(":", "-"));
    }
}
