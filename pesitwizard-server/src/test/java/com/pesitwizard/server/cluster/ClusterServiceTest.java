package com.pesitwizard.server.cluster;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests for ClusterService when running in standalone/disabled mode.
 * Full cluster testing requires JGroups integration tests.
 */
@DisplayName("ClusterService Tests (Standalone Mode)")
class ClusterServiceTest {

    private ClusterService service;

    @BeforeEach
    void setUp() {
        service = new ClusterService();
        // Configure for standalone mode
        ReflectionTestUtils.setField(service, "clusterEnabled", false);
        ReflectionTestUtils.setField(service, "nodeName", "test-node");
    }

    @Test
    @DisplayName("init should set leader to true when cluster disabled")
    void initShouldSetLeaderWhenDisabled() {
        service.init();

        assertTrue(service.isLeader());
    }

    @Test
    @DisplayName("acquireServerOwnership should succeed when cluster disabled")
    void acquireServerOwnershipShouldSucceedWhenDisabled() {
        service.init();

        assertTrue(service.acquireServerOwnership("server1"));
    }

    @Test
    @DisplayName("releaseServerOwnership should not throw when cluster disabled")
    void releaseServerOwnershipShouldNotThrowWhenDisabled() {
        service.init();

        assertDoesNotThrow(() -> service.releaseServerOwnership("server1"));
    }

    @Test
    @DisplayName("ownsServer should return true when cluster disabled")
    void ownsServerShouldReturnTrueWhenDisabled() {
        service.init();

        assertTrue(service.ownsServer("any-server"));
    }

    @Test
    @DisplayName("getClusterMembers should return single node when disabled")
    void getClusterMembersShouldReturnSingleNodeWhenDisabled() {
        service.init();

        assertEquals(1, service.getClusterMembers().size());
        assertEquals("test-node", service.getClusterMembers().get(0));
    }

    @Test
    @DisplayName("getClusterSize should return 1 when disabled")
    void getClusterSizeShouldReturnOneWhenDisabled() {
        service.init();

        assertEquals(1, service.getClusterSize());
    }

    @Test
    @DisplayName("isClusterEnabled should return false when disabled")
    void isClusterEnabledShouldReturnFalseWhenDisabled() {
        service.init();

        assertFalse(service.isClusterEnabled());
    }

    @Test
    @DisplayName("getNodeName should return configured name")
    void getNodeNameShouldReturnConfiguredName() {
        service.init();

        assertEquals("test-node", service.getNodeName());
    }

    @Test
    @DisplayName("addListener should not throw")
    void addListenerShouldNotThrow() {
        ClusterEventListener listener = event -> {
        };

        assertDoesNotThrow(() -> service.addListener(listener));
    }

    @Test
    @DisplayName("removeListener should not throw")
    void removeListenerShouldNotThrow() {
        ClusterEventListener listener = event -> {
        };
        service.addListener(listener);

        assertDoesNotThrow(() -> service.removeListener(listener));
    }

    @Test
    @DisplayName("broadcast should not throw when cluster disabled")
    void broadcastShouldNotThrowWhenDisabled() {
        service.init();
        ClusterMessage message = new ClusterMessage(ClusterMessage.Type.SERVER_ACQUIRED, "server1", "test-node");

        assertDoesNotThrow(() -> service.broadcast(message));
    }

    @Test
    @DisplayName("close should not throw when not connected")
    void closeShouldNotThrowWhenNotConnected() {
        service.init();

        assertDoesNotThrow(() -> service.close());
    }

    @Test
    @DisplayName("isConnected should return false when disabled")
    void isConnectedShouldReturnFalseWhenDisabled() {
        service.init();

        assertFalse(service.isConnected());
    }

    @Test
    @DisplayName("getServerOwner should return null for unknown server")
    void getServerOwnerShouldReturnNullForUnknown() {
        service.init();

        assertNull(service.getServerOwner("unknown"));
    }

    @Test
    @DisplayName("getAllServerOwnership should return empty map initially")
    void getAllServerOwnershipShouldReturnEmptyMapInitially() {
        service.init();

        assertTrue(service.getAllServerOwnership().isEmpty());
    }
}
