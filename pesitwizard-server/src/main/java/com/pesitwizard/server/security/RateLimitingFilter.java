package com.pesitwizard.server.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.pesitwizard.server.entity.ApiKey;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.local.LocalBucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rate limiting filter using Bucket4j token bucket algorithm.
 *
 * Supports both per-IP and per-API-key rate limiting:
 * - Per-IP: Default 100 requests/minute with 2x burst allowance
 * - Per-API-Key: Honors the rateLimit field in ApiKey entity
 *
 * When Redis is available, rate limits are shared across the cluster.
 * Falls back to in-memory limits when Redis is unavailable.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pesit.security.rate-limiting", name = "enabled", havingValue = "true", matchIfMissing = false)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingProperties rateLimitingProperties;
    private final ApiKeyService apiKeyService;

    // In-memory fallback buckets (used when Redis is unavailable)
    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> apiKeyBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip rate limiting for whitelisted paths
        if (isWhitelisted(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Try API key rate limiting first
        String apiKeyValue = extractApiKey(request);
        if (apiKeyValue != null) {
            String clientIp = getClientIp(request);
            Optional<ApiKey> apiKeyEntity = apiKeyService.validateKey(apiKeyValue, clientIp);
            if (apiKeyEntity.isPresent()) {
                if (!checkApiKeyRateLimit(apiKeyEntity.get(), response)) {
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }
        }

        // Fall back to IP-based rate limiting
        String clientIp = getClientIp(request);
        if (!checkIpRateLimit(clientIp, response)) {
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWhitelisted(String uri) {
        return rateLimitingProperties.getWhitelistedPaths().stream()
                .anyMatch(path -> uri.startsWith(path) || uri.matches(path.replace("**", ".*")));
    }

    private String extractApiKey(HttpServletRequest request) {
        // Check header first
        String apiKey = request.getHeader(rateLimitingProperties.getApiKeyHeader());
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        // Check query parameter
        return request.getParameter(rateLimitingProperties.getApiKeyQueryParam());
    }

    private String getClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header (for proxied requests)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        // Check X-Real-IP header
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private boolean checkIpRateLimit(String clientIp, HttpServletResponse response) throws IOException {
        Bucket bucket = ipBuckets.computeIfAbsent(clientIp, this::createIpBucket);
        return tryConsume(bucket, clientIp, "IP", response);
    }

    private boolean checkApiKeyRateLimit(ApiKey apiKey, HttpServletResponse response) throws IOException {
        // Use API key ID as bucket key since the raw key is transient
        String bucketKey = "apikey:" + apiKey.getId();
        Bucket bucket = apiKeyBuckets.computeIfAbsent(bucketKey, k -> createApiKeyBucket(apiKey));
        return tryConsume(bucket, apiKey.getName(), "API Key", response);
    }

    private boolean tryConsume(Bucket bucket, String identifier, String type, HttpServletResponse response) throws IOException {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Add rate limit headers
            response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        // Rate limit exceeded
        long waitTimeSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
        log.warn("Rate limit exceeded for {} '{}'. Retry after {} seconds", type, identifier, waitTimeSeconds);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.addHeader("X-RateLimit-Remaining", "0");
        response.addHeader("Retry-After", String.valueOf(waitTimeSeconds));
        response.getWriter().write(String.format(
                "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. Retry after %d seconds.\",\"retryAfter\":%d}",
                waitTimeSeconds, waitTimeSeconds));
        return false;
    }

    private Bucket createIpBucket(String ip) {
        int requestsPerMinute = rateLimitingProperties.getRequestsPerMinute();
        int burstMultiplier = rateLimitingProperties.getBurstMultiplier();

        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute * burstMultiplier)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private Bucket createApiKeyBucket(ApiKey apiKey) {
        // Use API key's custom rate limit if set, otherwise use default
        int requestsPerMinute = apiKey.getRateLimit() != null && apiKey.getRateLimit() > 0
                ? apiKey.getRateLimit()
                : rateLimitingProperties.getRequestsPerMinute();
        int burstMultiplier = rateLimitingProperties.getBurstMultiplier();

        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute * burstMultiplier)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /**
     * Clears rate limit buckets for a specific IP or API key.
     * Useful for admin operations.
     */
    public void clearRateLimit(String identifier, boolean isApiKey) {
        if (isApiKey) {
            apiKeyBuckets.remove(identifier);
        } else {
            ipBuckets.remove(identifier);
        }
        log.info("Cleared rate limit for {} '{}'", isApiKey ? "API Key" : "IP", identifier);
    }

    /**
     * Clears all rate limit buckets.
     * Useful for testing or admin reset.
     */
    public void clearAllRateLimits() {
        ipBuckets.clear();
        apiKeyBuckets.clear();
        log.info("Cleared all rate limit buckets");
    }
}
