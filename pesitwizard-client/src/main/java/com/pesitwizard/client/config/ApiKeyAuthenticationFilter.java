package com.pesitwizard.client.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Simple API key authentication filter for the client module.
 * Validates a static API key from configuration against the request header.
 */
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-API-Key";
    private final String apiKeyHash;

    public ApiKeyAuthenticationFilter(String apiKey) {
        this.apiKeyHash = hashKey(apiKey);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = extractApiKey(request);
        if (providedKey != null && MessageDigest.isEqual(
                apiKeyHash.getBytes(StandardCharsets.UTF_8),
                hashKey(providedKey).getBytes(StandardCharsets.UTF_8))) {

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "api-key-user", null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("Authenticated via API key");
        }

        filterChain.doFilter(request, response);
    }

    private String extractApiKey(HttpServletRequest request) {
        String key = request.getHeader(HEADER_NAME);
        if (key != null && !key.isBlank()) return key;

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("ApiKey ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private static String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash key", e);
        }
    }
}
