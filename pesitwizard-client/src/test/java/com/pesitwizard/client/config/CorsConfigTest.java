package com.pesitwizard.client.config;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

@DisplayName("CorsConfig Tests")
class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    @Nested
    @DisplayName("addCorsMappings")
    class AddCorsMappingsTests {

        @Test
        @DisplayName("should configure CORS for /api/** path")
        void shouldConfigureCorsForApiPath() {
            TestCorsRegistry registry = new TestCorsRegistry();

            corsConfig.addCorsMappings(registry);

            assertThat(registry.getPathPattern()).isEqualTo("/api/**");
        }
    }

    @Nested
    @DisplayName("corsConfigurationSource")
    class CorsConfigurationSourceTests {

        @Test
        @DisplayName("should return UrlBasedCorsConfigurationSource")
        void shouldReturnUrlBasedCorsConfigurationSource() {
            CorsConfigurationSource source = corsConfig.corsConfigurationSource();

            assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);
        }

        @Test
        @DisplayName("should allow all origin patterns")
        void shouldAllowAllOriginPatterns() {
            UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) corsConfig
                    .corsConfigurationSource();

            CorsConfiguration config = source.getCorsConfiguration(
                    new org.springframework.mock.web.MockHttpServletRequest("GET", "/test"));

            assertThat(config).isNotNull();
            assertThat(config.getAllowedOriginPatterns()).contains("*");
        }

        @Test
        @DisplayName("should allow standard HTTP methods")
        void shouldAllowStandardHttpMethods() {
            UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) corsConfig
                    .corsConfigurationSource();

            CorsConfiguration config = source.getCorsConfiguration(
                    new org.springframework.mock.web.MockHttpServletRequest("GET", "/test"));

            assertThat(config).isNotNull();
            assertThat(config.getAllowedMethods())
                    .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
        }

        @Test
        @DisplayName("should allow all headers")
        void shouldAllowAllHeaders() {
            UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) corsConfig
                    .corsConfigurationSource();

            CorsConfiguration config = source.getCorsConfiguration(
                    new org.springframework.mock.web.MockHttpServletRequest("GET", "/test"));

            assertThat(config).isNotNull();
            assertThat(config.getAllowedHeaders()).contains("*");
        }

        @Test
        @DisplayName("should allow credentials")
        void shouldAllowCredentials() {
            UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) corsConfig
                    .corsConfigurationSource();

            CorsConfiguration config = source.getCorsConfiguration(
                    new org.springframework.mock.web.MockHttpServletRequest("GET", "/test"));

            assertThat(config).isNotNull();
            assertThat(config.getAllowCredentials()).isTrue();
        }

        @Test
        @DisplayName("should set max age to 3600 seconds")
        void shouldSetMaxAge() {
            UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) corsConfig
                    .corsConfigurationSource();

            CorsConfiguration config = source.getCorsConfiguration(
                    new org.springframework.mock.web.MockHttpServletRequest("GET", "/test"));

            assertThat(config).isNotNull();
            assertThat(config.getMaxAge()).isEqualTo(3600L);
        }

        @Test
        @DisplayName("should apply configuration to all paths")
        void shouldApplyConfigurationToAllPaths() {
            UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) corsConfig
                    .corsConfigurationSource();

            // Test various paths
            List<String> paths = List.of("/", "/api/test", "/other/path", "/deeply/nested/path");

            for (String path : paths) {
                CorsConfiguration config = source.getCorsConfiguration(
                        new org.springframework.mock.web.MockHttpServletRequest("GET", path));
                assertThat(config).as("Config for path: %s", path).isNotNull();
            }
        }
    }

    /**
     * Test helper to capture CorsRegistry configuration
     */
    private static class TestCorsRegistry extends CorsRegistry {
        private String pathPattern;

        @Override
        public CorsRegistration addMapping(String pathPattern) {
            this.pathPattern = pathPattern;
            return super.addMapping(pathPattern);
        }

        public String getPathPattern() {
            return pathPattern;
        }
    }
}
