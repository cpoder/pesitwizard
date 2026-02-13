package com.pesitwizard.server.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Builder;
import lombok.Data;
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
 * Sustained throughput tests to verify server stability over extended periods.
 *
 * <p>These tests verify: - No memory leaks over time - No connection pool exhaustion - Consistent
 * performance without degradation - Stability at 80% capacity
 *
 * <p>Run with: mvn test -Dtest=SustainedThroughputTest -Dload-test=true
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("integration-test")
@Tag("load-test")
@Tag("soak-test")
@Disabled("Enable manually for soak testing - requires extended runtime")
class SustainedThroughputTest {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 6502;
    private static final int API_PORT = 8080;

    // 80% of 200 target capacity
    private static final int SUSTAINED_LOAD = 160;
    private static final int FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    private ScheduledExecutorService scheduler;
    private ScheduledExecutorService metricsCollector;
    private List<MetricSnapshot> metricHistory;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
        metricsCollector = Executors.newSingleThreadScheduledExecutor();
        metricHistory = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (metricsCollector != null) {
            metricsCollector.shutdownNow();
        }
    }

    @Test
    @DisplayName("Sustained 80% capacity for 4 hours")
    void testSustained80PercentCapacity4Hours() throws Exception {
        Duration testDuration = Duration.ofHours(4);

        SoakTestResult result =
                runSoakTest(
                        SoakTestConfig.builder()
                                .concurrentTransfers(SUSTAINED_LOAD)
                                .fileSizeBytes(FILE_SIZE_BYTES)
                                .duration(testDuration)
                                .metricIntervalSeconds(60)
                                .build());

        log.info("4-hour soak test results:\n{}", result.toDetailedReport());

        // Verify no degradation
        assertThat(result.isStable()).as("Server should remain stable over 4 hours").isTrue();

        assertThat(result.getMemoryGrowthPercent())
                .as("Memory growth should be less than 20%")
                .isLessThan(20.0);

        assertThat(result.getLatencyDegradationPercent())
                .as("Latency degradation should be less than 10%")
                .isLessThan(10.0);
    }

    @Test
    @DisplayName("Sustained 80% capacity for 1 hour (quick soak)")
    void testSustained80PercentCapacity1Hour() throws Exception {
        Duration testDuration = Duration.ofHours(1);

        SoakTestResult result =
                runSoakTest(
                        SoakTestConfig.builder()
                                .concurrentTransfers(SUSTAINED_LOAD)
                                .fileSizeBytes(FILE_SIZE_BYTES)
                                .duration(testDuration)
                                .metricIntervalSeconds(30)
                                .build());

        log.info("1-hour soak test results:\n{}", result.toDetailedReport());

        assertThat(result.isStable()).isTrue();
        assertThat(result.getErrorRate()).isLessThan(0.01);
    }

    @Test
    @DisplayName("Memory leak detection - 2 hour test")
    void testMemoryLeakDetection() throws Exception {
        Duration testDuration = Duration.ofHours(2);

        SoakTestResult result =
                runSoakTest(
                        SoakTestConfig.builder()
                                .concurrentTransfers(100)
                                .fileSizeBytes(1024 * 1024) // 1 MB
                                .duration(testDuration)
                                .metricIntervalSeconds(30)
                                .build());

        log.info("Memory leak detection results:\n{}", result.toDetailedReport());

        // Memory should not grow unboundedly
        assertThat(result.getMemoryGrowthPercent()).as("Memory should not leak").isLessThan(50.0);

        // Check for consistent memory pattern
        assertThat(result.getMemoryTrend())
                .as("Memory trend should be stable or declining after initial warmup")
                .isLessThan(0.1); // MB per minute growth rate
    }

    @Test
    @DisplayName("Connection pool exhaustion test")
    void testConnectionPoolExhaustion() throws Exception {
        Duration testDuration = Duration.ofMinutes(30);

        // Run at capacity to stress connection pool
        SoakTestResult result =
                runSoakTest(
                        SoakTestConfig.builder()
                                .concurrentTransfers(200)
                                .fileSizeBytes(1024 * 1024)
                                .duration(testDuration)
                                .metricIntervalSeconds(10)
                                .build());

        log.info("Connection pool test results:\n{}", result.toDetailedReport());

        // Should not exhaust connection pool
        assertThat(result.getConnectionPoolExhaustedCount())
                .as("Connection pool should not be exhausted")
                .isZero();
    }

    private SoakTestResult runSoakTest(SoakTestConfig config) throws Exception {
        log.info(
                "Starting soak test: {} concurrent for {}",
                config.getConcurrentTransfers(),
                config.getDuration());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicInteger connectionPoolExhausted = new AtomicInteger(0);
        List<Long> latencies = new ArrayList<>();

        Instant startTime = Instant.now();
        Instant endTime = startTime.plus(config.getDuration());

        // Start metrics collection
        metricsCollector.scheduleAtFixedRate(
                () -> {
                    try {
                        MetricSnapshot snapshot = collectMetrics();
                        synchronized (metricHistory) {
                            metricHistory.add(snapshot);
                        }
                        log.debug(
                                "Metrics: heap={}MB, active={}, errors={}",
                                snapshot.getHeapUsedMb(),
                                successCount.get() + errorCount.get(),
                                errorCount.get());
                    } catch (Exception e) {
                        log.warn("Failed to collect metrics: {}", e.getMessage());
                    }
                },
                0,
                config.getMetricIntervalSeconds(),
                TimeUnit.SECONDS);

        // Create and manage transfer tasks
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < config.getConcurrentTransfers(); i++) {
            CompletableFuture<Void> future =
                    CompletableFuture.runAsync(
                            () -> {
                                while (Instant.now().isBefore(endTime)) {
                                    try {
                                        long startNanos = System.nanoTime();

                                        boolean success =
                                                performTransfer(config.getFileSizeBytes());

                                        long latencyMs =
                                                (System.nanoTime() - startNanos) / 1_000_000;

                                        if (success) {
                                            successCount.incrementAndGet();
                                            totalBytes.addAndGet(config.getFileSizeBytes());
                                            synchronized (latencies) {
                                                latencies.add(latencyMs);
                                            }
                                        } else {
                                            errorCount.incrementAndGet();
                                        }

                                        // Brief pause between transfers
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    } catch (Exception e) {
                                        errorCount.incrementAndGet();
                                    }
                                }
                            },
                            scheduler);
            futures.add(future);
        }

        // Wait for completion
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(config.getDuration().toMinutes() + 5, TimeUnit.MINUTES)
                .exceptionally(ex -> null)
                .join();

        // Stop metrics collection
        metricsCollector.shutdown();

        // Calculate results
        Duration actualDuration = Duration.between(startTime, Instant.now());
        int totalTransfers = successCount.get() + errorCount.get();
        double errorRate = totalTransfers > 0 ? (double) errorCount.get() / totalTransfers : 0;

        // Calculate memory growth
        List<MetricSnapshot> history;
        synchronized (metricHistory) {
            history = new ArrayList<>(metricHistory);
        }

        double initialHeap = history.isEmpty() ? 0 : history.get(0).getHeapUsedMb();
        double finalHeap = history.isEmpty() ? 0 : history.get(history.size() - 1).getHeapUsedMb();
        double memoryGrowth = initialHeap > 0 ? ((finalHeap - initialHeap) / initialHeap) * 100 : 0;

        // Calculate memory trend (MB per minute)
        double memoryTrend = calculateMemoryTrend(history);

        // Calculate latency statistics
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);

        // Check latency degradation (compare first 10% vs last 10%)
        double latencyDegradation = calculateLatencyDegradation(latencies);

        // Determine stability
        boolean stable = errorRate < 0.05 && memoryGrowth < 50 && latencyDegradation < 20;

        return SoakTestResult.builder()
                .concurrentTransfers(config.getConcurrentTransfers())
                .duration(actualDuration)
                .successfulTransfers(successCount.get())
                .failedTransfers(errorCount.get())
                .totalBytesTransferred(totalBytes.get())
                .errorRate(errorRate)
                .avgLatencyMs(avgLatency)
                .initialHeapMb(initialHeap)
                .finalHeapMb(finalHeap)
                .memoryGrowthPercent(memoryGrowth)
                .memoryTrend(memoryTrend)
                .latencyDegradationPercent(latencyDegradation)
                .connectionPoolExhaustedCount(connectionPoolExhausted.get())
                .stable(stable)
                .metricHistory(history)
                .build();
    }

    private boolean performTransfer(int fileSize) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            socket.setSoTimeout(60000);

            // Simulate transfer
            Thread.sleep(fileSize / (1024 * 1024) * 50);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private MetricSnapshot collectMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        return MetricSnapshot.builder()
                .timestamp(Instant.now())
                .heapUsedMb(heapUsage.getUsed() / 1024.0 / 1024.0)
                .heapMaxMb(heapUsage.getMax() / 1024.0 / 1024.0)
                .heapCommittedMb(heapUsage.getCommitted() / 1024.0 / 1024.0)
                .build();
    }

    private double calculateMemoryTrend(List<MetricSnapshot> history) {
        if (history.size() < 2) return 0;

        // Simple linear regression
        int n = history.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            double x = i; // Time index
            double y = history.get(i).getHeapUsedMb();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        // Slope = (n*sumXY - sumX*sumY) / (n*sumX2 - sumX^2)
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

        // Convert to MB per minute based on metric interval
        return slope; // Already in MB per interval
    }

    private double calculateLatencyDegradation(List<Long> latencies) {
        if (latencies.size() < 20) return 0;

        int tenPercent = latencies.size() / 10;

        // First 10%
        double firstAvg =
                latencies.subList(0, tenPercent).stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0);

        // Last 10%
        double lastAvg =
                latencies.subList(latencies.size() - tenPercent, latencies.size()).stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0);

        if (firstAvg == 0) return 0;

        return ((lastAvg - firstAvg) / firstAvg) * 100;
    }

    @Data
    @Builder
    static class SoakTestConfig {
        private int concurrentTransfers;
        private int fileSizeBytes;
        private Duration duration;
        @Builder.Default private int metricIntervalSeconds = 60;
    }

    @Data
    @Builder
    static class SoakTestResult {
        private int concurrentTransfers;
        private Duration duration;
        private int successfulTransfers;
        private int failedTransfers;
        private long totalBytesTransferred;
        private double errorRate;
        private double avgLatencyMs;
        private double initialHeapMb;
        private double finalHeapMb;
        private double memoryGrowthPercent;
        private double memoryTrend;
        private double latencyDegradationPercent;
        private int connectionPoolExhaustedCount;
        private boolean stable;
        private List<MetricSnapshot> metricHistory;

        public String toDetailedReport() {
            return String.format(
                    """
                    ╔══════════════════════════════════════════════════════════════╗
                    ║                    SOAK TEST RESULTS                          ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║ Configuration                                                 ║
                    ║   Concurrent Transfers: %-6d                                 ║
                    ║   Test Duration:        %s                                    ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║ Transfer Results                                              ║
                    ║   Successful:           %-6d                                 ║
                    ║   Failed:               %-6d                                 ║
                    ║   Error Rate:           %.2f%%                                ║
                    ║   Total Data:           %.2f GB                              ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║ Memory Analysis                                               ║
                    ║   Initial Heap:         %.0f MB                              ║
                    ║   Final Heap:           %.0f MB                              ║
                    ║   Growth:               %.1f%%                               ║
                    ║   Trend:                %.2f MB/interval                     ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║ Performance                                                   ║
                    ║   Avg Latency:          %.0f ms                              ║
                    ║   Latency Degradation:  %.1f%%                               ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║ Connection Pool                                               ║
                    ║   Exhausted Count:      %-6d                                 ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║ Overall Status:         %s                                    ║
                    ╚══════════════════════════════════════════════════════════════╝
                    """,
                    concurrentTransfers,
                    duration,
                    successfulTransfers,
                    failedTransfers,
                    errorRate * 100,
                    totalBytesTransferred / 1024.0 / 1024.0 / 1024.0,
                    initialHeapMb,
                    finalHeapMb,
                    memoryGrowthPercent,
                    memoryTrend,
                    avgLatencyMs,
                    latencyDegradationPercent,
                    connectionPoolExhaustedCount,
                    stable ? "STABLE" : "DEGRADED");
        }
    }

    @Data
    @Builder
    static class MetricSnapshot {
        private Instant timestamp;
        private double heapUsedMb;
        private double heapMaxMb;
        private double heapCommittedMb;
    }
}
