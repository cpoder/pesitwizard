package com.vectis.server.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for storing audit events.
 * Provides comprehensive audit trail for security and compliance.
 */
@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_type", columnList = "eventType"),
        @Index(name = "idx_audit_category", columnList = "category"),
        @Index(name = "idx_audit_user", columnList = "username"),
        @Index(name = "idx_audit_resource", columnList = "resourceType, resourceId"),
        @Index(name = "idx_audit_outcome", columnList = "outcome"),
        @Index(name = "idx_audit_session", columnList = "sessionId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Event timestamp
     */
    @Column(nullable = false)
    private Instant timestamp;

    /**
     * Event category
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditCategory category;

    /**
     * Event type
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditEventType eventType;

    /**
     * Event outcome
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuditOutcome outcome = AuditOutcome.SUCCESS;

    /**
     * Username who triggered the event
     */
    @Column(length = 100)
    private String username;

    /**
     * Authentication method used
     */
    @Column(length = 30)
    private String authMethod;

    /**
     * Client IP address
     */
    @Column(length = 64)
    private String clientIp;

    /**
     * Session ID
     */
    @Column(length = 64)
    private String sessionId;

    /**
     * Resource type being accessed
     */
    @Column(length = 50)
    private String resourceType;

    /**
     * Resource ID being accessed
     */
    @Column(length = 100)
    private String resourceId;

    /**
     * Action performed
     */
    @Column(length = 50)
    private String action;

    /**
     * Server/node ID
     */
    @Column(length = 64)
    private String serverId;

    /**
     * Partner ID (for transfer events)
     */
    @Column(length = 64)
    private String partnerId;

    /**
     * Transfer ID (for transfer events)
     */
    @Column(length = 64)
    private String transferId;

    /**
     * Filename (for transfer events)
     */
    @Column(length = 255)
    private String filename;

    /**
     * Bytes transferred (for transfer events)
     */
    private Long bytesTransferred;

    /**
     * Duration in milliseconds
     */
    private Long durationMs;

    /**
     * Error code if failed
     */
    @Column(length = 20)
    private String errorCode;

    /**
     * Error message if failed
     */
    @Column(length = 1000)
    private String errorMessage;

    /**
     * Additional details as JSON
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * User agent (for API calls)
     */
    @Column(length = 500)
    private String userAgent;

    /**
     * Request URI (for API calls)
     */
    @Column(length = 500)
    private String requestUri;

    /**
     * HTTP method (for API calls)
     */
    @Column(length = 10)
    private String httpMethod;

    /**
     * HTTP status code (for API calls)
     */
    private Integer httpStatus;

    /**
     * Audit event categories
     */
    public enum AuditCategory {
        AUTHENTICATION, // Login, logout, token events
        AUTHORIZATION, // Access control decisions
        TRANSFER, // File transfer events
        ADMIN, // Administrative actions
        CONFIGURATION, // Configuration changes
        SECURITY, // Security-related events
        SYSTEM // System events
    }

    /**
     * Audit event types
     */
    public enum AuditEventType {
        // Authentication events
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        TOKEN_ISSUED,
        TOKEN_REFRESHED,
        TOKEN_REVOKED,
        API_KEY_USED,

        // Authorization events
        ACCESS_GRANTED,
        ACCESS_DENIED,
        PERMISSION_CHECK,

        // Transfer events
        TRANSFER_STARTED,
        TRANSFER_PROGRESS,
        TRANSFER_COMPLETED,
        TRANSFER_FAILED,
        TRANSFER_CANCELLED,
        TRANSFER_RESUMED,
        TRANSFER_RETRIED,

        // Admin events
        USER_CREATED,
        USER_UPDATED,
        USER_DELETED,
        API_KEY_CREATED,
        API_KEY_REVOKED,
        API_KEY_DELETED,

        // Configuration events
        SERVER_CREATED,
        SERVER_UPDATED,
        SERVER_DELETED,
        SERVER_STARTED,
        SERVER_STOPPED,
        PARTNER_CREATED,
        PARTNER_UPDATED,
        PARTNER_DELETED,
        VIRTUAL_FILE_CREATED,
        VIRTUAL_FILE_UPDATED,
        VIRTUAL_FILE_DELETED,
        CERTIFICATE_UPLOADED,
        CERTIFICATE_DELETED,
        CERTIFICATE_EXPIRED,
        SECRET_CREATED,
        SECRET_UPDATED,
        SECRET_DELETED,
        SECRET_ROTATED,

        // Security events
        CERTIFICATE_VALIDATION_FAILED,
        TLS_HANDSHAKE_FAILED,
        INVALID_SIGNATURE,
        RATE_LIMIT_EXCEEDED,
        SUSPICIOUS_ACTIVITY,

        // System events
        APPLICATION_STARTED,
        APPLICATION_STOPPED,
        CLUSTER_JOINED,
        CLUSTER_LEFT,
        LEADER_ELECTED,
        HEALTH_CHECK_FAILED
    }

    /**
     * Audit event outcomes
     */
    public enum AuditOutcome {
        SUCCESS,
        FAILURE,
        DENIED,
        ERROR,
        TIMEOUT,
        CANCELLED
    }
}
