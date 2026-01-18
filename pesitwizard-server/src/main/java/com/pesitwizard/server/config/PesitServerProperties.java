package com.pesitwizard.server.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for PeSIT server
 */
@Data
@Component
@ConfigurationProperties(prefix = "pesit.server")
public class PesitServerProperties {

    /** TCP port to listen on */
    private int port = 5000;

    /** TLS port to listen on (when SSL enabled) */
    private int tlsPort = 5001;

    /** Server identifier (PI 4) */
    private String serverId = "PESIT_SERVER";

    /** Protocol version (PI 6) - default is version 2 for Hors-SIT */
    private int protocolVersion = 2;

    /** Maximum connections allowed */
    private int maxConnections = 100;

    /** Connection timeout in milliseconds */
    private int connectionTimeout = 30000;

    /** Read timeout in milliseconds */
    private int readTimeout = 60000;

    /** Default directory for received files (used if no logical file match) */
    private String receiveDirectory = "/data/received";

    /** Default directory for files to send (used if no logical file match) */
    private String sendDirectory = "/data/send";

    /** Maximum entity size (PI 25) */
    private int maxEntitySize = 4096;

    /** Enable sync points by default */
    private boolean syncPointsEnabled = true;

    /** Sync point interval in KB (D2-222 validation). 0 = no limit */
    private int syncIntervalKb = 0; // 0 = disabled, let client handle sync points

    /** Enable resynchronization by default */
    private boolean resyncEnabled = true;

    /** Enable CRC checking */
    private boolean crcEnabled = false;

    /** Require partner to be configured (if false, unknown partners are allowed) */
    private boolean strictPartnerCheck = true;

    /** Require logical file to be configured (if false, any filename is allowed) */
    private boolean strictFileCheck = false;

    /** Enable session recording for debugging/testing */
    private boolean sessionRecordingEnabled = false;

    /** Directory to save recorded sessions */
    private String sessionRecordingDirectory = "recorded-sessions";

    /** Configured partners (key = partner ID) */
    private Map<String, PartnerConfig> partners = new HashMap<>();

    /** Configured logical files (key = logical file ID / filename pattern) */
    private Map<String, LogicalFileConfig> files = new HashMap<>();

    /**
     * Get partner configuration by ID
     * 
     * @return PartnerConfig or null if not found
     */
    public PartnerConfig getPartner(String partnerId) {
        if (partnerId == null)
            return null;

        // Try exact match first
        PartnerConfig config = partners.get(partnerId);
        if (config != null)
            return config;

        // Try case-insensitive match
        for (Map.Entry<String, PartnerConfig> entry : partners.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(partnerId)) {
                return entry.getValue();
            }
            if (entry.getValue().getId() != null &&
                    entry.getValue().getId().equalsIgnoreCase(partnerId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Get logical file configuration by filename
     * 
     * @return LogicalFileConfig or null if not found
     */
    public LogicalFileConfig getLogicalFile(String filename) {
        if (filename == null)
            return null;

        // Try exact match first
        LogicalFileConfig config = files.get(filename);
        if (config != null)
            return config;

        // Try case-insensitive match
        for (Map.Entry<String, LogicalFileConfig> entry : files.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(filename)) {
                return entry.getValue();
            }
            if (entry.getValue().getId() != null &&
                    entry.getValue().getId().equalsIgnoreCase(filename)) {
                return entry.getValue();
            }
        }

        // Try pattern match (for wildcards like FILE*)
        for (Map.Entry<String, LogicalFileConfig> entry : files.entrySet()) {
            String pattern = entry.getKey().replace("*", ".*");
            if (filename.matches(pattern)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Check if a partner exists
     */
    public boolean hasPartner(String partnerId) {
        return getPartner(partnerId) != null;
    }

    /**
     * Check if a logical file exists
     */
    public boolean hasLogicalFile(String filename) {
        return getLogicalFile(filename) != null;
    }
}
