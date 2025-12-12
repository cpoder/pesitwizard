package com.vectis.server.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.vectis.server.security.ApiKeyService;
import com.vectis.server.security.JwtRoleConverter;
import com.vectis.server.security.SecurityProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Security integration tests using mock JWT tokens and API keys.
 * <p>
 * For full Keycloak integration testing:
 * 1. Start Keycloak: cd src/test/resources/keycloak && docker-compose up -d
 * 2. Run tests with profile: -Dspring.profiles.active=keycloak-test
 * 
 * Users configured in Keycloak:
 * - admin/admin (ADMIN role)
 * - operator/operator (OPERATOR role)
 * - user/user (USER role)
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Security Integration Tests")
@SuppressWarnings("null") // Spring Security test utilities are safe
public class KeycloakSecurityTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ApiKeyService apiKeyService;

        @Autowired
        private SecurityProperties securityProperties;

        @Nested
        @DisplayName("JWT Role Extraction Tests")
        class JwtRoleExtractionTests {

                private JwtRoleConverter roleConverter;

                @BeforeEach
                void setup() {
                        roleConverter = new JwtRoleConverter(securityProperties);
                }

                @Test
                @DisplayName("Should extract roles from 'roles' claim")
                void shouldExtractRolesFromRolesClaim() {
                        Jwt jwt = Jwt.withTokenValue("test-token")
                                        .header("alg", "RS256")
                                        .claim("sub", "user123")
                                        .claim("preferred_username", "testuser")
                                        .claim("roles", List.of("ADMIN", "USER"))
                                        .issuedAt(Instant.now())
                                        .expiresAt(Instant.now().plusSeconds(3600))
                                        .build();

                        var authorities = roleConverter.convert(jwt);

                        assertThat(authorities).extracting("authority")
                                        .contains("ROLE_ADMIN", "ROLE_USER");

                        log.info("Extracted roles from 'roles' claim: {}", authorities);
                }

                @Test
                @DisplayName("Should extract roles from Keycloak realm_access.roles")
                void shouldExtractRolesFromKeycloakRealmAccess() {
                        Jwt jwt = Jwt.withTokenValue("test-token")
                                        .header("alg", "RS256")
                                        .claim("sub", "user123")
                                        .claim("preferred_username", "testuser")
                                        .claim("realm_access", Map.of("roles", List.of("OPERATOR")))
                                        .issuedAt(Instant.now())
                                        .expiresAt(Instant.now().plusSeconds(3600))
                                        .build();

                        var authorities = roleConverter.convert(jwt);

                        assertThat(authorities).extracting("authority")
                                        .contains("ROLE_OPERATOR");

                        log.info("Extracted roles from Keycloak realm_access: {}", authorities);
                }

                @Test
                @DisplayName("Should extract username from JWT")
                void shouldExtractUsername() {
                        Jwt jwt = Jwt.withTokenValue("test-token")
                                        .header("alg", "RS256")
                                        .claim("sub", "user123")
                                        .claim("preferred_username", "john.doe")
                                        .claim("email", "john@example.com")
                                        .issuedAt(Instant.now())
                                        .expiresAt(Instant.now().plusSeconds(3600))
                                        .build();

                        String username = roleConverter.extractUsername(jwt);

                        assertThat(username).isEqualTo("john.doe");
                        log.info("Extracted username: {}", username);
                }

                @Test
                @DisplayName("Should use default roles when no roles in JWT")
                void shouldUseDefaultRoles() {
                        Jwt jwt = Jwt.withTokenValue("test-token")
                                        .header("alg", "RS256")
                                        .claim("sub", "user123")
                                        .issuedAt(Instant.now())
                                        .expiresAt(Instant.now().plusSeconds(3600))
                                        .build();

                        var authorities = roleConverter.convert(jwt);

                        // Should have default USER role
                        assertThat(authorities).extracting("authority")
                                        .contains("ROLE_USER");

                        log.info("Default roles applied: {}", authorities);
                }
        }

        @Nested
        @DisplayName("API Key Authentication Tests")
        class ApiKeyAuthenticationTests {

                @Test
                @DisplayName("Should authenticate with valid API key")
                void shouldAuthenticateWithValidApiKey() throws Exception {
                        // Create API key
                        var result = apiKeyService.createApiKey(
                                        "test-api-key-" + System.currentTimeMillis(),
                                        "Test API key",
                                        List.of("USER"),
                                        null,
                                        null,
                                        null,
                                        null,
                                        "test");

                        String apiKey = result.getPlainKey();
                        log.info("Created API key: {}", result.getApiKey().getKeyPrefix() + "...");

                        // Test authentication
                        mockMvc.perform(get("/api/v1/transfers")
                                        .header("X-API-Key", apiKey))
                                        .andExpect(status().isOk());

                        log.info("API key authentication successful");
                }

                @Test
                @DisplayName("Should reject invalid API key")
                void shouldRejectInvalidApiKey() throws Exception {
                        mockMvc.perform(get("/api/v1/transfers")
                                        .header("X-API-Key", "invalid-key"))
                                        .andExpect(status().isUnauthorized());

                        log.info("Invalid API key correctly rejected");
                }

                @Test
                @DisplayName("Should authenticate with API key in query parameter")
                void shouldAuthenticateWithApiKeyInQueryParam() throws Exception {
                        var result = apiKeyService.createApiKey(
                                        "query-param-key-" + System.currentTimeMillis(),
                                        "Query param test key",
                                        List.of("USER"),
                                        null,
                                        null,
                                        null,
                                        null,
                                        "test");

                        mockMvc.perform(get("/api/v1/transfers")
                                        .param("api_key", result.getPlainKey()))
                                        .andExpect(status().isOk());

                        log.info("API key in query parameter authentication successful");
                }

                @Test
                @DisplayName("Should authenticate with Authorization header")
                void shouldAuthenticateWithAuthorizationHeader() throws Exception {
                        var result = apiKeyService.createApiKey(
                                        "auth-header-key-" + System.currentTimeMillis(),
                                        "Auth header test key",
                                        List.of("USER"),
                                        null,
                                        null,
                                        null,
                                        null,
                                        "test");

                        mockMvc.perform(get("/api/v1/transfers")
                                        .header("Authorization", "ApiKey " + result.getPlainKey()))
                                        .andExpect(status().isOk());

                        log.info("API key in Authorization header authentication successful");
                }
        }

        @Nested
        @DisplayName("Role-Based Access Control Tests")
        class RbacTests {

                @Test
                @DisplayName("ADMIN should access admin endpoints")
                void adminShouldAccessAdminEndpoints() throws Exception {
                        mockMvc.perform(get("/api/v1/certificates")
                                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                                        .andExpect(status().isOk());

                        log.info("ADMIN access to /api/v1/certificates: OK");
                }

                @Test
                @DisplayName("USER should not access admin endpoints")
                void userShouldNotAccessAdminEndpoints() throws Exception {
                        mockMvc.perform(get("/api/v1/certificates")
                                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                                        .andExpect(status().isForbidden());

                        log.info("USER access to /api/v1/certificates: Forbidden (correct)");
                }

                @Test
                @DisplayName("OPERATOR should access server endpoints")
                void operatorShouldAccessServerEndpoints() throws Exception {
                        // Note: /api/v1/servers may return 404 if no servers configured, but that's OK
                        // We're testing that OPERATOR role is allowed (not 401/403), not that servers
                        // exist
                        MvcResult result = mockMvc.perform(get("/api/v1/servers")
                                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                                        .authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                                        .andReturn();

                        int status = result.getResponse().getStatus();
                        assertThat(status).isIn(200, 404); // OK or Not Found, but not 401/403

                        log.info("OPERATOR access to /api/v1/servers: status={} (allowed)", status);
                }

                @Test
                @DisplayName("USER should access transfer endpoints")
                void userShouldAccessTransferEndpoints() throws Exception {
                        mockMvc.perform(get("/api/v1/transfers")
                                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                                        .andExpect(status().isOk());

                        log.info("USER access to /api/v1/transfers: OK");
                }

                @Test
                @DisplayName("Unauthenticated should not access protected endpoints")
                void unauthenticatedShouldNotAccessProtectedEndpoints() throws Exception {
                        mockMvc.perform(get("/api/v1/transfers"))
                                        .andExpect(status().isUnauthorized());

                        log.info("Unauthenticated access to /api/v1/transfers: Unauthorized (correct)");
                }

                @Test
                @DisplayName("Should access public health endpoint without auth")
                void shouldAccessPublicEndpointWithoutAuth() throws Exception {
                        // Health endpoint may return 503 if cluster is not connected, but should not
                        // return 401/403
                        MvcResult result = mockMvc.perform(get("/actuator/health"))
                                        .andReturn();

                        int status = result.getResponse().getStatus();
                        // Accept 200 (healthy) or 503 (unhealthy but accessible) - not 401/403
                        assertThat(status).isIn(200, 503);

                        log.info("Public endpoint /actuator/health: status={} (accessible without auth)", status);
                }
        }

        @Nested
        @DisplayName("API Key Management Tests")
        class ApiKeyManagementTests {

                @Test
                @DisplayName("Should create and revoke API key")
                void shouldCreateAndRevokeApiKey() throws Exception {
                        // Create key
                        var result = apiKeyService.createApiKey(
                                        "revoke-test-key-" + System.currentTimeMillis(),
                                        "Key to be revoked",
                                        List.of("USER"),
                                        null,
                                        null,
                                        null,
                                        null,
                                        "test");

                        String apiKey = result.getPlainKey();
                        Long keyId = result.getApiKey().getId();

                        // Verify it works
                        mockMvc.perform(get("/api/v1/transfers")
                                        .header("X-API-Key", apiKey))
                                        .andExpect(status().isOk());

                        // Revoke it
                        apiKeyService.revokeApiKey(keyId);

                        // Verify it no longer works
                        mockMvc.perform(get("/api/v1/transfers")
                                        .header("X-API-Key", apiKey))
                                        .andExpect(status().isUnauthorized());

                        log.info("API key revocation working correctly");
                }

                @Test
                @DisplayName("Should regenerate API key")
                void shouldRegenerateApiKey() throws Exception {
                        var result = apiKeyService.createApiKey(
                                        "regen-test-key-" + System.currentTimeMillis(),
                                        "Key to be regenerated",
                                        List.of("USER"),
                                        null,
                                        null,
                                        null,
                                        null,
                                        "test");

                        String oldKey = result.getPlainKey();
                        Long keyId = result.getApiKey().getId();

                        // Regenerate
                        var newResult = apiKeyService.regenerateApiKey(keyId);
                        String newKey = newResult.getPlainKey();

                        assertThat(newKey).isNotEqualTo(oldKey);

                        // Old key should not work
                        mockMvc.perform(get("/api/v1/transfers")
                                        .header("X-API-Key", oldKey))
                                        .andExpect(status().isUnauthorized());

                        // New key should work
                        mockMvc.perform(get("/api/v1/transfers")
                                        .header("X-API-Key", newKey))
                                        .andExpect(status().isOk());

                        log.info("API key regeneration working correctly");
                }

                @Test
                @DisplayName("Should enforce API key roles")
                void shouldEnforceApiKeyRoles() throws Exception {
                        // Create key with USER role only
                        var result = apiKeyService.createApiKey(
                                        "role-test-key-" + System.currentTimeMillis(),
                                        "Key with USER role",
                                        List.of("USER"),
                                        null,
                                        null,
                                        null,
                                        null,
                                        "test");

                        String apiKey = result.getPlainKey();

                        // Should access USER endpoints
                        mockMvc.perform(get("/api/v1/transfers")
                                        .header("X-API-Key", apiKey))
                                        .andExpect(status().isOk());

                        // Should NOT access ADMIN endpoints
                        mockMvc.perform(get("/api/v1/certificates")
                                        .header("X-API-Key", apiKey))
                                        .andExpect(status().isForbidden());

                        log.info("API key role enforcement working correctly");
                }
        }

        @Nested
        @DisplayName("Keycloak Integration Tests")
        @Disabled("Requires running Keycloak - enable for integration testing")
        class KeycloakIntegrationTests {

                private static final String KEYCLOAK_URL = "http://localhost:8180";
                private static final String REALM = "pesit";
                private static final String CLIENT_ID = "pesit-server";
                private static final String CLIENT_SECRET = "pesit-secret";

                @Test
                @DisplayName("Should obtain token from Keycloak")
                void shouldObtainTokenFromKeycloak() throws Exception {
                        // This test requires Keycloak to be running
                        // Start with: cd src/test/resources/keycloak && docker-compose up -d

                        String tokenEndpoint = KEYCLOAK_URL + "/realms/" + REALM + "/protocol/openid-connect/token";

                        // Get token using password grant
                        // In real test, use RestTemplate or WebClient to call Keycloak

                        log.info("Token endpoint: {}", tokenEndpoint);
                        log.info("To test manually:");
                        log.info("curl -X POST {} \\", tokenEndpoint);
                        log.info("  -d 'grant_type=password' \\");
                        log.info("  -d 'client_id={}' \\", CLIENT_ID);
                        log.info("  -d 'client_secret={}' \\", CLIENT_SECRET);
                        log.info("  -d 'username=admin' \\");
                        log.info("  -d 'password=admin'");
                }

                @Test
                @DisplayName("Should validate JWT from Keycloak")
                void shouldValidateJwtFromKeycloak() {
                        // This test requires Keycloak to be running
                        // The application should be configured with:
                        // pesit.security.oauth2.jwk-set-uri=http://localhost:8180/realms/pesit/protocol/openid-connect/certs

                        log.info("JWK Set URI: {}/realms/{}/protocol/openid-connect/certs", KEYCLOAK_URL, REALM);
                }
        }
}
