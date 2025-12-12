package com.vectis.client;

import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;

import com.vectis.client.config.ClientConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * List command - shows current configuration
 */
@Component
@Command(name = "list", aliases = { "config", "info" }, description = "Show current configuration")
public class ListCommand implements Callable<Integer> {

    private final ClientConfig config;

    @Option(names = { "--json" }, description = "Output in JSON format")
    private boolean jsonOutput;

    public ListCommand(ClientConfig config) {
        this.config = config;
    }

    @Override
    public Integer call() {
        if (jsonOutput) {
            printJson();
        } else {
            printConfig();
        }
        return 0;
    }

    private void printConfig() {
        System.out.println("PeSIT Client Configuration");
        System.out.println("==========================");
        System.out.println();
        System.out.println("Connection:");
        System.out.println("  Host:              " + config.getHost());
        System.out.println("  Port:              " + config.getPort());
        System.out.println("  Client ID:         " + config.getClientId());
        System.out.println("  Connection Timeout: " + config.getConnectionTimeout() + " ms");
        System.out.println("  Read Timeout:      " + config.getReadTimeout() + " ms");
        System.out.println();
        System.out.println("TLS/SSL:");
        System.out.println("  Enabled:           " + config.isTlsEnabled());
        if (config.isTlsEnabled()) {
            System.out.println("  Keystore:          "
                    + (config.getKeystorePath() != null ? config.getKeystorePath() : "Not configured"));
            System.out.println("  Truststore:        "
                    + (config.getTruststorePath() != null ? config.getTruststorePath() : "Not configured"));
        }
        System.out.println();
        System.out.println("Transfer:");
        System.out.println("  Receive Directory: " + config.getReceiveDirectory());
        System.out.println("  Retry Count:       " + config.getRetryCount());
        System.out.println("  Retry Delay:       " + config.getRetryDelay() + " ms");
        System.out.println("  Strict Mode:       " + config.isStrictMode());
    }

    private void printJson() {
        System.out.println("{");
        System.out.println("  \"connection\": {");
        System.out.println("    \"host\": \"" + config.getHost() + "\",");
        System.out.println("    \"port\": " + config.getPort() + ",");
        System.out.println("    \"clientId\": \"" + config.getClientId() + "\",");
        System.out.println("    \"connectionTimeout\": " + config.getConnectionTimeout() + ",");
        System.out.println("    \"readTimeout\": " + config.getReadTimeout());
        System.out.println("  },");
        System.out.println("  \"tls\": {");
        System.out.println("    \"enabled\": " + config.isTlsEnabled() + ",");
        System.out.println("    \"keystorePath\": "
                + (config.getKeystorePath() != null ? "\"" + config.getKeystorePath() + "\"" : "null") + ",");
        System.out.println("    \"truststorePath\": "
                + (config.getTruststorePath() != null ? "\"" + config.getTruststorePath() + "\"" : "null"));
        System.out.println("  },");
        System.out.println("  \"transfer\": {");
        System.out.println("    \"receiveDirectory\": \"" + config.getReceiveDirectory() + "\",");
        System.out.println("    \"retryCount\": " + config.getRetryCount() + ",");
        System.out.println("    \"retryDelay\": " + config.getRetryDelay() + ",");
        System.out.println("    \"strictMode\": " + config.isStrictMode());
        System.out.println("  }");
        System.out.println("}");
    }
}
