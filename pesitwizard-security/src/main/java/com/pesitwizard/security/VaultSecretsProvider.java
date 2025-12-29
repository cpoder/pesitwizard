package com.pesitwizard.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * HashiCorp Vault secrets provider.
 * Supports both Token and AppRole authentication.
 * Stores secrets in Vault KV v2 engine.
 */
@Slf4j
public class VaultSecretsProvider implements SecretsProvider {

    private static final String PREFIX = "vault:";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TOKEN_REFRESH_THRESHOLD = Duration.ofMinutes(5);

    private final String vaultAddr;
    private final String secretsPath;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final boolean available;

    // Authentication
    private final AuthMethod authMethod;
    private final String staticToken;
    private final String roleId;
    private final String secretId;

    // Dynamic token from AppRole (refreshed automatically)
    private final AtomicReference<String> currentToken = new AtomicReference<>();
    private final AtomicReference<Instant> tokenExpiry = new AtomicReference<>();

    public enum AuthMethod {
        TOKEN, APPROLE
    }

    /**
     * Constructor for static token authentication
     */
    public VaultSecretsProvider(String vaultAddr, String vaultToken, String secretsPath) {
        this(vaultAddr, secretsPath, AuthMethod.TOKEN, vaultToken, null, null);
    }

    /**
     * Constructor for AppRole authentication
     */
    public VaultSecretsProvider(String vaultAddr, String secretsPath, String roleId, String secretId) {
        this(vaultAddr, secretsPath, AuthMethod.APPROLE, null, roleId, secretId);
    }

    /**
     * Full constructor
     */
    public VaultSecretsProvider(String vaultAddr, String secretsPath, AuthMethod authMethod,
            String staticToken, String roleId, String secretId) {
        this.vaultAddr = vaultAddr;
        this.secretsPath = secretsPath;
        this.authMethod = authMethod;
        this.staticToken = staticToken;
        this.roleId = roleId;
        this.secretId = secretId;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        if (vaultAddr == null || vaultAddr.isBlank()) {
            log.info("Vault address not configured");
            this.available = false;
        } else if (authMethod == AuthMethod.TOKEN && (staticToken == null || staticToken.isBlank())) {
            log.info("Vault token not configured");
            this.available = false;
        } else if (authMethod == AuthMethod.APPROLE
                && (roleId == null || roleId.isBlank() || secretId == null || secretId.isBlank())) {
            log.info("Vault AppRole credentials not configured");
            this.available = false;
        } else {
            // Initialize token
            if (authMethod == AuthMethod.TOKEN) {
                this.currentToken.set(staticToken);
                log.info("Vault configured with static token");
            } else {
                // AppRole: get initial token
                if (refreshAppRoleToken()) {
                    log.info("Vault configured with AppRole authentication");
                } else {
                    log.warn("Failed to authenticate with AppRole");
                }
            }
            this.available = testConnection();
            if (this.available) {
                log.info("Vault secrets provider initialized: {} (auth: {})", vaultAddr, authMethod);
            } else {
                log.warn("Vault configured but not reachable: {}", vaultAddr);
            }
        }
    }

    /**
     * Refresh token using AppRole authentication
     */
    private boolean refreshAppRoleToken() {
        if (authMethod != AuthMethod.APPROLE) {
            return false;
        }

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

                currentToken.set(token);
                tokenExpiry.set(Instant.now().plusSeconds(leaseDuration));

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
     * Get current valid token, refreshing if needed
     */
    private String getToken() {
        if (authMethod == AuthMethod.TOKEN) {
            return staticToken;
        }

        // Check if token needs refresh
        Instant expiry = tokenExpiry.get();
        if (expiry == null || Instant.now().plus(TOKEN_REFRESH_THRESHOLD).isAfter(expiry)) {
            refreshAppRoleToken();
        }

        return currentToken.get();
    }

    private boolean testConnection() {
        try {
            String token = getToken();
            if (token == null) {
                return false;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddr + "/v1/sys/health"))
                    .header("X-Vault-Token", token)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 429 || response.statusCode() == 472
                    || response.statusCode() == 473;
        } catch (Exception e) {
            log.warn("Vault health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String encrypt(String plaintext) {
        if (!available) {
            log.warn("Vault not available, returning plaintext");
            return plaintext;
        }

        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }

        // Generate unique key and store in Vault (legacy behavior)
        String key = java.util.UUID.randomUUID().toString();
        storeSecret(key, plaintext);

        // Return vault reference
        return PREFIX + key;
    }

    @Override
    public String encrypt(String plaintext, String context) {
        if (!available) {
            log.warn("Vault not available, returning plaintext");
            return plaintext;
        }

        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }

        if (context == null || context.isBlank()) {
            // Fallback to UUID-based key if no context provided
            return encrypt(plaintext);
        }

        // Sanitize context for use as Vault path
        String sanitizedContext = sanitizeVaultPath(context);
        storeSecret(sanitizedContext, plaintext);

        // Return vault reference with contextual path
        log.debug("Stored secret in Vault at path: {}", sanitizedContext);
        return PREFIX + sanitizedContext;
    }

    /**
     * Sanitize a context string for use as a Vault path.
     * Replaces special characters and ensures valid path format.
     */
    private String sanitizeVaultPath(String context) {
        if (context == null)
            return null;
        // Replace spaces and special chars with dashes, lowercase, remove consecutive
        // dashes
        return context.toLowerCase()
                .replaceAll("[^a-z0-9/_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    @Override
    public String decrypt(String ciphertext) {
        // If it's a vault reference, fetch from Vault
        if (ciphertext != null && ciphertext.startsWith(PREFIX)) {
            String key = ciphertext.substring(PREFIX.length());
            log.debug("Retrieving secret from Vault with key: {}", key.substring(0, Math.min(8, key.length())) + "...");
            String value = getSecret(key);
            if (value == null) {
                log.error("Failed to retrieve secret from Vault. Key: {}. Check Vault connectivity and secret path.",
                        key.substring(0, Math.min(8, key.length())) + "...");
                // Return original reference to signal the failure
                return ciphertext;
            }
            log.debug("Successfully decrypted secret from Vault");
            return value;
        }
        return ciphertext;
    }

    @Override
    public void storeSecret(String key, String value) {
        if (!available) {
            log.warn("Vault not available, cannot store secret: {}", key);
            return;
        }

        try {
            ObjectNode dataNode = objectMapper.createObjectNode();
            ObjectNode innerData = objectMapper.createObjectNode();
            innerData.put("value", value);
            dataNode.set("data", innerData);

            String url = vaultAddr + "/v1/" + secretsPath + "/" + key;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Vault-Token", getToken())
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(dataNode)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Secret stored in Vault: {}", key);
            } else {
                log.error("Failed to store secret in Vault: {} - {}", key, response.body());
            }

        } catch (Exception e) {
            log.error("Failed to store secret in Vault: {} - {}", key, e.getMessage());
            throw new RuntimeException("Vault store failed", e);
        }
    }

    @Override
    public String getSecret(String key) {
        if (!available) {
            log.warn("Vault not available, cannot retrieve secret: {}", key);
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

    @Override
    public void deleteSecret(String key) {
        if (!available) {
            log.warn("Vault not available, cannot delete secret: {}", key);
            return;
        }

        try {
            // For KV v2, we need to delete metadata to fully remove
            String url = vaultAddr + "/v1/" + secretsPath.replace("/data/", "/metadata/") + "/" + key;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Vault-Token", getToken())
                    .timeout(TIMEOUT)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Secret deleted from Vault: {}", key);
            } else {
                log.warn("Failed to delete secret from Vault: {} - {}", key, response.statusCode());
            }

        } catch (Exception e) {
            log.error("Failed to delete secret from Vault: {} - {}", key, e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getProviderType() {
        return "VAULT";
    }

    /**
     * Create a vault reference for storing in database.
     */
    public String createReference(String key) {
        return PREFIX + key;
    }

    /**
     * Check if a value is a vault reference.
     */
    public boolean isVaultReference(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
