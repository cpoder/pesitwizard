package com.pesitwizard.server.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtRoleConverter Tests")
class JwtRoleConverterTest {

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private SecurityProperties.OAuth2Config oauth2Config;

    @Mock
    private SecurityProperties.RoleMappingConfig roleMappingConfig;

    private JwtRoleConverter converter;

    @BeforeEach
    void setUp() {
        when(securityProperties.getOauth2()).thenReturn(oauth2Config);
        when(securityProperties.getRoleMapping()).thenReturn(roleMappingConfig);
        when(oauth2Config.getRolesClaim()).thenReturn("roles");
        when(oauth2Config.getAlternativeRolesClaims()).thenReturn(List.of());
        when(roleMappingConfig.getDefaultRoles()).thenReturn(List.of("USER"));
        when(roleMappingConfig.getRolePrefix()).thenReturn("ROLE_");
        when(roleMappingConfig.getMappings()).thenReturn(Map.of());

        converter = new JwtRoleConverter(securityProperties);
    }

    private Jwt createJwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"), claims);
    }

    @Nested
    @DisplayName("Role Extraction Tests")
    class RoleExtractionTests {

        @Test
        @DisplayName("Should extract roles from simple claim")
        void shouldExtractRolesFromSimpleClaim() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", List.of("ADMIN", "USER"));
            Jwt jwt = createJwt(claims);

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).hasSize(2);
            assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        }

        @Test
        @DisplayName("Should extract roles from nested claim")
        void shouldExtractRolesFromNestedClaim() {
            when(oauth2Config.getRolesClaim()).thenReturn("realm_access.roles");

            Map<String, Object> claims = new HashMap<>();
            claims.put("realm_access", Map.of("roles", List.of("admin")));
            Jwt jwt = createJwt(claims);

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).hasSize(1);
            assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ADMIN");
        }

        @Test
        @DisplayName("Should use default roles when no roles found")
        void shouldUseDefaultRolesWhenNoRolesFound() {
            Jwt jwt = createJwt(Map.of("sub", "test-subject"));

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).hasSize(1);
            assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("Should handle comma-separated roles string")
        void shouldHandleCommaSeparatedRolesString() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", "ADMIN,USER");
            Jwt jwt = createJwt(claims);

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Role Mapping Tests")
    class RoleMappingTests {

        @Test
        @DisplayName("Should apply role mappings")
        void shouldApplyRoleMappings() {
            when(roleMappingConfig.getMappings()).thenReturn(Map.of("admin", "ADMIN"));

            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", List.of("admin"));
            Jwt jwt = createJwt(claims);

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ADMIN");
        }

        @Test
        @DisplayName("Should uppercase unmapped roles")
        void shouldUppercaseUnmappedRoles() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", List.of("operator"));
            Jwt jwt = createJwt(claims);

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_OPERATOR");
        }
    }

    @Nested
    @DisplayName("Username Extraction Tests")
    class UsernameExtractionTests {

        @Test
        @DisplayName("Should extract username from configured claim")
        void shouldExtractUsernameFromConfiguredClaim() {
            when(oauth2Config.getUsernameClaim()).thenReturn("username");

            Map<String, Object> claims = new HashMap<>();
            claims.put("username", "testuser");
            Jwt jwt = createJwt(claims);

            String username = converter.extractUsername(jwt);

            assertThat(username).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should fallback to preferred_username")
        void shouldFallbackToPreferredUsername() {
            when(oauth2Config.getUsernameClaim()).thenReturn("custom_claim");

            Map<String, Object> claims = new HashMap<>();
            claims.put("preferred_username", "fallback_user");
            Jwt jwt = createJwt(claims);

            String username = converter.extractUsername(jwt);

            assertThat(username).isEqualTo("fallback_user");
        }

        @Test
        @DisplayName("Should fallback to email")
        void shouldFallbackToEmail() {
            when(oauth2Config.getUsernameClaim()).thenReturn("custom_claim");

            Map<String, Object> claims = new HashMap<>();
            claims.put("email", "user@example.com");
            Jwt jwt = createJwt(claims);

            String username = converter.extractUsername(jwt);

            assertThat(username).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("Should fallback to sub")
        void shouldFallbackToSub() {
            when(oauth2Config.getUsernameClaim()).thenReturn("custom_claim");

            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", "subject-id");
            Jwt jwt = createJwt(claims);

            String username = converter.extractUsername(jwt);

            assertThat(username).isEqualTo("subject-id");
        }

        @Test
        @DisplayName("Should return unknown when no username found")
        void shouldReturnUnknownWhenNoUsernameFound() {
            when(oauth2Config.getUsernameClaim()).thenReturn("custom_claim");

            Jwt jwt = createJwt(Map.of("iss", "test-issuer"));

            String username = converter.extractUsername(jwt);

            assertThat(username).isEqualTo("unknown");
        }
    }

    @Nested
    @DisplayName("Alternative Claims Tests")
    class AlternativeClaimsTests {

        @Test
        @DisplayName("Should try alternative role claims")
        void shouldTryAlternativeRoleClaims() {
            when(oauth2Config.getRolesClaim()).thenReturn("roles");
            when(oauth2Config.getAlternativeRolesClaims()).thenReturn(List.of("groups"));

            Map<String, Object> claims = new HashMap<>();
            claims.put("groups", List.of("ADMIN"));
            Jwt jwt = createJwt(claims);

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ADMIN");
        }

        @Test
        @DisplayName("Should replace client_id placeholder")
        void shouldReplaceClientIdPlaceholder() {
            when(oauth2Config.getRolesClaim()).thenReturn("roles");
            when(oauth2Config.getAlternativeRolesClaims()).thenReturn(List.of("resource_access.${client_id}.roles"));
            when(oauth2Config.getClientId()).thenReturn("my-client");

            Map<String, Object> claims = new HashMap<>();
            claims.put("resource_access", Map.of("my-client", Map.of("roles", List.of("CLIENT_ADMIN"))));
            Jwt jwt = createJwt(claims);

            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_CLIENT_ADMIN");
        }
    }
}
