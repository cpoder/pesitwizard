package com.vectis.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenTelemetry configuration that conditionally enables tracing.
 * Tracing is disabled by default to avoid connection errors when no collector
 * is configured.
 */
@Slf4j
@Configuration
public class ObservabilityConfig {

    /**
     * Provides a no-op tracer when tracing is disabled.
     * This prevents connection errors to non-existent OTLP endpoints.
     */
    @Bean
    @ConditionalOnProperty(name = "pesit.observability.tracing.enabled", havingValue = "false", matchIfMissing = true)
    public Tracer noopTracer() {
        log.info("OpenTelemetry tracing is DISABLED. Set OTEL_ENABLED=true and OTEL_EXPORTER_OTLP_ENDPOINT to enable.");
        return TracerProvider.noop().get("pesit-server");
    }
}
