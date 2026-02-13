package com.pesitwizard.client.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication status and login endpoint for the Client UI. All endpoints are public (permitted
 * in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final Environment environment;

    @Value("${pesitwizard.security.api-key:}")
    private String configuredApiKey;

    /** Returns the current authentication mode so the UI knows whether to show a login page. */
    @GetMapping("/status")
    public Map<String, Object> getAuthStatus() {
        boolean nosecurity = isProfileActive("nosecurity");
        boolean apikey = isProfileActive("apikey");
        String mode = nosecurity ? "none" : (apikey ? "apikey" : "oauth2");
        return Map.of(
                "authRequired", !nosecurity,
                "mode", mode);
    }

    /**
     * Validates an API key (used by the login page in apikey mode). Returns 200 if the key is
     * valid, 401 otherwise.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> body) {
        if (!isProfileActive("apikey")) {
            return ResponseEntity.badRequest().body(Map.of("error", "API key auth not enabled"));
        }

        String providedKey = body.get("apiKey");
        if (providedKey == null || providedKey.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "API key required"));
        }

        if (configuredApiKey != null
                && !configuredApiKey.isBlank()
                && MessageDigest.isEqual(
                        configuredApiKey.getBytes(StandardCharsets.UTF_8),
                        providedKey.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.ok(Map.of("status", "authenticated"));
        }

        return ResponseEntity.status(401).body(Map.of("error", "Invalid API key"));
    }

    private boolean isProfileActive(String profile) {
        for (String active : environment.getActiveProfiles()) {
            if (active.equals(profile)) return true;
        }
        return false;
    }
}
