package com.pesitwizard.server.security;

import com.pesitwizard.server.entity.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Filter for API key authentication. Checks for API key in header or query parameter. */
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final SecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Skip if already authenticated
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip if API key auth is disabled
        if (!securityProperties.getApiKey().isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Try to extract API key
        String apiKey = extractApiKey(request);
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get client IP
        String clientIp = getClientIp(request);

        // Validate the key
        Optional<ApiKey> validKey = apiKeyService.validateKey(apiKey, clientIp);
        if (validKey.isPresent()) {
            ApiKey key = validKey.get();

            // Create authentication token
            List<SimpleGrantedAuthority> authorities =
                    key.getRoles().stream()
                            .map(
                                    role ->
                                            new SimpleGrantedAuthority(
                                                    securityProperties
                                                                    .getRoleMapping()
                                                                    .getRolePrefix()
                                                            + role))
                            .toList();

            ApiKeyAuthenticationToken authentication =
                    new ApiKeyAuthenticationToken(key.getName(), key, authorities);
            authentication.setDetails(new ApiKeyAuthenticationDetails(request, key));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated with API key: {}", key.getName());
        } else {
            log.debug("Invalid API key provided");
        }

        filterChain.doFilter(request, response);
    }

    /** Extract API key from request (header or query param) */
    private String extractApiKey(HttpServletRequest request) {
        // Try header first
        String headerName = securityProperties.getApiKey().getHeaderName();
        String apiKey = request.getHeader(headerName);
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }

        // Try query parameter (deprecated: API keys in URLs are logged by proxies/servers)
        String queryParam = securityProperties.getApiKey().getQueryParam();
        apiKey = request.getParameter(queryParam);
        if (apiKey != null && !apiKey.isBlank()) {
            log.warn(
                    "API key passed via query parameter '{}' for {} — this is insecure. "
                            + "Use the {} header instead.",
                    queryParam,
                    sanitizeForLog(request.getRequestURI()),
                    headerName);
            return apiKey;
        }

        // Try Authorization header with ApiKey scheme
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("ApiKey ")) {
            return authHeader.substring(7);
        }

        return null;
    }

    /** Sanitize a value for safe logging by removing CRLF characters that could forge log entries. */
    private static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n]", "_");
    }

    /**
     * Get client IP address. Uses the direct remote address for security. X-Forwarded-For is only
     * used for logging context, not for authentication or rate limiting decisions, because it can
     * be spoofed by untrusted clients.
     */
    private String getClientIp(HttpServletRequest request) {
        // Always use the direct connection address for security-sensitive decisions
        return request.getRemoteAddr();
    }

    /** Custom authentication token for API keys */
    public static class ApiKeyAuthenticationToken extends UsernamePasswordAuthenticationToken {
        private static final long serialVersionUID = 1L;
        private final transient ApiKey apiKey;

        public ApiKeyAuthenticationToken(
                String principal, ApiKey apiKey, List<SimpleGrantedAuthority> authorities) {
            super(principal, null, authorities);
            this.apiKey = apiKey;
        }

        public ApiKey getApiKey() {
            return apiKey;
        }
    }

    /** Authentication details for API key */
    @lombok.Data
    public static class ApiKeyAuthenticationDetails {
        private final String remoteAddress;
        private final String sessionId;
        private final String apiKeyName;
        private final String partnerId;

        public ApiKeyAuthenticationDetails(HttpServletRequest request, ApiKey apiKey) {
            this.remoteAddress = request.getRemoteAddr();
            this.sessionId =
                    request.getSession(false) != null ? request.getSession().getId() : null;
            this.apiKeyName = apiKey.getName();
            this.partnerId = apiKey.getPartnerId();
        }
    }
}
