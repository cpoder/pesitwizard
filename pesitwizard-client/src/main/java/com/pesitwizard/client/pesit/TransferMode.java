package com.pesitwizard.client.pesit;

/**
 * Transfer mode presets for PeSIT transfers.
 *
 * <p>SIMPLE mode auto-configures most parameters for ease of use. ADVANCED mode allows full control
 * over all PeSIT parameters.
 */
public enum TransferMode {

    /**
     * Simple mode - automatic configuration - Sync points: auto-calculated based on file size -
     * Record length: uses server default - Entity size: negotiated automatically - Compression:
     * disabled - Resync: disabled
     */
    SIMPLE("Simple", "Automatic configuration for most use cases"),

    /**
     * Advanced mode - full parameter control - All PI parameters can be explicitly set - Sync
     * points interval configurable - Record length (PI_32) configurable - Entity size (PI_25)
     * configurable - Compression (PI_21) configurable - Resync (PI_23) configurable
     */
    ADVANCED("Advanced", "Full control over all PeSIT parameters");

    private final String displayName;
    private final String description;

    TransferMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get default sync point interval for this mode based on file size.
     *
     * @param fileSize File size in bytes
     * @return Sync interval in bytes, or 0 if sync points should be disabled
     */
    public long getDefaultSyncInterval(long fileSize) {
        if (this == SIMPLE) {
            // Auto-calculate based on file size
            if (fileSize < 1024 * 1024) {
                return 0; // < 1MB: no sync points
            } else if (fileSize < 10 * 1024 * 1024) {
                return 256 * 1024; // 1-10MB: every 256KB
            } else if (fileSize < 100 * 1024 * 1024) {
                return 1024 * 1024; // 10-100MB: every 1MB
            } else {
                return 5 * 1024 * 1024; // > 100MB: every 5MB
            }
        }
        // ADVANCED mode: return 0, let user configure
        return 0;
    }

    /**
     * Get default record length for this mode.
     *
     * @return Default record length (PI_32), or 0 to use server default
     */
    public int getDefaultRecordLength() {
        if (this == SIMPLE) {
            return 0; // Use server default
        }
        return 0; // ADVANCED: let user configure
    }

    /**
     * Get default entity size for this mode.
     *
     * @return Default max entity size (PI_25), or 0 to negotiate max
     */
    public int getDefaultEntitySize() {
        if (this == SIMPLE) {
            return 65535; // Request maximum, let server negotiate down
        }
        return 0; // ADVANCED: let user configure
    }

    /** Whether sync points should be enabled by default for this mode. */
    public boolean isSyncPointsEnabledByDefault(long fileSize) {
        if (this == SIMPLE) {
            return fileSize >= 1024 * 1024; // Enable for files >= 1MB
        }
        return false; // ADVANCED: let user configure
    }

    /** Whether resync should be enabled by default for this mode. */
    public boolean isResyncEnabledByDefault() {
        return false; // Disabled by default in both modes
    }

    /** Whether compression should be enabled by default for this mode. */
    public boolean isCompressionEnabledByDefault() {
        return false; // Disabled by default in both modes
    }
}
