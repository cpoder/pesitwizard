package com.pesitwizard.server.cluster;

import java.util.List;
import java.util.Map;

/**
 * Interface for cluster management. Provides abstraction for standalone vs clustered deployments.
 *
 * <p>In standalone mode (OSS), the node is always the leader. In clustered mode (Enterprise),
 * leader election is handled by JGroups.
 */
public interface ClusterProvider {

    /** Check if this node is the leader. In standalone mode, always returns true. */
    boolean isLeader();

    /** Check if cluster mode is enabled. */
    boolean isClusterEnabled();

    /** Check if this node is connected to the cluster. In standalone mode, always returns true. */
    boolean isConnected();

    /** Get the number of nodes in the cluster. In standalone mode, returns 1. */
    int getClusterSize();

    /** Get this node's name. */
    String getNodeName();

    /** Get list of cluster member names. In standalone mode, returns a list with only this node. */
    List<String> getClusterMembers();

    /** Add a listener for cluster events. */
    void addListener(ClusterEventListener listener);

    /** Remove a cluster event listener. */
    void removeListener(ClusterEventListener listener);

    /**
     * Try to acquire ownership of a server (for starting it). In standalone mode, always returns
     * true.
     *
     * @param serverId the server identifier
     * @return true if ownership was acquired
     */
    boolean acquireServerOwnership(String serverId);

    /**
     * Release ownership of a server (when stopping it).
     *
     * @param serverId the server identifier
     */
    void releaseServerOwnership(String serverId);

    /**
     * Check if this node owns a server. In standalone mode, always returns true.
     *
     * @param serverId the server identifier
     * @return true if this node owns the server
     */
    boolean ownsServer(String serverId);

    /**
     * Get the node that owns a server.
     *
     * @param serverId the server identifier
     * @return the node name, or null if not owned
     */
    String getServerOwner(String serverId);

    /**
     * Get all server ownership mappings.
     *
     * @return map of serverId to nodeId
     */
    Map<String, String> getAllServerOwnership();

    /**
     * Broadcast a message to all cluster members. In standalone mode, this is a no-op.
     *
     * @param message the message to broadcast
     */
    void broadcast(ClusterMessage message);
}
