package com.pesitwizard.server.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration properties for rate limiting.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "pesit.security.rate-limiting")
public class RateLimitingProperties {

    /**
     * Enable rate limiting (default: false).
     */
    private boolean enabled = false;

    /**
     * Default requests per minute for IP-based limiting.
     */
    private int requestsPerMinute = 100;

    /**
     * Burst multiplier - allows temporary spikes above sustained rate.
     * E.g., 2x means 200 requests can be made in a burst before limiting.
     */
    private int burstMultiplier = 2;

    /**
     * Header name for API key authentication.
     */
    private String apiKeyHeader = "X-API-Key";

    /**
     * Query parameter name for API key authentication.
     */
    private String apiKeyQueryParam = "api_key";

    /**
     * Paths excluded from rate limiting.
     */
    private List<String> whitelistedPaths = List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    );

    /**
     * Enable Redis-based distributed rate limiting.
     * When false, uses in-memory buckets (not cluster-aware).
     */
    private boolean redisEnabled = false;

    /**
     * Redis key prefix for rate limit buckets.
     */
    private String redisKeyPrefix = "pesitwizard:ratelimit:";

    /**
     * TTL for Redis rate limit entries (seconds).
     */
    private int redisTtlSeconds = 300;
}
