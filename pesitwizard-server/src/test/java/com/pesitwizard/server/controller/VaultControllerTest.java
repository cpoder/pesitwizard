package com.pesitwizard.server.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.pesitwizard.security.SecretsProvider;
import com.pesitwizard.security.SecretsService;
import com.pesitwizard.server.entity.Partner;
import com.pesitwizard.server.service.ConfigService;

@WebMvcTest(VaultController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("VaultController Tests")
class VaultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SecretsService secretsService;

    @MockBean
    private SecretsProvider secretsProvider;

    @MockBean
    private ConfigService configService;

    @Nested
    @DisplayName("GET /api/v1/config/vault/status")
    class StatusTests {

        @Test
        @DisplayName("Should return Vault status")
        void shouldReturnVaultStatus() throws Exception {
            when(secretsProvider.isAvailable()).thenReturn(true);
            when(secretsService.getStatus()).thenReturn(
                    new SecretsService.SecretsProviderStatus("VAULT", true, "Vault available"));

            mockMvc.perform(get("/api/v1/config/vault/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.providerType").value("VAULT"))
                    .andExpect(jsonPath("$.available").value(true));
        }

        @Test
        @DisplayName("Should return status when Vault is not available")
        void shouldReturnStatusWhenVaultNotAvailable() throws Exception {
            when(secretsProvider.isAvailable()).thenReturn(false);
            when(secretsService.getStatus()).thenReturn(
                    new SecretsService.SecretsProviderStatus("AES", true, "AES fallback"));

            mockMvc.perform(get("/api/v1/config/vault/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.providerType").value("AES"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/config/vault/configure")
    class ConfigureTests {

        @Test
        @DisplayName("Should reject missing address")
        void shouldRejectMissingAddress() throws Exception {
            mockMvc.perform(post("/api/v1/config/vault/configure")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\": \"test-token\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Vault address and token are required"));
        }

        @Test
        @DisplayName("Should reject missing token")
        void shouldRejectMissingToken() throws Exception {
            mockMvc.perform(post("/api/v1/config/vault/configure")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"address\": \"http://vault:8200\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Should reject empty address")
        void shouldRejectEmptyAddress() throws Exception {
            mockMvc.perform(post("/api/v1/config/vault/configure")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"address\": \"\", \"token\": \"test-token\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Should handle valid configuration request")
        void shouldHandleValidConfigurationRequest() throws Exception {
            mockMvc.perform(post("/api/v1/config/vault/configure")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                            "{\"address\": \"http://vault:8200\", \"token\": \"test-token\", \"path\": \"secret/data/test\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Failed to connect to Vault"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/config/vault/encrypt-existing")
    class EncryptExistingTests {

        @Test
        @DisplayName("Should reject when Vault not available")
        void shouldRejectWhenVaultNotAvailable() throws Exception {
            when(secretsProvider.isAvailable()).thenReturn(false);

            mockMvc.perform(post("/api/v1/config/vault/encrypt-existing"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Vault not configured"));
        }

        @Test
        @DisplayName("Should encrypt unencrypted passwords")
        void shouldEncryptUnencryptedPasswords() throws Exception {
            when(secretsProvider.isAvailable()).thenReturn(true);

            Partner partner = new Partner();
            partner.setId("partner-1");
            partner.setPassword("plaintext-password");

            when(configService.getAllPartners()).thenReturn(List.of(partner));
            when(secretsService.isEncrypted("plaintext-password")).thenReturn(false);
            when(secretsService.encryptForStorage("plaintext-password")).thenReturn("AES:v2:encrypted");

            mockMvc.perform(post("/api/v1/config/vault/encrypt-existing"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.encrypted").value(1))
                    .andExpect(jsonPath("$.skipped").value(0));

            verify(configService).savePartner(any(Partner.class));
        }

        @Test
        @DisplayName("Should skip already encrypted passwords")
        void shouldSkipAlreadyEncryptedPasswords() throws Exception {
            when(secretsProvider.isAvailable()).thenReturn(true);

            Partner partner = new Partner();
            partner.setId("partner-1");
            partner.setPassword("AES:v2:already-encrypted");

            when(configService.getAllPartners()).thenReturn(List.of(partner));
            when(secretsService.isEncrypted("AES:v2:already-encrypted")).thenReturn(true);

            mockMvc.perform(post("/api/v1/config/vault/encrypt-existing"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.encrypted").value(0))
                    .andExpect(jsonPath("$.skipped").value(1));

            verify(configService, never()).savePartner(any());
        }

        @Test
        @DisplayName("Should skip partners with null password")
        void shouldSkipPartnersWithNullPassword() throws Exception {
            when(secretsProvider.isAvailable()).thenReturn(true);

            Partner partner = new Partner();
            partner.setId("partner-1");
            partner.setPassword(null);

            when(configService.getAllPartners()).thenReturn(List.of(partner));

            mockMvc.perform(post("/api/v1/config/vault/encrypt-existing"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.encrypted").value(0))
                    .andExpect(jsonPath("$.skipped").value(1));
        }

        @Test
        @DisplayName("Should skip partners with empty password")
        void shouldSkipPartnersWithEmptyPassword() throws Exception {
            when(secretsProvider.isAvailable()).thenReturn(true);

            Partner partner = new Partner();
            partner.setId("partner-1");
            partner.setPassword("");

            when(configService.getAllPartners()).thenReturn(List.of(partner));

            mockMvc.perform(post("/api/v1/config/vault/encrypt-existing"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.encrypted").value(0))
                    .andExpect(jsonPath("$.skipped").value(1));
        }

        @Test
        @DisplayName("Should handle empty partner list")
        void shouldHandleEmptyPartnerList() throws Exception {
            when(secretsProvider.isAvailable()).thenReturn(true);
            when(configService.getAllPartners()).thenReturn(List.of());

            mockMvc.perform(post("/api/v1/config/vault/encrypt-existing"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.encrypted").value(0))
                    .andExpect(jsonPath("$.skipped").value(0));
        }
    }
}
