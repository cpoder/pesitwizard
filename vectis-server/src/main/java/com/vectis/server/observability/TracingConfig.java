package com.vectis.server.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.tracing.ConditionalOnEnabledTracing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for distributed tracing with OpenTelemetry.
 */
@Slf4j
@Configuration
@ConditionalOnEnabledTracing
public class TracingConfig {

    @Value("${pesit.observability.service-name:pesit-server}")
    private String serviceName;

    @Value("${pesit.observability.tracing.enabled:true}")
    private boolean tracingEnabled;

    /**
     * Enable @Observed annotation support for automatic tracing
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        log.info("Tracing enabled for service: {}", serviceName);
        return new ObservedAspect(observationRegistry);
    }
}
