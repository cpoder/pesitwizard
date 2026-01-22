package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Integration tests for VaultManager using a real Vault instance.
 * Requires Vault running at localhost:8200 with dev token.
 */
@Tag("integration")
@DisplayName("VaultManager Integration Tests")
@EnabledIf("isVaultAvailable")
class VaultManagerIntegrationTest {

    private static final String VAULT_ADDR = "http://localhost:8200";
    private static final String VAULT_TOKEN = "test-root-token";

    private VaultManager vaultManager;

    static boolean isVaultAvailable() {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(2)).build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(VAULT_ADDR + "/v1/sys/health"))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .GET().build();
            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        vaultManager = new VaultManager(VAULT_ADDR);
    }

    @Nested
    @DisplayName("Connection Tests")
    class ConnectionTests {

        @Test
        @DisplayName("Should test connection successfully with valid token")
        void shouldTestConnectionWithValidToken() {
            VaultManager.VaultTestResult result = vaultManager.testConnection(VAULT_TOKEN, null);

            assertThat(result.success()).isTrue();
            assertThat(result.message()).contains("Connected to Vault");
            assertThat(result.token()).isEqualTo(VAULT_TOKEN);
        }

        @Test
        @DisplayName("Should handle empty token")
        void shouldHandleEmptyToken() {
            // In dev mode, health check may succeed even without proper auth
            // This test verifies the call doesn't throw exceptions
            VaultManager.VaultTestResult result = vaultManager.testConnection("", null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should test connection with namespace")
        void shouldTestConnectionWithNamespace() {
            // In dev mode, namespaces aren't available but the call should work
            VaultManager.VaultTestResult result = vaultManager.testConnection(VAULT_TOKEN, "");

            assertThat(result.success()).isTrue();
        }
    }

    @Nested
    @DisplayName("KV Secrets Engine Tests")
    class KvSecretsEngineTests {

        @Test
        @DisplayName("Should detect existing KV secrets engine")
        void shouldDetectExistingKvSecretsEngine() {
            // In dev mode, 'secret/' is already mounted
            VaultManager.SetupResult result = vaultManager.ensureKvSecretsEngine(
                    VAULT_TOKEN, "secret/data/test", null);

            assertThat(result.success()).isTrue();
            assertThat(result.message()).containsIgnoringCase("secret");
        }

        @Test
        @DisplayName("Should handle custom path for KV engine")
        void shouldHandleCustomPath() {
            VaultManager.SetupResult result = vaultManager.ensureKvSecretsEngine(
                    VAULT_TOKEN, "pesitwizard/data/secrets", null);

            // Should succeed or report already exists
            assertThat(result.success()).isTrue();
        }
    }

    @Nested
    @DisplayName("AppRole Setup Tests")
    class AppRoleSetupTests {

        @Test
        @DisplayName("Should setup AppRole")
        void shouldSetupAppRole() {
            String policy = """
                    path "secret/data/test/*" {
                      capabilities = ["create", "read", "update", "delete", "list"]
                    }
                    """;

            VaultManager.SetupResult result = vaultManager.setupAppRole(
                    VAULT_TOKEN, "test-role", policy, null);

            assertThat(result.success()).isTrue();
            assertThat(result.message()).contains("test-role");
        }

        @Test
        @DisplayName("Should get role ID after setup")
        void shouldGetRoleIdAfterSetup() {
            // First setup the role
            vaultManager.setupAppRole(VAULT_TOKEN, "id-test-role", null, null);

            // Then get the role ID
            String roleId = vaultManager.getRoleId(VAULT_TOKEN, "id-test-role", null);

            assertThat(roleId).isNotNull();
            assertThat(roleId).isNotBlank();
        }

        @Test
        @DisplayName("Should generate secret ID")
        void shouldGenerateSecretId() {
            // First setup the role
            vaultManager.setupAppRole(VAULT_TOKEN, "secret-test-role", null, null);

            // Then generate a secret ID
            String secretId = vaultManager.generateSecretId(VAULT_TOKEN, "secret-test-role", null);

            assertThat(secretId).isNotNull();
            assertThat(secretId).isNotBlank();
        }

        @Test
        @DisplayName("Should authenticate with AppRole credentials")
        void shouldAuthenticateWithAppRole() {
            // Setup role
            vaultManager.setupAppRole(VAULT_TOKEN, "auth-test-role", null, null);
            String roleId = vaultManager.getRoleId(VAULT_TOKEN, "auth-test-role", null);
            String secretId = vaultManager.generateSecretId(VAULT_TOKEN, "auth-test-role", null);

            // Test authentication
            VaultManager.VaultTestResult result = vaultManager.testAppRole(roleId, secretId, null);

            assertThat(result.success()).isTrue();
            assertThat(result.token()).isNotNull();
            assertThat(result.message()).contains("AppRole authentication successful");
        }

        @Test
        @DisplayName("Should fail AppRole auth with invalid credentials")
        void shouldFailAppRoleAuthWithInvalidCredentials() {
            VaultManager.VaultTestResult result = vaultManager.testAppRole(
                    "invalid-role-id", "invalid-secret-id", null);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).containsIgnoringCase("failed");
        }
    }

    @Nested
    @DisplayName("Record Types Tests")
    class RecordTypesTests {

        @Test
        @DisplayName("VaultTestResult should have all fields")
        void vaultTestResultShouldHaveAllFields() {
            VaultManager.VaultTestResult result = new VaultManager.VaultTestResult(
                    true, "Test message", "{\"details\":true}", "test-token");

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Test message");
            assertThat(result.details()).isEqualTo("{\"details\":true}");
            assertThat(result.token()).isEqualTo("test-token");
        }

        @Test
        @DisplayName("SetupResult should have all fields")
        void setupResultShouldHaveAllFields() {
            VaultManager.SetupResult result = new VaultManager.SetupResult(true, "Setup complete");

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Setup complete");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle trailing slash in address")
        void shouldHandleTrailingSlash() {
            VaultManager manager = new VaultManager(VAULT_ADDR + "/");
            VaultManager.VaultTestResult result = manager.testConnection(VAULT_TOKEN, null);

            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("Should return null for non-existent role ID")
        void shouldReturnNullForNonExistentRoleId() {
            String roleId = vaultManager.getRoleId(VAULT_TOKEN, "non-existent-role-xyz", null);
            assertThat(roleId).isNull();
        }

        @Test
        @DisplayName("Should return null for non-existent secret ID")
        void shouldReturnNullForNonExistentSecretId() {
            String secretId = vaultManager.generateSecretId(VAULT_TOKEN, "non-existent-role-xyz", null);
            assertThat(secretId).isNull();
        }
    }
}
