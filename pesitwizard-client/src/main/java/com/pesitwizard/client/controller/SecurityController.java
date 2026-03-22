package com.pesitwizard.client.controller;

import com.pesitwizard.client.service.EncryptionMigrationService;
import com.pesitwizard.security.SecretsService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for security/encryption configuration in the client. */
@Slf4j
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
public class SecurityController {

    private static final java.security.SecureRandom SECURE_RANDOM =
            new java.security.SecureRandom();

    private final SecretsService secretsService;
    private final EncryptionMigrationService migrationService;

    @Value("${pesitwizard.security.encryption-mode:AES}")
    private String encryptionMode;

    @Value("${pesitwizard.security.vault.address:}")
    private String vaultAddress;

    @Value("${pesitwizard.security.vault.auth-method:token}")
    private String vaultAuthMethod;

    @Value("${pesitwizard.security.vault.path:secret/data/pesitwizard/client}")
    private String vaultPath;

    /** Get current encryption status (unified format matching admin) */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        var status = secretsService.getStatus();

        // Check if using auto-generated key
        String masterKeyEnv = System.getenv("PESITWIZARD_SECURITY_MASTER_KEY");
        String masterKeyFile = System.getenv("PESITWIZARD_SECURITY_MASTER_KEY_FILE");
        boolean usingFixedKey =
                (masterKeyEnv != null && !masterKeyEnv.isBlank())
                        || (masterKeyFile != null && !masterKeyFile.isBlank());

        boolean vaultEnabled = "VAULT".equalsIgnoreCase(encryptionMode);
        boolean vaultConnected =
                vaultEnabled && status.available() && "VAULT".equals(status.providerType());

        return ResponseEntity.ok(
                Map.of(
                        "encryption",
                                Map.of(
                                        "enabled", status.available(),
                                        "mode", status.providerType(),
                                        "message", status.message()),
                        "aes",
                                Map.of(
                                        "configured",
                                        true,
                                        "usingFixedKey",
                                        usingFixedKey,
                                        "message",
                                        usingFixedKey
                                                ? "✅ Using fixed master key"
                                                : "⚠️ Using auto-generated key (not portable)"),
                        "vault",
                                Map.of(
                                        "enabled", vaultEnabled,
                                        "connected", vaultConnected,
                                        "address", vaultAddress != null ? vaultAddress : "",
                                        "authMethod",
                                                vaultAuthMethod != null
                                                        ? vaultAuthMethod.toUpperCase()
                                                        : "TOKEN",
                                        "message",
                                                vaultConnected
                                                        ? "Connected"
                                                        : (vaultEnabled ? "Not connected" : ""))));
    }

    /** Generate a new master key (for display only - user must set it as env var) */
    @PostMapping("/generate-key")
    public ResponseEntity<Map<String, Object>> generateKey() {
        byte[] key = new byte[32];
        SECURE_RANDOM.nextBytes(key);
        String base64Key = java.util.Base64.getEncoder().encodeToString(key);

        return ResponseEntity.ok(
                Map.of(
                        "key",
                        base64Key,
                        "instructions",
                        "Set this key as environment variable PESITWIZARD_SECURITY_MASTER_KEY and restart the application."));
    }

    /**
     * Migrate existing secrets to Vault. Handles PeSIT server passwords and storage connection
     * credentials.
     */
    @PostMapping("/encryption/migrate")
    public ResponseEntity<Map<String, Object>> migrateToVault() {
        try {
            var result = migrationService.migrateAllToVault();
            log.info(
                    "Vault migration: {} migrated, {} skipped",
                    result.totalMigrated(),
                    result.totalSkipped());

            return ResponseEntity.ok(
                    Map.of(
                            "success", result.success(),
                            "message", result.message(),
                            "totalMigrated", result.totalMigrated(),
                            "totalSkipped", result.totalSkipped(),
                            "details", result.details()));
        } catch (RuntimeException e) {
            log.error("Migration failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    "Migration failed: " + e.getMessage()));
        }
    }

    /** Test if a value can be encrypted/decrypted */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testEncryption(
            @RequestBody Map<String, String> request) {
        String testValue = request.getOrDefault("value", "test-encryption-value");

        try {
            if (!secretsService.isAvailable()) {
                return ResponseEntity.ok(
                        Map.of(
                                "success",
                                false,
                                "message",
                                "Encryption not configured. Set PESITWIZARD_SECURITY_MASTER_KEY environment variable."));
            }

            String encrypted = secretsService.encrypt(testValue);
            String decrypted = secretsService.decrypt(encrypted);
            boolean success = testValue.equals(decrypted);

            return ResponseEntity.ok(
                    Map.of(
                            "success",
                            success,
                            "message",
                            success ? "Encryption working correctly" : "Decryption mismatch",
                            "encrypted",
                            encrypted.substring(0, Math.min(20, encrypted.length())) + "..."));
        } catch (RuntimeException e) {
            log.error("Encryption test failed: {}", e.getMessage());
            return ResponseEntity.ok(
                    Map.of(
                            "success",
                            false,
                            "message",
                            "Encryption test failed: " + e.getMessage()));
        }
    }

    /** Test Vault connection */
    @PostMapping("/vault/test")
    public ResponseEntity<Map<String, Object>> testVault(@RequestBody Map<String, String> request) {
        String address = request.get("address");
        String token = request.get("token");

        if (address == null || address.isBlank() || token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    "Vault address and token are required"));
        }

        try {
            URI vaultUri = buildVaultUri(address, "/v1/sys/health");
            var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

            var httpRequest =
                    HttpRequest.newBuilder()
                            .uri(vaultUri)
                            .header("X-Vault-Token", token)
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 429) {
                return ResponseEntity.ok(
                        Map.of(
                                "success",
                                true,
                                "message",
                                "Vault connection successful",
                                "details",
                                Map.of("raw", response.body())));
            } else {
                return ResponseEntity.ok(
                        Map.of(
                                "success",
                                false,
                                "message",
                                "Vault returned status " + response.statusCode()));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Vault test blocked by SSRF validation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Vault test failed: {}", e.getMessage());
            return ResponseEntity.ok(
                    Map.of("success", false, "message", "Connection failed: " + e.getMessage()));
        }
    }

    /** Get Vault configuration instructions */
    @GetMapping("/vault/config")
    public ResponseEntity<Map<String, Object>> getVaultConfig() {
        return ResponseEntity.ok(
                Map.of(
                        "instructions",
                                "To connect to Vault, set these environment variables and restart:",
                        "variables",
                                Map.of(
                                        "PESITWIZARD_SECURITY_MODE", "VAULT",
                                        "PESITWIZARD_SECURITY_VAULT_ADDRESS",
                                                "http://localhost:30200",
                                        "PESITWIZARD_SECURITY_VAULT_TOKEN", "<your-token>",
                                        "PESITWIZARD_SECURITY_VAULT_PATH",
                                                "secret/data/pesitwizard/client"),
                        "vaultAvailable", secretsService.isVaultAvailable(),
                        "currentMode", secretsService.getEncryptionMode()));
    }

    /** Get Vault status (connection info from env vars) */
    @GetMapping("/vault/status")
    public ResponseEntity<Map<String, Object>> getVaultStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("mode", encryptionMode);
        status.put("configured", "VAULT".equalsIgnoreCase(encryptionMode));
        status.put("vaultAddress", vaultAddress);
        status.put("authMethod", vaultAuthMethod);
        status.put("secretPath", vaultPath);

        // Test connection if Vault is configured
        if ("VAULT".equalsIgnoreCase(encryptionMode)
                && vaultAddress != null
                && !vaultAddress.isBlank()) {
            status.put("connected", secretsService.isVaultAvailable());
            status.put(
                    "connectionMessage",
                    secretsService.isVaultAvailable()
                            ? "Connected to Vault"
                            : "Cannot connect to Vault");
        } else {
            status.put("connected", false);
            status.put("connectionMessage", "Vault not configured");
        }

        return ResponseEntity.ok(status);
    }

    /** Test Vault connection with AppRole authentication */
    @PostMapping("/vault/test-approle")
    public ResponseEntity<Map<String, Object>> testVaultAppRole(
            @RequestBody Map<String, String> request) {
        String address = request.get("address");
        String roleId = request.get("roleId");
        String secretId = request.get("secretId");

        if (address == null || address.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Vault address is required"));
        }
        if (roleId == null || roleId.isBlank() || secretId == null || secretId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    "Role ID and Secret ID are required"));
        }

        try {
            URI loginUri = buildVaultUri(address, "/v1/auth/approle/login");

            HttpClient client =
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

            // Login with AppRole
            String loginBody =
                    String.format("{\"role_id\":\"%s\",\"secret_id\":\"%s\"}", roleId, secretId);
            HttpRequest loginRequest =
                    HttpRequest.newBuilder()
                            .uri(loginUri)
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(5))
                            .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                            .build();

            HttpResponse<String> response =
                    client.send(loginRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return ResponseEntity.ok(
                        Map.of("success", true, "message", "AppRole authentication successful"));
            } else {
                return ResponseEntity.ok(
                        Map.of(
                                "success",
                                false,
                                "message",
                                "AppRole authentication failed: " + response.statusCode()));
            }
        } catch (IllegalArgumentException e) {
            log.warn("AppRole test blocked by SSRF validation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("AppRole test failed: {}", e.getMessage());
            return ResponseEntity.ok(
                    Map.of("success", false, "message", "Connection failed: " + e.getMessage()));
        }
    }

    /** Setup Vault: create KV engine and optionally AppRole */
    @PostMapping("/vault/setup")
    public ResponseEntity<Map<String, Object>> setupVault(@RequestBody Map<String, String> params) {
        String address = params.get("address");
        String token = params.get("token");
        boolean setupAppRole = Boolean.parseBoolean(params.getOrDefault("setupAppRole", "false"));

        if (address == null || address.isBlank() || token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    "Vault address and token are required"));
        }

        try {
            URI baseUri = validateAndParseUrl(address);

            HttpClient client =
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            // 1. Enable KV v2 secrets engine at 'secret/' if not exists
            HttpRequest enableKv =
                    HttpRequest.newBuilder()
                            .uri(baseUri.resolve("/v1/sys/mounts/secret"))
                            .header("X-Vault-Token", token)
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"type\":\"kv\",\"options\":{\"version\":\"2\"}}"))
                            .build();

            // 204 = success, 400 = already exists (both OK)
            client.send(enableKv, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Vault initialized successfully");

            // 2. Setup AppRole if requested
            if (setupAppRole) {
                // Enable AppRole auth
                HttpRequest enableAppRole =
                        HttpRequest.newBuilder()
                                .uri(baseUri.resolve("/v1/sys/auth/approle"))
                                .header("X-Vault-Token", token)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"approle\"}"))
                                .build();
                client.send(enableAppRole, HttpResponse.BodyHandlers.ofString());

                // Create role
                String roleName = "pesitwizard-client";
                String policyJson =
                        "{\"token_ttl\":\"1h\",\"token_max_ttl\":\"4h\",\"policies\":[\"default\"],\"secret_id_ttl\":\"0\"}";
                HttpRequest createRole =
                        HttpRequest.newBuilder()
                                .uri(baseUri.resolve("/v1/auth/approle/role/" + roleName))
                                .header("X-Vault-Token", token)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(policyJson))
                                .build();
                client.send(createRole, HttpResponse.BodyHandlers.ofString());

                // Get role ID
                HttpRequest getRoleId =
                        HttpRequest.newBuilder()
                                .uri(
                                        baseUri.resolve(
                                                "/v1/auth/approle/role/" + roleName + "/role-id"))
                                .header("X-Vault-Token", token)
                                .GET()
                                .build();
                HttpResponse<String> roleIdResp =
                        client.send(getRoleId, HttpResponse.BodyHandlers.ofString());

                // Generate secret ID
                HttpRequest genSecretId =
                        HttpRequest.newBuilder()
                                .uri(
                                        baseUri.resolve(
                                                "/v1/auth/approle/role/" + roleName + "/secret-id"))
                                .header("X-Vault-Token", token)
                                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                                .build();
                HttpResponse<String> secretIdResp =
                        client.send(genSecretId, HttpResponse.BodyHandlers.ofString());

                // Parse responses (simple extraction)
                String roleIdBody = roleIdResp.body();
                String secretIdBody = secretIdResp.body();

                // Extract role_id from {"data":{"role_id":"xxx"}}
                String extractedRoleId = extractJsonValue(roleIdBody, "role_id");
                String extractedSecretId = extractJsonValue(secretIdBody, "secret_id");

                if (extractedRoleId != null && extractedSecretId != null) {
                    result.put("roleId", extractedRoleId);
                    result.put("secretId", extractedSecretId);
                    result.put("message", "Vault initialized with AppRole credentials");
                }
            }

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Vault setup blocked by SSRF validation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Vault setup failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Setup failed: " + e.getMessage()));
        }
    }

    /**
     * Validates that a URL does not target internal/private network ranges (SSRF protection).
     * Returns a safe base URI constructed from validated components to break the taint chain.
     *
     * @param url the URL to validate
     * @return a validated URI constructed from safe components
     * @throws IllegalArgumentException if the URL targets a restricted network range
     */
    private URI validateAndParseUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL: " + e.getMessage());
        }

        // Reject non-HTTP/HTTPS schemes
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only HTTP and HTTPS schemes are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must contain a valid hostname");
        }

        // Reject localhost by hostname
        if (host.equalsIgnoreCase("localhost")) {
            throw new IllegalArgumentException("URL targets a restricted network range");
        }

        // Resolve hostname to IP address and validate
        InetAddress resolved;
        try {
            resolved = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve hostname: " + host);
        }

        if (resolved.isLoopbackAddress()
                || resolved.isSiteLocalAddress()
                || resolved.isLinkLocalAddress()
                || resolved.isAnyLocalAddress()) {
            throw new IllegalArgumentException("URL targets a restricted network range");
        }

        // Additional check for IPv6 unique local addresses (fd00::/8)
        byte[] addressBytes = resolved.getAddress();
        if (addressBytes.length == 16 && (addressBytes[0] & 0xFF) == 0xFD) {
            throw new IllegalArgumentException("URL targets a restricted network range");
        }

        // Reconstruct URI from validated components to break taint chain
        try {
            return new URI(scheme, null, host, uri.getPort(), null, null, null);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL components: " + e.getMessage());
        }
    }

    /** Build a validated URI by combining the SSRF-checked base with a safe path. */
    private URI buildVaultUri(String userAddress, String path) {
        URI base = validateAndParseUrl(userAddress);
        return base.resolve(path);
    }

    private String extractJsonValue(String json, String key) {
        // Simple JSON value extraction (avoids Jackson dependency)
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}
