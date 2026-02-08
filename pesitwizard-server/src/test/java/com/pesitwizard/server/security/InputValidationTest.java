package com.pesitwizard.server.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.server.controller.ApiKeyController.CreateApiKeyRequest;

/**
 * Tests for input validation across all API endpoints.
 * Verifies that malformed input is rejected with appropriate error messages.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InputValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("API Key Validation")
    class ApiKeyValidation {

        @Test
        @DisplayName("Should reject API key creation with empty name")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectEmptyName() throws Exception {
            CreateApiKeyRequest request = new CreateApiKeyRequest();
            request.setName("");
            request.setDescription("Test key");

            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Validation Failed"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')]").exists());
        }

        @Test
        @DisplayName("Should reject API key creation with name containing special characters")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectNameWithSpecialCharacters() throws Exception {
            CreateApiKeyRequest request = new CreateApiKeyRequest();
            request.setName("test@key#$%");
            request.setDescription("Test key");

            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Validation Failed"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')]").exists());
        }

        @Test
        @DisplayName("Should reject API key creation with name exceeding max length")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectNameExceedingMaxLength() throws Exception {
            CreateApiKeyRequest request = new CreateApiKeyRequest();
            request.setName("a".repeat(101)); // Max is 100
            request.setDescription("Test key");

            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Validation Failed"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')]").exists());
        }

        @Test
        @DisplayName("Should reject API key creation with invalid role")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectInvalidRole() throws Exception {
            String json = """
                    {
                        "name": "valid-name",
                        "description": "Test key",
                        "roles": ["INVALID_ROLE"]
                    }
                    """;

            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Validation Failed"));
        }

        @Test
        @DisplayName("Should reject API key creation with rate limit below minimum")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectRateLimitBelowMinimum() throws Exception {
            String json = """
                    {
                        "name": "valid-name",
                        "description": "Test key",
                        "rateLimit": 0
                    }
                    """;

            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Validation Failed"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field=='rateLimit')]").exists());
        }

        @Test
        @DisplayName("Should reject API key creation with rate limit above maximum")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectRateLimitAboveMaximum() throws Exception {
            String json = """
                    {
                        "name": "valid-name",
                        "description": "Test key",
                        "rateLimit": 100000
                    }
                    """;

            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Validation Failed"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field=='rateLimit')]").exists());
        }

        @Test
        @DisplayName("Should accept valid API key creation request")
        @WithMockUser(roles = "ADMIN")
        void shouldAcceptValidRequest() throws Exception {
            String json = """
                    {
                        "name": "valid-api-key",
                        "description": "A valid test key",
                        "roles": ["USER", "OPERATOR"],
                        "rateLimit": 100
                    }
                    """;

            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.apiKey.name").value("valid-api-key"));
        }
    }

    @Nested
    @DisplayName("Malformed Request Handling")
    class MalformedRequestHandling {

        @Test
        @DisplayName("Should reject malformed JSON")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectMalformedJson() throws Exception {
            String malformedJson = "{ this is not valid json }";

            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Malformed Request"));
        }

        @Test
        @DisplayName("Should reject empty request body")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectEmptyRequestBody() throws Exception {
            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject unsupported media type")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectUnsupportedMediaType() throws Exception {
            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("name=test"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
        }

        @Test
        @DisplayName("Should reject wrong HTTP method")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectWrongHttpMethod() throws Exception {
            mockMvc.perform(put("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.error").value("Method Not Allowed"));
        }
    }

    @Nested
    @DisplayName("Path Variable Validation")
    class PathVariableValidation {

        @Test
        @DisplayName("Should handle invalid ID format gracefully")
        @WithMockUser(roles = "ADMIN")
        void shouldHandleInvalidIdFormat() throws Exception {
            mockMvc.perform(post("/api/v1/apikeys/not-a-number/revoke").with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Type Mismatch"));
        }
    }

    @Nested
    @DisplayName("Secret Validation")
    class SecretValidation {

        @Test
        @DisplayName("Should reject secret creation with empty name")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectSecretWithEmptyName() throws Exception {
            String json = """
                    {
                        "name": "",
                        "value": "secret-value",
                        "description": "Test secret"
                    }
                    """;

            mockMvc.perform(post("/api/v1/secrets").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Validation Failed"));
        }

        @Test
        @DisplayName("Should reject secret creation with empty value")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectSecretWithEmptyValue() throws Exception {
            String json = """
                    {
                        "name": "test-secret",
                        "value": "",
                        "description": "Test secret"
                    }
                    """;

            mockMvc.perform(post("/api/v1/secrets").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Validation Failed"));
        }

        @Test
        @DisplayName("Should reject secret with name containing invalid characters")
        @WithMockUser(roles = "ADMIN")
        void shouldRejectSecretWithInvalidNameCharacters() throws Exception {
            String json = """
                    {
                        "name": "test secret with spaces",
                        "value": "secret-value",
                        "description": "Test secret"
                    }
                    """;

            mockMvc.perform(post("/api/v1/secrets").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Validation Failed"));
        }
    }

    @Nested
    @DisplayName("SQL Injection Prevention")
    class SqlInjectionPrevention {

        @Test
        @DisplayName("Should safely handle SQL injection attempts in name field")
        @WithMockUser(roles = "ADMIN")
        void shouldSafelyHandleSqlInjectionInName() throws Exception {
            String json = """
                    {
                        "name": "'; DROP TABLE api_keys; --",
                        "description": "Malicious attempt"
                    }
                    """;

            // Should be rejected due to invalid characters in name pattern
            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("XSS Prevention")
    class XssPrevention {

        @Test
        @DisplayName("Should safely handle XSS attempts in description field")
        @WithMockUser(roles = "ADMIN")
        void shouldSafelyHandleXssInDescription() throws Exception {
            String json = """
                    {
                        "name": "valid-name",
                        "description": "<script>alert('xss')</script>"
                    }
                    """;

            // Request should succeed, but XSS content should be stored safely
            // (not executed) due to proper output encoding
            mockMvc.perform(post("/api/v1/apikeys").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());
        }
    }
}
