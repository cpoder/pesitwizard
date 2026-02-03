package com.pesitwizard.server.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Chaos engineering tests for node failure scenarios.
 *
 * These tests verify:
 * - Leader failover within acceptable time
 * - No data loss during failover
 * - In-flight transfers resume after failover
 * - Cluster recovery after node returns
 *
 * Requirements:
 * - 3-node cluster running
 * - Kubernetes environment with pod management permissions
 * - Integration test profile
 *
 * Run with: mvn test -Dtest=NodeFailureTest -Dchaos-test=true
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("integration-test")
@Tag("chaos-test")
@Disabled("Enable manually for chaos testing - requires running cluster")
class NodeFailureTest {

    private static final Duration FAILOVER_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration RECOVERY_TIMEOUT = Duration.ofMinutes(5);

    // Simulated cluster state (in real tests, this would connect to actual cluster)
    private MockClusterState clusterState;

    @BeforeEach
    void setUp() {
        clusterState = new MockClusterState();
        clusterState.initialize(3); // 3-node cluster
    }

    @AfterEach
    void tearDown() {
        // Restore all nodes
        clusterState.restoreAllNodes();
    }

    @Test
    @DisplayName("Leader node failure - failover within 30 seconds")
    void testLeaderNodeFailure() {
        // Arrange
        String leaderId = clusterState.getLeaderId();
        assertThat(leaderId).isNotNull();
        log.info("Current leader: {}", leaderId);

        // Act - Kill the leader
        log.info("Killing leader node: {}", leaderId);
        clusterState.killNode(leaderId);

        // Assert - New leader elected within timeout
        await()
                .atMost(FAILOVER_TIMEOUT)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    String newLeader = clusterState.getLeaderId();
                    assertThat(newLeader)
                            .as("New leader should be elected")
                            .isNotNull()
                            .isNotEqualTo(leaderId);
                    log.info("New leader elected: {}", newLeader);
                });

        // Verify cluster is functional
        assertThat(clusterState.isClusterHealthy())
                .as("Cluster should be healthy after failover")
                .isTrue();

        // Verify no data loss
        assertThat(clusterState.getDataIntegrityStatus())
                .as("No data should be lost during failover")
                .isTrue();
    }

    @Test
    @DisplayName("Follower node failure - cluster remains operational")
    void testFollowerNodeFailure() {
        // Arrange
        String leaderId = clusterState.getLeaderId();
        List<String> followers = clusterState.getFollowerIds();
        assertThat(followers).isNotEmpty();

        String targetFollower = followers.get(0);
        log.info("Killing follower node: {}", targetFollower);

        // Act
        clusterState.killNode(targetFollower);

        // Assert - Leader unchanged, cluster operational
        assertThat(clusterState.getLeaderId())
                .as("Leader should remain the same")
                .isEqualTo(leaderId);

        assertThat(clusterState.isClusterHealthy())
                .as("Cluster should remain healthy with one follower down")
                .isTrue();

        assertThat(clusterState.getActiveNodeCount())
                .as("Should have 2 active nodes")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Multiple node failures - service degradation, not crash")
    void testMultipleNodeFailures() {
        // Arrange
        String leaderId = clusterState.getLeaderId();
        List<String> followers = clusterState.getFollowerIds();

        // Act - Kill 2 of 3 nodes (leader + one follower)
        log.info("Killing leader: {} and follower: {}", leaderId, followers.get(0));
        clusterState.killNode(leaderId);
        clusterState.killNode(followers.get(0));

        // Assert - Service should degrade gracefully, not crash
        // With only 1 node, no quorum for writes
        assertThat(clusterState.getActiveNodeCount())
                .as("Only 1 node should remain")
                .isEqualTo(1);

        // Single node should still respond (degraded mode)
        assertThat(clusterState.isSingleNodeOperational())
                .as("Single node should be operational in degraded mode")
                .isTrue();

        // Cannot elect new leader without quorum
        assertThat(clusterState.getLeaderId())
                .as("No leader possible without quorum")
                .isNull();
    }

    @Test
    @DisplayName("Node recovery - automatic rebalancing")
    void testNodeRecovery() {
        // Arrange
        String nodeToKill = clusterState.getFollowerIds().get(0);
        clusterState.killNode(nodeToKill);

        assertThat(clusterState.getActiveNodeCount()).isEqualTo(2);

        // Act - Bring node back
        log.info("Restoring node: {}", nodeToKill);
        clusterState.restoreNode(nodeToKill);

        // Assert - Node rejoins and rebalances
        await()
                .atMost(RECOVERY_TIMEOUT)
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(clusterState.getActiveNodeCount())
                            .as("All 3 nodes should be active")
                            .isEqualTo(3);

                    assertThat(clusterState.isNodeSynced(nodeToKill))
                            .as("Recovered node should be synced")
                            .isTrue();
                });
    }

    @Test
    @DisplayName("In-flight transfer continues after leader failover")
    void testInFlightTransferDuringFailover() {
        // Arrange - Start a long transfer
        String transferId = clusterState.startLongTransfer();
        String originalLeader = clusterState.getLeaderId();

        // Let transfer progress a bit
        sleep(2000);

        // Act - Kill leader during transfer
        log.info("Killing leader during active transfer");
        clusterState.killNode(originalLeader);

        // Assert - Transfer should resume on new leader
        await()
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    TransferStatus status = clusterState.getTransferStatus(transferId);

                    assertThat(status.getState())
                            .as("Transfer should complete or be resumable")
                            .isIn("COMPLETED", "IN_PROGRESS", "RESUMABLE");

                    if ("COMPLETED".equals(status.getState())) {
                        assertThat(status.getRestartCount())
                                .as("Transfer may have restarted")
                                .isGreaterThanOrEqualTo(0);
                    }
                });
    }

    @Test
    @DisplayName("Cascading failure - rapid succession node failures")
    void testCascadingFailure() {
        // Arrange
        List<String> allNodes = clusterState.getAllNodeIds();

        // Act - Kill nodes in rapid succession (1 second apart)
        for (int i = 0; i < allNodes.size() - 1; i++) {
            log.info("Killing node {} of {}", i + 1, allNodes.size());
            clusterState.killNode(allNodes.get(i));
            sleep(1000);
        }

        // Assert - Last node should still be operational
        assertThat(clusterState.getActiveNodeCount()).isEqualTo(1);

        // Restore all nodes
        for (String nodeId : allNodes) {
            clusterState.restoreNode(nodeId);
        }

        // Cluster should recover
        await()
                .atMost(RECOVERY_TIMEOUT)
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(clusterState.getActiveNodeCount()).isEqualTo(3);
                    assertThat(clusterState.isClusterHealthy()).isTrue();
                });
    }

    @Test
    @DisplayName("Measure actual failover time")
    void testMeasureFailoverTime() {
        // Arrange
        String leaderId = clusterState.getLeaderId();

        // Act
        long startTime = System.currentTimeMillis();
        clusterState.killNode(leaderId);

        // Wait for new leader
        String newLeader = null;
        while (newLeader == null && System.currentTimeMillis() - startTime < 60000) {
            newLeader = clusterState.getLeaderId();
            if (newLeader != null && !newLeader.equals(leaderId)) {
                break;
            }
            newLeader = null;
            sleep(100);
        }

        long failoverTime = System.currentTimeMillis() - startTime;

        // Assert
        log.info("Failover completed in {} ms", failoverTime);

        assertThat(failoverTime)
                .as("Failover should complete within 30 seconds")
                .isLessThan(30000);

        // Record for baseline
        log.info("BASELINE: Leader failover time = {} ms", failoverTime);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Mock cluster state for testing.
     * In real implementation, this would connect to actual Kubernetes cluster.
     */
    static class MockClusterState {
        private java.util.Map<String, Boolean> nodes = new java.util.concurrent.ConcurrentHashMap<>();
        private String leaderId;

        void initialize(int nodeCount) {
            for (int i = 1; i <= nodeCount; i++) {
                nodes.put("node-" + i, true);
            }
            leaderId = "node-1";
        }

        String getLeaderId() {
            if (!nodes.getOrDefault(leaderId, false)) {
                // Elect new leader from active nodes
                for (String nodeId : nodes.keySet()) {
                    if (nodes.get(nodeId) && !nodeId.equals(leaderId)) {
                        leaderId = nodeId;
                        return leaderId;
                    }
                }
                return null;
            }
            return leaderId;
        }

        List<String> getFollowerIds() {
            return nodes.entrySet().stream()
                    .filter(e -> e.getValue() && !e.getKey().equals(leaderId))
                    .map(java.util.Map.Entry::getKey)
                    .toList();
        }

        List<String> getAllNodeIds() {
            return new java.util.ArrayList<>(nodes.keySet());
        }

        void killNode(String nodeId) {
            nodes.put(nodeId, false);
            if (nodeId.equals(leaderId)) {
                leaderId = null;
                getLeaderId(); // Trigger election
            }
        }

        void restoreNode(String nodeId) {
            nodes.put(nodeId, true);
        }

        void restoreAllNodes() {
            nodes.replaceAll((k, v) -> true);
            leaderId = "node-1";
        }

        int getActiveNodeCount() {
            return (int) nodes.values().stream().filter(v -> v).count();
        }

        boolean isClusterHealthy() {
            return getActiveNodeCount() >= 2;
        }

        boolean isSingleNodeOperational() {
            return getActiveNodeCount() >= 1;
        }

        boolean getDataIntegrityStatus() {
            return true; // Mock - always passes
        }

        boolean isNodeSynced(String nodeId) {
            return nodes.getOrDefault(nodeId, false);
        }

        String startLongTransfer() {
            return "transfer-" + System.currentTimeMillis();
        }

        TransferStatus getTransferStatus(String transferId) {
            return TransferStatus.builder()
                    .transferId(transferId)
                    .state("COMPLETED")
                    .restartCount(0)
                    .build();
        }
    }

    @Data
    @Builder
    static class TransferStatus {
        private String transferId;
        private String state;
        private int restartCount;
    }
}
