package com.pesitwizard.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Security configuration for the PeSIT server. Supports OAuth2/OIDC, API keys, and basic auth. */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final ApiKeyService apiKeyService;

    /** Main security filter chain */
    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (!securityProperties.isEnabled()) {
            log.warn("Security is DISABLED - all endpoints are open");
            // CSRF protection is intentionally disabled: this is a stateless REST API
            // that uses API key / JWT authentication, not browser-based session cookies.
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        // Build public endpoints pattern
        String[] publicEndpoints = securityProperties.getPublicEndpoints().toArray(new String[0]);

        http
                // CSRF protection with cookie-based token for SPA compatibility
                // API endpoints use stateless auth (API keys, JWT) so CSRF is not needed
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(
                                                CookieCsrfTokenRepository.withHttpOnlyFalse())
                                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                                        .ignoringRequestMatchers("/api/**", "/actuator/**"))

                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Security headers (OWASP recommended)
                .headers(
                        headers ->
                                headers.frameOptions(frame -> frame.deny())
                                        .contentTypeOptions(Customizer.withDefaults())
                                        .xssProtection(Customizer.withDefaults())
                                        .httpStrictTransportSecurity(
                                                hsts ->
                                                        hsts.maxAgeInSeconds(31536000)
                                                                .includeSubDomains(true))
                                        .contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'")))

                // Stateless session
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // Public endpoints
                                        .requestMatchers(publicEndpoints)
                                        .permitAll()
                                        // Admin endpoints require ADMIN role
                                        .requestMatchers("/api/v1/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/v1/certificates/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/v1/apikeys/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/v1/secrets/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/v1/audit/**")
                                        .hasRole("ADMIN")
                                        // Server management requires OPERATOR or ADMIN
                                        .requestMatchers("/api/v1/servers/**")
                                        .hasAnyRole("OPERATOR", "ADMIN")
                                        // Configuration management (partners, files) requires
                                        // OPERATOR or ADMIN
                                        .requestMatchers("/api/v1/config/**")
                                        .hasAnyRole("OPERATOR", "ADMIN")
                                        // Transfer management requires USER or higher
                                        .requestMatchers("/api/v1/transfers/**")
                                        .hasAnyRole("USER", "OPERATOR", "ADMIN")
                                        // Cluster status is readable by any authenticated user
                                        .requestMatchers("/api/v1/cluster/**")
                                        .authenticated()
                                        // All other requests require authentication
                                        .anyRequest()
                                        .authenticated());

        // Add API key filter before username/password filter
        http.addFilterBefore(
                new ApiKeyAuthenticationFilter(apiKeyService, securityProperties),
                UsernamePasswordAuthenticationFilter.class);

        // Configure OAuth2 if enabled
        if (securityProperties.getOauth2().isEnabled()
                && securityProperties.getOauth2().getJwkSetUri() != null) {
            http.oauth2ResourceServer(
                    oauth2 ->
                            oauth2.jwt(
                                    jwt ->
                                            jwt.jwtAuthenticationConverter(
                                                    jwtAuthenticationConverter())));
            log.info("OAuth2/JWT authentication enabled");
        }

        // Configure basic auth if enabled (for development)
        if (securityProperties.getBasicAuth().isEnabled()) {
            http.httpBasic(Customizer.withDefaults());
            log.info("Basic authentication enabled (development mode)");
        }

        return http.build();
    }

    /** JWT authentication converter with role extraction */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter(securityProperties));
        return converter;
    }

    /** JWT decoder for OAuth2 with audience and issuer validation */
    @Bean
    @ConditionalOnProperty(prefix = "pesit.security.oauth2", name = "jwk-set-uri")
    public JwtDecoder jwtDecoder() {
        SecurityProperties.OAuth2Config oauth2 = securityProperties.getOauth2();
        String jwkSetUri = oauth2.getJwkSetUri();
        log.info("Configuring JWT decoder with JWK Set URI: {}", jwkSetUri);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        // Add audience and issuer validation if configured
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefault());

        if (oauth2.getAudience() != null && !oauth2.getAudience().isBlank()) {
            validators.add(
                    new JwtClaimValidator<List<String>>(
                            "aud", aud -> aud != null && aud.contains(oauth2.getAudience())));
            log.info("JWT audience validation enabled: {}", oauth2.getAudience());
        }

        if (oauth2.getIssuerUri() != null && !oauth2.getIssuerUri().isBlank()) {
            validators.add(JwtValidators.createDefaultWithIssuer(oauth2.getIssuerUri()));
            log.info("JWT issuer validation enabled: {}", oauth2.getIssuerUri());
        }

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /** CORS configuration */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        SecurityProperties.CorsConfig corsConfig = securityProperties.getCors();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsConfig.getAllowedOrigins());
        configuration.setAllowedMethods(corsConfig.getAllowedMethods());
        configuration.setAllowedHeaders(corsConfig.getAllowedHeaders());
        configuration.setExposedHeaders(corsConfig.getExposedHeaders());
        configuration.setAllowCredentials(corsConfig.isAllowCredentials());
        configuration.setMaxAge(corsConfig.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** Password encoder */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** User details service for basic auth (development) */
    @Bean
    @ConditionalOnProperty(
            prefix = "pesit.security.basic-auth",
            name = "enabled",
            havingValue = "true")
    public UserDetailsService basicAuthUserDetailsService(PasswordEncoder passwordEncoder) {
        List<UserDetails> users =
                securityProperties.getBasicAuth().getUsers().entrySet().stream()
                        .filter(entry -> entry.getValue().isEnabled())
                        .map(
                                entry -> {
                                    SecurityProperties.UserEntry userEntry = entry.getValue();
                                    String[] roles = userEntry.getRoles().toArray(new String[0]);
                                    return User.builder()
                                            .username(entry.getKey())
                                            .password(
                                                    passwordEncoder.encode(userEntry.getPassword()))
                                            .roles(roles)
                                            .build();
                                })
                        .toList();

        if (users.isEmpty()) {
            // Create default admin user if none configured
            log.warn("No basic auth users configured, creating default admin user");
            users =
                    List.of(
                            User.builder()
                                    .username("admin")
                                    .password(passwordEncoder.encode("admin"))
                                    .roles("ADMIN")
                                    .build());
        }

        return new InMemoryUserDetailsManager(users);
    }

    /**
     * SPA-compatible CSRF token request handler for Spring Security 6+. Uses XOR-based token
     * masking for BREACH protection when the token comes via a header (set by the SPA from the
     * XSRF-TOKEN cookie), and falls back to the plain attribute handler for form submissions.
     */
    static final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
        private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(
                HttpServletRequest request,
                HttpServletResponse response,
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
