package com.pesitwizard.security;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check indicator for secrets providers.
 * Can be integrated with Spring Boot Actuator.
 */
public class SecretsHealthIndicator {

    private final SecretsProvider provider;

    public SecretsHealthIndicator(SecretsProvider provider) {
        this.provider = provider;
    }

    public HealthStatus health() {
        Map<String, Object> details = new HashMap<>();
        details.put("provider", provider.getProviderType());
        details.put("available", provider.isAvailable());

        if (provider instanceof VaultSecretsProvider vaultProvider) {
            details.put("vaultConnected", vaultProvider.isAvailable());
            details.put("cacheEnabled", true);
            details.put("circuitBreakerEnabled", true);
        }

        if (provider instanceof CompositeSecretsProvider) {
            details.put("mode", "composite");
            details.put("migrationSupported", true);
        }

        if (provider instanceof AesSecretsProvider) {
            details.put("encryptionAlgorithm", "AES-256-GCM");
            details.put("keyDerivation", "PBKDF2-HMAC-SHA256");
        }

        boolean healthy = provider.isAvailable();
        return new HealthStatus(healthy ? "UP" : "DOWN", details);
    }

    public record HealthStatus(String status, Map<String, Object> details) {
    }
}
