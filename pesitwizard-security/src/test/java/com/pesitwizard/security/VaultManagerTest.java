package com.pesitwizard.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for VaultManager.
 */
@DisplayName("VaultManager Tests")
class VaultManagerTest {

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should create VaultManager with valid address")
        void shouldCreateVaultManagerWithValidAddress() {
            VaultManager manager = new VaultManager("http://localhost:8200");
            assertThat(manager).isNotNull();
        }

        @Test
        @DisplayName("should strip trailing slashes from address")
        void shouldStripTrailingSlashesFromAddress() {
            VaultManager manager = new VaultManager("http://localhost:8200///");
            assertThat(manager).isNotNull();
        }
    }

    @Nested
    @DisplayName("Test Connection")
    class TestConnectionTests {

        @Test
        @DisplayName("should fail connection to non-existent Vault")
        void shouldFailConnectionToNonExistentVault() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            VaultManager.VaultTestResult result = manager.testConnection("test-token", null);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("Connection failed");
        }

        @Test
        @DisplayName("should fail connection with namespace")
        void shouldFailConnectionWithNamespace() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            VaultManager.VaultTestResult result = manager.testConnection("test-token", "test-namespace");

            assertThat(result.success()).isFalse();
        }
    }

    @Nested
    @DisplayName("Test AppRole")
    class TestAppRoleTests {

        @Test
        @DisplayName("should fail AppRole test to non-existent Vault")
        void shouldFailAppRoleTestToNonExistentVault() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            VaultManager.VaultTestResult result = manager.testAppRole("role-id", "secret-id", null);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("AppRole test failed");
        }

        @Test
        @DisplayName("should fail AppRole test with namespace")
        void shouldFailAppRoleTestWithNamespace() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            VaultManager.VaultTestResult result = manager.testAppRole("role-id", "secret-id", "my-namespace");

            assertThat(result.success()).isFalse();
        }
    }

    @Nested
    @DisplayName("Ensure KV Secrets Engine")
    class EnsureKvSecretsEngineTests {

        @Test
        @DisplayName("should fail to ensure KV when Vault not available")
        void shouldFailToEnsureKvWhenVaultNotAvailable() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            VaultManager.SetupResult result = manager.ensureKvSecretsEngine("token", "secret/data/test", null);

            assertThat(result.success()).isFalse();
        }

        @Test
        @DisplayName("should handle path starting with secret")
        void shouldHandlePathStartingWithSecret() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            VaultManager.SetupResult result = manager.ensureKvSecretsEngine("token", "secret/data/pesitwizard", null);

            assertThat(result.success()).isFalse();
        }

        @Test
        @DisplayName("should handle custom path")
        void shouldHandleCustomPath() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            VaultManager.SetupResult result = manager.ensureKvSecretsEngine("token", "custom/data/pesitwizard", null);

            assertThat(result.success()).isFalse();
        }
    }

    @Nested
    @DisplayName("Setup AppRole")
    class SetupAppRoleTests {

        @Test
        @DisplayName("should fail to setup AppRole when Vault not available")
        void shouldFailToSetupAppRoleWhenVaultNotAvailable() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            VaultManager.SetupResult result = manager.setupAppRole("token", "pesitwizard", null, null);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("failed");
        }

        @Test
        @DisplayName("should fail to setup AppRole with policy")
        void shouldFailToSetupAppRoleWithPolicy() {
            VaultManager manager = new VaultManager("http://localhost:59999");
            String policy = "path \"secret/*\" { capabilities = [\"read\"] }";

            VaultManager.SetupResult result = manager.setupAppRole("token", "pesitwizard", policy, null);

            assertThat(result.success()).isFalse();
        }

        @Test
        @DisplayName("should fail to setup AppRole with namespace")
        void shouldFailToSetupAppRoleWithNamespace() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            VaultManager.SetupResult result = manager.setupAppRole("token", "pesitwizard", null, "my-namespace");

            assertThat(result.success()).isFalse();
        }
    }

    @Nested
    @DisplayName("Get Role ID")
    class GetRoleIdTests {

        @Test
        @DisplayName("should return null when Vault not available")
        void shouldReturnNullWhenVaultNotAvailable() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            String roleId = manager.getRoleId("token", "pesitwizard", null);

            assertThat(roleId).isNull();
        }

        @Test
        @DisplayName("should return null with namespace when Vault not available")
        void shouldReturnNullWithNamespaceWhenVaultNotAvailable() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            String roleId = manager.getRoleId("token", "pesitwizard", "my-namespace");

            assertThat(roleId).isNull();
        }
    }

    @Nested
    @DisplayName("Generate Secret ID")
    class GenerateSecretIdTests {

        @Test
        @DisplayName("should return null when Vault not available")
        void shouldReturnNullWhenVaultNotAvailable() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            String secretId = manager.generateSecretId("token", "pesitwizard", null);

            assertThat(secretId).isNull();
        }

        @Test
        @DisplayName("should return null with namespace when Vault not available")
        void shouldReturnNullWithNamespaceWhenVaultNotAvailable() {
            VaultManager manager = new VaultManager("http://localhost:59999");

            String secretId = manager.generateSecretId("token", "pesitwizard", "my-namespace");

            assertThat(secretId).isNull();
        }
    }

    @Nested
    @DisplayName("Default Policy")
    class DefaultPolicyTests {

        @Test
        @DisplayName("should generate default policy for path")
        void shouldGenerateDefaultPolicyForPath() {
            String policy = VaultManager.getDefaultPolicy("secret/data/pesitwizard");

            assertThat(policy).contains("secret/data/pesitwizard/*");
            assertThat(policy).contains("capabilities");
            assertThat(policy).contains("create");
            assertThat(policy).contains("read");
            assertThat(policy).contains("update");
            assertThat(policy).contains("delete");
            assertThat(policy).contains("list");
        }

        @Test
        @DisplayName("should generate policy with metadata path")
        void shouldGeneratePolicyWithMetadataPath() {
            String policy = VaultManager.getDefaultPolicy("secret/data/myapp");

            assertThat(policy).contains("secret/metadata/data");
        }
    }

    @Nested
    @DisplayName("VaultTestResult Record")
    class VaultTestResultTests {

        @Test
        @DisplayName("should create result with all fields")
        void shouldCreateResultWithAllFields() {
            VaultManager.VaultTestResult result = new VaultManager.VaultTestResult(
                    true, "Success", "details", "token123");

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Success");
            assertThat(result.details()).isEqualTo("details");
            assertThat(result.token()).isEqualTo("token123");
        }

        @Test
        @DisplayName("should create result without token")
        void shouldCreateResultWithoutToken() {
            VaultManager.VaultTestResult result = new VaultManager.VaultTestResult(
                    false, "Failed", "error details");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("Failed");
            assertThat(result.details()).isEqualTo("error details");
            assertThat(result.token()).isNull();
        }
    }

    @Nested
    @DisplayName("SetupResult Record")
    class SetupResultTests {

        @Test
        @DisplayName("should create successful result")
        void shouldCreateSuccessfulResult() {
            VaultManager.SetupResult result = new VaultManager.SetupResult(true, "Setup complete");

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Setup complete");
        }

        @Test
        @DisplayName("should create failed result")
        void shouldCreateFailedResult() {
            VaultManager.SetupResult result = new VaultManager.SetupResult(false, "Setup failed");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("Setup failed");
        }
    }
}
