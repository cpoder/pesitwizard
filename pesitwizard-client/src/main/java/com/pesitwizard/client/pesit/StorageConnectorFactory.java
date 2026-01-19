package com.pesitwizard.client.pesit;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.client.connector.ConnectorRegistry;
import com.pesitwizard.client.entity.StorageConnection;
import com.pesitwizard.client.repository.StorageConnectionRepository;
import com.pesitwizard.connector.StorageConnector;
import com.pesitwizard.security.SecretsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating StorageConnector instances from connection
 * configurations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageConnectorFactory {

    private static final List<String> SENSITIVE_FIELDS = List.of(
            "password", "secret", "secretKey", "accessKeySecret",
            "privateKey", "passphrase", "apiKey", "token");

    private final StorageConnectionRepository connectionRepository;
    private final ConnectorRegistry connectorRegistry;
    private final ObjectMapper objectMapper;
    private final SecretsService secretsService;

    /**
     * Create a connector from a connection ID.
     *
     * @param connectionId Storage connection ID
     * @return Configured StorageConnector
     * @throws IllegalArgumentException if connection not found or disabled
     */
    public StorageConnector createFromConnectionId(String connectionId) {
        StorageConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Storage connection not found: " + connectionId));

        if (!connection.isEnabled()) {
            throw new IllegalArgumentException(
                    "Storage connection is disabled: " + connection.getName());
        }

        return createFromConnection(connection);
    }

    /**
     * Create a connector from a StorageConnection entity.
     */
    public StorageConnector createFromConnection(StorageConnection connection) {
        try {
            Map<String, String> config = objectMapper.readValue(
                    connection.getConfigJson(),
                    new TypeReference<Map<String, String>>() {
                    });

            config = decryptSensitiveFields(config);

            return connectorRegistry.createConnector(connection.getConnectorType(), config);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to create connector for connection " + connection.getName()
                            + ": " + e.getMessage(),
                    e);
        }
    }

    private Map<String, String> decryptSensitiveFields(Map<String, String> config) {
        if (config == null) {
            return null;
        }

        Map<String, String> result = new java.util.HashMap<>(config);
        for (String field : SENSITIVE_FIELDS) {
            if (result.containsKey(field) && result.get(field) != null) {
                result.put(field, secretsService.decrypt(result.get(field)));
            }
        }
        return result;
    }
}
