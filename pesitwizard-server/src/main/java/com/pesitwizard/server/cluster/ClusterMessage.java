package com.pesitwizard.server.cluster;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message sent between cluster nodes
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClusterMessage implements Serializable {

    private static final long serialVersionUID = 2L;

    public enum Type {
        SERVER_ACQUIRED,
        SERVER_RELEASED,
        SERVER_STATE_CHANGED,
        CONFIG_CHANGED
    }

    private Type type;
    private String serverId;
    private String nodeId;

    /**
     * For CONFIG_CHANGED messages: the entity type (PARTNER or VIRTUAL_FILE)
     */
    private String entityType;

    /**
     * For CONFIG_CHANGED messages: the entity ID
     */
    private String entityId;

    /**
     * For CONFIG_CHANGED messages: the operation (CREATED, UPDATED, DELETED)
     */
    private String operation;

    /**
     * Constructor for server ownership messages (backward compatible)
     */
    public ClusterMessage(Type type, String serverId, String nodeId) {
        this.type = type;
        this.serverId = serverId;
        this.nodeId = nodeId;
    }

    /**
     * Create a config change notification
     */
    public static ClusterMessage configChanged(String nodeId, String entityType, String entityId, String operation) {
        ClusterMessage msg = new ClusterMessage();
        msg.type = Type.CONFIG_CHANGED;
        msg.nodeId = nodeId;
        msg.entityType = entityType;
        msg.entityId = entityId;
        msg.operation = operation;
        return msg;
    }
}
