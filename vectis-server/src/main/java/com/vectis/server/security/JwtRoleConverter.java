package com.vectis.server.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts JWT claims to Spring Security authorities.
 * Supports multiple IDP formats (Keycloak, Azure AD, Okta, etc.)
 */
@Slf4j
@RequiredArgsConstructor
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final SecurityProperties securityProperties;

    @Override
    public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {
        List<String> roles = new ArrayList<>();

        SecurityProperties.OAuth2Config oauth2 = securityProperties.getOauth2();
        SecurityProperties.RoleMappingConfig roleMapping = securityProperties.getRoleMapping();

        // Try primary roles claim
        roles.addAll(extractRoles(jwt, oauth2.getRolesClaim()));

        // Try alternative role claims (for different IDPs)
        for (String claimPath : oauth2.getAlternativeRolesClaims()) {
            // Replace ${client_id} placeholder
            String resolvedPath = claimPath.replace("${client_id}",
                    oauth2.getClientId() != null ? oauth2.getClientId() : "");
            roles.addAll(extractRoles(jwt, resolvedPath));
        }

        // Add default roles if no roles found
        if (roles.isEmpty()) {
            roles.addAll(roleMapping.getDefaultRoles());
        }

        // Apply role mappings and prefix
        return roles.stream()
                .map(role -> mapRole(role, roleMapping))
                .map(role -> new SimpleGrantedAuthority(roleMapping.getRolePrefix() + role))
                .collect(Collectors.toList());
    }

    /**
     * Extract roles from a claim path (supports nested paths like
     * "realm_access.roles")
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt, String claimPath) {
        if (claimPath == null || claimPath.isBlank()) {
            return List.of();
        }

        String[] parts = claimPath.split("\\.");
        Object current = jwt.getClaims();

        for (String part : parts) {
            if (current == null) {
                return List.of();
            }

            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return List.of();
            }
        }

        if (current instanceof List) {
            return ((List<?>) current).stream()
                    .filter(item -> item instanceof String)
                    .map(item -> (String) item)
                    .toList();
        } else if (current instanceof String) {
            // Some IDPs return roles as comma-separated string
            return List.of(((String) current).split(","));
        }

        return List.of();
    }

    /**
     * Map external role to internal role
     */
    private String mapRole(String externalRole, SecurityProperties.RoleMappingConfig roleMapping) {
        String mapped = roleMapping.getMappings().get(externalRole);
        return mapped != null ? mapped : externalRole.toUpperCase();
    }

    /**
     * Extract username from JWT
     */
    public String extractUsername(Jwt jwt) {
        SecurityProperties.OAuth2Config oauth2 = securityProperties.getOauth2();
        String usernameClaim = oauth2.getUsernameClaim();

        // Try configured claim
        String username = jwt.getClaimAsString(usernameClaim);
        if (username != null) {
            return username;
        }

        // Fallback to common claims
        username = jwt.getClaimAsString("preferred_username");
        if (username != null)
            return username;

        username = jwt.getClaimAsString("email");
        if (username != null)
            return username;

        username = jwt.getClaimAsString("sub");
        if (username != null)
            return username;

        return "unknown";
    }
}
