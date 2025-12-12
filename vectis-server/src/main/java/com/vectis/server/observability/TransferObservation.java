package com.vectis.server.observability;

import org.springframework.stereotype.Component;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Observation helper for creating traced transfer operations.
 * Integrates with OpenTelemetry for distributed tracing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferObservation {

    private final ObservationRegistry observationRegistry;
    private final PesitMetrics metrics;

    /**
     * Create an observation for a file transfer
     */
    public Observation createTransferObservation(String transferId, String partnerId,
            String filename, String direction) {
        return Observation.createNotStarted("pesit.transfer", observationRegistry)
                .lowCardinalityKeyValue("direction", direction)
                .lowCardinalityKeyValue("partner_id", partnerId)
                .highCardinalityKeyValue("transfer_id", transferId)
                .highCardinalityKeyValue("filename", filename != null ? filename : "unknown");
    }

    /**
     * Create an observation for a PeSIT session
     */
    public Observation createSessionObservation(String sessionId, String remoteAddress) {
        return Observation.createNotStarted("pesit.session", observationRegistry)
                .highCardinalityKeyValue("session_id", sessionId)
                .highCardinalityKeyValue("remote_address", remoteAddress);
    }

    /**
     * Create an observation for FPDU processing
     */
    public Observation createFpduObservation(String fpduType, String sessionId) {
        return Observation.createNotStarted("pesit.fpdu", observationRegistry)
                .lowCardinalityKeyValue("fpdu_type", fpduType)
                .highCardinalityKeyValue("session_id", sessionId);
    }

    /**
     * Record a transfer start event
     */
    public void recordTransferStart(String transferId, String partnerId, String direction) {
        metrics.transferStarted(partnerId, direction);
        log.debug("Transfer started: id={}, partner={}, direction={}",
                transferId, partnerId, direction);
    }

    /**
     * Record a transfer completion event
     */
    public void recordTransferComplete(String transferId, String partnerId,
            String direction, long bytes, long durationMs) {
        metrics.transferCompleted(partnerId, direction, bytes, durationMs);
        log.debug("Transfer completed: id={}, partner={}, bytes={}, duration={}ms",
                transferId, partnerId, bytes, durationMs);
    }

    /**
     * Record a transfer failure event
     */
    public void recordTransferFailed(String transferId, String partnerId,
            String direction, String errorCode, String errorMessage) {
        metrics.transferFailed(partnerId, direction, errorCode);
        log.warn("Transfer failed: id={}, partner={}, error={}: {}",
                transferId, partnerId, errorCode, errorMessage);
    }

    /**
     * Record FPDU received
     */
    public void recordFpduReceived(String fpduType) {
        metrics.fpduReceived(fpduType);
    }

    /**
     * Record FPDU sent
     */
    public void recordFpduSent(String fpduType) {
        metrics.fpduSent(fpduType);
    }

    /**
     * Record protocol error
     */
    public void recordProtocolError(String errorType) {
        metrics.protocolError(errorType);
    }
}
