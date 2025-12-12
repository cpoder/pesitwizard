package com.vectis.server.cluster;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.util.Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * JGroups-based cluster service for High Availability.
 * Provides:
 * - Cluster membership management
 * - Leader election (coordinator is the leader)
 * - State replication across nodes
 * - Cluster-wide messaging
 */
@Slf4j
@Service
public class ClusterService implements Receiver, Closeable {

    private static final String CLUSTER_NAME = "pesit-cluster";

    @Value("${pesit.cluster.enabled:false}")
    private boolean clusterEnabled;

    @Value("${pesit.cluster.node-name:#{T(java.util.UUID).randomUUID().toString().substring(0,8)}}")
    private String nodeName;

    @Value("${pesit.cluster.config:udp.xml}")
    private String jgroupsConfig;

    private JChannel channel;

    @Getter
    private volatile boolean leader = false;

    @Getter
    private volatile boolean connected = false;

    private final List<ClusterEventListener> listeners = new CopyOnWriteArrayList<>();

    // Shared cluster state: serverId -> node that owns it
    private final Map<String, String> serverOwnership = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!clusterEnabled) {
            log.info("Cluster mode disabled. Running in standalone mode.");
            leader = true; // Standalone node is always leader
            return;
        }

        try {
            log.info("Initializing cluster node: {}", nodeName);

            // Create JGroups channel
            channel = new JChannel(jgroupsConfig);
            channel.setName(nodeName);
            channel.setReceiver(this);
            channel.setDiscardOwnMessages(true);

            // Connect to cluster
            channel.connect(CLUSTER_NAME);
            connected = true;

            // Get initial state from existing members
            channel.getState(null, 10000);

            log.info("Connected to cluster '{}' as node '{}'", CLUSTER_NAME, nodeName);
            logClusterView();

        } catch (Exception e) {
            log.error("Failed to initialize cluster: {}", e.getMessage(), e);
            // Fall back to standalone mode
            leader = true;
            connected = false;
        }
    }

    @PreDestroy
    @Override
    public void close() {
        if (channel != null && channel.isConnected()) {
            log.info("Leaving cluster...");

            // Release ownership of all servers on this node
            releaseAllServerOwnership();

            channel.close();
            connected = false;
            log.info("Disconnected from cluster");
        }
    }

    /**
     * Called when cluster view changes (member join/leave)
     */
    @Override
    public void viewAccepted(View newView) {
        log.info("Cluster view changed: {}", newView);

        Address coordinator = newView.getCoord();
        Address localAddress = channel.getAddress();

        boolean wasLeader = leader;
        leader = coordinator != null && coordinator.equals(localAddress);

        if (leader && !wasLeader) {
            log.info("This node is now the LEADER");
            notifyListeners(ClusterEvent.becameLeader(nodeName));
        } else if (!leader && wasLeader) {
            log.info("This node is no longer the leader");
            notifyListeners(ClusterEvent.lostLeadership(nodeName));
        }

        // Clean up ownership for nodes that left
        Set<String> currentMembers = newView.getMembers().stream()
                .map(Address::toString)
                .collect(Collectors.toSet());

        serverOwnership.entrySet().removeIf(entry -> {
            if (!currentMembers.contains(entry.getValue())) {
                log.info("Releasing ownership of server '{}' (node '{}' left)",
                        entry.getKey(), entry.getValue());
                return true;
            }
            return false;
        });

        notifyListeners(ClusterEvent.viewChanged(nodeName, newView.size(), leader));
        logClusterView();
    }

    /**
     * Called when a message is received from another node
     */
    @Override
    public void receive(Message msg) {
        try {
            Object payload = msg.getObject();
            if (payload instanceof ClusterMessage clusterMsg) {
                handleClusterMessage(clusterMsg, msg.getSrc());
            }
        } catch (Exception e) {
            log.error("Error processing cluster message: {}", e.getMessage(), e);
        }
    }

    /**
     * Called to get state for new joining node
     */
    @Override
    public void getState(OutputStream output) throws Exception {
        synchronized (serverOwnership) {
            Util.objectToStream(new ConcurrentHashMap<>(serverOwnership), new DataOutputStream(output));
        }
    }

    /**
     * Called when this node receives state from existing member
     */
    @Override
    public void setState(InputStream input) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> state = (Map<String, String>) Util.objectFromStream(new DataInputStream(input));
        synchronized (serverOwnership) {
            serverOwnership.clear();
            serverOwnership.putAll(state);
        }
        log.info("Received cluster state: {} server assignments", serverOwnership.size());
    }

    /**
     * Try to acquire ownership of a server (for starting it)
     */
    public boolean acquireServerOwnership(String serverId) {
        if (!clusterEnabled) {
            return true; // Standalone mode always succeeds
        }

        synchronized (serverOwnership) {
            String currentOwner = serverOwnership.get(serverId);
            if (currentOwner != null && !currentOwner.equals(nodeName)) {
                log.warn("Server '{}' is owned by node '{}'", serverId, currentOwner);
                return false;
            }

            serverOwnership.put(serverId, nodeName);
            broadcastOwnershipChange(serverId, nodeName, true);
            log.info("Acquired ownership of server '{}'", serverId);
            return true;
        }
    }

    /**
     * Release ownership of a server (when stopping it)
     */
    public void releaseServerOwnership(String serverId) {
        if (!clusterEnabled) {
            return;
        }

        synchronized (serverOwnership) {
            String currentOwner = serverOwnership.get(serverId);
            if (nodeName.equals(currentOwner)) {
                serverOwnership.remove(serverId);
                broadcastOwnershipChange(serverId, nodeName, false);
                log.info("Released ownership of server '{}'", serverId);
            }
        }
    }

    /**
     * Check if this node owns a server
     */
    public boolean ownsServer(String serverId) {
        if (!clusterEnabled) {
            return true;
        }
        return nodeName.equals(serverOwnership.get(serverId));
    }

    /**
     * Get the node that owns a server
     */
    public String getServerOwner(String serverId) {
        return serverOwnership.get(serverId);
    }

    /**
     * Get all server ownership mappings
     */
    public Map<String, String> getAllServerOwnership() {
        return Map.copyOf(serverOwnership);
    }

    /**
     * Get cluster members
     */
    public List<String> getClusterMembers() {
        if (!clusterEnabled || channel == null) {
            return List.of(nodeName);
        }
        return channel.getView().getMembers().stream()
                .map(Address::toString)
                .collect(Collectors.toList());
    }

    /**
     * Get cluster size
     */
    public int getClusterSize() {
        if (!clusterEnabled || channel == null) {
            return 1;
        }
        return channel.getView().size();
    }

    /**
     * Get this node's name
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Check if cluster mode is enabled
     */
    public boolean isClusterEnabled() {
        return clusterEnabled;
    }

    /**
     * Add a cluster event listener
     */
    public void addListener(ClusterEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a cluster event listener
     */
    public void removeListener(ClusterEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Broadcast a message to all cluster members
     */
    public void broadcast(ClusterMessage message) {
        if (!clusterEnabled || channel == null) {
            return;
        }
        try {
            channel.send(new ObjectMessage(null, message));
        } catch (Exception e) {
            log.error("Failed to broadcast message: {}", e.getMessage(), e);
        }
    }

    private void broadcastOwnershipChange(String serverId, String nodeId, boolean acquired) {
        broadcast(new ClusterMessage(
                acquired ? ClusterMessage.Type.SERVER_ACQUIRED : ClusterMessage.Type.SERVER_RELEASED,
                serverId,
                nodeId));
    }

    private void releaseAllServerOwnership() {
        synchronized (serverOwnership) {
            serverOwnership.entrySet().stream()
                    .filter(e -> nodeName.equals(e.getValue()))
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(this::releaseServerOwnership);
        }
    }

    private void handleClusterMessage(ClusterMessage msg, Address src) {
        log.debug("Received cluster message from {}: {}", src, msg);

        switch (msg.getType()) {
            case SERVER_ACQUIRED -> {
                serverOwnership.put(msg.getServerId(), msg.getNodeId());
                notifyListeners(ClusterEvent.serverAcquired(msg.getServerId(), msg.getNodeId()));
            }
            case SERVER_RELEASED -> {
                serverOwnership.remove(msg.getServerId());
                notifyListeners(ClusterEvent.serverReleased(msg.getServerId(), msg.getNodeId()));
            }
            case SERVER_STATE_CHANGED -> {
                notifyListeners(ClusterEvent.serverStateChanged(msg.getServerId(), msg.getNodeId()));
            }
        }
    }

    private void notifyListeners(ClusterEvent event) {
        for (ClusterEventListener listener : listeners) {
            try {
                listener.onClusterEvent(event);
            } catch (Exception e) {
                log.error("Error notifying cluster listener: {}", e.getMessage(), e);
            }
        }
    }

    private void logClusterView() {
        if (channel != null && channel.isConnected()) {
            View view = channel.getView();
            log.info("Cluster members ({}):", view.size());
            for (Address member : view.getMembers()) {
                String marker = member.equals(view.getCoord()) ? " [LEADER]" : "";
                String self = member.equals(channel.getAddress()) ? " (this node)" : "";
                log.info("  - {}{}{}", member, marker, self);
            }
        }
    }
}
