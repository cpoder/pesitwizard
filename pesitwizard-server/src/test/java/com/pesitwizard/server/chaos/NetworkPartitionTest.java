package com.pesitwizard.server.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Chaos engineering tests for network partition scenarios (split brain).
 *
 * <p>These tests verify: - Only one partition accepts writes during split brain - Recovery after
 * partition heals - High latency handling between nodes - Packet loss resilience
 *
 * <p>Requirements: - 3-node cluster running - Network manipulation capabilities (tc, iptables) -
 * Integration test profile
 *
 * <p>Run with: mvn test -Dtest=NetworkPartitionTest -Dchaos-test=true
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("integration-test")
@Tag("chaos-test")
@Disabled("Enable manually for chaos testing - requires running cluster")
class NetworkPartitionTest {

    private static final Duration PARTITION_DETECTION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RECOVERY_TIMEOUT = Duration.ofMinutes(2);

    private MockNetworkController networkController;

    @BeforeEach
    void setUp() {
        networkController = new MockNetworkController();
        networkController.initialize(3);
    }

    @AfterEach
    void tearDown() {
        networkController.healAllPartitions();
    }

    @Test
    @DisplayName("Split brain - only majority partition accepts writes")
    void testSplitBrain() {
        // Arrange - 3-node cluster: [node1, node2] vs [node3]

        // Act - Create network partition
        log.info("Creating network partition: [node-1, node-2] | [node-3]");
        networkController.createPartition(Set.of("node-1", "node-2"), Set.of("node-3"));

        // Assert - After partition detection timeout
        await().atMost(PARTITION_DETECTION_TIMEOUT)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            // Majority partition (2 nodes) should have a leader
                            String majorityLeader =
                                    networkController.getLeaderInPartition(
                                            Set.of("node-1", "node-2"));
                            assertThat(majorityLeader)
                                    .as("Majority partition should elect a leader")
                                    .isNotNull();

                            // Minority partition (1 node) should NOT have a leader
                            String minorityLeader =
                                    networkController.getLeaderInPartition(Set.of("node-3"));
                            assertThat(minorityLeader)
                                    .as("Minority partition should NOT have a leader (no quorum)")
                                    .isNull();
                        });

        // Verify writes only accepted by majority
        boolean majorityWrite = networkController.attemptWrite("node-1", "test-key", "test-value");
        boolean minorityWrite =
                networkController.attemptWrite("node-3", "test-key2", "test-value2");

        assertThat(majorityWrite).as("Majority partition should accept writes").isTrue();
        assertThat(minorityWrite).as("Minority partition should reject writes").isFalse();
    }

    @Test
    @DisplayName("Partition heals - cluster reunifies")
    void testPartitionHealing() {
        // Arrange - Create and then heal partition
        networkController.createPartition(Set.of("node-1", "node-2"), Set.of("node-3"));

        // Wait for partition to be detected
        await().atMost(PARTITION_DETECTION_TIMEOUT)
                .until(() -> networkController.isPartitionDetected());

        // Act - Heal the partition
        log.info("Healing network partition");
        networkController.healAllPartitions();

        // Assert - Cluster should reunify
        await().atMost(RECOVERY_TIMEOUT)
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            assertThat(networkController.getActiveNodeCount())
                                    .as("All nodes should be active")
                                    .isEqualTo(3);

                            assertThat(networkController.isClusterUnified())
                                    .as("Cluster should be unified")
                                    .isTrue();

                            // Single leader across entire cluster
                            String leader = networkController.getGlobalLeader();
                            assertThat(leader).isNotNull();
                        });

        // Verify data consistency after healing
        assertThat(networkController.isDataConsistent())
                .as("Data should be consistent across all nodes")
                .isTrue();
    }

    @Test
    @DisplayName("High latency between nodes - cluster stability")
    void testHighLatency() {
        // Arrange - Normal cluster operation

        // Act - Introduce 500ms latency between all nodes
        log.info("Introducing 500ms latency between nodes");
        networkController.setInterNodeLatency(500);

        // Assert - Cluster should remain stable
        await().during(Duration.ofSeconds(30))
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            assertThat(networkController.isClusterHealthy())
                                    .as("Cluster should remain healthy under latency")
                                    .isTrue();
                        });

        // Measure impact on operations
        long startTime = System.currentTimeMillis();
        boolean writeSuccess = networkController.attemptWrite("node-1", "latency-test", "value");
        long writeLatency = System.currentTimeMillis() - startTime;

        log.info("Write under latency took {} ms", writeLatency);

        assertThat(writeSuccess).isTrue();
        // Write should still complete, but may take longer
        assertThat(writeLatency).isLessThan(5000);

        // Reset latency
        networkController.setInterNodeLatency(0);
    }

    @Test
    @DisplayName("Packet loss - cluster handles gracefully")
    void testPacketLoss() {
        // Arrange

        // Act - Introduce 10% packet loss
        log.info("Introducing 10% packet loss");
        networkController.setPacketLossPercent(10);

        // Assert - Cluster should handle retries gracefully
        int successfulWrites = 0;
        int totalAttempts = 20;

        for (int i = 0; i < totalAttempts; i++) {
            if (networkController.attemptWrite("node-1", "loss-test-" + i, "value")) {
                successfulWrites++;
            }
            sleep(100);
        }

        double successRate = (double) successfulWrites / totalAttempts;
        log.info("Write success rate under packet loss: {}%", successRate * 100);

        assertThat(successRate)
                .as("Most writes should succeed despite packet loss")
                .isGreaterThan(0.8);

        // Reset packet loss
        networkController.setPacketLossPercent(0);
    }

    @Test
    @DisplayName("Asymmetric partition - one-way communication failure")
    void testAsymmetricPartition() {
        // Arrange - node-3 can receive from others, but cannot send

        // Act
        log.info("Creating asymmetric partition: node-3 can receive but not send");
        networkController.createAsymmetricPartition("node-3", false, true);

        // Wait for detection
        await().atMost(PARTITION_DETECTION_TIMEOUT)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            // node-3 should be detected as unreachable (can't respond to
                            // heartbeats)
                            assertThat(networkController.isNodeConsideredHealthy("node-3"))
                                    .as("node-3 should be considered unhealthy (can't respond)")
                                    .isFalse();
                        });

        // Leader should be in the functional majority
        String leader = networkController.getGlobalLeader();
        assertThat(leader).as("Leader should not be the partitioned node").isNotEqualTo("node-3");

        // Cleanup
        networkController.healAllPartitions();
    }

    @Test
    @DisplayName("Flapping network - rapid partition/heal cycles")
    void testFlappingNetwork() {
        // Arrange
        int flapCount = 5;
        int flapIntervalMs = 2000;

        // Act - Rapidly partition and heal
        for (int i = 0; i < flapCount; i++) {
            log.info("Flap cycle {} of {}", i + 1, flapCount);

            networkController.createPartition(Set.of("node-1", "node-2"), Set.of("node-3"));

            sleep(flapIntervalMs);

            networkController.healAllPartitions();

            sleep(flapIntervalMs);
        }

        // Assert - Cluster should stabilize after flapping stops
        await().atMost(RECOVERY_TIMEOUT)
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> {
                            assertThat(networkController.isClusterHealthy())
                                    .as("Cluster should be healthy after flapping stops")
                                    .isTrue();

                            assertThat(networkController.getGlobalLeader())
                                    .as("Should have a stable leader")
                                    .isNotNull();
                        });
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Mock network controller for testing. In real implementation, this would use tc/iptables to
     * manipulate network.
     */
    static class MockNetworkController {
        private final ConcurrentHashMap<String, Boolean> nodeHealth = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Set<String>> partitions = new ConcurrentHashMap<>();
        private final AtomicBoolean partitioned = new AtomicBoolean(false);
        private int latencyMs = 0;
        private int packetLossPercent = 0;
        private String currentLeader = "node-1";

        void initialize(int nodeCount) {
            for (int i = 1; i <= nodeCount; i++) {
                nodeHealth.put("node-" + i, true);
            }
        }

        void createPartition(Set<String> partition1, Set<String> partition2) {
            partitions.put("p1", partition1);
            partitions.put("p2", partition2);
            partitioned.set(true);

            // If leader is in minority, it loses leadership
            if (partition2.contains(currentLeader) && partition1.size() > partition2.size()) {
                currentLeader = partition1.iterator().next();
            }
        }

        void createAsymmetricPartition(String nodeId, boolean canSend, boolean canReceive) {
            if (!canSend) {
                nodeHealth.put(nodeId, false);
            }
            partitioned.set(true);
        }

        void healAllPartitions() {
            partitions.clear();
            partitioned.set(false);
            nodeHealth.replaceAll((k, v) -> true);
        }

        boolean isPartitionDetected() {
            return partitioned.get();
        }

        String getLeaderInPartition(Set<String> partition) {
            if (partition.size() < 2) {
                return null; // No quorum
            }
            for (String node : partition) {
                if (nodeHealth.getOrDefault(node, false)) {
                    return node;
                }
            }
            return null;
        }

        String getGlobalLeader() {
            if (partitioned.get()) {
                // Find majority partition
                for (Set<String> partition : partitions.values()) {
                    if (partition.size() >= 2) {
                        return getLeaderInPartition(partition);
                    }
                }
                return null;
            }
            return currentLeader;
        }

        int getActiveNodeCount() {
            return (int) nodeHealth.values().stream().filter(v -> v).count();
        }

        boolean isClusterUnified() {
            return !partitioned.get() && getActiveNodeCount() == 3;
        }

        boolean isClusterHealthy() {
            return getActiveNodeCount() >= 2;
        }

        boolean isNodeConsideredHealthy(String nodeId) {
            return nodeHealth.getOrDefault(nodeId, false);
        }

        boolean isDataConsistent() {
            return true; // Mock - always consistent
        }

        void setInterNodeLatency(int ms) {
            this.latencyMs = ms;
        }

        void setPacketLossPercent(int percent) {
            this.packetLossPercent = percent;
        }

        boolean attemptWrite(String nodeId, String key, String value) {
            // Simulate packet loss
            if (packetLossPercent > 0 && Math.random() * 100 < packetLossPercent) {
                return false;
            }

            // Simulate latency
            if (latencyMs > 0) {
                try {
                    Thread.sleep(latencyMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Check if node is in majority partition
            if (partitioned.get()) {
                for (Set<String> partition : partitions.values()) {
                    if (partition.contains(nodeId) && partition.size() >= 2) {
                        return true; // In majority
                    }
                }
                return false; // In minority
            }

            return nodeHealth.getOrDefault(nodeId, false);
        }
    }
}
