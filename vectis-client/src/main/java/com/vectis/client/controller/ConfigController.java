package com.vectis.client.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.client.config.OtlpConfigService;

import lombok.RequiredArgsConstructor;

/**
 * REST API for client configuration.
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final OtlpConfigService otlpConfigService;

    /**
     * Get current OTLP configuration
     */
    @GetMapping("/otlp")
    public ResponseEntity<Map<String, Object>> getOtlpConfig() {
        return ResponseEntity.ok(Map.of(
                "endpoint", otlpConfigService.getEndpoint() != null ? otlpConfigService.getEndpoint() : "",
                "metricsEnabled", otlpConfigService.isMetricsEnabled(),
                "tracingEnabled", otlpConfigService.isTracingEnabled()));
    }

    /**
     * Update OTLP configuration
     */
    @PutMapping("/otlp")
    public ResponseEntity<Map<String, Object>> updateOtlpConfig(@RequestBody OtlpConfigRequest request) {
        otlpConfigService.updateConfig(
                request.endpoint(),
                request.metricsEnabled(),
                request.tracingEnabled());

        return ResponseEntity.ok(Map.of(
                "endpoint", otlpConfigService.getEndpoint() != null ? otlpConfigService.getEndpoint() : "",
                "metricsEnabled", otlpConfigService.isMetricsEnabled(),
                "tracingEnabled", otlpConfigService.isTracingEnabled(),
                "message", "OTLP configuration updated. Restart required for changes to take effect."));
    }

    public record OtlpConfigRequest(
            String endpoint,
            boolean metricsEnabled,
            boolean tracingEnabled) {
    }
}
