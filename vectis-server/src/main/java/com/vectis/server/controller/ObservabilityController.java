package com.vectis.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.server.config.ObservabilityProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for managing observability settings.
 * Note: Changes to tracing configuration require application restart to take
 * effect.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/observability")
@RequiredArgsConstructor
public class ObservabilityController {

    private final ObservabilityProperties observabilityProperties;

    /**
     * Get current observability configuration
     */
    @GetMapping("/config")
    public ResponseEntity<ObservabilityConfigResponse> getConfig() {
        return ResponseEntity.ok(new ObservabilityConfigResponse(
                observabilityProperties.getServiceName(),
                observabilityProperties.getTracing().isEnabled(),
                observabilityProperties.getTracing().getEndpoint(),
                observabilityProperties.getMetrics().isEnabled()));
    }

    /**
     * Update observability configuration.
     * Note: Tracing changes require application restart to take effect.
     */
    @PostMapping("/config")
    public ResponseEntity<ObservabilityConfigResponse> updateConfig(@RequestBody ObservabilityConfigRequest request) {
        log.info("Updating observability config: {}", request);

        if (request.tracingEnabled() != null) {
            observabilityProperties.getTracing().setEnabled(request.tracingEnabled());
        }
        if (request.tracingEndpoint() != null) {
            observabilityProperties.getTracing().setEndpoint(request.tracingEndpoint());
        }
        if (request.metricsEnabled() != null) {
            observabilityProperties.getMetrics().setEnabled(request.metricsEnabled());
        }

        boolean restartRequired = request.tracingEnabled() != null || request.tracingEndpoint() != null;

        log.info("Observability config updated. Restart required for tracing changes: {}", restartRequired);

        return ResponseEntity.ok(new ObservabilityConfigResponse(
                observabilityProperties.getServiceName(),
                observabilityProperties.getTracing().isEnabled(),
                observabilityProperties.getTracing().getEndpoint(),
                observabilityProperties.getMetrics().isEnabled(),
                restartRequired ? "Restart required for tracing changes to take effect" : null));
    }

    public record ObservabilityConfigRequest(
            Boolean tracingEnabled,
            String tracingEndpoint,
            Boolean metricsEnabled) {
    }

    public record ObservabilityConfigResponse(
            String serviceName,
            boolean tracingEnabled,
            String tracingEndpoint,
            boolean metricsEnabled,
            String message) {
        public ObservabilityConfigResponse(String serviceName, boolean tracingEnabled, String tracingEndpoint,
                boolean metricsEnabled) {
            this(serviceName, tracingEnabled, tracingEndpoint, metricsEnabled, null);
        }
    }
}
