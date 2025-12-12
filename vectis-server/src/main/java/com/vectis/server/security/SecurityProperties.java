package com.vectis.server.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Security configuration properties.
 * Supports multiple authentication methods: OAuth2/OIDC, API keys, and basic
 * auth.
 */
@Data
@Component
@ConfigurationProperties(prefix = "pesit.security")
public class SecurityProperties {

    /**
     * Enable security (if false, all endpoints are open)
     */
    private boolean enabled = true;

    /**
     * Authentication mode: oauth2, apikey, basic, or mixed
     */
    private AuthMode mode = AuthMode.MIXED;

    /**
     * OAuth2/OIDC configuration
     */
    private OAuth2Config oauth2 = new OAuth2Config();

    /**
     * API key configuration
     */
    private ApiKeyConfig apiKey = new ApiKeyConfig();

    /**
     * Basic auth configuration (for development/testing)
     */
    private BasicAuthConfig basicAuth = new BasicAuthConfig();

    /**
     * Public endpoints that don't require authentication
     */
    private List<String> publicEndpoints = new ArrayList<>(List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"));

    /**
     * CORS configuration
     */
    private CorsConfig cors = new CorsConfig();

    /**
     * Role mappings from JWT claims to application roles
     */
    private RoleMappingConfig roleMapping = new RoleMappingConfig();

    /**
     * Authentication mode enum
     */
    public enum AuthMode {
        OAUTH2, // Only OAuth2/OIDC (JWT)
        APIKEY, // Only API keys
        BASIC, // Only basic auth
        MIXED // Support multiple methods
    }

    /**
     * OAuth2/OIDC configuration
     */
    @Data
    public static class OAuth2Config {
        /**
         * Enable OAuth2/OIDC authentication
         */
        private boolean enabled = true;

        /**
         * JWT issuer URI (for token validation)
         */
        private String issuerUri;

        /**
         * JWK Set URI (for public key retrieval)
         */
        private String jwkSetUri;

        /**
         * Expected audience in JWT
         */
        private String audience;

        /**
         * Claim name for username
         */
        private String usernameClaim = "preferred_username";

        /**
         * Claim name for roles
         */
        private String rolesClaim = "roles";

        /**
         * Alternative claim paths for roles (e.g., realm_access.roles for Keycloak)
         */
        private List<String> alternativeRolesClaims = new ArrayList<>(List.of(
                "realm_access.roles",
                "resource_access.${client_id}.roles",
                "groups"));

        /**
         * Client ID (for audience validation and role extraction)
         */
        private String clientId;

        /**
         * Supported IDPs with their configurations
         */
        private Map<String, IdpConfig> providers = new HashMap<>();
    }

    /**
     * IDP-specific configuration
     */
    @Data
    public static class IdpConfig {
        private String name;
        private String issuerUri;
        private String jwkSetUri;
        private String clientId;
        private String clientSecret;
        private String authorizationUri;
        private String tokenUri;
        private String userInfoUri;
        private List<String> scopes = new ArrayList<>(List.of("openid", "profile", "email"));
    }

    /**
     * API key configuration
     */
    @Data
    public static class ApiKeyConfig {
        /**
         * Enable API key authentication
         */
        private boolean enabled = true;

        /**
         * Header name for API key
         */
        private String headerName = "X-API-Key";

        /**
         * Query parameter name for API key (alternative to header)
         */
        private String queryParam = "api_key";

        /**
         * Static API keys (for simple setups - use database for production)
         */
        private Map<String, ApiKeyEntry> keys = new HashMap<>();
    }

    /**
     * API key entry
     */
    @Data
    public static class ApiKeyEntry {
        private String name;
        private String description;
        private List<String> roles = new ArrayList<>();
        private boolean enabled = true;
    }

    /**
     * Basic auth configuration
     */
    @Data
    public static class BasicAuthConfig {
        /**
         * Enable basic auth (for development/testing only)
         */
        private boolean enabled = false;

        /**
         * Static users (for simple setups - use database for production)
         */
        private Map<String, UserEntry> users = new HashMap<>();
    }

    /**
     * User entry for basic auth
     */
    @Data
    public static class UserEntry {
        private String password;
        private List<String> roles = new ArrayList<>();
        private boolean enabled = true;
    }

    /**
     * CORS configuration
     */
    @Data
    public static class CorsConfig {
        private boolean enabled = true;
        private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
        private List<String> exposedHeaders = new ArrayList<>();
        private boolean allowCredentials = false;
        private long maxAge = 3600;
    }

    /**
     * Role mapping configuration
     */
    @Data
    public static class RoleMappingConfig {
        /**
         * Prefix to add to roles (e.g., "ROLE_")
         */
        private String rolePrefix = "ROLE_";

        /**
         * Map external roles to internal roles
         */
        private Map<String, String> mappings = new HashMap<>();

        /**
         * Default roles for authenticated users
         */
        private List<String> defaultRoles = new ArrayList<>(List.of("USER"));
    }
}
