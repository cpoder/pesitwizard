package com.pesitwizard.security;

import java.util.UUID;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * Distributed tracing support for secrets operations.
 * Provides span creation and context propagation.
 * Can be integrated with OpenTelemetry or other tracing systems.
 */
@Slf4j
public class SecretsTracing {

    public <T> T trace(String operationName, String provider, Supplier<T> operation) {
        String traceId = generateTraceId();
        long startTime = System.nanoTime();

        log.debug("[trace:{}] Starting {} with provider {}", traceId, operationName, provider);

        try {
            T result = operation.get();
            long duration = (System.nanoTime() - startTime) / 1_000_000;
            log.debug("[trace:{}] Completed {} in {}ms", traceId, operationName, duration);
            return result;
        } catch (Exception e) {
            long duration = (System.nanoTime() - startTime) / 1_000_000;
            log.error("[trace:{}] Failed {} after {}ms: {}", traceId, operationName, duration, e.getMessage());
            throw e;
        }
    }

    public void traceVoid(String operationName, String provider, Runnable operation) {
        trace(operationName, provider, () -> {
            operation.run();
            return null;
        });
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public record SpanContext(String traceId, String spanId, String parentSpanId) {
        public static SpanContext create() {
            return new SpanContext(
                    UUID.randomUUID().toString().replace("-", ""),
                    UUID.randomUUID().toString().substring(0, 16),
                    null);
        }
    }
}
