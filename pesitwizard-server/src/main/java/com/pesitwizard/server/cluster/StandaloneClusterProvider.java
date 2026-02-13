package com.pesitwizard.server.cluster;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Standalone implementation of ClusterProvider. Used when clustering is disabled (default OSS
 * mode). This node is always the leader and owns all servers.
 */
@Slf4j
@Service
@ConditionalOnProperty(
        name = "pesitwizard.cluster.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class StandaloneClusterProvider implements ClusterProvider {

    @Value("${pesitwizard.cluster.node-name:standalone}")
    private String nodeName;

    private final Map<String, String> serverOwnership = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("Cluster mode disabled. Running in standalone mode as '{}'.", nodeName);
    }

    @Override
    public boolean isLeader() {
        return true;
    }

    @Override
    public boolean isClusterEnabled() {
        return false;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public int getClusterSize() {
        return 1;
    }

    @Override
    public String getNodeName() {
        return nodeName;
    }

    @Override
    public List<String> getClusterMembers() {
        return List.of(nodeName);
    }

    @Override
    public void addListener(ClusterEventListener listener) {
        // No-op in standalone mode - no cluster events to listen to
    }

    @Override
    public void removeListener(ClusterEventListener listener) {
        // No-op in standalone mode
    }

    @Override
    public boolean acquireServerOwnership(String serverId) {
        serverOwnership.put(serverId, nodeName);
        log.debug("Acquired ownership of server '{}' (standalone mode)", serverId);
        return true;
    }

    @Override
    public void releaseServerOwnership(String serverId) {
        serverOwnership.remove(serverId);
        log.debug("Released ownership of server '{}' (standalone mode)", serverId);
    }

    @Override
    public boolean ownsServer(String serverId) {
        return true;
    }

    @Override
    public String getServerOwner(String serverId) {
        return serverOwnership.getOrDefault(serverId, nodeName);
    }

    @Override
    public Map<String, String> getAllServerOwnership() {
        return Map.copyOf(serverOwnership);
    }

    @Override
    public void broadcast(ClusterMessage message) {
        // No-op in standalone mode - no other nodes to broadcast to
    }
}
