package com.vectis.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for PeSIT client
 */
@Data
@Component
@ConfigurationProperties(prefix = "pesit.client")
public class ClientConfig {

    /**
     * Default server host
     */
    private String host = "localhost";

    /**
     * Default server port
     */
    private int port = 5000;

    /**
     * Client ID (local partner ID)
     */
    private String clientId = "PESIT_CLIENT";

    /**
     * Client password (if required)
     */
    private String password;

    /**
     * Connection timeout in milliseconds
     */
    private int connectionTimeout = 30000;

    /**
     * Read timeout in milliseconds
     */
    private int readTimeout = 60000;

    /**
     * Enable TLS/SSL
     */
    private boolean tlsEnabled = false;

    /**
     * Path to keystore for TLS
     */
    private String keystorePath;

    /**
     * Keystore password
     */
    private String keystorePassword;

    /**
     * Path to truststore for TLS
     */
    private String truststorePath;

    /**
     * Truststore password
     */
    private String truststorePassword;

    /**
     * Enable strict protocol checking
     */
    private boolean strictMode = false;

    /**
     * Default receive directory
     */
    private String receiveDirectory = "./received";

    /**
     * Retry count for failed transfers
     */
    private int retryCount = 3;

    /**
     * Retry delay in milliseconds
     */
    private int retryDelay = 5000;
}
