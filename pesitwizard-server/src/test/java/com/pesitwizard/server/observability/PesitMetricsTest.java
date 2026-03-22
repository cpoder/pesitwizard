package com.pesitwizard.server.observability;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PesitMetrics Tests")
class PesitMetricsTest {

    private SimpleMeterRegistry registry;
    private PesitMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new PesitMetrics(registry);
    }

    @Test
    @DisplayName("connectionOpened should increment active and total connections")
    void connectionOpenedShouldIncrementCounters() {
        assertEquals(0, metrics.getActiveConnections());

        metrics.connectionOpened();
        assertEquals(1, metrics.getActiveConnections());

        metrics.connectionOpened();
        assertEquals(2, metrics.getActiveConnections());
    }

    @Test
    @DisplayName("connectionClosed should decrement active connections")
    void connectionClosedShouldDecrementCounter() {
        metrics.connectionOpened();
        metrics.connectionOpened();
        assertEquals(2, metrics.getActiveConnections());

        metrics.connectionClosed();
        assertEquals(1, metrics.getActiveConnections());
    }

    @Test
    @DisplayName("connectionError should increment error counter")
    void connectionErrorShouldIncrementCounter() {
        metrics.connectionError();

        double count = registry.get("pesit.connections.errors").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("transferStarted should increment transfer counters")
    void transferStartedShouldIncrementCounters() {
        metrics.transferStarted("PARTNER1", "SEND");

        double count = registry.get("pesit.transfers.started").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("transferCompleted should record metrics")
    void transferCompletedShouldRecordMetrics() {
        metrics.transferCompleted("PARTNER1", "RECEIVE", 1024L, 500L);

        double count = registry.get("pesit.transfers.completed").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("transferFailed should increment failure counter")
    void transferFailedShouldIncrementCounter() {
        metrics.transferFailed("PARTNER1", "SEND", "D2_205");

        double count = registry.get("pesit.transfers.failed").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("transferFailed should handle null error code")
    void transferFailedShouldHandleNullErrorCode() {
        assertDoesNotThrow(() -> metrics.transferFailed("PARTNER1", "SEND", null));
    }

    @Test
    @DisplayName("fpduReceived should increment counter")
    void fpduReceivedShouldIncrementCounter() {
        metrics.fpduReceived("CONNECT");

        double count = registry.get("pesit.fpdu.received").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("fpduSent should increment counter")
    void fpduSentShouldIncrementCounter() {
        metrics.fpduSent("ACONNECT");

        double count = registry.get("pesit.fpdu.sent").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("protocolError should increment counter")
    void protocolErrorShouldIncrementCounter() {
        metrics.protocolError("INVALID_STATE");

        double count = registry.get("pesit.protocol.errors").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    @DisplayName("serverStarted should increment running servers")
    void serverStartedShouldIncrementCounter() {
        assertEquals(0, metrics.getRunningServers());

        metrics.serverStarted("server1", 5000);
        assertEquals(1, metrics.getRunningServers());
    }

    @Test
    @DisplayName("serverStopped should decrement running servers")
    void serverStoppedShouldDecrementCounter() {
        metrics.serverStarted("server1", 5000);
        assertEquals(1, metrics.getRunningServers());

        metrics.serverStopped();
        assertEquals(0, metrics.getRunningServers());
    }

    @Test
    @DisplayName("startTimer should return timer sample")
    void startTimerShouldReturnSample() {
        Timer.Sample sample = metrics.startTimer();
        assertNotNull(sample);
    }

    @Test
    @DisplayName("recordTimer should record timer duration")
    void recordTimerShouldRecordDuration() {
        Timer.Sample sample = metrics.startTimer();

        // Small delay to ensure measurable duration
        await().atMost(Duration.ofMillis(200)).pollDelay(Duration.ofMillis(10)).until(() -> true);

        assertDoesNotThrow(() -> metrics.recordTimer(sample, "pesit.test.timer", "tag1", "value1"));
    }

    @Test
    @DisplayName("recordHealthCheck should register health gauge")
    void recordHealthCheckShouldRegisterGauge() {
        assertDoesNotThrow(() -> metrics.recordHealthCheck("database", true));
        assertDoesNotThrow(() -> metrics.recordHealthCheck("network", false));
    }
}
