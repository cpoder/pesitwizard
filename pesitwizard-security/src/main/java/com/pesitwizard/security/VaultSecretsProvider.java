package com.pesitwizard.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * HashiCorp Vault secrets provider with cache and circuit breaker.
 * Supports both Token and AppRole authentication.
 * Stores secrets in Vault KV v2 engine.
 */
@Slf4j
public class VaultSecretsProvider implements SecretsProvider {

    private static final String PREFIX = "vault:";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TOKEN_REFRESH_THRESHOLD = Duration.ofMinutes(5);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int CIRCUIT_FAILURE_THRESHOLD = 5;
    private static final Duration CIRCUIT_OPEN_DURATION = Duration.ofMinutes(1);

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

    // Dynamic token from AppRole
    private final AtomicReference<String> currentToken = new AtomicReference<>();
    private final AtomicReference<Instant> tokenExpiry = new AtomicReference<>();

    // Cache
    private final ConcurrentHashMap<String, CachedSecret> secretCache = new ConcurrentHashMap<>();

    // Circuit breaker
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<Instant> circuitOpenUntil = new AtomicReference<>();

    private record CachedSecret(String value, Instant expiry) {
        boolean isExpired() {
            return Instant.now().isAfter(expiry);
        }
    }

    public enum AuthMethod {
        TOKEN, APPROLE
    }

    public VaultSecretsProvider(String vaultAddr, String vaultToken, String secretsPath) {
        this(vaultAddr, secretsPath, AuthMethod.TOKEN, vaultToken, null, null);
    }

    public VaultSecretsProvider(String vaultAddr, String secretsPath, String roleId, String secretId) {
        this(vaultAddr, secretsPath, AuthMethod.APPROLE, null, roleId, secretId);
    }

    public VaultSecretsProvider(String vaultAddr, String secretsPath, AuthMethod authMethod,
            String staticToken, String roleId, String secretId) {
        this.vaultAddr = vaultAddr;
        this.secretsPath = secretsPath;
        this.authMethod = authMethod;
        this.staticToken = staticToken;
        this.roleId = roleId;
        this.secretId = secretId;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

        if (vaultAddr == null || vaultAddr.isBlank()) {
            log.info("Vault address not configured");
            this.available = false;
        } else if (authMethod == AuthMethod.TOKEN && (staticToken == null || staticToken.isBlank())) {
            log.info("Vault token not configured");
            this.available = false;
        } else if (authMethod == AuthMethod.APPROLE && (roleId == null || secretId == null)) {
            log.info("Vault AppRole credentials not configured");
            this.available = false;
        } else {
            if (authMethod == AuthMethod.TOKEN) {
                this.currentToken.set(staticToken);
            } else {
                refreshAppRoleToken();
            }
            this.available = testConnection();
            if (this.available) {
                log.info("Vault secrets provider initialized: {} (auth: {})", vaultAddr, authMethod);
            }
        }
    }

    private boolean isCircuitOpen() {
        Instant openUntil = circuitOpenUntil.get();
        if (openUntil != null && Instant.now().isBefore(openUntil)) {
            log.warn("Circuit breaker OPEN, failing fast");
            return true;
        }
        return false;
    }

    private void recordSuccess() {
        failureCount.set(0);
        circuitOpenUntil.set(null);
    }

    private void recordFailure() {
        int failures = failureCount.incrementAndGet();
        if (failures >= CIRCUIT_FAILURE_THRESHOLD) {
            Instant openUntil = Instant.now().plus(CIRCUIT_OPEN_DURATION);
            circuitOpenUntil.set(openUntil);
            log.error("Circuit breaker OPENED after {} failures, retry at {}", failures, openUntil);
        }
    }

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
                currentToken.set(token);
                tokenExpiry.set(Instant.now().plusSeconds(leaseDuration));
                log.debug("AppRole token refreshed");
                return true;
            }
            log.error("AppRole login failed: {}", response.statusCode());
            return false;
        } catch (Exception e) {
            log.error("AppRole login failed: {}", e.getMessage());
            return false;
        }
    }

    private String getToken() {
        if (authMethod == AuthMethod.TOKEN)
            return staticToken;
        Instant expiry = tokenExpiry.get();
        if (expiry == null || Instant.now().plus(TOKEN_REFRESH_THRESHOLD).isAfter(expiry)) {
            refreshAppRoleToken();
        }
        return currentToken.get();
    }

    private boolean testConnection() {
        try {
            String token = getToken();
            if (token == null)
                return false;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddr + "/v1/sys/health"))
                    .header("X-Vault-Token", token)
                    .timeout(TIMEOUT).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 429;
        } catch (Exception e) {
            log.warn("Vault health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String encrypt(String plaintext) {
        if (!available || plaintext == null || plaintext.isBlank())
            return plaintext;
        String key = java.util.UUID.randomUUID().toString();
        storeSecret(key, plaintext);
        return PREFIX + key;
    }

    @Override
    public String encrypt(String plaintext, String context) {
        if (!available || plaintext == null || plaintext.isBlank())
            return plaintext;
        if (context == null || context.isBlank())
            return encrypt(plaintext);
        String sanitizedContext = sanitizeVaultPath(context);
        storeSecret(sanitizedContext, plaintext);
        return PREFIX + sanitizedContext;
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext != null && ciphertext.startsWith(PREFIX)) {
            String key = ciphertext.substring(PREFIX.length());
            String value = getSecret(key);
            if (value == null) {
                log.error("Failed to retrieve secret from Vault");
                return ciphertext;
            }
            return value;
        }
        return ciphertext;
    }

    @Override
    public void storeSecret(String key, String value) {
        if (!available || isCircuitOpen())
            return;
        secretCache.remove(key);
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
                recordSuccess();
                log.debug("Secret stored in Vault: {}", key);
            } else {
                recordFailure();
                log.error("Failed to store secret: {} - {}", key, response.body());
            }
        } catch (Exception e) {
            recordFailure();
            log.error("Failed to store secret: {}", e.getMessage());
            throw new EncryptionException("Vault store failed", e);
        }
    }

    @Override
    public String getSecret(String key) {
        if (!available)
            return null;

        // Check cache first
        CachedSecret cached = secretCache.get(key);
        if (cached != null && !cached.isExpired()) {
            log.debug("Cache hit for secret: {}", key);
            return cached.value();
        }

        if (isCircuitOpen())
            return null;

        try {
            String url = vaultAddr + "/v1/" + secretsPath + "/" + key;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Vault-Token", getToken())
                    .timeout(TIMEOUT).GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                recordSuccess();
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.path("data").path("data").path("value");
                if (!data.isMissingNode()) {
                    String value = data.asText();
                    secretCache.put(key, new CachedSecret(value, Instant.now().plus(CACHE_TTL)));
                    return value;
                }
            } else if (response.statusCode() == 404) {
                log.debug("Secret not found: {}", key);
            } else {
                recordFailure();
                log.error("Failed to get secret: {} - {}", key, response.statusCode());
            }
            return null;
        } catch (Exception e) {
            recordFailure();
            log.error("Failed to get secret: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void deleteSecret(String key) {
        if (!available || isCircuitOpen())
            return;
        secretCache.remove(key);
        try {
            String url = vaultAddr + "/v1/" + secretsPath.replace("/data/", "/metadata/") + "/" + key;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Vault-Token", getToken())
                    .timeout(TIMEOUT).DELETE().build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            recordSuccess();
        } catch (Exception e) {
            recordFailure();
            log.error("Failed to delete secret: {}", e.getMessage());
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

    public String createReference(String key) {
        return PREFIX + key;
    }

    public boolean isVaultReference(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private String sanitizeVaultPath(String context) {
        if (context == null)
            return null;
        return context.toLowerCase()
                .replaceAll("[^a-z0-9/_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
