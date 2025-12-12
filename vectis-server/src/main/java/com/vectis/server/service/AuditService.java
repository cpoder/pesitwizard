package com.vectis.server.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectis.server.entity.AuditEvent;
import com.vectis.server.entity.AuditEvent.AuditCategory;
import com.vectis.server.entity.AuditEvent.AuditEventType;
import com.vectis.server.entity.AuditEvent.AuditOutcome;
import com.vectis.server.repository.AuditEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for audit logging.
 * Provides structured audit trail for security and compliance.
 */
@SuppressWarnings("null") // Spring Data JPA methods never return null for save/findById
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditRepository;
    private final ObjectMapper objectMapper;

    @Value("${pesit.audit.retention-days:365}")
    private int retentionDays;

    @Value("${pesit.audit.log-to-console:true}")
    private boolean logToConsole;

    // ========== Audit Event Creation ==========

    /**
     * Log an audit event asynchronously
     */
    @Async
    @Transactional
    public void logAsync(AuditEvent.AuditEventBuilder builder) {
        log(builder);
    }

    /**
     * Log an audit event synchronously
     */
    @Transactional
    public AuditEvent log(AuditEvent.AuditEventBuilder builder) {
        AuditEvent event = builder
                .timestamp(Instant.now())
                .build();

        // Save to database
        event = auditRepository.save(event);

        // Log to console in structured format
        if (logToConsole) {
            logToConsole(event);
        }

        return event;
    }

    /**
     * Log to console in JSON format for SIEM integration
     */
    private void logToConsole(AuditEvent event) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("@timestamp", event.getTimestamp().toString());
            logEntry.put("audit.category", event.getCategory().name());
            logEntry.put("audit.type", event.getEventType().name());
            logEntry.put("audit.outcome", event.getOutcome().name());

            if (event.getUsername() != null) {
                logEntry.put("user.name", event.getUsername());
            }
            if (event.getClientIp() != null) {
                logEntry.put("client.ip", event.getClientIp());
            }
            if (event.getSessionId() != null) {
                logEntry.put("session.id", event.getSessionId());
            }
            if (event.getResourceType() != null) {
                logEntry.put("resource.type", event.getResourceType());
            }
            if (event.getResourceId() != null) {
                logEntry.put("resource.id", event.getResourceId());
            }
            if (event.getAction() != null) {
                logEntry.put("event.action", event.getAction());
            }
            if (event.getPartnerId() != null) {
                logEntry.put("partner.id", event.getPartnerId());
            }
            if (event.getTransferId() != null) {
                logEntry.put("transfer.id", event.getTransferId());
            }
            if (event.getFilename() != null) {
                logEntry.put("file.name", event.getFilename());
            }
            if (event.getBytesTransferred() != null) {
                logEntry.put("bytes.transferred", event.getBytesTransferred());
            }
            if (event.getDurationMs() != null) {
                logEntry.put("event.duration", event.getDurationMs());
            }
            if (event.getErrorCode() != null) {
                logEntry.put("error.code", event.getErrorCode());
            }
            if (event.getErrorMessage() != null) {
                logEntry.put("error.message", event.getErrorMessage());
            }
            if (event.getHttpMethod() != null) {
                logEntry.put("http.method", event.getHttpMethod());
            }
            if (event.getRequestUri() != null) {
                logEntry.put("http.uri", event.getRequestUri());
            }
            if (event.getHttpStatus() != null) {
                logEntry.put("http.status", event.getHttpStatus());
            }

            String json = objectMapper.writeValueAsString(logEntry);
            log.info("AUDIT: {}", json);
        } catch (Exception e) {
            log.warn("Failed to log audit event to console: {}", e.getMessage());
        }
    }

    // ========== Convenience Methods ==========

    /**
     * Log authentication success
     */
    public void logAuthSuccess(String username, String authMethod, String clientIp, String sessionId) {
        log(AuditEvent.builder()
                .category(AuditCategory.AUTHENTICATION)
                .eventType(AuditEventType.LOGIN_SUCCESS)
                .outcome(AuditOutcome.SUCCESS)
                .username(username)
                .authMethod(authMethod)
                .clientIp(clientIp)
                .sessionId(sessionId));
    }

    /**
     * Log authentication failure
     */
    public void logAuthFailure(String username, String authMethod, String clientIp, String reason) {
        log(AuditEvent.builder()
                .category(AuditCategory.AUTHENTICATION)
                .eventType(AuditEventType.LOGIN_FAILURE)
                .outcome(AuditOutcome.FAILURE)
                .username(username)
                .authMethod(authMethod)
                .clientIp(clientIp)
                .errorMessage(reason));
    }

    /**
     * Log access denied
     */
    public void logAccessDenied(String username, String resource, String action, String clientIp) {
        log(AuditEvent.builder()
                .category(AuditCategory.AUTHORIZATION)
                .eventType(AuditEventType.ACCESS_DENIED)
                .outcome(AuditOutcome.DENIED)
                .username(username)
                .resourceType(resource)
                .action(action)
                .clientIp(clientIp));
    }

    /**
     * Log transfer started
     */
    public void logTransferStarted(String transferId, String partnerId, String filename,
            String direction, String username, String clientIp) {
        log(AuditEvent.builder()
                .category(AuditCategory.TRANSFER)
                .eventType(AuditEventType.TRANSFER_STARTED)
                .outcome(AuditOutcome.SUCCESS)
                .transferId(transferId)
                .partnerId(partnerId)
                .filename(filename)
                .action(direction)
                .username(username)
                .clientIp(clientIp));
    }

    /**
     * Log transfer completed
     */
    public void logTransferCompleted(String transferId, String partnerId, String filename,
            long bytesTransferred, long durationMs) {
        log(AuditEvent.builder()
                .category(AuditCategory.TRANSFER)
                .eventType(AuditEventType.TRANSFER_COMPLETED)
                .outcome(AuditOutcome.SUCCESS)
                .transferId(transferId)
                .partnerId(partnerId)
                .filename(filename)
                .bytesTransferred(bytesTransferred)
                .durationMs(durationMs));
    }

    /**
     * Log transfer failed
     */
    public void logTransferFailed(String transferId, String partnerId, String filename,
            String errorCode, String errorMessage) {
        log(AuditEvent.builder()
                .category(AuditCategory.TRANSFER)
                .eventType(AuditEventType.TRANSFER_FAILED)
                .outcome(AuditOutcome.FAILURE)
                .transferId(transferId)
                .partnerId(partnerId)
                .filename(filename)
                .errorCode(errorCode)
                .errorMessage(errorMessage));
    }

    /**
     * Log configuration change
     */
    public void logConfigChange(AuditEventType eventType, String resourceType, String resourceId,
            String username, String details) {
        log(AuditEvent.builder()
                .category(AuditCategory.CONFIGURATION)
                .eventType(eventType)
                .outcome(AuditOutcome.SUCCESS)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .username(username)
                .details(details));
    }

    /**
     * Log security event
     */
    public void logSecurityEvent(AuditEventType eventType, AuditOutcome outcome,
            String clientIp, String errorMessage) {
        log(AuditEvent.builder()
                .category(AuditCategory.SECURITY)
                .eventType(eventType)
                .outcome(outcome)
                .clientIp(clientIp)
                .errorMessage(errorMessage));
    }

    /**
     * Log API request
     */
    public void logApiRequest(String username, String httpMethod, String requestUri,
            int httpStatus, String clientIp, long durationMs) {
        AuditOutcome outcome = httpStatus < 400 ? AuditOutcome.SUCCESS
                : (httpStatus == 401 || httpStatus == 403) ? AuditOutcome.DENIED : AuditOutcome.FAILURE;

        log(AuditEvent.builder()
                .category(AuditCategory.ADMIN)
                .eventType(AuditEventType.ACCESS_GRANTED)
                .outcome(outcome)
                .username(username)
                .httpMethod(httpMethod)
                .requestUri(requestUri)
                .httpStatus(httpStatus)
                .clientIp(clientIp)
                .durationMs(durationMs));
    }

    // ========== Query Methods ==========

    /**
     * Search audit events
     */
    public Page<AuditEvent> search(AuditCategory category, AuditEventType eventType,
            AuditOutcome outcome, String username, String partnerId, String clientIp,
            Instant startTime, Instant endTime, int page, int size) {
        return auditRepository.search(category, eventType, outcome, username, partnerId,
                clientIp, startTime, endTime, PageRequest.of(page, size));
    }

    /**
     * Get recent events
     */
    public Page<AuditEvent> getRecentEvents(int page, int size) {
        return auditRepository.findAll(PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("timestamp").descending()));
    }

    /**
     * Get events by category
     */
    public Page<AuditEvent> getEventsByCategory(AuditCategory category, int page, int size) {
        return auditRepository.findByCategoryOrderByTimestampDesc(category, PageRequest.of(page, size));
    }

    /**
     * Get failures
     */
    public Page<AuditEvent> getFailures(int page, int size) {
        return auditRepository.findFailures(PageRequest.of(page, size));
    }

    /**
     * Get security events
     */
    public Page<AuditEvent> getSecurityEvents(int page, int size) {
        return auditRepository.findSecurityEvents(PageRequest.of(page, size));
    }

    /**
     * Get transfer events
     */
    public Page<AuditEvent> getTransferEvents(int page, int size) {
        return auditRepository.findTransferEvents(PageRequest.of(page, size));
    }

    /**
     * Get events for a user
     */
    public Page<AuditEvent> getEventsForUser(String username, int page, int size) {
        return auditRepository.findByUsernameOrderByTimestampDesc(username, PageRequest.of(page, size));
    }

    // ========== Statistics ==========

    /**
     * Get audit statistics
     */
    public AuditStatistics getStatistics(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);

        AuditStatistics stats = new AuditStatistics();
        stats.setPeriodHours(hours);
        stats.setTotalEvents(auditRepository.count());
        stats.setFailureCount(auditRepository.countFailuresSince(since));

        // Count by category
        Map<String, Long> categoryCount = new HashMap<>();
        for (Object[] row : auditRepository.countByCategories(since)) {
            categoryCount.put(row[0].toString(), (Long) row[1]);
        }
        stats.setEventsByCategory(categoryCount);

        // Count by outcome
        Map<String, Long> outcomeCount = new HashMap<>();
        for (Object[] row : auditRepository.countByOutcomes(since)) {
            outcomeCount.put(row[0].toString(), (Long) row[1]);
        }
        stats.setEventsByOutcome(outcomeCount);

        return stats;
    }

    // ========== Cleanup ==========

    /**
     * Clean up old audit events
     */
    @Transactional
    @Scheduled(cron = "0 0 3 * * ?") // Run at 3 AM daily
    public void cleanupOldEvents() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = auditRepository.deleteOldEvents(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} old audit events (older than {} days)", deleted, retentionDays);
        }
    }

    // ========== Statistics DTO ==========

    @lombok.Data
    public static class AuditStatistics {
        private int periodHours;
        private long totalEvents;
        private long failureCount;
        private Map<String, Long> eventsByCategory;
        private Map<String, Long> eventsByOutcome;
    }
}
