package com.pesitwizard.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pesitwizard.server.entity.AuditEvent.AuditCategory;
import com.pesitwizard.server.entity.AuditEvent.AuditEventType;
import com.pesitwizard.server.entity.AuditEvent.AuditOutcome;

@DisplayName("AuditEvent Entity Tests")
class AuditEventTest {

    @Test
    @DisplayName("should have default outcome as SUCCESS")
    void shouldHaveDefaultOutcomeAsSuccess() {
        AuditEvent event = AuditEvent.builder().build();
        assertEquals(AuditOutcome.SUCCESS, event.getOutcome());
    }

    @Test
    @DisplayName("should have all audit categories")
    void shouldHaveAllAuditCategories() {
        assertEquals(7, AuditCategory.values().length);
        assertNotNull(AuditCategory.AUTHENTICATION);
        assertNotNull(AuditCategory.AUTHORIZATION);
        assertNotNull(AuditCategory.TRANSFER);
        assertNotNull(AuditCategory.ADMIN);
        assertNotNull(AuditCategory.CONFIGURATION);
        assertNotNull(AuditCategory.SECURITY);
        assertNotNull(AuditCategory.SYSTEM);
    }

    @Test
    @DisplayName("should have all audit outcomes")
    void shouldHaveAllAuditOutcomes() {
        assertEquals(6, AuditOutcome.values().length);
        assertNotNull(AuditOutcome.SUCCESS);
        assertNotNull(AuditOutcome.FAILURE);
        assertNotNull(AuditOutcome.DENIED);
        assertNotNull(AuditOutcome.ERROR);
        assertNotNull(AuditOutcome.TIMEOUT);
        assertNotNull(AuditOutcome.CANCELLED);
    }

    @Test
    @DisplayName("should have authentication event types")
    void shouldHaveAuthenticationEventTypes() {
        assertNotNull(AuditEventType.LOGIN_SUCCESS);
        assertNotNull(AuditEventType.LOGIN_FAILURE);
        assertNotNull(AuditEventType.LOGOUT);
        assertNotNull(AuditEventType.TOKEN_ISSUED);
        assertNotNull(AuditEventType.TOKEN_REFRESHED);
        assertNotNull(AuditEventType.TOKEN_REVOKED);
        assertNotNull(AuditEventType.API_KEY_USED);
    }

    @Test
    @DisplayName("should have transfer event types")
    void shouldHaveTransferEventTypes() {
        assertNotNull(AuditEventType.TRANSFER_STARTED);
        assertNotNull(AuditEventType.TRANSFER_PROGRESS);
        assertNotNull(AuditEventType.TRANSFER_COMPLETED);
        assertNotNull(AuditEventType.TRANSFER_FAILED);
        assertNotNull(AuditEventType.TRANSFER_CANCELLED);
        assertNotNull(AuditEventType.TRANSFER_RESUMED);
        assertNotNull(AuditEventType.TRANSFER_RETRIED);
    }

    @Test
    @DisplayName("should store all attributes")
    void shouldStoreAllAttributes() {
        Instant now = Instant.now();

        AuditEvent event = AuditEvent.builder()
                .id(1L)
                .timestamp(now)
                .category(AuditCategory.TRANSFER)
                .eventType(AuditEventType.TRANSFER_COMPLETED)
                .outcome(AuditOutcome.SUCCESS)
                .username("admin")
                .authMethod("JWT")
                .clientIp("192.168.1.100")
                .sessionId("sess_123")
                .resourceType("TRANSFER")
                .resourceId("TX_001")
                .action("SEND")
                .serverId("SERVER1")
                .partnerId("PARTNER1")
                .transferId("TX_001")
                .filename("data.txt")
                .bytesTransferred(1024L)
                .durationMs(500L)
                .errorCode(null)
                .errorMessage(null)
                .details("{\"key\": \"value\"}")
                .userAgent("PesitClient/1.0")
                .requestUri("/api/v1/transfers")
                .httpMethod("POST")
                .httpStatus(200)
                .build();

        assertEquals(1L, event.getId());
        assertEquals(now, event.getTimestamp());
        assertEquals(AuditCategory.TRANSFER, event.getCategory());
        assertEquals(AuditEventType.TRANSFER_COMPLETED, event.getEventType());
        assertEquals(AuditOutcome.SUCCESS, event.getOutcome());
        assertEquals("admin", event.getUsername());
        assertEquals("JWT", event.getAuthMethod());
        assertEquals("192.168.1.100", event.getClientIp());
        assertEquals("sess_123", event.getSessionId());
        assertEquals("TRANSFER", event.getResourceType());
        assertEquals("TX_001", event.getResourceId());
        assertEquals("SEND", event.getAction());
        assertEquals("SERVER1", event.getServerId());
        assertEquals("PARTNER1", event.getPartnerId());
        assertEquals("TX_001", event.getTransferId());
        assertEquals("data.txt", event.getFilename());
        assertEquals(1024L, event.getBytesTransferred());
        assertEquals(500L, event.getDurationMs());
        assertNull(event.getErrorCode());
        assertNull(event.getErrorMessage());
        assertEquals("{\"key\": \"value\"}", event.getDetails());
        assertEquals("PesitClient/1.0", event.getUserAgent());
        assertEquals("/api/v1/transfers", event.getRequestUri());
        assertEquals("POST", event.getHttpMethod());
        assertEquals(200, event.getHttpStatus());
    }

    @Test
    @DisplayName("should store error information for failed events")
    void shouldStoreErrorInformationForFailedEvents() {
        AuditEvent event = AuditEvent.builder()
                .category(AuditCategory.AUTHENTICATION)
                .eventType(AuditEventType.LOGIN_FAILURE)
                .outcome(AuditOutcome.FAILURE)
                .errorCode("AUTH_001")
                .errorMessage("Invalid credentials")
                .build();

        assertEquals(AuditOutcome.FAILURE, event.getOutcome());
        assertEquals("AUTH_001", event.getErrorCode());
        assertEquals("Invalid credentials", event.getErrorMessage());
    }
}
