package com.pesitwizard.server.cluster;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("PodLabelService Tests")
class PodLabelServiceTest {

    @Mock private ClusterProvider clusterProvider;

    private PodLabelService service;

    @BeforeEach
    void setUp() {
        service = new PodLabelService();
        ReflectionTestUtils.setField(service, "clusterProvider", clusterProvider);
    }

    @Test
    @DisplayName("init should not initialize when cluster disabled")
    void initShouldNotInitializeWhenClusterDisabled() {
        ReflectionTestUtils.setField(service, "clusterEnabled", false);
        ReflectionTestUtils.setField(service, "podName", "test-pod");

        service.init();

        verify(clusterProvider, never()).addListener(any());
    }

    @Test
    @DisplayName("init should not initialize when pod name is empty")
    void initShouldNotInitializeWhenPodNameEmpty() {
        ReflectionTestUtils.setField(service, "clusterEnabled", true);
        ReflectionTestUtils.setField(service, "podName", "");

        service.init();

        verify(clusterProvider, never()).addListener(any());
    }

    @Test
    @DisplayName("init should not initialize when pod name is null")
    void initShouldNotInitializeWhenPodNameNull() {
        ReflectionTestUtils.setField(service, "clusterEnabled", true);
        ReflectionTestUtils.setField(service, "podName", null);

        service.init();

        verify(clusterProvider, never()).addListener(any());
    }

    @Test
    @DisplayName("onClusterEvent should handle BECAME_LEADER event")
    void onClusterEventShouldHandleBecameLeader() {
        ReflectionTestUtils.setField(service, "kubernetesAvailable", false);

        ClusterEvent event = ClusterEvent.becameLeader("node1");

        assertDoesNotThrow(() -> service.onClusterEvent(event));
    }

    @Test
    @DisplayName("onClusterEvent should handle LOST_LEADERSHIP event")
    void onClusterEventShouldHandleLostLeadership() {
        ReflectionTestUtils.setField(service, "kubernetesAvailable", false);

        ClusterEvent event = ClusterEvent.lostLeadership("node1");

        assertDoesNotThrow(() -> service.onClusterEvent(event));
    }

    @Test
    @DisplayName("onClusterEvent should ignore VIEW_CHANGED event")
    void onClusterEventShouldIgnoreViewChanged() {
        ReflectionTestUtils.setField(service, "kubernetesAvailable", false);

        ClusterEvent event = ClusterEvent.viewChanged("node1", 3, true);

        assertDoesNotThrow(() -> service.onClusterEvent(event));
    }

    @Test
    @DisplayName("onClusterEvent should ignore SERVER_ACQUIRED event")
    void onClusterEventShouldIgnoreServerAcquired() {
        ReflectionTestUtils.setField(service, "kubernetesAvailable", false);

        ClusterEvent event = ClusterEvent.serverAcquired("server1", "node1");

        assertDoesNotThrow(() -> service.onClusterEvent(event));
    }

    @Test
    @DisplayName("cleanup should not throw when kubernetes client is null")
    void cleanupShouldNotThrowWhenClientNull() {
        assertDoesNotThrow(() -> service.cleanup());
    }
}
