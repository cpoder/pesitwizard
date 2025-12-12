package com.vectis.server.security;

import java.util.List;

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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Security configuration for the PeSIT server.
 * Supports OAuth2/OIDC, API keys, and basic auth.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final ApiKeyService apiKeyService;

    /**
     * Main security filter chain
     */
    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (!securityProperties.isEnabled()) {
            log.warn("Security is DISABLED - all endpoints are open");
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        // Build public endpoints pattern
        String[] publicEndpoints = securityProperties.getPublicEndpoints().toArray(new String[0]);

        http
                // Disable CSRF for REST API
                .csrf(csrf -> csrf.disable())

                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Stateless session
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(publicEndpoints).permitAll()
                        // Admin endpoints require ADMIN role
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/certificates/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/apikeys/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/secrets/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/audit/**").hasRole("ADMIN")
                        // Server management requires OPERATOR or ADMIN
                        .requestMatchers("/api/v1/servers/**").hasAnyRole("OPERATOR", "ADMIN")
                        // Configuration management (partners, files) requires OPERATOR or ADMIN
                        .requestMatchers("/api/v1/config/**").hasAnyRole("OPERATOR", "ADMIN")
                        // Transfer management requires USER or higher
                        .requestMatchers("/api/v1/transfers/**").hasAnyRole("USER", "OPERATOR", "ADMIN")
                        // Cluster status is readable by any authenticated user
                        .requestMatchers("/api/v1/cluster/**").authenticated()
                        // All other requests require authentication
                        .anyRequest().authenticated());

        // Add API key filter before username/password filter
        http.addFilterBefore(
                new ApiKeyAuthenticationFilter(apiKeyService, securityProperties),
                UsernamePasswordAuthenticationFilter.class);

        // Configure OAuth2 if enabled
        if (securityProperties.getOauth2().isEnabled() &&
                securityProperties.getOauth2().getJwkSetUri() != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                            .jwtAuthenticationConverter(jwtAuthenticationConverter())));
            log.info("OAuth2/JWT authentication enabled");
        }

        // Configure basic auth if enabled (for development)
        if (securityProperties.getBasicAuth().isEnabled()) {
            http.httpBasic(Customizer.withDefaults());
            log.info("Basic authentication enabled (development mode)");
        }

        return http.build();
    }

    /**
     * JWT authentication converter with role extraction
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter(securityProperties));
        return converter;
    }

    /**
     * JWT decoder for OAuth2
     */
    @Bean
    @ConditionalOnProperty(prefix = "pesit.security.oauth2", name = "jwk-set-uri")
    public JwtDecoder jwtDecoder() {
        String jwkSetUri = securityProperties.getOauth2().getJwkSetUri();
        log.info("Configuring JWT decoder with JWK Set URI: {}", jwkSetUri);
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    /**
     * CORS configuration
     */
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

    /**
     * Password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * User details service for basic auth (development)
     */
    @Bean
    @ConditionalOnProperty(prefix = "pesit.security.basic-auth", name = "enabled", havingValue = "true")
    public UserDetailsService basicAuthUserDetailsService(PasswordEncoder passwordEncoder) {
        List<UserDetails> users = securityProperties.getBasicAuth().getUsers().entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .map(entry -> {
                    SecurityProperties.UserEntry userEntry = entry.getValue();
                    String[] roles = userEntry.getRoles().toArray(new String[0]);
                    return User.builder()
                            .username(entry.getKey())
                            .password(passwordEncoder.encode(userEntry.getPassword()))
                            .roles(roles)
                            .build();
                })
                .toList();

        if (users.isEmpty()) {
            // Create default admin user if none configured
            log.warn("No basic auth users configured, creating default admin user");
            users = List.of(
                    User.builder()
                            .username("admin")
                            .password(passwordEncoder.encode("admin"))
                            .roles("ADMIN")
                            .build());
        }

        return new InMemoryUserDetailsManager(users);
    }
}
