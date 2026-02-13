package com.pesitwizard.server.cluster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("StandaloneClusterProvider Tests")
class StandaloneClusterProviderTest {

    private StandaloneClusterProvider provider;

    @BeforeEach
    void setUp() {
        provider = new StandaloneClusterProvider();
        ReflectionTestUtils.setField(provider, "nodeName", "test-node");
    }

    @Test
    @DisplayName("should always be leader in standalone mode")
    void shouldAlwaysBeLeader() {
        assertTrue(provider.isLeader());
    }

    @Test
    @DisplayName("should report cluster as disabled")
    void shouldReportClusterDisabled() {
        assertFalse(provider.isClusterEnabled());
    }

    @Test
    @DisplayName("should always be connected")
    void shouldAlwaysBeConnected() {
        assertTrue(provider.isConnected());
    }

    @Test
    @DisplayName("should return cluster size of 1")
    void shouldReturnClusterSizeOne() {
        assertEquals(1, provider.getClusterSize());
    }

    @Test
    @DisplayName("should return configured node name")
    void shouldReturnNodeName() {
        assertEquals("test-node", provider.getNodeName());
    }

    @Test
    @DisplayName("should return single member list")
    void shouldReturnSingleMemberList() {
        List<String> members = provider.getClusterMembers();
        assertEquals(1, members.size());
        assertEquals("test-node", members.get(0));
    }

    @Test
    @DisplayName("should always acquire server ownership")
    void shouldAlwaysAcquireOwnership() {
        assertTrue(provider.acquireServerOwnership("server-1"));
    }

    @Test
    @DisplayName("should release server ownership")
    void shouldReleaseOwnership() {
        provider.acquireServerOwnership("server-1");
        provider.releaseServerOwnership("server-1");

        Map<String, String> ownership = provider.getAllServerOwnership();
        assertFalse(ownership.containsKey("server-1"));
    }

    @Test
    @DisplayName("should always own server in standalone mode")
    void shouldAlwaysOwnServer() {
        assertTrue(provider.ownsServer("any-server"));
    }

    @Test
    @DisplayName("should return node name as server owner")
    void shouldReturnNodeNameAsOwner() {
        provider.acquireServerOwnership("server-1");
        assertEquals("test-node", provider.getServerOwner("server-1"));
    }

    @Test
    @DisplayName("should return node name for unacquired server")
    void shouldReturnNodeNameForUnacquiredServer() {
        assertEquals("test-node", provider.getServerOwner("unknown-server"));
    }

    @Test
    @DisplayName("should return all server ownership")
    void shouldReturnAllOwnership() {
        provider.acquireServerOwnership("server-1");
        provider.acquireServerOwnership("server-2");

        Map<String, String> ownership = provider.getAllServerOwnership();
        assertEquals(2, ownership.size());
        assertEquals("test-node", ownership.get("server-1"));
        assertEquals("test-node", ownership.get("server-2"));
    }

    @Test
    @DisplayName("should accept listener without error")
    void shouldAcceptListener() {
        ClusterEventListener listener = event -> {};
        assertDoesNotThrow(() -> provider.addListener(listener));
    }

    @Test
    @DisplayName("should accept remove listener without error")
    void shouldAcceptRemoveListener() {
        ClusterEventListener listener = event -> {};
        assertDoesNotThrow(() -> provider.removeListener(listener));
    }

    @Test
    @DisplayName("should accept broadcast without error")
    void shouldAcceptBroadcast() {
        ClusterMessage message =
                new ClusterMessage(ClusterMessage.Type.SERVER_ACQUIRED, "server-1", "test-node");
        assertDoesNotThrow(() -> provider.broadcast(message));
    }
}
