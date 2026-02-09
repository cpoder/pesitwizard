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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Security configuration with multiple authentication modes:
 * <ul>
 *   <li>{@code nosecurity} profile: all endpoints open (development only)</li>
 *   <li>{@code apikey} profile: static API key authentication</li>
 *   <li>default (no profile match): OAuth2/JWT authentication</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${pesitwizard.security.api-key:}")
    private String apiKey;

    /**
     * OAuth2/JWT security (production default when not using apikey or nosecurity profiles)
     */
    @Bean
    @Profile("!nosecurity & !apikey")
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
                        .requestMatchers("/api/v1/auth/status").permitAll()
                        // All other API endpoints require authentication
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder())));

        return http.build();
    }

    /**
     * API key security (simple static key for standalone deployments)
     */
    @Bean
    @Profile("apikey")
    public SecurityFilterChain apiKeyFilterChain(HttpSecurity http) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "API key profile is active but pesitwizard.security.api-key is not configured. "
                    + "Set PESITWIZARD_SECURITY_API_KEY environment variable.");
        }

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers("/api/v1/auth/login")
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .requestMatchers("/api/v1/auth/status").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKey),
                        UsernamePasswordAuthenticationFilter.class);

        log.info("API key authentication enabled");
        return http.build();
    }

    /**
     * No security (development only)
     */
    @Bean
    @Profile("nosecurity")
    public SecurityFilterChain noSecurityFilterChain(HttpSecurity http) throws Exception {
        log.warn("*** SECURITY DISABLED *** The 'nosecurity' profile is active. "
                + "All endpoints are unauthenticated. Do NOT use in production. "
                + "Set SPRING_PROFILES_ACTIVE=apikey and configure PESITWIZARD_SECURITY_API_KEY for production.");

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
