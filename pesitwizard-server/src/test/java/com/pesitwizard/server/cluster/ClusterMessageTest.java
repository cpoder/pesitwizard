package com.pesitwizard.server.cluster;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClusterMessage Tests")
class ClusterMessageTest {

    @Test
    @DisplayName("should create message with all args constructor")
    void shouldCreateMessageWithAllArgs() {
        ClusterMessage message = new ClusterMessage(
                ClusterMessage.Type.SERVER_ACQUIRED, "server1", "node1");

        assertEquals(ClusterMessage.Type.SERVER_ACQUIRED, message.getType());
        assertEquals("server1", message.getServerId());
        assertEquals("node1", message.getNodeId());
    }

    @Test
    @DisplayName("should create message with no args constructor and setters")
    void shouldCreateMessageWithNoArgsAndSetters() {
        ClusterMessage message = new ClusterMessage();
        message.setType(ClusterMessage.Type.SERVER_RELEASED);
        message.setServerId("server2");
        message.setNodeId("node2");

        assertEquals(ClusterMessage.Type.SERVER_RELEASED, message.getType());
        assertEquals("server2", message.getServerId());
        assertEquals("node2", message.getNodeId());
    }

    @Test
    @DisplayName("should have all message types")
    void shouldHaveAllMessageTypes() {
        assertEquals(3, ClusterMessage.Type.values().length);
        assertNotNull(ClusterMessage.Type.SERVER_ACQUIRED);
        assertNotNull(ClusterMessage.Type.SERVER_RELEASED);
        assertNotNull(ClusterMessage.Type.SERVER_STATE_CHANGED);
    }

    @Test
    @DisplayName("should be serializable")
    void shouldBeSerializable() throws Exception {
        ClusterMessage original = new ClusterMessage(
                ClusterMessage.Type.SERVER_STATE_CHANGED, "server1", "node1");

        // Serialize
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        // Deserialize
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
        java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);
        ClusterMessage deserialized = (ClusterMessage) ois.readObject();
        ois.close();

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getServerId(), deserialized.getServerId());
        assertEquals(original.getNodeId(), deserialized.getNodeId());
    }
}
