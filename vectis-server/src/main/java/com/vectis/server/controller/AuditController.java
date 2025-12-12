package com.vectis.server.controller;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.server.entity.AuditEvent;
import com.vectis.server.entity.AuditEvent.AuditCategory;
import com.vectis.server.entity.AuditEvent.AuditEventType;
import com.vectis.server.entity.AuditEvent.AuditOutcome;
import com.vectis.server.service.AuditService;
import com.vectis.server.service.AuditService.AuditStatistics;

import lombok.RequiredArgsConstructor;

/**
 * REST API for audit log queries.
 * Requires ADMIN role for all endpoints.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    /**
     * Search audit events
     */
    @GetMapping
    public ResponseEntity<Page<AuditEvent>> searchEvents(
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String partnerId,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) Instant startTime,
            @RequestParam(required = false) Instant endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(auditService.search(
                category, eventType, outcome, username, partnerId, clientIp,
                startTime, endTime, page, size));
    }

    /**
     * Get recent events
     */
    @GetMapping("/recent")
    public ResponseEntity<Page<AuditEvent>> getRecentEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getRecentEvents(page, size));
    }

    /**
     * Get events by category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<Page<AuditEvent>> getEventsByCategory(
            @RequestParam AuditCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getEventsByCategory(category, page, size));
    }

    /**
     * Get failures
     */
    @GetMapping("/failures")
    public ResponseEntity<Page<AuditEvent>> getFailures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getFailures(page, size));
    }

    /**
     * Get security events
     */
    @GetMapping("/security")
    public ResponseEntity<Page<AuditEvent>> getSecurityEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getSecurityEvents(page, size));
    }

    /**
     * Get transfer events
     */
    @GetMapping("/transfers")
    public ResponseEntity<Page<AuditEvent>> getTransferEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getTransferEvents(page, size));
    }

    /**
     * Get events for a user
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<Page<AuditEvent>> getEventsForUser(
            @RequestParam String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getEventsForUser(username, page, size));
    }

    /**
     * Get audit statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<AuditStatistics> getStatistics(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(auditService.getStatistics(hours));
    }
}
