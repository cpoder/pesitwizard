package com.pesitwizard.server.observability;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Protocol-level metrics for PeSIT operations.
 *
 * Tracks:
 * - FPDU types sent/received
 * - Sync point operations
 * - Transfer restarts
 * - Protocol errors
 * - Transfer duration
 * - Active connections
 */
@Slf4j
@Component
public class ProtocolMetrics {

    private static final String METRIC_PREFIX = "pesitwizard";

    // FPDU counters by type
    private final Counter fpduConnectCounter;
    private final Counter fpduCreateCounter;
    private final Counter fpduOpenCounter;
    private final Counter fpduWriteCounter;
    private final Counter fpduReadCounter;
    private final Counter fpduCloseCounter;
    private final Counter fpduAbortCounter;
    private final Counter fpduDtfCounter;

    // Sync point metrics
    private final Counter syncPointSentCounter;
    private final Counter syncPointReceivedCounter;
    private final Counter syncPointAckCounter;

    // Restart metrics
    private final Counter restartRequestedCounter;
    private final Counter restartSuccessCounter;
    private final Counter restartFailedCounter;

    // Protocol error metrics
    private final Counter protocolErrorCounter;
    private final Counter diagnosticErrorCounter;
    private final Counter timeoutErrorCounter;
    private final Counter connectionErrorCounter;

    // Transfer metrics
    private final Counter transferStartedCounter;
    private final Counter transferCompletedCounter;
    private final Counter transferFailedCounter;
    private final Counter transferCancelledCounter;
    private final Timer transferDurationTimer;
    private final Counter bytesTransferredCounter;

    // Active connections gauge
    @Getter
    private final AtomicLong activeConnections = new AtomicLong(0);

    @Getter
    private final AtomicLong activeTransfers = new AtomicLong(0);

    public ProtocolMetrics(MeterRegistry registry) {
        // FPDU counters
        this.fpduConnectCounter = Counter.builder(METRIC_PREFIX + ".fpdu.total")
                .tag("type", "CONNECT")
                .description("Number of CONNECT FPDUs")
                .register(registry);

        this.fpduCreateCounter = Counter.builder(METRIC_PREFIX + ".fpdu.total")
                .tag("type", "CREATE")
                .description("Number of CREATE FPDUs")
                .register(registry);

        this.fpduOpenCounter = Counter.builder(METRIC_PREFIX + ".fpdu.total")
                .tag("type", "OPEN")
                .description("Number of OPEN FPDUs")
                .register(registry);

        this.fpduWriteCounter = Counter.builder(METRIC_PREFIX + ".fpdu.total")
                .tag("type", "WRITE")
                .description("Number of WRITE FPDUs")
                .register(registry);

        this.fpduReadCounter = Counter.builder(METRIC_PREFIX + ".fpdu.total")
                .tag("type", "READ")
                .description("Number of READ FPDUs")
                .register(registry);

        this.fpduCloseCounter = Counter.builder(METRIC_PREFIX + ".fpdu.total")
                .tag("type", "CLOSE")
                .description("Number of CLOSE FPDUs")
                .register(registry);

        this.fpduAbortCounter = Counter.builder(METRIC_PREFIX + ".fpdu.total")
                .tag("type", "ABORT")
                .description("Number of ABORT FPDUs")
                .register(registry);

        this.fpduDtfCounter = Counter.builder(METRIC_PREFIX + ".fpdu.total")
                .tag("type", "DTF")
                .description("Number of Data Transfer Frame FPDUs")
                .register(registry);

        // Sync point metrics
        this.syncPointSentCounter = Counter.builder(METRIC_PREFIX + ".syncpoint.total")
                .tag("operation", "sent")
                .description("Number of sync points sent")
                .register(registry);

        this.syncPointReceivedCounter = Counter.builder(METRIC_PREFIX + ".syncpoint.total")
                .tag("operation", "received")
                .description("Number of sync points received")
                .register(registry);

        this.syncPointAckCounter = Counter.builder(METRIC_PREFIX + ".syncpoint.total")
                .tag("operation", "ack")
                .description("Number of sync point acknowledgments")
                .register(registry);

        // Restart metrics
        this.restartRequestedCounter = Counter.builder(METRIC_PREFIX + ".restart.total")
                .tag("status", "requested")
                .description("Number of restart requests")
                .register(registry);

        this.restartSuccessCounter = Counter.builder(METRIC_PREFIX + ".restart.total")
                .tag("status", "success")
                .description("Number of successful restarts")
                .register(registry);

        this.restartFailedCounter = Counter.builder(METRIC_PREFIX + ".restart.total")
                .tag("status", "failed")
                .description("Number of failed restarts")
                .register(registry);

        // Protocol error metrics
        this.protocolErrorCounter = Counter.builder(METRIC_PREFIX + ".error.total")
                .tag("type", "protocol")
                .description("Number of protocol errors")
                .register(registry);

        this.diagnosticErrorCounter = Counter.builder(METRIC_PREFIX + ".error.total")
                .tag("type", "diagnostic")
                .description("Number of diagnostic errors from partner")
                .register(registry);

        this.timeoutErrorCounter = Counter.builder(METRIC_PREFIX + ".error.total")
                .tag("type", "timeout")
                .description("Number of timeout errors")
                .register(registry);

        this.connectionErrorCounter = Counter.builder(METRIC_PREFIX + ".error.total")
                .tag("type", "connection")
                .description("Number of connection errors")
                .register(registry);

        // Transfer metrics
        this.transferStartedCounter = Counter.builder(METRIC_PREFIX + ".transfer.total")
                .tag("status", "started")
                .description("Number of transfers started")
                .register(registry);

        this.transferCompletedCounter = Counter.builder(METRIC_PREFIX + ".transfer.total")
                .tag("status", "completed")
                .description("Number of transfers completed")
                .register(registry);

        this.transferFailedCounter = Counter.builder(METRIC_PREFIX + ".transfer.total")
                .tag("status", "failed")
                .description("Number of transfers failed")
                .register(registry);

        this.transferCancelledCounter = Counter.builder(METRIC_PREFIX + ".transfer.total")
                .tag("status", "cancelled")
                .description("Number of transfers cancelled")
                .register(registry);

        this.transferDurationTimer = Timer.builder(METRIC_PREFIX + ".transfer.duration")
                .description("Transfer duration")
                .publishPercentileHistogram()
                .register(registry);

        this.bytesTransferredCounter = Counter.builder(METRIC_PREFIX + ".bytes.transferred")
                .description("Total bytes transferred")
                .register(registry);

        // Gauges for active counts
        Gauge.builder(METRIC_PREFIX + ".connections.active", activeConnections, AtomicLong::get)
                .description("Number of active connections")
                .register(registry);

        Gauge.builder(METRIC_PREFIX + ".transfers.active", activeTransfers, AtomicLong::get)
                .description("Number of active transfers")
                .register(registry);

        log.info("Protocol metrics initialized");
    }

    // FPDU recording methods
    public void recordFpduConnect() {
        fpduConnectCounter.increment();
    }

    public void recordFpduCreate() {
        fpduCreateCounter.increment();
    }

    public void recordFpduOpen() {
        fpduOpenCounter.increment();
    }

    public void recordFpduWrite() {
        fpduWriteCounter.increment();
    }

    public void recordFpduRead() {
        fpduReadCounter.increment();
    }

    public void recordFpduClose() {
        fpduCloseCounter.increment();
    }

    public void recordFpduAbort() {
        fpduAbortCounter.increment();
    }

    public void recordFpduDtf() {
        fpduDtfCounter.increment();
    }

    // Sync point recording
    public void recordSyncPointSent() {
        syncPointSentCounter.increment();
    }

    public void recordSyncPointReceived() {
        syncPointReceivedCounter.increment();
    }

    public void recordSyncPointAck() {
        syncPointAckCounter.increment();
    }

    // Restart recording
    public void recordRestartRequested() {
        restartRequestedCounter.increment();
    }

    public void recordRestartSuccess() {
        restartSuccessCounter.increment();
    }

    public void recordRestartFailed() {
        restartFailedCounter.increment();
    }

    // Error recording
    public void recordProtocolError() {
        protocolErrorCounter.increment();
    }

    public void recordDiagnosticError(int diagnosticCode) {
        diagnosticErrorCounter.increment();
    }

    public void recordTimeoutError() {
        timeoutErrorCounter.increment();
    }

    public void recordConnectionError() {
        connectionErrorCounter.increment();
    }

    // Transfer recording
    public void recordTransferStarted() {
        transferStartedCounter.increment();
        activeTransfers.incrementAndGet();
    }

    public void recordTransferCompleted(long durationMs, long bytesTransferred) {
        transferCompletedCounter.increment();
        transferDurationTimer.record(java.time.Duration.ofMillis(durationMs));
        bytesTransferredCounter.increment(bytesTransferred);
        activeTransfers.decrementAndGet();
    }

    public void recordTransferFailed() {
        transferFailedCounter.increment();
        activeTransfers.decrementAndGet();
    }

    public void recordTransferCancelled() {
        transferCancelledCounter.increment();
        activeTransfers.decrementAndGet();
    }

    // Connection tracking
    public void connectionOpened() {
        activeConnections.incrementAndGet();
    }

    public void connectionClosed() {
        activeConnections.decrementAndGet();
    }

    /**
     * Record a timed operation.
     */
    public Timer.Sample startTimer() {
        return Timer.start();
    }

    /**
     * Record transfer with timer sample.
     */
    public void recordTransfer(Timer.Sample sample, boolean success, long bytesTransferred) {
        if (success) {
            long duration = sample.stop(transferDurationTimer);
            bytesTransferredCounter.increment(bytesTransferred);
            transferCompletedCounter.increment();
        } else {
            transferFailedCounter.increment();
        }
        activeTransfers.decrementAndGet();
    }
}
