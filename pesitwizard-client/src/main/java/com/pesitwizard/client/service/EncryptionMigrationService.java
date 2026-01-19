package com.pesitwizard.client.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.client.entity.StorageConnection;
import com.pesitwizard.client.repository.PesitServerRepository;
import com.pesitwizard.client.repository.StorageConnectionRepository;
import com.pesitwizard.security.AbstractEncryptionMigrationService;
import com.pesitwizard.security.SecretsService;

import lombok.extern.slf4j.Slf4j;

/**
 * Service to migrate existing secrets to Vault.
 * Handles PeSIT server passwords and storage connection credentials.
 */
@Slf4j
@Service
public class EncryptionMigrationService extends AbstractEncryptionMigrationService {

    private final PesitServerRepository serverRepository;
    private final StorageConnectionRepository connectionRepository;
    private final ObjectMapper objectMapper;

    public EncryptionMigrationService(SecretsService secretsService,
            PesitServerRepository serverRepository,
            StorageConnectionRepository connectionRepository,
            ObjectMapper objectMapper) {
        super(secretsService);
        this.serverRepository = serverRepository;
        this.connectionRepository = connectionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    protected MigrationResult doMigration() {
        List<String> details = new ArrayList<>();
        int totalMigrated = 0;
        int totalSkipped = 0;

        // Migrate PeSIT server passwords
        var serverResult = migratePesitServers();
        totalMigrated += serverResult.migrated();
        totalSkipped += serverResult.skipped();
        details.add("PeSIT Servers: " + serverResult.migrated() + " migrated, " + serverResult.skipped() + " skipped");

        // Migrate storage connection credentials
        var connResult = migrateStorageConnections();
        totalMigrated += connResult.migrated();
        totalSkipped += connResult.skipped();
        details.add(
                "Storage Connections: " + connResult.migrated() + " migrated, " + connResult.skipped() + " skipped");

        log.info("Vault migration completed: {} migrated, {} skipped", totalMigrated, totalSkipped);

        return new MigrationResult(true, "Migration completed successfully", totalMigrated, totalSkipped, details);
    }

    private MigrationCount migratePesitServers() {
        int migrated = 0;
        int skipped = 0;

        for (PesitServer server : serverRepository.findAll()) {
            boolean modified = false;

            // Migrate truststore password
            if (server.getTruststorePassword() != null && !isVaultRef(server.getTruststorePassword())) {
                try {
                    String key = "server/" + server.getId() + "/truststorePassword";
                    String plaintext = decryptIfNeeded(server.getTruststorePassword());
                    String vaultRef = secretsService.storeInVault(key, plaintext);
                    server.setTruststorePassword(vaultRef);
                    modified = true;
                    log.debug("Migrated truststore password for server: {}", server.getName());
                } catch (Exception e) {
                    log.error("Failed to migrate truststore password for {}: {}", server.getName(), e.getMessage());
                }
            }

            // Migrate keystore password
            if (server.getKeystorePassword() != null && !isVaultRef(server.getKeystorePassword())) {
                try {
                    String key = "server/" + server.getId() + "/keystorePassword";
                    String plaintext = decryptIfNeeded(server.getKeystorePassword());
                    String vaultRef = secretsService.storeInVault(key, plaintext);
                    server.setKeystorePassword(vaultRef);
                    modified = true;
                    log.debug("Migrated keystore password for server: {}", server.getName());
                } catch (Exception e) {
                    log.error("Failed to migrate keystore password for {}: {}", server.getName(), e.getMessage());
                }
            }

            if (modified) {
                serverRepository.save(server);
                migrated++;
            } else {
                skipped++;
            }
        }

        return new MigrationCount(migrated, skipped);
    }

    private MigrationCount migrateStorageConnections() {
        int migrated = 0;
        int skipped = 0;

        for (StorageConnection conn : connectionRepository.findAll()) {
            if (conn.getConfigJson() == null || conn.getConfigJson().isBlank()) {
                skipped++;
                continue;
            }

            try {
                JsonNode config = objectMapper.readTree(conn.getConfigJson());
                boolean modified = false;

                // Migrate password fields in config JSON
                if (config instanceof ObjectNode configObj) {
                    modified = migrateJsonPasswords(configObj, "connection/" + conn.getId());
                }

                if (modified) {
                    conn.setConfigJson(objectMapper.writeValueAsString(config));
                    connectionRepository.save(conn);
                    migrated++;
                    log.debug("Migrated credentials for connection: {}", conn.getName());
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("Failed to migrate connection {}: {}", conn.getName(), e.getMessage());
                skipped++;
            }
        }

        return new MigrationCount(migrated, skipped);
    }

    /**
     * Recursively migrate password fields in JSON config to Vault.
     * Returns true if any field was modified.
     */
    private boolean migrateJsonPasswords(ObjectNode config, String keyPrefix) {
        boolean modified = false;
        var fieldNames = new ArrayList<String>();
        config.fieldNames().forEachRemaining(fieldNames::add);

        for (String fieldName : fieldNames) {
            JsonNode value = config.get(fieldName);

            // Check for password-like fields
            if (isPasswordField(fieldName) && value.isTextual()) {
                String textValue = value.asText();
                if (!textValue.isBlank() && !isVaultRef(textValue)) {
                    try {
                        String key = keyPrefix + "/" + fieldName;
                        String plaintext = decryptIfNeeded(textValue);
                        String vaultRef = secretsService.storeInVault(key, plaintext);
                        config.put(fieldName, vaultRef);
                        modified = true;
                    } catch (Exception e) {
                        log.warn("Failed to migrate field {}: {}", fieldName, e.getMessage());
                    }
                }
            }
            // Recurse into nested objects
            else if (value instanceof ObjectNode nestedObj) {
                if (migrateJsonPasswords(nestedObj, keyPrefix + "/" + fieldName)) {
                    modified = true;
                }
            }
        }

        return modified;
    }

    private boolean isPasswordField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("password") || lower.contains("secret") ||
                lower.contains("apikey") || lower.contains("api_key") ||
                lower.contains("accesskey") || lower.contains("access_key") ||
                lower.contains("privatekey") || lower.contains("private_key") ||
                lower.equals("token") || lower.equals("credential");
    }

}
