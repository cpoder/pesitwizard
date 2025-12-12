package com.vectis.server.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Event representing a cluster state change
 */
@Data
@AllArgsConstructor
public class ClusterEvent {

    public enum Type {
        VIEW_CHANGED,
        BECAME_LEADER,
        LOST_LEADERSHIP,
        SERVER_ACQUIRED,
        SERVER_RELEASED,
        SERVER_STATE_CHANGED
    }

    private final Type type;
    private final String nodeId;
    private final String serverId;
    private final int clusterSize;
    private final boolean isLeader;

    public static ClusterEvent viewChanged(String nodeId, int clusterSize, boolean isLeader) {
        return new ClusterEvent(Type.VIEW_CHANGED, nodeId, null, clusterSize, isLeader);
    }

    public static ClusterEvent becameLeader(String nodeId) {
        return new ClusterEvent(Type.BECAME_LEADER, nodeId, null, 0, true);
    }

    public static ClusterEvent lostLeadership(String nodeId) {
        return new ClusterEvent(Type.LOST_LEADERSHIP, nodeId, null, 0, false);
    }

    public static ClusterEvent serverAcquired(String serverId, String nodeId) {
        return new ClusterEvent(Type.SERVER_ACQUIRED, nodeId, serverId, 0, false);
    }

    public static ClusterEvent serverReleased(String serverId, String nodeId) {
        return new ClusterEvent(Type.SERVER_RELEASED, nodeId, serverId, 0, false);
    }

    public static ClusterEvent serverStateChanged(String serverId, String nodeId) {
        return new ClusterEvent(Type.SERVER_STATE_CHANGED, nodeId, serverId, 0, false);
    }
}
