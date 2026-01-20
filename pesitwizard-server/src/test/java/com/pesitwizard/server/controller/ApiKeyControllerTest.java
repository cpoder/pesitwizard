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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.server.entity.ApiKey;
import com.pesitwizard.server.security.ApiKeyService;
import com.pesitwizard.server.security.ApiKeyService.ApiKeyResult;

@WebMvcTest(ApiKeyController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ApiKeyController Tests")
class ApiKeyControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private ApiKeyService apiKeyService;

        @Test
        @DisplayName("listApiKeys should return all API keys")
        void listApiKeysShouldReturnAllKeys() throws Exception {
                ApiKey key1 = createTestApiKey(1L, "key1");
                ApiKey key2 = createTestApiKey(2L, "key2");
                when(apiKeyService.getAllApiKeys()).thenReturn(List.of(key1, key2));

                mockMvc.perform(get("/api/v1/apikeys"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(2))
                                .andExpect(jsonPath("$[0].name").value("key1"))
                                .andExpect(jsonPath("$[1].name").value("key2"));
        }

        @Test
        @DisplayName("listActiveApiKeys should return only active keys")
        void listActiveApiKeysShouldReturnActiveKeys() throws Exception {
                ApiKey activeKey = createTestApiKey(1L, "active");
                activeKey.setActive(true);
                when(apiKeyService.getActiveApiKeys()).thenReturn(List.of(activeKey));

                mockMvc.perform(get("/api/v1/apikeys/active"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].name").value("active"));
        }

        @Test
        @DisplayName("getApiKey should return key when found")
        void getApiKeyShouldReturnKeyWhenFound() throws Exception {
                ApiKey key = createTestApiKey(1L, "testkey");
                when(apiKeyService.getApiKey(1L)).thenReturn(Optional.of(key));

                mockMvc.perform(get("/api/v1/apikeys/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("testkey"));
        }

        @Test
        @DisplayName("getApiKey should return 404 when not found")
        void getApiKeyShouldReturn404WhenNotFound() throws Exception {
                when(apiKeyService.getApiKey(999L)).thenReturn(Optional.empty());

                mockMvc.perform(get("/api/v1/apikeys/999"))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("createApiKey should create and return new key")
        void createApiKeyShouldCreateAndReturnNewKey() throws Exception {
                ApiKey key = createTestApiKey(1L, "newkey");
                ApiKeyResult result = new ApiKeyResult(key, "pk_plain_key_12345");
                when(apiKeyService.createApiKey(anyString(), any(), any(), any(), any(), any(), any(), any()))
                                .thenReturn(result);

                String requestBody = """
                                {
                                    "name": "newkey",
                                    "description": "Test key",
                                    "roles": ["ROLE_API"]
                                }
                                """;

                mockMvc.perform(post("/api/v1/apikeys")
                                .principal(() -> "testuser")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.apiKey.name").value("newkey"))
                                .andExpect(jsonPath("$.key").value("pk_plain_key_12345"));
        }

        @Test
        @DisplayName("createApiKey should return 400 on invalid request")
        void createApiKeyShouldReturn400OnInvalidRequest() throws Exception {
                when(apiKeyService.createApiKey(anyString(), any(), any(), any(), any(), any(), any(), any()))
                                .thenThrow(new IllegalArgumentException("Name already exists"));

                String requestBody = """
                                {
                                    "name": "duplicate",
                                    "roles": ["ROLE_API"]
                                }
                                """;

                mockMvc.perform(post("/api/v1/apikeys")
                                .principal(() -> "testuser")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Name already exists"));
        }

        @Test
        @DisplayName("updateApiKey should update existing key")
        void updateApiKeyShouldUpdateExistingKey() throws Exception {
                ApiKey updated = createTestApiKey(1L, "updated");
                updated.setDescription("Updated description");
                when(apiKeyService.updateApiKey(eq(1L), any(), any(), any(), any(), any(), any()))
                                .thenReturn(updated);

                String requestBody = """
                                {
                                    "description": "Updated description",
                                    "active": true
                                }
                                """;

                mockMvc.perform(put("/api/v1/apikeys/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.description").value("Updated description"));
        }

        @Test
        @DisplayName("updateApiKey should return 404 when key not found")
        void updateApiKeyShouldReturn404WhenNotFound() throws Exception {
                when(apiKeyService.updateApiKey(eq(999L), any(), any(), any(), any(), any(), any()))
                                .thenThrow(new IllegalArgumentException("Not found"));

                mockMvc.perform(put("/api/v1/apikeys/999")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("revokeApiKey should revoke existing key")
        void revokeApiKeyShouldRevokeExistingKey() throws Exception {
                doNothing().when(apiKeyService).revokeApiKey(1L);

                mockMvc.perform(post("/api/v1/apikeys/1/revoke"))
                                .andExpect(status().isOk());

                verify(apiKeyService).revokeApiKey(1L);
        }

        @Test
        @DisplayName("revokeApiKey should return 404 when not found")
        void revokeApiKeyShouldReturn404WhenNotFound() throws Exception {
                doThrow(new IllegalArgumentException("Not found")).when(apiKeyService).revokeApiKey(999L);

                mockMvc.perform(post("/api/v1/apikeys/999/revoke"))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("regenerateApiKey should regenerate existing key")
        void regenerateApiKeyShouldRegenerateExistingKey() throws Exception {
                ApiKey key = createTestApiKey(1L, "regen");
                ApiKeyResult result = new ApiKeyResult(key, "pk_new_key_67890");
                when(apiKeyService.regenerateApiKey(1L)).thenReturn(result);

                mockMvc.perform(post("/api/v1/apikeys/1/regenerate"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.key").value("pk_new_key_67890"));
        }

        @Test
        @DisplayName("regenerateApiKey should return 404 when not found")
        void regenerateApiKeyShouldReturn404WhenNotFound() throws Exception {
                when(apiKeyService.regenerateApiKey(999L)).thenThrow(new IllegalArgumentException("Not found"));

                mockMvc.perform(post("/api/v1/apikeys/999/regenerate"))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deleteApiKey should delete existing key")
        void deleteApiKeyShouldDeleteExistingKey() throws Exception {
                doNothing().when(apiKeyService).deleteApiKey(1L);

                mockMvc.perform(delete("/api/v1/apikeys/1"))
                                .andExpect(status().isNoContent());

                verify(apiKeyService).deleteApiKey(1L);
        }

        @Test
        @DisplayName("deleteApiKey should return 404 when not found")
        void deleteApiKeyShouldReturn404WhenNotFound() throws Exception {
                doThrow(new IllegalArgumentException("Not found")).when(apiKeyService).deleteApiKey(999L);

                mockMvc.perform(delete("/api/v1/apikeys/999"))
                                .andExpect(status().isNotFound());
        }

        private ApiKey createTestApiKey(Long id, String name) {
                ApiKey key = new ApiKey();
                key.setId(id);
                key.setName(name);
                key.setDescription("Test description");
                key.setKeyPrefix("pk_test_");
                key.setRoles(List.of("ROLE_API"));
                key.setActive(true);
                key.setCreatedAt(Instant.now());
                key.setCreatedBy("test");
                return key;
        }
}
