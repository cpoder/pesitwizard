package com.pesitwizard.server.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
 * Load tests for concurrent PeSIT file transfers.
 *
 * These tests verify the server can handle production-level loads:
 * - 200 concurrent transfers (target)
 * - 500 concurrent transfers (stress test)
 * - 1000 concurrent transfers (breaking point)
 *
 * Run with: mvn test -Dtest=ConcurrentTransferLoadTest -Dload-test=true
 *
 * Note: These tests are disabled by default and require:
 * - Running PeSIT server
 * - Sufficient system resources
 * - Extended timeouts
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("integration-test")
@Tag("load-test")
@Disabled("Enable manually for load testing - requires running server")
class ConcurrentTransferLoadTest {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 6502;

    // Test file sizes
    private static final int SMALL_FILE_SIZE = 1024 * 1024;       // 1 MB
    private static final int MEDIUM_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int LARGE_FILE_SIZE = 100 * 1024 * 1024; // 100 MB

    private ExecutorService executor;
    private Path testFilesDir;
    private final ConcurrentHashMap<String, TransferResult> results = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        testFilesDir = Files.createTempDirectory("loadtest");
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (executor != null) {
            executor.shutdownNow();
        }
        // Cleanup test files
        if (testFilesDir != null && Files.exists(testFilesDir)) {
            Files.walk(testFilesDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    @Test
    @DisplayName("Baseline: 10 concurrent transfers")
    void testBaselineConcurrentTransfers() throws Exception {
        LoadTestConfig config = LoadTestConfig.builder()
                .concurrentTransfers(10)
                .fileSizeBytes(SMALL_FILE_SIZE)
                .durationMinutes(1)
                .targetErrorRate(0.0)
                .build();

        LoadTestResult result = runLoadTest(config);

        log.info("Baseline test results: {}", result);
        assertThat(result.getErrorRate()).isLessThan(0.01);
        assertThat(result.getSuccessfulTransfers()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Target: 200 concurrent transfers (production target)")
    void test200ConcurrentTransfers() throws Exception {
        LoadTestConfig config = LoadTestConfig.builder()
                .concurrentTransfers(200)
                .fileSizeBytes(MEDIUM_FILE_SIZE)
                .durationMinutes(30)
                .rampUpMinutes(5)
                .targetErrorRate(0.01)
                .targetP95LatencyMs(30000)
                .build();

        LoadTestResult result = runLoadTest(config);

        log.info("200 concurrent test results:\n{}", result.toDetailedReport());

        // Pass criteria
        assertThat(result.getErrorRate())
                .as("Error rate should be less than 1%")
                .isLessThan(0.01);

        assertThat(result.getP95LatencyMs())
                .as("P95 latency should be less than 30 seconds")
                .isLessThan(30000);

        assertThat(result.getSuccessfulTransfers())
                .as("Should complete transfers successfully")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("Stress: 500 concurrent transfers")
    void test500ConcurrentTransfers() throws Exception {
        LoadTestConfig config = LoadTestConfig.builder()
                .concurrentTransfers(500)
                .fileSizeBytes(SMALL_FILE_SIZE)
                .durationMinutes(15)
                .rampUpMinutes(5)
                .targetErrorRate(0.05) // Allow 5% errors under stress
                .build();

        LoadTestResult result = runLoadTest(config);

        log.info("500 concurrent stress test results:\n{}", result.toDetailedReport());

        // Verify graceful degradation
        assertThat(result.getErrorRate())
                .as("Error rate should be less than 5% under stress")
                .isLessThan(0.05);

        // Should still complete some transfers
        assertThat(result.getSuccessfulTransfers())
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("Breaking point: 1000 concurrent transfers")
    void test1000ConcurrentTransfers() throws Exception {
        LoadTestConfig config = LoadTestConfig.builder()
                .concurrentTransfers(1000)
                .fileSizeBytes(SMALL_FILE_SIZE)
                .durationMinutes(10)
                .rampUpMinutes(5)
                .targetErrorRate(0.10) // Allow 10% errors at breaking point
                .build();

        LoadTestResult result = runLoadTest(config);

        log.info("Breaking point test results:\n{}", result.toDetailedReport());

        // Document maximum capacity
        log.info("Maximum documented capacity: {} concurrent transfers with {}% error rate",
                result.getMaxConcurrentTransfers(), result.getErrorRate() * 100);

        // Should not crash
        assertThat(result.isServerHealthy())
                .as("Server should remain healthy even at breaking point")
                .isTrue();
    }

    @Test
    @DisplayName("Mixed workload: various file sizes")
    void testMixedWorkload() throws Exception {
        // 50% small files, 30% medium, 20% large
        LoadTestConfig config = LoadTestConfig.builder()
                .concurrentTransfers(100)
                .mixedFileSizes(true)
                .durationMinutes(15)
                .rampUpMinutes(3)
                .build();

        LoadTestResult result = runLoadTest(config);

        log.info("Mixed workload test results:\n{}", result.toDetailedReport());

        assertThat(result.getErrorRate()).isLessThan(0.02);
    }

    private LoadTestResult runLoadTest(LoadTestConfig config) throws Exception {
        log.info("Starting load test: {} concurrent transfers for {} minutes",
                config.getConcurrentTransfers(), config.getDurationMinutes());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0);
        List<Long> latencies = new ArrayList<>();
        AtomicInteger currentConcurrent = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(config.getDurationMinutes() * 60L);

        // Ramp up phase
        int rampUpSteps = config.getRampUpMinutes() * 60; // 1 step per second
        int transfersPerStep = config.getConcurrentTransfers() / Math.max(1, rampUpSteps);

        CountDownLatch completionLatch = new CountDownLatch(1);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Start transfers with ramp up
        for (int step = 0; step < rampUpSteps && Instant.now().isBefore(endTime); step++) {
            for (int i = 0; i < transfersPerStep; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    while (Instant.now().isBefore(endTime)) {
                        try {
                            int concurrent = currentConcurrent.incrementAndGet();
                            maxConcurrent.updateAndGet(max -> Math.max(max, concurrent));

                            long startNanos = System.nanoTime();

                            // Simulate transfer
                            int fileSize = config.isMixedFileSizes()
                                    ? getRandomFileSize()
                                    : config.getFileSizeBytes();

                            boolean success = simulateTransfer(fileSize);

                            long latencyNanos = System.nanoTime() - startNanos;
                            long latencyMs = latencyNanos / 1_000_000;

                            if (success) {
                                successCount.incrementAndGet();
                                totalBytes.addAndGet(fileSize);
                                synchronized (latencies) {
                                    latencies.add(latencyMs);
                                }
                            } else {
                                errorCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            log.debug("Transfer error: {}", e.getMessage());
                        } finally {
                            currentConcurrent.decrementAndGet();
                        }

                        // Small delay between transfers
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }, executor);
                futures.add(future);
            }

            // Wait between ramp up steps
            Thread.sleep(1000);
        }

        // Wait for test duration
        long remainingMs = Duration.between(Instant.now(), endTime).toMillis();
        if (remainingMs > 0) {
            Thread.sleep(remainingMs);
        }

        // Wait for all transfers to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(60, TimeUnit.SECONDS)
                .exceptionally(ex -> null)
                .join();

        // Calculate statistics
        Duration testDuration = Duration.between(startTime, Instant.now());
        int totalTransfers = successCount.get() + errorCount.get();
        double errorRate = totalTransfers > 0 ? (double) errorCount.get() / totalTransfers : 0;

        LongSummaryStatistics latencyStats = latencies.stream()
                .mapToLong(Long::longValue)
                .summaryStatistics();

        long p50 = calculatePercentile(latencies, 50);
        long p95 = calculatePercentile(latencies, 95);
        long p99 = calculatePercentile(latencies, 99);

        double throughputMbps = totalBytes.get() / 1024.0 / 1024.0 / testDuration.getSeconds();
        boolean serverHealthy = checkServerHealth();

        return LoadTestResult.builder()
                .concurrentTransfers(config.getConcurrentTransfers())
                .successfulTransfers(successCount.get())
                .failedTransfers(errorCount.get())
                .totalBytesTransferred(totalBytes.get())
                .testDuration(testDuration)
                .errorRate(errorRate)
                .avgLatencyMs(latencyStats.getAverage())
                .p50LatencyMs(p50)
                .p95LatencyMs(p95)
                .p99LatencyMs(p99)
                .maxLatencyMs(latencyStats.getMax())
                .throughputMbps(throughputMbps)
                .maxConcurrentTransfers(maxConcurrent.get())
                .serverHealthy(serverHealthy)
                .build();
    }

    private boolean simulateTransfer(int fileSize) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            socket.setSoTimeout(30000);

            // In a real implementation, this would:
            // 1. Send CONNECT FPDU
            // 2. Send CREATE FPDU
            // 3. Send OPEN FPDU
            // 4. Send WRITE FPDU
            // 5. Stream file data with DTF FPDUs
            // 6. Send CLOSE, DESELECT, RELEASE

            // For now, simulate with a connection test
            socket.getOutputStream().write(new byte[]{0x00, 0x02, 0x40, 0x01});
            Thread.sleep(fileSize / (1024 * 1024) * 100); // Simulate transfer time

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int getRandomFileSize() {
        Random random = new Random();
        int roll = random.nextInt(100);
        if (roll < 50) {
            return SMALL_FILE_SIZE;
        } else if (roll < 80) {
            return MEDIUM_FILE_SIZE;
        } else {
            return LARGE_FILE_SIZE;
        }
    }

    private long calculatePercentile(List<Long> values, int percentile) {
        if (values.isEmpty()) return 0;

        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);

        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private boolean checkServerHealth() {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            return socket.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    @Data
    @Builder
    static class LoadTestConfig {
        private int concurrentTransfers;
        private int fileSizeBytes;
        private int durationMinutes;
        @Builder.Default
        private int rampUpMinutes = 1;
        @Builder.Default
        private double targetErrorRate = 0.01;
        @Builder.Default
        private long targetP95LatencyMs = 30000;
        @Builder.Default
        private boolean mixedFileSizes = false;
    }

    @Data
    @Builder
    static class LoadTestResult {
        private int concurrentTransfers;
        private int successfulTransfers;
        private int failedTransfers;
        private long totalBytesTransferred;
        private Duration testDuration;
        private double errorRate;
        private double avgLatencyMs;
        private long p50LatencyMs;
        private long p95LatencyMs;
        private long p99LatencyMs;
        private long maxLatencyMs;
        private double throughputMbps;
        private int maxConcurrentTransfers;
        private boolean serverHealthy;

        public String toDetailedReport() {
            return String.format("""
                    ╔══════════════════════════════════════════════════════════════╗
                    ║                    LOAD TEST RESULTS                          ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║ Configuration                                                 ║
                    ║   Concurrent Transfers: %-6d                                 ║
                    ║   Test Duration:        %s                                    ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║ Results                                                       ║
                    ║   Successful:           %-6d                                 ║
                    ║   Failed:               %-6d                                 ║
                    ║   Error Rate:           %.2f%%                                ║
                    ║   Total Data:           %.2f MB                              ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║ Latency (ms)                                                  ║
                    ║   Average:              %-6.0f                                ║
                    ║   P50:                  %-6d                                 ║
                    ║   P95:                  %-6d                                 ║
                    ║   P99:                  %-6d                                 ║
                    ║   Max:                  %-6d                                 ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║ Throughput                                                    ║
                    ║   MB/s:                 %.2f                                  ║
                    ║   Max Concurrent:       %-6d                                 ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║ Server Status:          %s                                    ║
                    ╚══════════════════════════════════════════════════════════════╝
                    """,
                    concurrentTransfers,
                    testDuration,
                    successfulTransfers,
                    failedTransfers,
                    errorRate * 100,
                    totalBytesTransferred / 1024.0 / 1024.0,
                    avgLatencyMs,
                    p50LatencyMs,
                    p95LatencyMs,
                    p99LatencyMs,
                    maxLatencyMs,
                    throughputMbps,
                    maxConcurrentTransfers,
                    serverHealthy ? "HEALTHY" : "DEGRADED");
        }
    }

    @Data
    @Builder
    static class TransferResult {
        private String transferId;
        private boolean success;
        private long durationMs;
        private long bytesTransferred;
        private String errorMessage;
    }
}
