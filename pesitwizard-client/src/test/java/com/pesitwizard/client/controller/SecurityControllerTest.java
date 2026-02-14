package com.pesitwizard.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "nosecurity"})
class SecurityControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void getStatus_returnsEncryptionInfo() throws Exception {
        mockMvc.perform(get("/api/v1/security/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encryption").exists())
                .andExpect(jsonPath("$.encryption.enabled").exists())
                .andExpect(jsonPath("$.encryption.mode").exists())
                .andExpect(jsonPath("$.aes").exists())
                .andExpect(jsonPath("$.vault").exists());
    }

    @Test
    void generateKey_returnsBase64Key() throws Exception {
        mockMvc.perform(post("/api/v1/security/generate-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").exists())
                .andExpect(jsonPath("$.instructions").exists());
    }

    @Test
    void testEncryption_returnsResult() throws Exception {
        var request = Map.of("value", "hello");
        mockMvc.perform(
                        post("/api/v1/security/test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists());
    }

    @Test
    void testEncryption_defaultValue() throws Exception {
        mockMvc.perform(
                        post("/api/v1/security/test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists());
    }

    @Test
    void getVaultConfig_returnsInstructions() throws Exception {
        mockMvc.perform(get("/api/v1/security/vault/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructions").exists())
                .andExpect(jsonPath("$.variables").exists())
                .andExpect(jsonPath("$.currentMode").exists());
    }

    @Test
    void getVaultStatus_returnsStatus() throws Exception {
        mockMvc.perform(get("/api/v1/security/vault/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").exists())
                .andExpect(jsonPath("$.configured").exists());
    }

    @Test
    void testVault_missingParams_returns400() throws Exception {
        var request = Map.of("address", "");
        mockMvc.perform(
                        post("/api/v1/security/vault/test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testVault_localhostSsrf_returns400() throws Exception {
        var request = Map.of("address", "http://localhost:8200", "token", "test-token");
        mockMvc.perform(
                        post("/api/v1/security/vault/test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testVaultAppRole_missingParams_returns400() throws Exception {
        var request = Map.of("address", "");
        mockMvc.perform(
                        post("/api/v1/security/vault/test-approle")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testVaultAppRole_missingRoleAndSecret_returns400() throws Exception {
        var request = Map.of("address", "http://vault.example.com:8200");
        mockMvc.perform(
                        post("/api/v1/security/vault/test-approle")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setupVault_missingParams_returns400() throws Exception {
        var request = Map.of("address", "");
        mockMvc.perform(
                        post("/api/v1/security/vault/setup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void setupVault_localhostSsrf_returns400() throws Exception {
        var request = Map.of("address", "http://localhost:8200", "token", "root");
        mockMvc.perform(
                        post("/api/v1/security/vault/setup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void migrateToVault_returnsResult() throws Exception {
        mockMvc.perform(post("/api/v1/security/encryption/migrate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists());
    }
}
