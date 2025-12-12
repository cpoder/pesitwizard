package com.vectis.server.cluster;

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

    private static final long serialVersionUID = 1L;

    public enum Type {
        SERVER_ACQUIRED,
        SERVER_RELEASED,
        SERVER_STATE_CHANGED
    }

    private Type type;
    private String serverId;
    private String nodeId;
}
