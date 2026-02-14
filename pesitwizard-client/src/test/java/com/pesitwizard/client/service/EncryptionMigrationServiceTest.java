package com.pesitwizard.client.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.client.entity.StorageConnection;
import com.pesitwizard.client.repository.PesitServerRepository;
import com.pesitwizard.client.repository.StorageConnectionRepository;
import com.pesitwizard.security.AbstractEncryptionMigrationService.MigrationResult;
import com.pesitwizard.security.SecretsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EncryptionMigrationServiceTest {

    @Mock private SecretsService secretsService;
    @Mock private PesitServerRepository serverRepository;
    @Mock private StorageConnectionRepository connectionRepository;

    private EncryptionMigrationService migrationService;

    @BeforeEach
    void setUp() {
        migrationService =
                new EncryptionMigrationService(
                        secretsService, serverRepository, connectionRepository, new ObjectMapper());
    }

    @Test
    void migrateAllToVault_vaultNotAvailable_returnsFalse() {
        when(secretsService.isVaultAvailable()).thenReturn(false);

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("not available");
    }

    @Test
    void migrateAllToVault_noData_succeeds() {
        when(secretsService.isVaultAvailable()).thenReturn(true);
        when(serverRepository.findAll()).thenReturn(List.of());
        when(connectionRepository.findAll()).thenReturn(List.of());

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.success()).isTrue();
        assertThat(result.totalMigrated()).isEqualTo(0);
        assertThat(result.totalSkipped()).isEqualTo(0);
    }

    @Test
    void migrateAllToVault_migratesTruststorePassword() {
        when(secretsService.isVaultAvailable()).thenReturn(true);

        PesitServer server =
                PesitServer.builder()
                        .name("test-server")
                        .host("localhost")
                        .port(6502)
                        .serverId("SRV1")
                        .build();
        server.setId("srv1");
        server.setTruststorePassword("AES:encrypted");
        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(connectionRepository.findAll()).thenReturn(List.of());

        when(secretsService.isEncrypted("AES:encrypted")).thenReturn(true);
        when(secretsService.decrypt("AES:encrypted")).thenReturn("plaintext");
        when(secretsService.storeInVault(eq("server/srv1/truststorePassword"), eq("plaintext")))
                .thenReturn("vault:ref1");

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.success()).isTrue();
        assertThat(result.totalMigrated()).isEqualTo(1);
        verify(serverRepository).save(server);
    }

    @Test
    void migrateAllToVault_migratesKeystorePassword() {
        when(secretsService.isVaultAvailable()).thenReturn(true);

        PesitServer server =
                PesitServer.builder()
                        .name("test-server")
                        .host("localhost")
                        .port(6502)
                        .serverId("SRV1")
                        .build();
        server.setId("srv1");
        server.setKeystorePassword("plain-password");
        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(connectionRepository.findAll()).thenReturn(List.of());

        when(secretsService.isEncrypted("plain-password")).thenReturn(false);
        when(secretsService.storeInVault(eq("server/srv1/keystorePassword"), eq("plain-password")))
                .thenReturn("vault:ref2");

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.success()).isTrue();
        assertThat(result.totalMigrated()).isEqualTo(1);
    }

    @Test
    void migrateAllToVault_skipsVaultRefPasswords() {
        when(secretsService.isVaultAvailable()).thenReturn(true);

        PesitServer server =
                PesitServer.builder()
                        .name("test-server")
                        .host("localhost")
                        .port(6502)
                        .serverId("SRV1")
                        .build();
        server.setId("srv1");
        server.setTruststorePassword("vault:already-in-vault");
        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(connectionRepository.findAll()).thenReturn(List.of());

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.success()).isTrue();
        assertThat(result.totalSkipped()).isEqualTo(1);
        verify(serverRepository, never()).save(any());
    }

    @Test
    void migrateAllToVault_migratesConnectionPasswords() {
        when(secretsService.isVaultAvailable()).thenReturn(true);
        when(serverRepository.findAll()).thenReturn(List.of());

        StorageConnection conn = new StorageConnection();
        conn.setId("c1");
        conn.setName("sftp-conn");
        conn.setConfigJson("{\"host\":\"example.com\",\"password\":\"secret123\"}");
        when(connectionRepository.findAll()).thenReturn(List.of(conn));

        when(secretsService.isEncrypted("secret123")).thenReturn(false);
        when(secretsService.storeInVault(eq("connection/c1/password"), eq("secret123")))
                .thenReturn("vault:pw1");

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.success()).isTrue();
        assertThat(result.totalMigrated()).isEqualTo(1);
        verify(connectionRepository).save(conn);
    }

    @Test
    void migrateAllToVault_skipsBlankConfigJson() {
        when(secretsService.isVaultAvailable()).thenReturn(true);
        when(serverRepository.findAll()).thenReturn(List.of());

        StorageConnection conn = new StorageConnection();
        conn.setId("c1");
        conn.setName("empty");
        conn.setConfigJson("");
        when(connectionRepository.findAll()).thenReturn(List.of(conn));

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.success()).isTrue();
        assertThat(result.totalSkipped()).isEqualTo(1);
    }

    @Test
    void migrateAllToVault_skipsNullConfigJson() {
        when(secretsService.isVaultAvailable()).thenReturn(true);
        when(serverRepository.findAll()).thenReturn(List.of());

        StorageConnection conn = new StorageConnection();
        conn.setId("c1");
        conn.setName("null-config");
        conn.setConfigJson(null);
        when(connectionRepository.findAll()).thenReturn(List.of(conn));

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.success()).isTrue();
        assertThat(result.totalSkipped()).isEqualTo(1);
    }

    @Test
    void migrateAllToVault_handlesInvalidJson() {
        when(secretsService.isVaultAvailable()).thenReturn(true);
        when(serverRepository.findAll()).thenReturn(List.of());

        StorageConnection conn = new StorageConnection();
        conn.setId("c1");
        conn.setName("bad-json");
        conn.setConfigJson("not json");
        when(connectionRepository.findAll()).thenReturn(List.of(conn));

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.success()).isTrue();
        assertThat(result.totalSkipped()).isEqualTo(1);
    }

    @Test
    void migrateAllToVault_skipsVaultRefInConnection() {
        when(secretsService.isVaultAvailable()).thenReturn(true);
        when(serverRepository.findAll()).thenReturn(List.of());

        StorageConnection conn = new StorageConnection();
        conn.setId("c1");
        conn.setName("vault-conn");
        conn.setConfigJson("{\"password\":\"vault:already\"}");
        when(connectionRepository.findAll()).thenReturn(List.of(conn));

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.success()).isTrue();
        assertThat(result.totalSkipped()).isEqualTo(1);
    }

    @Test
    void migrateAllToVault_returnsDetails() {
        when(secretsService.isVaultAvailable()).thenReturn(true);
        when(serverRepository.findAll()).thenReturn(List.of());
        when(connectionRepository.findAll()).thenReturn(List.of());

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.details()).hasSize(2);
        assertThat(result.details().get(0)).contains("PeSIT Servers");
        assertThat(result.details().get(1)).contains("Storage Connections");
    }

    @Test
    void migrateAllToVault_handlesMigrationError() {
        when(secretsService.isVaultAvailable()).thenReturn(true);

        PesitServer server =
                PesitServer.builder()
                        .name("test-server")
                        .host("localhost")
                        .port(6502)
                        .serverId("SRV1")
                        .build();
        server.setId("srv1");
        server.setTruststorePassword("AES:encrypted");
        when(serverRepository.findAll()).thenReturn(List.of(server));
        when(connectionRepository.findAll()).thenReturn(List.of());

        when(secretsService.isEncrypted("AES:encrypted")).thenReturn(true);
        when(secretsService.decrypt("AES:encrypted")).thenReturn("plaintext");
        when(secretsService.storeInVault(anyString(), anyString()))
                .thenThrow(new RuntimeException("Vault connection failed"));

        MigrationResult result = migrationService.migrateAllToVault();

        assertThat(result.success()).isTrue();
        assertThat(result.totalSkipped()).isEqualTo(1);
    }
}
