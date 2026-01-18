package com.pesitwizard.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Shared Vault management utilities for configuration and setup.
 * Used by both pesitwizard-admin and pesitwizard-client.
 */
@Slf4j
public class VaultManager {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String vaultAddress;
    private final HttpClient httpClient;

    public VaultManager(String vaultAddress) {
        this.vaultAddress = vaultAddress.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Test Vault connection with token authentication.
     */
    public VaultTestResult testConnection(String token, String namespace) {
        return testHealth(token, namespace);
    }

    /**
     * Test Vault connection with AppRole authentication.
     * Returns the obtained token if successful.
     */
    public VaultTestResult testAppRole(String roleId, String secretId, String namespace) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("role_id", roleId);
            body.put("secret_id", secretId);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddress + "/v1/auth/approle/login"))
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));

            if (namespace != null && !namespace.isBlank()) {
                requestBuilder.header("X-Vault-Namespace", namespace);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String token = root.path("auth").path("client_token").asText();
                int ttl = root.path("auth").path("lease_duration").asInt(3600);

                // Test health with the obtained token
                VaultTestResult healthResult = testHealth(token, namespace);
                if (healthResult.success()) {
                    return new VaultTestResult(true,
                            "AppRole authentication successful (token TTL: " + ttl + "s)",
                            healthResult.details(), token);
                }
                return healthResult;
            } else {
                String error = parseVaultError(response.body());
                return new VaultTestResult(false, "AppRole login failed: " + error, null, null);
            }
        } catch (Exception e) {
            log.error("AppRole test failed: {}", e.getMessage());
            return new VaultTestResult(false, "AppRole test failed: " + e.getMessage(), null, null);
        }
    }

    /**
     * Check Vault health status.
     */
    private VaultTestResult testHealth(String token, String namespace) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddress + "/v1/sys/health"))
                    .timeout(TIMEOUT)
                    .header("X-Vault-Token", token)
                    .GET();

            if (namespace != null && !namespace.isBlank()) {
                requestBuilder.header("X-Vault-Namespace", namespace);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 429) {
                JsonNode health = objectMapper.readTree(response.body());
                String version = health.path("version").asText("unknown");
                boolean sealed = health.path("sealed").asBoolean(false);

                if (sealed) {
                    return new VaultTestResult(false, "Vault is sealed", null, token);
                }
                return new VaultTestResult(true, "Connected to Vault " + version, health.toString(), token);
            } else if (response.statusCode() == 503) {
                return new VaultTestResult(false, "Vault is sealed", null, null);
            } else {
                return new VaultTestResult(false, "Vault returned status: " + response.statusCode(), null, null);
            }
        } catch (IOException e) {
            log.error("Failed to connect to Vault: {}", e.getMessage());
            return new VaultTestResult(false, "Connection failed: " + e.getMessage(), null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new VaultTestResult(false, "Connection interrupted", null, null);
        }
    }

    /**
     * Ensure KV secrets engine is enabled at the specified path.
     * If not enabled, attempts to enable it (requires appropriate permissions).
     */
    public SetupResult ensureKvSecretsEngine(String token, String path, String namespace) {
        String mountPath = path.startsWith("secret") ? "secret" : path.split("/")[0];

        try {
            // Check if already mounted
            if (isSecretsMounted(token, mountPath, namespace)) {
                log.info("KV secrets engine already enabled at: {}", mountPath);
                return new SetupResult(true, "KV secrets engine already enabled at: " + mountPath);
            }

            // Try to enable it
            log.info("Enabling KV v2 secrets engine at: {}", mountPath);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("type", "kv-v2");
            ObjectNode options = objectMapper.createObjectNode();
            options.put("version", "2");
            body.set("options", options);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddress + "/v1/sys/mounts/" + mountPath))
                    .header("X-Vault-Token", token)
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));

            if (namespace != null && !namespace.isBlank()) {
                requestBuilder.header("X-Vault-Namespace", namespace);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("KV secrets engine enabled successfully at: {}", mountPath);
                return new SetupResult(true, "KV secrets engine enabled at: " + mountPath);
            } else if (response.statusCode() == 400 && response.body().contains("existing mount")) {
                return new SetupResult(true, "KV secrets engine already exists at: " + mountPath);
            } else {
                String error = parseVaultError(response.body());
                log.warn("Failed to enable KV secrets engine: {}", error);
                return new SetupResult(false, "Failed to enable secrets engine: " + error);
            }
        } catch (Exception e) {
            log.error("Failed to setup KV secrets engine: {}", e.getMessage());
            return new SetupResult(false, "Setup failed: " + e.getMessage());
        }
    }

    /**
     * Check if a secrets engine is mounted at the given path.
     */
    private boolean isSecretsMounted(String token, String path, String namespace) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddress + "/v1/sys/mounts"))
                    .header("X-Vault-Token", token)
                    .timeout(TIMEOUT)
                    .GET();

            if (namespace != null && !namespace.isBlank()) {
                requestBuilder.header("X-Vault-Namespace", namespace);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode mounts = objectMapper.readTree(response.body());
                return mounts.has(path + "/") || mounts.path("data").has(path + "/");
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to check mounts: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Setup AppRole authentication method.
     */
    public SetupResult setupAppRole(String token, String roleName, String policy, String namespace) {
        try {
            // 1. Enable AppRole auth if not enabled
            enableAppRoleAuth(token, namespace);

            // 2. Create/update policy
            if (policy != null && !policy.isBlank()) {
                createPolicy(token, roleName + "-policy", policy, namespace);
            }

            // 3. Create AppRole
            ObjectNode roleConfig = objectMapper.createObjectNode();
            roleConfig.put("token_policies", policy != null ? roleName + "-policy" : "default");
            roleConfig.put("token_ttl", "1h");
            roleConfig.put("token_max_ttl", "4h");
            roleConfig.put("secret_id_ttl", "0"); // No expiration

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddress + "/v1/auth/approle/role/" + roleName))
                    .header("X-Vault-Token", token)
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(roleConfig)));

            if (namespace != null && !namespace.isBlank()) {
                requestBuilder.header("X-Vault-Namespace", namespace);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("AppRole '{}' configured successfully", roleName);
                return new SetupResult(true, "AppRole '" + roleName + "' configured");
            } else {
                String error = parseVaultError(response.body());
                return new SetupResult(false, "Failed to create AppRole: " + error);
            }
        } catch (Exception e) {
            log.error("Failed to setup AppRole: {}", e.getMessage());
            return new SetupResult(false, "AppRole setup failed: " + e.getMessage());
        }
    }

    /**
     * Get Role ID for an AppRole.
     */
    public Optional<String> getRoleId(String token, String roleName, String namespace) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddress + "/v1/auth/approle/role/" + roleName + "/role-id"))
                    .header("X-Vault-Token", token)
                    .timeout(TIMEOUT)
                    .GET();

            if (namespace != null && !namespace.isBlank()) {
                requestBuilder.header("X-Vault-Namespace", namespace);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                return Optional.of(root.path("data").path("role_id").asText());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get role_id: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Generate a new Secret ID for an AppRole.
     */
    public Optional<String> generateSecretId(String token, String roleName, String namespace) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddress + "/v1/auth/approle/role/" + roleName + "/secret-id"))
                    .header("X-Vault-Token", token)
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString("{}"));

            if (namespace != null && !namespace.isBlank()) {
                requestBuilder.header("X-Vault-Namespace", namespace);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                return Optional.of(root.path("data").path("secret_id").asText());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to generate secret_id: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void enableAppRoleAuth(String token, String namespace) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", "approle");

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(vaultAddress + "/v1/sys/auth/approle"))
                .header("X-Vault-Token", token)
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));

        if (namespace != null && !namespace.isBlank()) {
            requestBuilder.header("X-Vault-Namespace", namespace);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

        // 200 = enabled, 400 with "already in use" = already enabled
        if (response.statusCode() != 200 && response.statusCode() != 204
                && !response.body().contains("already in use")) {
            log.warn("AppRole auth enable response: {}", response.body());
        }
    }

    private void createPolicy(String token, String policyName, String policy, String namespace) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("policy", policy);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(vaultAddress + "/v1/sys/policies/acl/" + policyName))
                .header("X-Vault-Token", token)
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));

        if (namespace != null && !namespace.isBlank()) {
            requestBuilder.header("X-Vault-Namespace", namespace);
        }

        httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String parseVaultError(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errors = root.path("errors");
            if (errors.isArray() && errors.size() > 0) {
                return errors.get(0).asText();
            }
        } catch (Exception ignored) {
        }
        return responseBody;
    }

    // Result records
    public record VaultTestResult(boolean success, String message, String details, String token) {
        public VaultTestResult(boolean success, String message, String details) {
            this(success, message, details, null);
        }
    }

    public record SetupResult(boolean success, String message) {
    }

    /**
     * Default policy for PeSIT Wizard.
     */
    public static String getDefaultPolicy(String secretPath) {
        return String.format("""
                path "%s/*" {
                  capabilities = ["create", "read", "update", "delete", "list"]
                }
                path "%s" {
                  capabilities = ["list"]
                }
                """, secretPath, secretPath.split("/")[0] + "/metadata/" + secretPath.split("/")[1]);
    }
}
