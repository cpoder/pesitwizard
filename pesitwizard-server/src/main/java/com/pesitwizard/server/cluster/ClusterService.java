package com.pesitwizard.server.cluster;

import com.pesitwizard.server.entity.ServerOwnershipRecord;
import com.pesitwizard.server.repository.ServerOwnershipRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.MergeView;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.util.NameCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JGroups-based cluster service for High Availability. Provides: - Cluster membership management -
 * Leader election (coordinator is the leader) - State replication across nodes - Cluster-wide
 * messaging
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "pesitwizard.cluster.enabled", havingValue = "true")
public class ClusterService implements ClusterProvider, Receiver, Closeable {

    private static final String CLUSTER_NAME = "pesitwizard-cluster";

    @Value("${pesitwizard.cluster.enabled:true}")
    private boolean clusterEnabled;

    @Value(
            "${pesitwizard.cluster.node-name:#{T(java.util.UUID).randomUUID().toString().substring(0,8)}}")
    private String nodeName;

    @Value("${pesitwizard.cluster.config:tcp.xml}")
    private String jgroupsConfig;

    private JChannel channel;

    @Getter private volatile boolean leader = false;

    @Getter private volatile boolean connected = false;

    private final ServerOwnershipRepository ownershipRepository;
    private final List<ClusterEventListener> listeners = new CopyOnWriteArrayList<>();

    // In-memory cache of ownership (populated from DB on startup and kept in sync via broadcasts)
    private final Map<String, String> serverOwnership = new ConcurrentHashMap<>();

    private ScheduledExecutorService reconnectScheduler;
    private ScheduledFuture<?> reconnectTask;

    public ClusterService(ServerOwnershipRepository ownershipRepository) {
        this.ownershipRepository = ownershipRepository;
    }

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
            // Do NOT assume leadership when disconnected — risks split-brain
            leader = false;
            connected = false;
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (reconnectScheduler == null) {
            reconnectScheduler =
                    Executors.newSingleThreadScheduledExecutor(
                            r -> {
                                Thread t = new Thread(r, "cluster-reconnect");
                                t.setDaemon(true);
                                return t;
                            });
        }
        if (reconnectTask == null || reconnectTask.isDone()) {
            reconnectTask =
                    reconnectScheduler.scheduleWithFixedDelay(
                            this::attemptReconnect, 30, 30, TimeUnit.SECONDS);
            log.info("Scheduled cluster reconnection every 30 seconds");
        }
    }

    private void attemptReconnect() {
        if (connected) {
            cancelReconnect();
            return;
        }
        try {
            log.info("Attempting cluster reconnection...");
            if (channel != null) {
                channel.close();
            }
            channel = new JChannel(jgroupsConfig);
            channel.setName(nodeName);
            channel.setReceiver(this);
            channel.setDiscardOwnMessages(true);
            channel.connect(CLUSTER_NAME);
            connected = true;
            channel.getState(null, 10000);
            log.info("Successfully reconnected to cluster '{}'", CLUSTER_NAME);
            cancelReconnect();
            logClusterView();
        } catch (Exception e) {
            log.warn("Cluster reconnection failed: {}", e.getMessage());
        }
    }

    private void cancelReconnect() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    @PreDestroy
    @Override
    public void close() {
        cancelReconnect();
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
        }
        if (channel != null && channel.isConnected()) {
            log.info("Leaving cluster...");

            // Release ownership of all servers on this node
            releaseAllServerOwnership();

            channel.close();
            connected = false;
            log.info("Disconnected from cluster");
        }
    }

    /** Called when cluster view changes (member join/leave) */
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

        // Detect merge view (split-brain recovery)
        if (newView instanceof MergeView mergeView && leader) {
            log.warn(
                    "MERGE VIEW detected — resolving split-brain. Sub-groups: {}",
                    mergeView.getSubgroups());
            resolveMergeConflicts();
        }

        // Clean up ownership for nodes that left (in-memory cache and DB)
        // Use logical names (set via channel.setName()) rather than Address.toString()
        // because ownership DB stores the logical node name, not the address representation
        Set<String> currentMembers =
                newView.getMembers().stream()
                        .map(addr -> NameCache.get(addr))
                        .collect(Collectors.toSet());

        serverOwnership
                .entrySet()
                .removeIf(
                        entry -> {
                            if (!currentMembers.contains(entry.getValue())) {
                                log.info(
                                        "Releasing ownership of server '{}' (node '{}' left)",
                                        entry.getKey(),
                                        entry.getValue());
                                // Leader cleans up DB records for departed nodes
                                if (leader) {
                                    try {
                                        ownershipRepository.deleteByOwnerNode(entry.getValue());
                                    } catch (RuntimeException e) {
                                        log.warn(
                                                "Failed to clean up DB ownership for departed node '{}': {}",
                                                entry.getValue(),
                                                e.getMessage());
                                    }
                                }
                                return true;
                            }
                            return false;
                        });

        notifyListeners(ClusterEvent.viewChanged(nodeName, newView.size(), leader));
        logClusterView();
    }

    /** Called when a message is received from another node */
    @Override
    public void receive(Message msg) {
        try {
            Object payload = msg.getObject();
            if (payload instanceof ClusterMessage clusterMsg) {
                handleClusterMessage(clusterMsg, msg.getSrc());
            }
        } catch (RuntimeException e) {
            log.error("Error processing cluster message: {}", e.getMessage(), e);
        }
    }

    /**
     * Called to get state for new joining node. Uses manual serialization (DataOutputStream)
     * instead of Java object serialization to prevent deserialization attacks via crafted JGroups
     * state transfer payloads.
     */
    @Override
    public void getState(OutputStream output) throws Exception {
        synchronized (serverOwnership) {
            DataOutputStream dos = new DataOutputStream(output);
            dos.writeInt(serverOwnership.size());
            for (Map.Entry<String, String> entry : serverOwnership.entrySet()) {
                dos.writeUTF(entry.getKey());
                dos.writeUTF(entry.getValue());
            }
            dos.flush();
        }
    }

    /**
     * Called when this node receives state from existing member. Uses manual deserialization
     * (DataInputStream) instead of Util.objectFromStream() to prevent Remote Code Execution via
     * deserialization gadget chains.
     */
    @Override
    public void setState(InputStream input) throws Exception {
        DataInputStream dis = new DataInputStream(input);
        int size = dis.readInt();
        if (size < 0 || size > 100_000) {
            throw new IllegalStateException("Invalid cluster state map size: " + size);
        }
        Map<String, String> state = new ConcurrentHashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = dis.readUTF();
            String value = dis.readUTF();
            state.put(key, value);
        }
        synchronized (serverOwnership) {
            serverOwnership.clear();
            serverOwnership.putAll(state);
        }
        log.info("Received cluster state: {} server assignments", serverOwnership.size());
    }

    /**
     * Try to acquire ownership of a server (for starting it). Uses database INSERT to serialize
     * concurrent acquisition across cluster nodes. The unique constraint on serverId ensures only
     * one node can own a server.
     */
    @Transactional
    public boolean acquireServerOwnership(String serverId) {
        if (!clusterEnabled) {
            return true; // Standalone mode always succeeds
        }

        // Check DB for existing ownership
        var existing = ownershipRepository.findByServerId(serverId);
        if (existing.isPresent()) {
            String currentOwner = existing.get().getOwnerNode();
            if (currentOwner.equals(nodeName)) {
                // We already own it
                return true;
            }
            log.warn("Server '{}' is owned by node '{}' (DB)", serverId, currentOwner);
            return false;
        }

        // Try to insert -- unique constraint on serverId prevents concurrent acquisition
        try {
            ownershipRepository.save(new ServerOwnershipRecord(serverId, nodeName, Instant.now()));
            ownershipRepository.flush(); // Force constraint check
            serverOwnership.put(serverId, nodeName);
            broadcastOwnershipChange(serverId, nodeName, true);
            log.info("Acquired ownership of server '{}' (DB-backed)", serverId);
            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Another node beat us to it
            log.warn("Server '{}' ownership race lost (concurrent insert)", serverId);
            return false;
        }
    }

    /**
     * Release ownership of a server (when stopping it). Deletes the database record to allow
     * another node to acquire.
     */
    @Transactional
    public void releaseServerOwnership(String serverId) {
        if (!clusterEnabled) {
            return;
        }

        int deleted = ownershipRepository.deleteByServerIdAndOwnerNode(serverId, nodeName);
        if (deleted > 0) {
            serverOwnership.remove(serverId);
            broadcastOwnershipChange(serverId, nodeName, false);
            log.info("Released ownership of server '{}' (DB-backed)", serverId);
        }
    }

    /** Check if this node owns a server */
    public boolean ownsServer(String serverId) {
        if (!clusterEnabled) {
            return true;
        }
        return nodeName.equals(serverOwnership.get(serverId));
    }

    /** Get the node that owns a server */
    public String getServerOwner(String serverId) {
        return serverOwnership.get(serverId);
    }

    /** Get all server ownership mappings */
    public Map<String, String> getAllServerOwnership() {
        return Map.copyOf(serverOwnership);
    }

    /** Get cluster members */
    public List<String> getClusterMembers() {
        if (!clusterEnabled || channel == null) {
            return List.of(nodeName);
        }
        return channel.getView().getMembers().stream()
                .map(addr -> NameCache.get(addr))
                .collect(Collectors.toList());
    }

    /** Get cluster size */
    public int getClusterSize() {
        if (!clusterEnabled || channel == null) {
            return 1;
        }
        return channel.getView().size();
    }

    /** Get this node's name */
    public String getNodeName() {
        return nodeName;
    }

    /** Check if cluster mode is enabled */
    public boolean isClusterEnabled() {
        return clusterEnabled;
    }

    /** Add a cluster event listener */
    public void addListener(ClusterEventListener listener) {
        listeners.add(listener);
    }

    /** Remove a cluster event listener */
    public void removeListener(ClusterEventListener listener) {
        listeners.remove(listener);
    }

    /** Broadcast a message to all cluster members */
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

    /**
     * Resolve ownership conflicts after a merge view (split-brain recovery). For each server, if
     * multiple nodes claim ownership, the one with the oldest acquiredAt timestamp wins. Losers are
     * force-released.
     */
    @Transactional
    private void resolveMergeConflicts() {
        try {
            // Query all ownership records from DB and group by serverId
            java.util.List<ServerOwnershipRecord> allRecords = ownershipRepository.findAll();
            Map<String, java.util.List<ServerOwnershipRecord>> byServer =
                    allRecords.stream()
                            .collect(Collectors.groupingBy(ServerOwnershipRecord::getServerId));

            for (var entry : byServer.entrySet()) {
                java.util.List<ServerOwnershipRecord> records = entry.getValue();
                if (records.size() > 1) {
                    log.warn(
                            "MERGE CONFLICT: Server '{}' owned by {} nodes — resolving by oldest acquiredAt",
                            entry.getKey(),
                            records.size());

                    // Keep the one with the oldest acquiredAt
                    records.sort(
                            java.util.Comparator.comparing(ServerOwnershipRecord::getAcquiredAt));
                    ServerOwnershipRecord winner = records.get(0);

                    for (int i = 1; i < records.size(); i++) {
                        ServerOwnershipRecord loser = records.get(i);
                        log.warn(
                                "MERGE CONFLICT: Releasing server '{}' from node '{}' (winner: '{}')",
                                entry.getKey(),
                                loser.getOwnerNode(),
                                winner.getOwnerNode());
                        ownershipRepository.deleteByServerIdAndOwnerNode(
                                entry.getKey(), loser.getOwnerNode());

                        // Notify the losing node
                        broadcast(
                                new ClusterMessage(
                                        ClusterMessage.Type.SERVER_RELEASED,
                                        entry.getKey(),
                                        loser.getOwnerNode()));
                    }

                    // Ensure in-memory cache reflects the winner
                    serverOwnership.put(entry.getKey(), winner.getOwnerNode());
                }
            }
        } catch (RuntimeException e) {
            log.error("Failed to resolve merge conflicts: {}", e.getMessage(), e);
        }
    }

    private void broadcastOwnershipChange(String serverId, String nodeId, boolean acquired) {
        broadcast(
                new ClusterMessage(
                        acquired
                                ? ClusterMessage.Type.SERVER_ACQUIRED
                                : ClusterMessage.Type.SERVER_RELEASED,
                        serverId,
                        nodeId));
    }

    @Transactional
    private void releaseAllServerOwnership() {
        int deleted = ownershipRepository.deleteByOwnerNode(nodeName);
        serverOwnership.entrySet().removeIf(e -> nodeName.equals(e.getValue()));
        if (deleted > 0) {
            log.info("Released {} server ownership records for node '{}'", deleted, nodeName);
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
                notifyListeners(
                        ClusterEvent.serverStateChanged(msg.getServerId(), msg.getNodeId()));
            }
            case CONFIG_CHANGED -> {
                log.info(
                        "Config change notification from node '{}': {} {} {}",
                        msg.getNodeId(),
                        msg.getOperation(),
                        msg.getEntityType(),
                        msg.getEntityId());
                notifyListeners(
                        ClusterEvent.configChanged(
                                msg.getNodeId(),
                                msg.getEntityType(),
                                msg.getEntityId(),
                                msg.getOperation()));
            }
        }
    }

    private void notifyListeners(ClusterEvent event) {
        for (ClusterEventListener listener : listeners) {
            try {
                listener.onClusterEvent(event);
            } catch (RuntimeException e) {
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
