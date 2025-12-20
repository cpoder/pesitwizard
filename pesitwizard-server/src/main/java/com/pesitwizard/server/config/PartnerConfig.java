package com.pesitwizard.server.config;

import lombok.Data;

/**
 * Configuration for a PeSIT partner (remote system that can connect)
 */
@Data
public class PartnerConfig {

    /** Partner identifier (must match PI 3 - Demandeur) */
    private String id;

    /** Partner description */
    private String description;

    /** Password for access control (PI 5) - empty means no password required */
    private String password = "";

    /** Whether this partner is enabled */
    private boolean enabled = true;

    /** Allowed access types: READ, WRITE, BOTH */
    private AccessType accessType = AccessType.BOTH;

    /** Maximum concurrent connections from this partner */
    private int maxConnections = 10;

    /** List of logical files this partner can access (empty = all) */
    private String[] allowedFiles = {};

    public enum AccessType {
        READ, // Partner can only read (SELECT)
        WRITE, // Partner can only write (CREATE)
        BOTH // Partner can read and write
    }

    /**
     * Check if partner can perform write operations
     */
    public boolean canWrite() {
        return accessType == AccessType.WRITE || accessType == AccessType.BOTH;
    }

    /**
     * Check if partner can perform read operations
     */
    public boolean canRead() {
        return accessType == AccessType.READ || accessType == AccessType.BOTH;
    }

    /**
     * Check if partner can access a specific file.
     * Patterns use glob-style wildcards: * matches any characters.
     */
    public boolean canAccessFile(String filename) {
        if (allowedFiles == null || allowedFiles.length == 0) {
            return true; // No restrictions
        }
        for (String allowed : allowedFiles) {
            if (allowed == null)
                continue;
            String pattern = allowed.trim();
            if (pattern.isEmpty())
                continue;
            // Convert glob pattern to regex: * -> .*
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            if (pattern.equals(filename) || filename.matches(regex)) {
                return true;
            }
        }
        return false;
    }
}
