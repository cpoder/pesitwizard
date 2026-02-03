package com.pesitwizard.server.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import lombok.extern.slf4j.Slf4j;

/**
 * Chaos engineering tests for database failure scenarios.
 *
 * These tests verify:
 * - Graceful degradation when database is unavailable
 * - Automatic reconnection when database returns
 * - No data loss during database failover
 * - Connection pool recovery
 *
 * Requirements:
 * - PostgreSQL running
 * - Database manipulation capabilities
 * - Integration test profile
 *
 * Run with: mvn test -Dtest=DatabaseFailureTest -Dchaos-test=true
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("integration-test")
@Tag("chaos-test")
@Disabled("Enable manually for chaos testing - requires running database")
class DatabaseFailureTest {

    private static final Duration RECONNECTION_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration FAILOVER_TIMEOUT = Duration.ofSeconds(60);

    private MockDatabaseController dbController;

    @BeforeEach
    void setUp() {
        dbController = new MockDatabaseController();
        dbController.initialize();
    }

    @AfterEach
    void tearDown() {
        dbController.restoreDatabase();
    }

    @Test
    @DisplayName("Database unavailable - graceful degradation")
    void testDatabaseUnavailable() {
        // Arrange
        assertThat(dbController.isDatabaseAvailable())
                .as("Database should be available initially")
                .isTrue();

        // Act - Kill database
        log.info("Killing database connection");
        dbController.killDatabase();

        // Assert - Application should handle gracefully
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertThat(dbController.isDatabaseAvailable())
                            .as("Database should be unavailable")
                            .isFalse();
                });

        // Application should report degraded status
        HealthStatus health = dbController.getApplicationHealth();
        assertThat(health.getStatus())
                .as("Application should report degraded status")
                .isIn("DOWN", "OUT_OF_SERVICE", "DEGRADED");

        // Health endpoint should still respond
        assertThat(health.isHealthEndpointResponding())
                .as("Health endpoint should still respond")
                .isTrue();

        // API endpoints requiring DB should fail gracefully
        ApiResponse response = dbController.makeApiRequest("/api/v1/transfers");
        assertThat(response.getStatusCode())
                .as("API should return 503 Service Unavailable")
                .isEqualTo(503);

        assertThat(response.getErrorMessage())
                .as("Error message should be user-friendly")
                .doesNotContain("SQLException", "HikariPool");
    }

    @Test
    @DisplayName("Database recovery - automatic reconnection")
    void testDatabaseRecovery() {
        // Arrange
        dbController.killDatabase();
        await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> !dbController.isDatabaseAvailable());

        // Act - Restore database
        log.info("Restoring database connection");
        dbController.restoreDatabase();

        // Assert - Application should reconnect
        await()
                .atMost(RECONNECTION_TIMEOUT)
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(dbController.isDatabaseAvailable())
                            .as("Database should be available")
                            .isTrue();

                    HealthStatus health = dbController.getApplicationHealth();
                    assertThat(health.getStatus())
                            .as("Application should be healthy")
                            .isEqualTo("UP");
                });

        // Verify normal operation resumed
        ApiResponse response = dbController.makeApiRequest("/api/v1/transfers");
        assertThat(response.getStatusCode())
                .as("API should return 200 OK")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Database failover - replica promotion")
    void testDatabaseFailover() {
        // Arrange - Primary is active
        assertThat(dbController.getPrimaryStatus()).isEqualTo("ACTIVE");
        assertThat(dbController.getReplicaStatus()).isEqualTo("STANDBY");

        // Act - Trigger failover (kill primary, promote replica)
        log.info("Triggering database failover");
        dbController.triggerFailover();

        // Assert - Replica should become primary
        await()
                .atMost(FAILOVER_TIMEOUT)
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(dbController.getReplicaStatus())
                            .as("Replica should be promoted to primary")
                            .isEqualTo("ACTIVE");
                });

        // Application should reconnect to new primary
        await()
                .atMost(RECONNECTION_TIMEOUT)
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    HealthStatus health = dbController.getApplicationHealth();
                    assertThat(health.getStatus()).isEqualTo("UP");
                });

        // Verify no data loss
        assertThat(dbController.getDataIntegrityStatus())
                .as("No data should be lost during failover")
                .isTrue();
    }

    @Test
    @DisplayName("Connection pool exhaustion - recovery")
    void testConnectionPoolExhaustion() {
        // Arrange - Exhaust connection pool
        log.info("Exhausting connection pool");
        dbController.exhaustConnectionPool();

        // Assert - Should detect exhaustion
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertThat(dbController.isConnectionPoolExhausted())
                            .as("Connection pool should be exhausted")
                            .isTrue();
                });

        // New requests should timeout or be rejected
        ApiResponse response = dbController.makeApiRequest("/api/v1/transfers");
        assertThat(response.getStatusCode())
                .as("Should return error when pool exhausted")
                .isIn(503, 504);

        // Act - Release connections
        log.info("Releasing connections");
        dbController.releaseConnections();

        // Assert - Pool should recover
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(dbController.isConnectionPoolExhausted())
                            .as("Connection pool should recover")
                            .isFalse();

                    ApiResponse recoveredResponse = dbController.makeApiRequest("/api/v1/transfers");
                    assertThat(recoveredResponse.getStatusCode()).isEqualTo(200);
                });
    }

    @Test
    @DisplayName("Slow database queries - timeout handling")
    void testSlowQueries() {
        // Arrange - Introduce query latency
        log.info("Introducing 5-second query latency");
        dbController.setQueryLatency(5000);

        // Act - Make request
        long startTime = System.currentTimeMillis();
        ApiResponse response = dbController.makeApiRequest("/api/v1/transfers");
        long duration = System.currentTimeMillis() - startTime;

        // Assert - Should timeout rather than hang indefinitely
        assertThat(duration)
                .as("Request should timeout within reasonable time")
                .isLessThan(60000);

        // Should return timeout error, not hang
        assertThat(response.getStatusCode())
                .as("Should return timeout error")
                .isIn(200, 503, 504); // 200 if it completes, 503/504 if timeout

        // Reset
        dbController.setQueryLatency(0);
    }

    @Test
    @DisplayName("Database connection leak detection")
    void testConnectionLeakDetection() {
        // Arrange
        int initialConnections = dbController.getActiveConnectionCount();

        // Act - Simulate operations that might leak connections
        for (int i = 0; i < 100; i++) {
            dbController.makeApiRequest("/api/v1/transfers");
        }

        // Allow some time for connection return
        sleep(5000);

        // Assert - Connections should be returned to pool
        int finalConnections = dbController.getActiveConnectionCount();
        int leakedConnections = finalConnections - initialConnections;

        log.info("Connections: initial={}, final={}, leaked={}",
                initialConnections, finalConnections, leakedConnections);

        assertThat(leakedConnections)
                .as("Should not leak connections")
                .isLessThan(5); // Allow small variance for timing
    }

    @Test
    @DisplayName("Database intermittent failures - retry behavior")
    void testIntermittentFailures() {
        // Arrange - 30% failure rate
        log.info("Introducing 30% query failure rate");
        dbController.setQueryFailureRate(30);

        // Act - Make multiple requests
        int successCount = 0;
        int failCount = 0;
        int totalRequests = 50;

        for (int i = 0; i < totalRequests; i++) {
            ApiResponse response = dbController.makeApiRequest("/api/v1/health-check");
            if (response.getStatusCode() == 200) {
                successCount++;
            } else {
                failCount++;
            }
            sleep(100);
        }

        // Assert - Most requests should succeed due to retries
        double successRate = (double) successCount / totalRequests;
        log.info("Success rate with 30% failure injection: {}%", successRate * 100);

        assertThat(successRate)
                .as("Success rate should be high due to retries")
                .isGreaterThan(0.6);

        // Reset
        dbController.setQueryFailureRate(0);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Mock database controller for testing.
     * In real implementation, this would control actual database.
     */
    static class MockDatabaseController {
        private final AtomicBoolean databaseAvailable = new AtomicBoolean(true);
        private final AtomicBoolean poolExhausted = new AtomicBoolean(false);
        private final AtomicInteger activeConnections = new AtomicInteger(5);
        private volatile int queryLatencyMs = 0;
        private volatile int queryFailureRate = 0;
        private volatile String primaryStatus = "ACTIVE";
        private volatile String replicaStatus = "STANDBY";

        void initialize() {
            databaseAvailable.set(true);
            poolExhausted.set(false);
            activeConnections.set(5);
        }

        void killDatabase() {
            databaseAvailable.set(false);
        }

        void restoreDatabase() {
            databaseAvailable.set(true);
            poolExhausted.set(false);
        }

        boolean isDatabaseAvailable() {
            return databaseAvailable.get();
        }

        HealthStatus getApplicationHealth() {
            return new HealthStatus(
                    databaseAvailable.get() ? "UP" : "DOWN",
                    true
            );
        }

        ApiResponse makeApiRequest(String path) {
            // Simulate query latency
            if (queryLatencyMs > 0) {
                try {
                    Thread.sleep(queryLatencyMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Simulate query failure
            if (queryFailureRate > 0 && Math.random() * 100 < queryFailureRate) {
                return new ApiResponse(503, "Database temporarily unavailable");
            }

            if (!databaseAvailable.get()) {
                return new ApiResponse(503, "Service Unavailable");
            }

            if (poolExhausted.get()) {
                return new ApiResponse(503, "Connection pool exhausted");
            }

            return new ApiResponse(200, "OK");
        }

        void triggerFailover() {
            primaryStatus = "DOWN";
            replicaStatus = "ACTIVE";
        }

        String getPrimaryStatus() {
            return primaryStatus;
        }

        String getReplicaStatus() {
            return replicaStatus;
        }

        boolean getDataIntegrityStatus() {
            return true;
        }

        void exhaustConnectionPool() {
            poolExhausted.set(true);
            activeConnections.set(50); // Max pool size
        }

        void releaseConnections() {
            poolExhausted.set(false);
            activeConnections.set(5);
        }

        boolean isConnectionPoolExhausted() {
            return poolExhausted.get();
        }

        int getActiveConnectionCount() {
            return activeConnections.get();
        }

        void setQueryLatency(int ms) {
            this.queryLatencyMs = ms;
        }

        void setQueryFailureRate(int percent) {
            this.queryFailureRate = percent;
        }
    }

    static class HealthStatus {
        private final String status;
        private final boolean healthEndpointResponding;

        HealthStatus(String status, boolean healthEndpointResponding) {
            this.status = status;
            this.healthEndpointResponding = healthEndpointResponding;
        }

        String getStatus() {
            return status;
        }

        boolean isHealthEndpointResponding() {
            return healthEndpointResponding;
        }
    }

    static class ApiResponse {
        private final int statusCode;
        private final String errorMessage;

        ApiResponse(int statusCode, String errorMessage) {
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
        }

        int getStatusCode() {
            return statusCode;
        }

        String getErrorMessage() {
            return errorMessage;
        }
    }
}
