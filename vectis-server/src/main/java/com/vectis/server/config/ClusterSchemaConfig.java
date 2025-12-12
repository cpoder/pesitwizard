package com.vectis.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for cluster-specific database schema.
 * Each pesit-server instance belongs to a cluster and uses that cluster's
 * schema.
 */
@Slf4j
@Configuration
@Getter
public class ClusterSchemaConfig {

    /**
     * The cluster ID this server instance belongs to.
     * Set via CLUSTER_ID environment variable.
     */
    @Value("${pesit.cluster.id:}")
    private String clusterId;

    /**
     * Get the schema name for this cluster.
     * Format: cluster_{sanitized_id}
     */
    public String getSchemaName() {
        if (clusterId == null || clusterId.isEmpty()) {
            return "public"; // Default schema if no cluster ID
        }
        // Sanitize cluster ID to be a valid PostgreSQL identifier
        String sanitized = clusterId.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        return "cluster_" + sanitized;
    }

    @PostConstruct
    public void init() {
        if (clusterId == null || clusterId.isEmpty()) {
            log.warn("No CLUSTER_ID configured - using default 'public' schema. " +
                    "Set PESIT_CLUSTER_ID environment variable for multi-tenant mode.");
        } else {
            log.info("Cluster ID: {} -> Schema: {}", clusterId, getSchemaName());
        }
    }
}
