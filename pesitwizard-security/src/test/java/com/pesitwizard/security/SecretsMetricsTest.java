package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for SecretsMetrics.
 */
@DisplayName("SecretsMetrics Tests")
class SecretsMetricsTest {

    private MeterRegistry registry;
    private SecretsMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new SecretsMetrics(registry);
    }

    @Nested
    @DisplayName("Counter Operations")
    class CounterTests {

        @Test
        @DisplayName("should increment encrypt counter")
        void shouldIncrementEncryptCounter() {
            metrics.recordEncrypt();
            metrics.recordEncrypt();

            double count = registry.get("pesitwizard.secrets.encrypt").counter().count();
            assertThat(count).isEqualTo(2.0);
        }

        @Test
        @DisplayName("should increment decrypt counter")
        void shouldIncrementDecryptCounter() {
            metrics.recordDecrypt();
            metrics.recordDecrypt();
            metrics.recordDecrypt();

            double count = registry.get("pesitwizard.secrets.decrypt").counter().count();
            assertThat(count).isEqualTo(3.0);
        }

        @Test
        @DisplayName("should increment encrypt error counter")
        void shouldIncrementEncryptErrorCounter() {
            metrics.recordEncryptError();

            double count = registry.get("pesitwizard.secrets.encrypt.errors").counter().count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should increment decrypt error counter")
        void shouldIncrementDecryptErrorCounter() {
            metrics.recordDecryptError();
            metrics.recordDecryptError();

            double count = registry.get("pesitwizard.secrets.decrypt.errors").counter().count();
            assertThat(count).isEqualTo(2.0);
        }

        @Test
        @DisplayName("should increment cache hit counter")
        void shouldIncrementCacheHitCounter() {
            metrics.recordCacheHit();

            double count = registry.get("pesitwizard.vault.cache.hit").counter().count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should increment cache miss counter")
        void shouldIncrementCacheMissCounter() {
            metrics.recordCacheMiss();

            double count = registry.get("pesitwizard.vault.cache.miss").counter().count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should increment circuit open counter")
        void shouldIncrementCircuitOpenCounter() {
            metrics.recordCircuitOpen();

            double count = registry.get("pesitwizard.vault.circuit.open").counter().count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Timer Operations")
    class TimerTests {

        @Test
        @DisplayName("should record encrypt time with supplier")
        void shouldRecordEncryptTimeWithSupplier() {
            String result = metrics.timeEncrypt(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "encrypted";
            });

            assertThat(result).isEqualTo("encrypted");
            assertThat(registry.get("pesitwizard.secrets.encrypt.time").timer().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record decrypt time with supplier")
        void shouldRecordDecryptTimeWithSupplier() {
            String result = metrics.timeDecrypt(() -> "decrypted");

            assertThat(result).isEqualTo("decrypted");
            assertThat(registry.get("pesitwizard.secrets.decrypt.time").timer().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record encrypt time in nanoseconds")
        void shouldRecordEncryptTimeNanos() {
            metrics.recordEncryptTime(1_000_000); // 1ms

            assertThat(registry.get("pesitwizard.secrets.encrypt.time").timer().count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record decrypt time in nanoseconds")
        void shouldRecordDecryptTimeNanos() {
            metrics.recordDecryptTime(2_000_000); // 2ms

            assertThat(registry.get("pesitwizard.secrets.decrypt.time").timer().count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("should register all metrics on creation")
        void shouldRegisterAllMetricsOnCreation() {
            assertThat(registry.get("pesitwizard.secrets.encrypt").counter()).isNotNull();
            assertThat(registry.get("pesitwizard.secrets.decrypt").counter()).isNotNull();
            assertThat(registry.get("pesitwizard.secrets.encrypt.errors").counter()).isNotNull();
            assertThat(registry.get("pesitwizard.secrets.decrypt.errors").counter()).isNotNull();
            assertThat(registry.get("pesitwizard.vault.cache.hit").counter()).isNotNull();
            assertThat(registry.get("pesitwizard.vault.cache.miss").counter()).isNotNull();
            assertThat(registry.get("pesitwizard.vault.circuit.open").counter()).isNotNull();
            assertThat(registry.get("pesitwizard.secrets.encrypt.time").timer()).isNotNull();
            assertThat(registry.get("pesitwizard.secrets.decrypt.time").timer()).isNotNull();
        }
    }
}
