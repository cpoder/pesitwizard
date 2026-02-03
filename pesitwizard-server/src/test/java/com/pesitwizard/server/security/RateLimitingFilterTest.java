package com.pesitwizard.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.pesitwizard.server.entity.ApiKey;

/**
 * Unit tests for the RateLimitingFilter.
 * Tests rate limiting behavior for both IP-based and API key-based limits.
 */
class RateLimitingFilterTest {

    private RateLimitingFilter rateLimitingFilter;
    private RateLimitingProperties rateLimitingProperties;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        rateLimitingProperties = new RateLimitingProperties();
        rateLimitingProperties.setEnabled(true);
        rateLimitingProperties.setRequestsPerMinute(10); // Low limit for testing
        rateLimitingProperties.setBurstMultiplier(2);
        rateLimitingProperties.setWhitelistedPaths(List.of("/actuator/health"));
        rateLimitingProperties.setApiKeyHeader("X-API-Key");
        rateLimitingProperties.setApiKeyQueryParam("api_key");

        apiKeyService = mock(ApiKeyService.class);
        rateLimitingFilter = new RateLimitingFilter(rateLimitingProperties, apiKeyService);
    }

    @Test
    @DisplayName("Should allow requests within rate limit")
    void shouldAllowRequestsWithinRateLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        request.setRequestURI("/api/v1/transfers");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        // First request should pass
        rateLimitingFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isNotNull();
    }

    @Test
    @DisplayName("Should block requests exceeding rate limit")
    void shouldBlockRequestsExceedingRateLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.2");
        request.setRequestURI("/api/v1/transfers");

        // Make 20 requests (burst limit is 2x10 = 20)
        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();
            rateLimitingFilter.doFilter(request, response, filterChain);
        }

        // 21st request should be blocked
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        rateLimitingFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(response.getContentAsString()).contains("Rate limit exceeded");
    }

    @Test
    @DisplayName("Should skip rate limiting for whitelisted paths")
    void shouldSkipRateLimitingForWhitelistedPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.3");
        request.setRequestURI("/actuator/health");

        // Make many requests to whitelisted path
        for (int i = 0; i < 100; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();
            rateLimitingFilter.doFilter(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Should use API key rate limit when API key is provided")
    void shouldUseApiKeyRateLimitWhenProvided() throws Exception {
        ApiKey apiKey = ApiKey.builder()
                .id(1L)
                .name("test-key")
                .keyHash("hash")
                .keyPrefix("test1234")
                .key("test1234-abcd-efgh-ijkl")
                .rateLimit(5) // Custom rate limit
                .active(true)
                .build();

        when(apiKeyService.validateKey("test1234-abcd-efgh-ijkl", "192.168.1.4")).thenReturn(Optional.of(apiKey));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.4");
        request.setRequestURI("/api/v1/transfers");
        request.addHeader("X-API-Key", "test1234-abcd-efgh-ijkl");

        // Make 10 requests (burst limit is 2x5 = 10)
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();
            rateLimitingFilter.doFilter(request, response, filterChain);
        }

        // 11th request should be blocked
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        rateLimitingFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For header")
    void shouldExtractClientIpFromXForwardedFor() throws Exception {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRemoteAddr("10.0.0.1"); // Proxy IP
        request1.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1"); // Real IP first
        request1.setRequestURI("/api/v1/transfers");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRemoteAddr("10.0.0.1"); // Same proxy
        request2.addHeader("X-Forwarded-For", "203.0.113.2, 10.0.0.1"); // Different real IP
        request2.setRequestURI("/api/v1/transfers");

        // Exhaust rate limit for first IP
        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();
            rateLimitingFilter.doFilter(request1, response, filterChain);
        }

        // Second IP should still have quota
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        rateLimitingFilter.doFilter(request2, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should extract API key from query parameter")
    void shouldExtractApiKeyFromQueryParam() throws Exception {
        ApiKey apiKey = ApiKey.builder()
                .id(2L)
                .name("query-key")
                .keyHash("hash2")
                .keyPrefix("query123")
                .key("query123-abcd-efgh-ijkl")
                .rateLimit(5)
                .active(true)
                .build();

        when(apiKeyService.validateKey("query123-abcd-efgh-ijkl", "192.168.1.5")).thenReturn(Optional.of(apiKey));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.5");
        request.setRequestURI("/api/v1/transfers");
        request.setParameter("api_key", "query123-abcd-efgh-ijkl");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        rateLimitingFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should clear rate limits for specific identifier")
    void shouldClearRateLimitsForIdentifier() throws Exception {
        String ip = "192.168.1.6";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        request.setRequestURI("/api/v1/transfers");

        // Exhaust rate limit
        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();
            rateLimitingFilter.doFilter(request, response, filterChain);
        }

        // Verify blocked
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedChain = new MockFilterChain();
        rateLimitingFilter.doFilter(request, blockedResponse, blockedChain);
        assertThat(blockedResponse.getStatus()).isEqualTo(429);

        // Clear rate limit
        rateLimitingFilter.clearRateLimit(ip, false);

        // Should be allowed again
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        MockFilterChain allowedChain = new MockFilterChain();
        rateLimitingFilter.doFilter(request, allowedResponse, allowedChain);
        assertThat(allowedResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should include rate limit remaining header in response")
    void shouldIncludeRateLimitRemainingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.7");
        request.setRequestURI("/api/v1/transfers");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        rateLimitingFilter.doFilter(request, response, filterChain);

        String remaining = response.getHeader("X-RateLimit-Remaining");
        assertThat(remaining).isNotNull();
        assertThat(Integer.parseInt(remaining)).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should fall back to IP rate limiting for unknown API key")
    void shouldFallBackToIpRateLimitingForUnknownApiKey() throws Exception {
        when(apiKeyService.validateKey("unknown-key", "192.168.1.8")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.8");
        request.setRequestURI("/api/v1/transfers");
        request.addHeader("X-API-Key", "unknown-key");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        rateLimitingFilter.doFilter(request, response, filterChain);

        // Should use IP-based limiting, not fail
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
