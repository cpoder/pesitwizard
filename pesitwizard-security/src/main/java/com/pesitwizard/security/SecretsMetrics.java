package com.pesitwizard.security;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Prometheus metrics for secrets operations.
 * Tracks encryption/decryption counts, timings, and errors.
 */
@Slf4j
public class SecretsMetrics {

    private final Counter encryptCounter;
    private final Counter decryptCounter;
    private final Counter encryptErrorCounter;
    private final Counter decryptErrorCounter;
    private final Counter vaultCacheHitCounter;
    private final Counter vaultCacheMissCounter;
    private final Counter vaultCircuitOpenCounter;
    private final Timer encryptTimer;
    private final Timer decryptTimer;

    public SecretsMetrics(MeterRegistry registry) {
        this.encryptCounter = Counter.builder("pesitwizard.secrets.encrypt")
                .description("Number of encryption operations")
                .tag("provider", "all")
                .register(registry);

        this.decryptCounter = Counter.builder("pesitwizard.secrets.decrypt")
                .description("Number of decryption operations")
                .tag("provider", "all")
                .register(registry);

        this.encryptErrorCounter = Counter.builder("pesitwizard.secrets.encrypt.errors")
                .description("Number of encryption errors")
                .register(registry);

        this.decryptErrorCounter = Counter.builder("pesitwizard.secrets.decrypt.errors")
                .description("Number of decryption errors")
                .register(registry);

        this.vaultCacheHitCounter = Counter.builder("pesitwizard.vault.cache.hit")
                .description("Vault cache hits")
                .register(registry);

        this.vaultCacheMissCounter = Counter.builder("pesitwizard.vault.cache.miss")
                .description("Vault cache misses")
                .register(registry);

        this.vaultCircuitOpenCounter = Counter.builder("pesitwizard.vault.circuit.open")
                .description("Vault circuit breaker open events")
                .register(registry);

        this.encryptTimer = Timer.builder("pesitwizard.secrets.encrypt.time")
                .description("Time spent encrypting")
                .register(registry);

        this.decryptTimer = Timer.builder("pesitwizard.secrets.decrypt.time")
                .description("Time spent decrypting")
                .register(registry);

        log.info("SecretsMetrics initialized");
    }

    public void recordEncrypt() {
        encryptCounter.increment();
    }

    public void recordDecrypt() {
        decryptCounter.increment();
    }

    public void recordEncryptError() {
        encryptErrorCounter.increment();
    }

    public void recordDecryptError() {
        decryptErrorCounter.increment();
    }

    public void recordCacheHit() {
        vaultCacheHitCounter.increment();
    }

    public void recordCacheMiss() {
        vaultCacheMissCounter.increment();
    }

    public void recordCircuitOpen() {
        vaultCircuitOpenCounter.increment();
    }

    public <T> T timeEncrypt(Supplier<T> operation) {
        return encryptTimer.record(operation);
    }

    public <T> T timeDecrypt(Supplier<T> operation) {
        return decryptTimer.record(operation);
    }

    public void recordEncryptTime(long nanos) {
        encryptTimer.record(nanos, TimeUnit.NANOSECONDS);
    }

    public void recordDecryptTime(long nanos) {
        decryptTimer.record(nanos, TimeUnit.NANOSECONDS);
    }
}
