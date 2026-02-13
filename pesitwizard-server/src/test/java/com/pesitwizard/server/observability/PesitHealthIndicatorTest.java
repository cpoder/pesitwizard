package com.pesitwizard.server.observability;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.server.cluster.ClusterProvider;
import com.pesitwizard.server.service.PesitServerInstance;
import com.pesitwizard.server.service.PesitServerManager;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PesitHealthIndicator Tests")
class PesitHealthIndicatorTest {

    @Mock private ClusterProvider clusterProvider;

    @Mock private PesitServerManager serverManager;

    private PesitHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new PesitHealthIndicator(clusterProvider, serverManager);
    }

    @Test
    @DisplayName("health should return UP when cluster disabled and servers running")
    void healthShouldReturnUpWhenStandalone() {
        PesitServerInstance mockInstance = mock(PesitServerInstance.class);
        when(serverManager.getRunningServers()).thenReturn(List.of(mockInstance));
        when(serverManager.getActiveConnectionCount()).thenReturn(5);
        when(clusterProvider.isClusterEnabled()).thenReturn(false);
        when(clusterProvider.isConnected()).thenReturn(true);
        when(clusterProvider.getClusterSize()).thenReturn(1);
        when(clusterProvider.getNodeName()).thenReturn("standalone");

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(1, health.getDetails().get("runningServers"));
        assertEquals(5, health.getDetails().get("activeConnections"));
        assertFalse((Boolean) health.getDetails().get("clusterEnabled"));
    }

    @Test
    @DisplayName("health should return DOWN when cluster enabled but not connected")
    void healthShouldReturnDownWhenClusterNotConnected() {
        PesitServerInstance mockInstance = mock(PesitServerInstance.class);
        when(serverManager.getRunningServers()).thenReturn(List.of(mockInstance));
        when(serverManager.getActiveConnectionCount()).thenReturn(0);
        when(clusterProvider.isClusterEnabled()).thenReturn(true);
        when(clusterProvider.isConnected()).thenReturn(false);
        when(clusterProvider.getClusterSize()).thenReturn(0);
        when(clusterProvider.getNodeName()).thenReturn("node1");

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Cluster not connected", health.getDetails().get("reason"));
    }

    @Test
    @DisplayName("health should return UP with status message when no servers running")
    void healthShouldReturnUpWhenNoServersRunning() {
        when(serverManager.getRunningServers()).thenReturn(Collections.emptyList());
        when(serverManager.getActiveConnectionCount()).thenReturn(0);
        when(clusterProvider.isClusterEnabled()).thenReturn(false);
        when(clusterProvider.isConnected()).thenReturn(true);
        when(clusterProvider.getClusterSize()).thenReturn(1);
        when(clusterProvider.getNodeName()).thenReturn("standalone");

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(0, health.getDetails().get("runningServers"));
        assertEquals("No servers running", health.getDetails().get("status"));
    }

    @Test
    @DisplayName("health should include cluster details when enabled and connected")
    void healthShouldIncludeClusterDetails() {
        PesitServerInstance mockInstance1 = mock(PesitServerInstance.class);
        PesitServerInstance mockInstance2 = mock(PesitServerInstance.class);
        when(serverManager.getRunningServers()).thenReturn(List.of(mockInstance1, mockInstance2));
        when(serverManager.getActiveConnectionCount()).thenReturn(10);
        when(clusterProvider.isClusterEnabled()).thenReturn(true);
        when(clusterProvider.isConnected()).thenReturn(true);
        when(clusterProvider.getClusterSize()).thenReturn(3);
        when(clusterProvider.getNodeName()).thenReturn("node1");

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertTrue((Boolean) health.getDetails().get("clusterEnabled"));
        assertTrue((Boolean) health.getDetails().get("clusterConnected"));
        assertEquals(3, health.getDetails().get("clusterSize"));
        assertEquals("node1", health.getDetails().get("nodeName"));
    }
}
