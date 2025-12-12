package com.vectis.server.observability;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom metrics for PeSIT server monitoring.
 * Exposes metrics for Prometheus scraping.
 */
@Slf4j
@Component
public class PesitMetrics {

    private final MeterRegistry registry;

    // Connection metrics
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final Counter totalConnections;
    private final Counter connectionErrors;

    // Transfer metrics
    private final Counter transfersStarted;
    private final Counter transfersCompleted;
    private final Counter transfersFailed;
    private final DistributionSummary transferBytes;
    private final Timer transferDuration;

    // Protocol metrics
    private final Counter fpduReceived;
    private final Counter fpduSent;
    private final Counter protocolErrors;

    // Server metrics
    private final AtomicInteger runningServers = new AtomicInteger(0);

    public PesitMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Register gauges for current state
        Gauge.builder("pesit.connections.active", activeConnections, AtomicInteger::get)
                .description("Number of active PeSIT connections")
                .register(registry);

        Gauge.builder("pesit.servers.running", runningServers, AtomicInteger::get)
                .description("Number of running PeSIT server instances")
                .register(registry);

        // Connection counters
        totalConnections = Counter.builder("pesit.connections.total")
                .description("Total number of PeSIT connections")
                .register(registry);

        connectionErrors = Counter.builder("pesit.connections.errors")
                .description("Total number of connection errors")
                .register(registry);

        // Transfer counters
        transfersStarted = Counter.builder("pesit.transfers.started")
                .description("Total number of transfers started")
                .register(registry);

        transfersCompleted = Counter.builder("pesit.transfers.completed")
                .description("Total number of transfers completed successfully")
                .register(registry);

        transfersFailed = Counter.builder("pesit.transfers.failed")
                .description("Total number of failed transfers")
                .register(registry);

        // Transfer size distribution
        transferBytes = DistributionSummary.builder("pesit.transfers.bytes")
                .description("Distribution of transfer sizes in bytes")
                .baseUnit("bytes")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(registry);

        // Transfer duration timer
        transferDuration = Timer.builder("pesit.transfers.duration")
                .description("Transfer duration")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(registry);

        // Protocol counters
        fpduReceived = Counter.builder("pesit.fpdu.received")
                .description("Total FPDUs received")
                .register(registry);

        fpduSent = Counter.builder("pesit.fpdu.sent")
                .description("Total FPDUs sent")
                .register(registry);

        protocolErrors = Counter.builder("pesit.protocol.errors")
                .description("Total protocol errors")
                .register(registry);

        log.info("PeSIT metrics initialized");
    }

    // ========== Connection Metrics ==========

    public void connectionOpened() {
        activeConnections.incrementAndGet();
        totalConnections.increment();
    }

    public void connectionClosed() {
        activeConnections.decrementAndGet();
    }

    public void connectionError() {
        connectionErrors.increment();
    }

    public int getActiveConnections() {
        return activeConnections.get();
    }

    // ========== Transfer Metrics ==========

    public void transferStarted(String partnerId, String direction) {
        transfersStarted.increment();
        Counter.builder("pesit.transfers.started.by_partner")
                .tag("partner", partnerId)
                .tag("direction", direction)
                .register(registry)
                .increment();
    }

    public void transferCompleted(String partnerId, String direction, long bytes, long durationMs) {
        transfersCompleted.increment();
        transferBytes.record(bytes);
        transferDuration.record(durationMs, TimeUnit.MILLISECONDS);

        Counter.builder("pesit.transfers.completed.by_partner")
                .tag("partner", partnerId)
                .tag("direction", direction)
                .register(registry)
                .increment();

        DistributionSummary.builder("pesit.transfers.bytes.by_partner")
                .tag("partner", partnerId)
                .tag("direction", direction)
                .register(registry)
                .record(bytes);
    }

    public void transferFailed(String partnerId, String direction, String errorCode) {
        transfersFailed.increment();

        Counter.builder("pesit.transfers.failed.by_partner")
                .tag("partner", partnerId)
                .tag("direction", direction)
                .tag("error", errorCode != null ? errorCode : "unknown")
                .register(registry)
                .increment();
    }

    // ========== Protocol Metrics ==========

    public void fpduReceived(String fpduType) {
        fpduReceived.increment();
        Counter.builder("pesit.fpdu.received.by_type")
                .tag("type", fpduType)
                .register(registry)
                .increment();
    }

    public void fpduSent(String fpduType) {
        fpduSent.increment();
        Counter.builder("pesit.fpdu.sent.by_type")
                .tag("type", fpduType)
                .register(registry)
                .increment();
    }

    public void protocolError(String errorType) {
        protocolErrors.increment();
        Counter.builder("pesit.protocol.errors.by_type")
                .tag("type", errorType)
                .register(registry)
                .increment();
    }

    // ========== Server Metrics ==========

    public void serverStarted(String serverId, int port) {
        runningServers.incrementAndGet();
        Gauge.builder("pesit.server.status", () -> 1)
                .tags(Tags.of("server_id", serverId, "port", String.valueOf(port)))
                .description("Server status (1=running, 0=stopped)")
                .register(registry);
    }

    public void serverStopped(String serverId) {
        runningServers.decrementAndGet();
    }

    public int getRunningServers() {
        return runningServers.get();
    }

    // ========== Custom Timer for Operations ==========

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordTimer(Timer.Sample sample, String name, String... tags) {
        sample.stop(Timer.builder(name)
                .tags(tags)
                .register(registry));
    }

    // ========== Health Indicators ==========

    public void recordHealthCheck(String component, boolean healthy) {
        Gauge.builder("pesit.health." + component, () -> healthy ? 1 : 0)
                .description("Health status of " + component)
                .register(registry);
    }
}
