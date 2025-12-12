package com.vectis.server.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for TLS/SSL.
 */
@Data
@Component
@ConfigurationProperties(prefix = "pesit.ssl")
public class SslProperties {

    /**
     * Enable TLS for PeSIT protocol
     */
    private boolean enabled = false;

    /**
     * Keystore name (from database)
     */
    private String keystoreName = "default-keystore";

    /**
     * Truststore name (from database) - optional
     */
    private String truststoreName;

    /**
     * Require client certificate (mutual TLS)
     */
    private boolean clientAuth = false;

    /**
     * TLS protocol version
     */
    private String protocol = "TLSv1.3";

    /**
     * Enabled cipher suites (empty = use defaults)
     */
    private List<String> cipherSuites = new ArrayList<>();
}
