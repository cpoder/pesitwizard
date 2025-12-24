package com.pesitwizard.server.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.pesitwizard.server.entity.SecretEntry;
import com.pesitwizard.server.entity.SecretEntry.SecretScope;
import com.pesitwizard.server.entity.SecretEntry.SecretType;
import com.pesitwizard.server.service.SecretService;
import com.pesitwizard.server.service.SecretService.SecretStatistics;

@WebMvcTest(SecretController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SecretController Tests")
class SecretControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SecretService secretService;

    @Test
    @DisplayName("listSecrets should return all secrets")
    void listSecretsShouldReturnAllSecrets() throws Exception {
        SecretEntry secret1 = createTestSecret(1L, "secret1");
        SecretEntry secret2 = createTestSecret(2L, "secret2");
        when(secretService.getAllSecrets()).thenReturn(List.of(secret1, secret2));

        mockMvc.perform(get("/api/v1/secrets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("secret1"));
    }

    @Test
    @DisplayName("listSecretsByType should filter by type")
    void listSecretsByTypeShouldFilterByType() throws Exception {
        SecretEntry secret = createTestSecret(1L, "password");
        secret.setSecretType(SecretType.PASSWORD);
        when(secretService.getSecretsByType(SecretType.PASSWORD)).thenReturn(List.of(secret));

        mockMvc.perform(get("/api/v1/secrets/type/PASSWORD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("listSecretsByScope should filter by scope")
    void listSecretsByScopeShouldFilterByScope() throws Exception {
        SecretEntry secret = createTestSecret(1L, "global");
        when(secretService.getSecretsByScope(SecretScope.GLOBAL)).thenReturn(List.of(secret));

        mockMvc.perform(get("/api/v1/secrets/scope/GLOBAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("getSecret should return secret when found")
    void getSecretShouldReturnSecretWhenFound() throws Exception {
        SecretEntry secret = createTestSecret(1L, "mysecret");
        when(secretService.getSecretById(1L)).thenReturn(Optional.of(secret));

        mockMvc.perform(get("/api/v1/secrets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("mysecret"));
    }

    @Test
    @DisplayName("getSecret should return 404 when not found")
    void getSecretShouldReturn404WhenNotFound() throws Exception {
        when(secretService.getSecretById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/secrets/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("getSecretByName should return secret when found")
    void getSecretByNameShouldReturnSecretWhenFound() throws Exception {
        SecretEntry secret = createTestSecret(1L, "named");
        when(secretService.getSecret("named")).thenReturn(Optional.of(secret));

        mockMvc.perform(get("/api/v1/secrets/name/named"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("named"));
    }

    @Test
    @DisplayName("getSecretValue should return decrypted value")
    void getSecretValueShouldReturnDecryptedValue() throws Exception {
        when(secretService.getSecretValue("mysecret")).thenReturn(Optional.of("decrypted_value"));

        mockMvc.perform(get("/api/v1/secrets/name/mysecret/value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("mysecret"))
                .andExpect(jsonPath("$.value").value("decrypted_value"));
    }

    @Test
    @DisplayName("getSecretsForPartner should return partner secrets")
    void getSecretsForPartnerShouldReturnPartnerSecrets() throws Exception {
        SecretEntry secret = createTestSecret(1L, "partner_secret");
        secret.setPartnerId("PARTNER1");
        when(secretService.getSecretsForPartner("PARTNER1")).thenReturn(List.of(secret));

        mockMvc.perform(get("/api/v1/secrets/partner/PARTNER1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("getSecretsForServer should return server secrets")
    void getSecretsForServerShouldReturnServerSecrets() throws Exception {
        SecretEntry secret = createTestSecret(1L, "server_secret");
        secret.setServerId("SERVER1");
        when(secretService.getSecretsForServer("SERVER1")).thenReturn(List.of(secret));

        mockMvc.perform(get("/api/v1/secrets/server/SERVER1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("createSecret should create new secret")
    void createSecretShouldCreateNewSecret() throws Exception {
        SecretEntry secret = createTestSecret(1L, "newsecret");
        when(secretService.createSecret(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(secret);

        String requestBody = """
                {
                    "name": "newsecret",
                    "value": "secret_value",
                    "description": "Test secret"
                }
                """;

        mockMvc.perform(post("/api/v1/secrets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("newsecret"));
    }

    @Test
    @DisplayName("createSecret should return 400 on invalid request")
    void createSecretShouldReturn400OnInvalidRequest() throws Exception {
        when(secretService.createSecret(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Name already exists"));

        String requestBody = """
                {
                    "name": "duplicate",
                    "value": "value"
                }
                """;

        mockMvc.perform(post("/api/v1/secrets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("updateSecretValue should update value")
    void updateSecretValueShouldUpdateValue() throws Exception {
        SecretEntry secret = createTestSecret(1L, "updated");
        when(secretService.updateSecretValue(eq("updated"), anyString(), any())).thenReturn(secret);

        mockMvc.perform(put("/api/v1/secrets/name/updated/value")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": \"new_value\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("updateSecretMetadata should update metadata")
    void updateSecretMetadataShouldUpdateMetadata() throws Exception {
        SecretEntry secret = createTestSecret(1L, "updated");
        when(secretService.updateSecretMetadata(eq(1L), any(), any(), any(), any(), any())).thenReturn(secret);

        mockMvc.perform(put("/api/v1/secrets/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"Updated\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("rotateSecret should rotate secret")
    void rotateSecretShouldRotateSecret() throws Exception {
        SecretEntry secret = createTestSecret(1L, "rotated");
        secret.setVersion(2);
        when(secretService.rotateSecret(eq("rotated"), anyString(), any())).thenReturn(secret);

        mockMvc.perform(post("/api/v1/secrets/name/rotated/rotate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": \"new_value\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("deleteSecret should delete existing secret")
    void deleteSecretShouldDeleteExistingSecret() throws Exception {
        doNothing().when(secretService).deleteSecret(1L);

        mockMvc.perform(delete("/api/v1/secrets/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("deleteSecret should return 404 when not found")
    void deleteSecretShouldReturn404WhenNotFound() throws Exception {
        doThrow(new IllegalArgumentException("Not found")).when(secretService).deleteSecret(999L);

        mockMvc.perform(delete("/api/v1/secrets/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deactivateSecret should deactivate secret")
    void deactivateSecretShouldDeactivateSecret() throws Exception {
        SecretEntry secret = createTestSecret(1L, "deactivated");
        secret.setActive(false);
        when(secretService.deactivateSecret(1L)).thenReturn(secret);

        mockMvc.perform(post("/api/v1/secrets/1/deactivate"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("generateEncryptionKey should return generated key")
    void generateEncryptionKeyShouldReturnGeneratedKey() throws Exception {
        when(secretService.generateEncryptionKey()).thenReturn("generated_key_12345");

        mockMvc.perform(get("/api/v1/secrets/generate-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("generated_key_12345"));
    }

    @Test
    @DisplayName("getStatistics should return stats")
    void getStatisticsShouldReturnStats() throws Exception {
        SecretStatistics stats = new SecretStatistics();
        stats.setTotalSecrets(10);
        stats.setActiveSecrets(8);
        stats.setExpiredSecrets(2);
        when(secretService.getStatistics()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/secrets/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSecrets").value(10));
    }

    private SecretEntry createTestSecret(Long id, String name) {
        SecretEntry secret = new SecretEntry();
        secret.setId(id);
        secret.setName(name);
        secret.setDescription("Test description");
        secret.setSecretType(SecretType.GENERIC);
        secret.setScope(SecretScope.GLOBAL);
        secret.setVersion(1);
        secret.setActive(true);
        secret.setCreatedAt(Instant.now());
        secret.setCreatedBy("test");
        return secret;
    }
}
