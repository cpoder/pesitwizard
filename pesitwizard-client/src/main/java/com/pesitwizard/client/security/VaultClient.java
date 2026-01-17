package com.pesitwizard.client.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Simple Vault client for storing and retrieving secrets.
 */
@Slf4j
public class VaultClient {

    private static final String PREFIX = "vault:";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TOKEN_REFRESH_THRESHOLD = Duration.ofMinutes(5);

    public enum AuthMethod {
        TOKEN, APPROLE
    }

    private final String vaultAddr;
    private final String secretsPath;
    private final AuthMethod authMethod;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private boolean available;

    // Token auth
    private final String staticToken;

    // AppRole auth
    private final String roleId;
    private final String secretId;
    private volatile String currentToken;
    private volatile java.time.Instant tokenExpiry;

    /**
     * Constructor for token authentication.
     */
    public VaultClient(String vaultAddr, String vaultToken, String secretsPath) {
        this(vaultAddr, secretsPath, AuthMethod.TOKEN, vaultToken, null, null);
    }

    /**
     * Constructor for AppRole authentication (recommended for production).
     */
    public VaultClient(String vaultAddr, String secretsPath, String roleId, String secretId) {
        this(vaultAddr, secretsPath, AuthMethod.APPROLE, null, roleId, secretId);
    }

    /**
     * Full constructor.
     */
    public VaultClient(String vaultAddr, String secretsPath, AuthMethod authMethod,
            String staticToken, String roleId, String secretId) {
        this.vaultAddr = vaultAddr;
        this.secretsPath = secretsPath != null ? secretsPath : "secret/data/pesitwizard/client";
        this.authMethod = authMethod;
        this.staticToken = staticToken;
        this.roleId = roleId;
        this.secretId = secretId;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        if (vaultAddr == null || vaultAddr.isBlank()) {
            log.debug("Vault address not configured");
            this.available = false;
        } else if (authMethod == AuthMethod.TOKEN && (staticToken == null || staticToken.isBlank())) {
            log.debug("Vault token not configured");
            this.available = false;
        } else if (authMethod == AuthMethod.APPROLE &&
                (roleId == null || roleId.isBlank() || secretId == null || secretId.isBlank())) {
            log.debug("Vault AppRole credentials not configured");
            this.available = false;
        } else {
            // Initialize token
            if (authMethod == AuthMethod.TOKEN) {
                this.currentToken = staticToken;
            } else {
                refreshAppRoleToken();
            }
            this.available = testConnection();
            if (this.available) {
                log.info("Vault client initialized: {} (auth: {})", vaultAddr, authMethod);
            } else {
                log.warn("Vault configured but not reachable: {}", vaultAddr);
            }
        }
    }

    /**
     * Refresh token using AppRole authentication.
     */
    private boolean refreshAppRoleToken() {
        if (authMethod != AuthMethod.APPROLE)
            return false;
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("role_id", roleId);
            body.put("secret_id", secretId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddr + "/v1/auth/approle/login"))
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String token = root.path("auth").path("client_token").asText();
                int leaseDuration = root.path("auth").path("lease_duration").asInt(3600);
                this.currentToken = token;
                this.tokenExpiry = java.time.Instant.now().plusSeconds(leaseDuration);
                log.debug("AppRole token refreshed, expires in {} seconds", leaseDuration);
                return true;
            } else {
                log.error("AppRole login failed: {} - {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("AppRole login failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get current valid token, refreshing if needed.
     */
    private String getToken() {
        if (authMethod == AuthMethod.TOKEN) {
            return staticToken;
        }
        // Check if token needs refresh
        if (tokenExpiry == null || java.time.Instant.now().plus(TOKEN_REFRESH_THRESHOLD).isAfter(tokenExpiry)) {
            refreshAppRoleToken();
        }
        return currentToken;
    }

    private boolean testConnection() {
        try {
            String token = getToken();
            if (token == null)
                return false;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddr + "/v1/sys/health"))
                    .header("X-Vault-Token", token)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 429
                    || response.statusCode() == 472 || response.statusCode() == 473;
        } catch (Exception e) {
            log.warn("Vault health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Store a secret in Vault and return a reference.
     */
    public String storeSecret(String key, String value) {
        if (!available) {
            log.warn("Vault not available, cannot store secret: {}", key);
            return value;
        }

        return storeSecretWithRetry(key, value, true);
    }

    private String storeSecretWithRetry(String key, String value, boolean retry) {
        try {
            ObjectNode dataNode = objectMapper.createObjectNode();
            ObjectNode innerData = objectMapper.createObjectNode();
            innerData.put("value", value);
            dataNode.set("data", innerData);

            String url = vaultAddr + "/v1/" + secretsPath + "/" + key;
            String token = getToken();
            log.debug("Storing secret at {} with auth method {} (token present: {})",
                    url, authMethod, token != null && !token.isBlank());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Vault-Token", token)
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(dataNode)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Secret stored in Vault: {}", key);
                return PREFIX + key;
            } else if (response.statusCode() == 403 && retry && authMethod == AuthMethod.APPROLE) {
                // Token may have expired, force refresh and retry once
                log.debug("Got 403, refreshing token and retrying...");
                if (refreshAppRoleToken()) {
                    return storeSecretWithRetry(key, value, false);
                }
                log.error("Failed to store secret in Vault after token refresh: {} - {}", key, response.body());
                throw new RuntimeException("Vault permission denied: " + response.body());
            } else {
                log.error("Failed to store secret in Vault: {} - {}", key, response.body());
                throw new RuntimeException("Vault error: " + response.body());
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to store secret in Vault: {} - {}", key, e.getMessage());
            throw new RuntimeException("Vault error: " + e.getMessage(), e);
        }
    }

    /**
     * Get a secret from Vault.
     */
    public String getSecret(String key) {
        if (!available) {
            return null;
        }

        try {
            String url = vaultAddr + "/v1/" + secretsPath + "/" + key;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Vault-Token", getToken())
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.path("data").path("data").path("value");
                if (!data.isMissingNode()) {
                    return data.asText();
                }
            } else if (response.statusCode() == 404) {
                log.debug("Secret not found in Vault: {}", key);
            } else {
                log.error("Failed to retrieve secret from Vault: {} - {}", key, response.statusCode());
            }

            return null;

        } catch (Exception e) {
            log.error("Failed to retrieve secret from Vault: {} - {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Check if a value is a Vault reference.
     */
    public boolean isVaultReference(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Resolve a Vault reference to its actual value.
     */
    public String resolveReference(String reference) {
        if (!isVaultReference(reference)) {
            return reference;
        }
        String key = reference.substring(PREFIX.length());
        String value = getSecret(key);
        return value != null ? value : reference;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getVaultAddr() {
        return vaultAddr;
    }
}
