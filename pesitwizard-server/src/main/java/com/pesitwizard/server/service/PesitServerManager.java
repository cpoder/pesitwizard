package com.pesitwizard.server.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pesitwizard.server.cluster.ClusterEvent;
import com.pesitwizard.server.cluster.ClusterEventListener;
import com.pesitwizard.server.cluster.ClusterProvider;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.config.SslProperties;
import com.pesitwizard.server.entity.PesitServerConfig;
import com.pesitwizard.server.entity.PesitServerConfig.ServerStatus;
import com.pesitwizard.server.handler.PesitSessionHandler;
import com.pesitwizard.server.repository.PesitServerConfigRepository;
import com.pesitwizard.server.ssl.SslContextFactory;
import com.pesitwizard.server.util.PesitIdValidator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing multiple PeSIT server instances.
 * Handles creation, starting, stopping, and configuration of server instances.
 * 
 * Listens for cluster events to handle leader election:
 * - When this node becomes leader, auto-start servers
 * - When this node loses leadership, stop servers (another node will take over)
 */
@SuppressWarnings("null") // Spring Data JPA methods never return null for save/findById
@Slf4j
@Service
@RequiredArgsConstructor
public class PesitServerManager implements ClusterEventListener {

    private final PesitServerConfigRepository configRepository;
    private final ClusterProvider clusterProvider;
    private final PesitSessionHandler sessionHandler;
    private final FileSystemService fileSystemService;
    private final SslProperties sslProperties;
    private final SslContextFactory sslContextFactory;

    // Map of running server instances: serverId -> PesitServerInstance
    private final Map<String, PesitServerInstance> runningServers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Register as cluster event listener to handle leader changes
        clusterProvider.addListener(this);

        // If cluster is not enabled (standalone mode), clusterService.isLeader()
        // returns true immediately
        // In cluster mode, we wait for the BECAME_LEADER event before starting servers
        if (!clusterProvider.isClusterEnabled()) {
            log.info("Standalone mode - auto-starting servers");
            autoStartServers();
        } else if (clusterProvider.isLeader()) {
            // Already leader at init time (we missed the event)
            log.info("Already cluster leader at init time - auto-starting servers");
            autoStartServers();
        } else {
            log.info("Cluster mode enabled - waiting for leader election before starting servers");
        }
    }

    @Override
    public void onClusterEvent(ClusterEvent event) {
        switch (event.getType()) {
            case BECAME_LEADER -> {
                log.info("This node became the cluster leader - auto-starting servers");
                autoStartServers();
            }
            case LOST_LEADERSHIP -> {
                log.info("This node lost cluster leadership - stopping servers");
                stopAllServers();
            }
            default -> {
                // Ignore other events
            }
        }
    }

    private void autoStartServers() {
        List<PesitServerConfig> autoStartServers = configRepository.findByAutoStartTrue();
        for (PesitServerConfig config : autoStartServers) {
            try {
                startServer(config.getServerId());
                log.info("Auto-started server: {}", config.getServerId());
            } catch (Exception e) {
                log.error("Failed to auto-start server {}: {}", config.getServerId(), e.getMessage());
            }
        }
    }

    private void stopAllServers() {
        for (String serverId : runningServers.keySet()) {
            try {
                stopServer(serverId);
                log.info("Stopped server due to leadership loss: {}", serverId);
            } catch (Exception e) {
                log.error("Error stopping server {}: {}", serverId, e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down all PeSIT servers...");
        for (String serverId : runningServers.keySet()) {
            try {
                stopServer(serverId);
            } catch (Exception e) {
                log.error("Error stopping server {}: {}", serverId, e.getMessage());
            }
        }
    }

    /**
     * Create a new server configuration
     */
    @Transactional
    public PesitServerConfig createServer(PesitServerConfig config) {
        // Validate server ID (max 8 chars, uppercase alphanumeric only)
        PesitIdValidator.validateOrThrow(config.getServerId(), "Server");

        // Validate unique constraints
        if (configRepository.existsByServerId(config.getServerId())) {
            throw new IllegalArgumentException("Server ID already exists: " + config.getServerId());
        }
        if (configRepository.existsByPort(config.getPort())) {
            throw new IllegalArgumentException("Port already in use: " + config.getPort());
        }

        config.setStatus(ServerStatus.STOPPED);
        return configRepository.save(config);
    }

    /**
     * Update an existing server configuration
     */
    @Transactional
    public PesitServerConfig updateServer(String serverId, PesitServerConfig updates) {
        PesitServerConfig existing = configRepository.findByServerId(serverId)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + serverId));

        // Don't allow updates while server is running
        if (runningServers.containsKey(serverId)) {
            throw new IllegalStateException("Cannot update running server. Stop it first.");
        }

        // Update fields (preserve id and serverId)
        if (updates.getPort() != existing.getPort()) {
            if (configRepository.existsByPort(updates.getPort())) {
                throw new IllegalArgumentException("Port already in use: " + updates.getPort());
            }
            existing.setPort(updates.getPort());
        }

        existing.setBindAddress(updates.getBindAddress());
        existing.setProtocolVersion(updates.getProtocolVersion());
        existing.setMaxConnections(updates.getMaxConnections());
        existing.setConnectionTimeout(updates.getConnectionTimeout());
        existing.setReadTimeout(updates.getReadTimeout());
        existing.setReceiveDirectory(updates.getReceiveDirectory());
        existing.setSendDirectory(updates.getSendDirectory());
        existing.setMaxEntitySize(updates.getMaxEntitySize());
        existing.setSyncPointsEnabled(updates.isSyncPointsEnabled());
        existing.setResyncEnabled(updates.isResyncEnabled());
        existing.setStrictPartnerCheck(updates.isStrictPartnerCheck());
        existing.setStrictFileCheck(updates.isStrictFileCheck());
        existing.setAutoStart(updates.isAutoStart());

        return configRepository.save(existing);
    }

    /**
     * Delete a server configuration
     */
    @Transactional
    public void deleteServer(String serverId) {
        PesitServerConfig config = configRepository.findByServerId(serverId)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + serverId));

        // Stop if running
        if (runningServers.containsKey(serverId)) {
            stopServer(serverId);
        }

        configRepository.delete(config);
        log.info("Deleted server configuration: {}", serverId);
    }

    /**
     * Start a server instance
     */
    @Transactional
    public void startServer(String serverId) {
        PesitServerConfig config = configRepository.findByServerId(serverId)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + serverId));

        if (runningServers.containsKey(serverId)) {
            throw new IllegalStateException("Server already running on this node: " + serverId);
        }

        // In cluster mode, try to acquire ownership
        if (!clusterProvider.acquireServerOwnership(serverId)) {
            String owner = clusterProvider.getServerOwner(serverId);
            throw new IllegalStateException("Server '" + serverId + "' is already running on node '" + owner + "'");
        }

        try {
            config.setStatus(ServerStatus.STARTING);
            configRepository.save(config);

            // Create properties from config
            PesitServerProperties properties = createPropertiesFromConfig(config);

            // Validate directories before starting
            validateServerDirectories(config);

            // Use injected session handler (Spring-managed with all dependencies)
            // Create and start server instance with SSL/mTLS support
            PesitServerInstance instance = new PesitServerInstance(
                    config, properties, sessionHandler, sslProperties, sslContextFactory);
            instance.start();

            runningServers.put(serverId, instance);

            config.setStatus(ServerStatus.RUNNING);
            config.setLastStartedAt(Instant.now());
            configRepository.save(config);

            log.info("Started server {} on port {}", serverId, config.getPort());

        } catch (Exception e) {
            config.setStatus(ServerStatus.ERROR);
            configRepository.save(config);
            throw new RuntimeException("Failed to start server: " + e.getMessage(), e);
        }
    }

    /**
     * Stop a server instance
     */
    @Transactional
    public void stopServer(String serverId) {
        PesitServerConfig config = configRepository.findByServerId(serverId)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + serverId));

        PesitServerInstance instance = runningServers.get(serverId);
        if (instance == null) {
            throw new IllegalStateException("Server not running: " + serverId);
        }

        try {
            config.setStatus(ServerStatus.STOPPING);
            configRepository.save(config);

            instance.stop();
            runningServers.remove(serverId);

            // Release cluster ownership
            clusterProvider.releaseServerOwnership(serverId);

            config.setStatus(ServerStatus.STOPPED);
            config.setLastStoppedAt(Instant.now());
            configRepository.save(config);

            log.info("Stopped server {}", serverId);

        } catch (Exception e) {
            config.setStatus(ServerStatus.ERROR);
            configRepository.save(config);
            throw new RuntimeException("Failed to stop server: " + e.getMessage(), e);
        }
    }

    /**
     * Get server configuration by ID
     */
    public Optional<PesitServerConfig> getServer(String serverId) {
        return configRepository.findByServerId(serverId);
    }

    /**
     * Get all server configurations
     */
    public List<PesitServerConfig> getAllServers() {
        return configRepository.findAll();
    }

    /**
     * Get server status
     */
    public ServerStatus getServerStatus(String serverId) {
        PesitServerInstance instance = runningServers.get(serverId);
        if (instance != null && instance.isRunning()) {
            return ServerStatus.RUNNING;
        }
        return configRepository.findByServerId(serverId)
                .map(PesitServerConfig::getStatus)
                .orElse(null);
    }

    /**
     * Get active connections for a server
     */
    public int getActiveConnections(String serverId) {
        PesitServerInstance instance = runningServers.get(serverId);
        return instance != null ? instance.getActiveConnections() : 0;
    }

    /**
     * Check if a server is running
     */
    public boolean isServerRunning(String serverId) {
        PesitServerInstance instance = runningServers.get(serverId);
        return instance != null && instance.isRunning();
    }

    /**
     * Get all running server instances
     */
    public List<PesitServerInstance> getRunningServers() {
        return runningServers.values().stream()
                .filter(PesitServerInstance::isRunning)
                .toList();
    }

    /**
     * Get total active connections across all servers
     */
    public int getActiveConnectionCount() {
        return runningServers.values().stream()
                .filter(PesitServerInstance::isRunning)
                .mapToInt(PesitServerInstance::getActiveConnections)
                .sum();
    }

    /**
     * Validate server directories before starting.
     * Checks that receive and send directories are accessible.
     */
    private void validateServerDirectories(PesitServerConfig config) {
        String serverDesc = "Server '" + config.getServerId() + "'";

        // Validate receive directory
        if (config.getReceiveDirectory() != null && !config.getReceiveDirectory().isBlank()) {
            var result = fileSystemService.validateReceiveDirectory(
                    config.getReceiveDirectory(), serverDesc);
            if (!result.success()) {
                throw new IllegalStateException(result.errorMessage());
            }
        }

        // Validate send directory (only if configured)
        if (config.getSendDirectory() != null && !config.getSendDirectory().isBlank()) {
            var result = fileSystemService.validateSendDirectory(
                    config.getSendDirectory(), serverDesc);
            if (!result.success()) {
                log.warn("{}: send directory validation failed: {}", serverDesc, result.errorMessage());
                // Don't fail startup for send directory - it may not be needed
            }
        }

        log.info("{}: directory validation completed", serverDesc);
    }

    /**
     * Create PesitServerProperties from database config
     */
    private PesitServerProperties createPropertiesFromConfig(PesitServerConfig config) {
        PesitServerProperties props = new PesitServerProperties();
        props.setServerId(config.getServerId());
        props.setPort(config.getPort());
        props.setProtocolVersion(config.getProtocolVersion());
        props.setMaxConnections(config.getMaxConnections());
        props.setConnectionTimeout(config.getConnectionTimeout());
        props.setReadTimeout(config.getReadTimeout());
        props.setReceiveDirectory(config.getReceiveDirectory());
        props.setSendDirectory(config.getSendDirectory());
        props.setMaxEntitySize(config.getMaxEntitySize());
        props.setSyncPointsEnabled(config.isSyncPointsEnabled());
        props.setSyncIntervalKb(config.getSyncIntervalKb());
        log.debug("Created properties from config: serverId={}, syncPointsEnabled={}, syncIntervalKb={}",
                config.getServerId(), config.isSyncPointsEnabled(), config.getSyncIntervalKb());
        props.setResyncEnabled(config.isResyncEnabled());
        props.setStrictPartnerCheck(config.isStrictPartnerCheck());
        props.setStrictFileCheck(config.isStrictFileCheck());
        return props;
    }
}
