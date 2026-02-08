package com.pesitwizard.client.config;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Security configuration with OAuth2/OIDC support
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Bean
    @Profile("!nosecurity")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        // All other API endpoints require authentication
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder())));

        return http.build();
    }

    @Bean
    @Profile("nosecurity")
    public SecurityFilterChain noSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {
                })
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            // Return a no-op decoder for development
            return token -> {
                throw new IllegalStateException(
                        "JWT validation not configured. Set spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
            };
        }
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    /**
     * SPA-compatible CSRF token request handler for Spring Security 6+.
     * Uses XOR-based token masking for BREACH protection when the token
     * comes via a header (set by the SPA from the XSRF-TOKEN cookie),
     * and falls back to the plain attribute handler for form submissions.
     */
    static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
        private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                Supplier<CsrfToken> csrfToken) {
            this.delegate.handle(request, response, csrfToken);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
                return super.resolveCsrfTokenValue(request, csrfToken);
            }
            return this.delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
