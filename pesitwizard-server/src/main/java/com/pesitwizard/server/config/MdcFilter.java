package com.pesitwizard.server.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * S3-09: Servlet filter that populates MDC (Mapped Diagnostic Context) with
 * per-request correlation fields. This enables structured log correlation
 * across all log statements within a single HTTP request lifecycle.
 *
 * MDC keys set:
 * - requestId: unique UUID per request
 * - remoteAddr: client IP address
 * - method: HTTP method (GET, POST, etc.)
 * - uri: request URI path
 */
@Component
@Order(0) // Run before other filters (rate limiting, security)
public class MdcFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_REMOTE_ADDR = "remoteAddr";
    public static final String MDC_METHOD = "method";
    public static final String MDC_URI = "uri";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString().substring(0, 8));
            MDC.put(MDC_REMOTE_ADDR, request.getRemoteAddr());
            MDC.put(MDC_METHOD, request.getMethod());
            MDC.put(MDC_URI, request.getRequestURI());

            // Also set the requestId as a response header for client correlation
            response.setHeader("X-Request-Id", MDC.get(MDC_REQUEST_ID));

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
