package com.pesitwizard.server.observability;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferObservation Tests")
class TransferObservationTest {

    @Mock private PesitMetrics metrics;

    private TransferObservation transferObservation;

    @BeforeEach
    void setUp() {
        // Use ObservationRegistry.NOOP which is a real functional registry that doesn't
        // record
        transferObservation = new TransferObservation(ObservationRegistry.NOOP, metrics);
    }

    @Test
    @DisplayName("createTransferObservation should create observation with all key values")
    void createTransferObservationShouldCreateWithAllKeyValues() {
        Observation observation =
                transferObservation.createTransferObservation(
                        "transfer-123", "PARTNER_A", "FILE1.dat", "RECEIVE");

        assertNotNull(observation);
    }

    @Test
    @DisplayName("createTransferObservation should handle null filename")
    void createTransferObservationShouldHandleNullFilename() {
        Observation observation =
                transferObservation.createTransferObservation(
                        "transfer-123", "PARTNER_A", null, "SEND");

        assertNotNull(observation);
    }

    @Test
    @DisplayName("createSessionObservation should create observation")
    void createSessionObservationShouldCreateObservation() {
        Observation observation =
                transferObservation.createSessionObservation("session-123", "192.168.1.100");

        assertNotNull(observation);
    }

    @Test
    @DisplayName("createFpduObservation should create observation")
    void createFpduObservationShouldCreateObservation() {
        Observation observation =
                transferObservation.createFpduObservation("CONNECT", "session-123");

        assertNotNull(observation);
    }

    @Test
    @DisplayName("recordTransferStart should call metrics")
    void recordTransferStartShouldCallMetrics() {
        transferObservation.recordTransferStart("transfer-123", "PARTNER_A", "RECEIVE");

        verify(metrics).transferStarted("PARTNER_A", "RECEIVE");
    }

    @Test
    @DisplayName("recordTransferComplete should call metrics with all parameters")
    void recordTransferCompleteShouldCallMetrics() {
        transferObservation.recordTransferComplete("transfer-123", "PARTNER_A", "SEND", 1024, 500);

        verify(metrics).transferCompleted("PARTNER_A", "SEND", 1024, 500);
    }

    @Test
    @DisplayName("recordTransferFailed should call metrics with error info")
    void recordTransferFailedShouldCallMetrics() {
        transferObservation.recordTransferFailed(
                "transfer-123", "PARTNER_A", "RECEIVE", "D2_205", "File not found");

        verify(metrics).transferFailed("PARTNER_A", "RECEIVE", "D2_205");
    }

    @Test
    @DisplayName("recordFpduReceived should call metrics")
    void recordFpduReceivedShouldCallMetrics() {
        transferObservation.recordFpduReceived("CONNECT");

        verify(metrics).fpduReceived("CONNECT");
    }

    @Test
    @DisplayName("recordFpduSent should call metrics")
    void recordFpduSentShouldCallMetrics() {
        transferObservation.recordFpduSent("ACONNECT");

        verify(metrics).fpduSent("ACONNECT");
    }

    @Test
    @DisplayName("recordProtocolError should call metrics")
    void recordProtocolErrorShouldCallMetrics() {
        transferObservation.recordProtocolError("INVALID_STATE");

        verify(metrics).protocolError("INVALID_STATE");
    }
}
