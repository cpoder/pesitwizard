package com.pesitwizard.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PeSIT Client Application
 * REST API for PeSIT file transfers
 */
@SpringBootApplication(exclude = {
        // Exclude OTLP auto-configuration - enable via OTEL_METRICS_ENABLED=true
        OtlpMetricsExportAutoConfiguration.class
})
@ComponentScan(basePackages = { "com.pesitwizard.client", "com.pesitwizard.security" })
@EnableScheduling
public class PesitClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(PesitClientApplication.class, args);
    }
}
