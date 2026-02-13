package com.pesitwizard.client.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing OTLP configuration. Persists settings to a properties file for use on
 * restart.
 */
@Slf4j
@Service
@Getter
public class OtlpConfigService {

    private static final Path CONFIG_FILE = Path.of("./data/otlp-config.properties");

    private String endpoint;
    private boolean metricsEnabled;
    private boolean tracingEnabled;

    @PostConstruct
    public void init() {
        loadConfig();
    }

    /** Load configuration from file */
    private void loadConfig() {
        if (Files.exists(CONFIG_FILE)) {
            try {
                Properties props = new Properties();
                try (var in = Files.newInputStream(CONFIG_FILE)) {
                    props.load(in);
                }
                endpoint = props.getProperty("otlp.endpoint", "");
                metricsEnabled =
                        Boolean.parseBoolean(props.getProperty("otlp.metrics.enabled", "false"));
                tracingEnabled =
                        Boolean.parseBoolean(props.getProperty("otlp.tracing.enabled", "false"));
                log.info(
                        "Loaded OTLP config: endpoint={}, metrics={}, tracing={}",
                        endpoint,
                        metricsEnabled,
                        tracingEnabled);
            } catch (IOException e) {
                log.warn("Failed to load OTLP config: {}", e.getMessage());
            }
        }
    }

    /** Update and persist configuration */
    public void updateConfig(String endpoint, boolean metricsEnabled, boolean tracingEnabled) {
        this.endpoint = endpoint;
        this.metricsEnabled = metricsEnabled;
        this.tracingEnabled = tracingEnabled;
        saveConfig();
    }

    /** Save configuration to file */
    private void saveConfig() {
        try {
            Path parent = CONFIG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties props = new Properties();
            props.setProperty("otlp.endpoint", endpoint != null ? endpoint : "");
            props.setProperty("otlp.metrics.enabled", String.valueOf(metricsEnabled));
            props.setProperty("otlp.tracing.enabled", String.valueOf(tracingEnabled));
            try (var out = Files.newOutputStream(CONFIG_FILE)) {
                props.store(out, "OTLP Configuration");
            }
            log.info(
                    "Saved OTLP config: endpoint={}, metrics={}, tracing={}",
                    endpoint,
                    metricsEnabled,
                    tracingEnabled);
        } catch (IOException e) {
            log.error("Failed to save OTLP config: {}", e.getMessage());
        }
    }
}
