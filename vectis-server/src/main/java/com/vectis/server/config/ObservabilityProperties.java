package com.vectis.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for observability (tracing and metrics).
 */
@Data
@Component
@ConfigurationProperties(prefix = "pesit.observability")
public class ObservabilityProperties {

    /**
     * Service name for tracing and metrics
     */
    private String serviceName = "pesit-server";

    /**
     * Tracing configuration
     */
    private TracingConfig tracing = new TracingConfig();

    /**
     * Metrics configuration
     */
    private MetricsConfig metrics = new MetricsConfig();

    @Data
    public static class TracingConfig {
        /**
         * Enable OpenTelemetry tracing export
         */
        private boolean enabled = false;

        /**
         * OTLP endpoint URL (e.g., http://otel-collector:4318)
         */
        private String endpoint;
    }

    @Data
    public static class MetricsConfig {
        /**
         * Enable metrics collection
         */
        private boolean enabled = true;
    }
}
