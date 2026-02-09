package com.pesitwizard.server.cluster;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ClusterEvent Tests")
class ClusterEventTest {

    @Test
    @DisplayName("viewChanged should create event with correct type and values")
    void viewChangedShouldCreateCorrectEvent() {
        ClusterEvent event = ClusterEvent.viewChanged("node1", 3, true);

        assertEquals(ClusterEvent.Type.VIEW_CHANGED, event.getType());
        assertEquals("node1", event.getNodeId());
        assertNull(event.getServerId());
        assertEquals(3, event.getClusterSize());
        assertTrue(event.isLeader());
    }

    @Test
    @DisplayName("becameLeader should create event with leader flag true")
    void becameLeaderShouldCreateCorrectEvent() {
        ClusterEvent event = ClusterEvent.becameLeader("node1");

        assertEquals(ClusterEvent.Type.BECAME_LEADER, event.getType());
        assertEquals("node1", event.getNodeId());
        assertTrue(event.isLeader());
    }

    @Test
    @DisplayName("lostLeadership should create event with leader flag false")
    void lostLeadershipShouldCreateCorrectEvent() {
        ClusterEvent event = ClusterEvent.lostLeadership("node1");

        assertEquals(ClusterEvent.Type.LOST_LEADERSHIP, event.getType());
        assertEquals("node1", event.getNodeId());
        assertFalse(event.isLeader());
    }

    @Test
    @DisplayName("serverAcquired should create event with server ID")
    void serverAcquiredShouldCreateCorrectEvent() {
        ClusterEvent event = ClusterEvent.serverAcquired("server1", "node1");

        assertEquals(ClusterEvent.Type.SERVER_ACQUIRED, event.getType());
        assertEquals("node1", event.getNodeId());
        assertEquals("server1", event.getServerId());
    }

    @Test
    @DisplayName("serverReleased should create event with server ID")
    void serverReleasedShouldCreateCorrectEvent() {
        ClusterEvent event = ClusterEvent.serverReleased("server1", "node1");

        assertEquals(ClusterEvent.Type.SERVER_RELEASED, event.getType());
        assertEquals("node1", event.getNodeId());
        assertEquals("server1", event.getServerId());
    }

    @Test
    @DisplayName("serverStateChanged should create event with server ID")
    void serverStateChangedShouldCreateCorrectEvent() {
        ClusterEvent event = ClusterEvent.serverStateChanged("server1", "node1");

        assertEquals(ClusterEvent.Type.SERVER_STATE_CHANGED, event.getType());
        assertEquals("node1", event.getNodeId());
        assertEquals("server1", event.getServerId());
    }

    @Test
    @DisplayName("should have all event types")
    void shouldHaveAllEventTypes() {
        assertEquals(7, ClusterEvent.Type.values().length);
        assertNotNull(ClusterEvent.Type.VIEW_CHANGED);
        assertNotNull(ClusterEvent.Type.BECAME_LEADER);
        assertNotNull(ClusterEvent.Type.LOST_LEADERSHIP);
        assertNotNull(ClusterEvent.Type.SERVER_ACQUIRED);
        assertNotNull(ClusterEvent.Type.SERVER_RELEASED);
        assertNotNull(ClusterEvent.Type.SERVER_STATE_CHANGED);
        assertNotNull(ClusterEvent.Type.CONFIG_CHANGED);
    }

    @Test
    @DisplayName("configChanged should create event with entity details")
    void configChangedShouldCreateCorrectEvent() {
        ClusterEvent event = ClusterEvent.configChanged("node1", "PARTNER", "BNPP", "CREATED");

        assertEquals(ClusterEvent.Type.CONFIG_CHANGED, event.getType());
        assertEquals("node1", event.getNodeId());
        assertEquals("PARTNER", event.getEntityType());
        assertEquals("BNPP", event.getEntityId());
        assertEquals("CREATED", event.getOperation());
        assertNull(event.getServerId());
    }
}
