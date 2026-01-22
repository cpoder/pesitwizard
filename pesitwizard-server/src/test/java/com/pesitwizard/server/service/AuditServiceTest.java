package com.pesitwizard.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.server.entity.AuditEvent;
import com.pesitwizard.server.entity.AuditEvent.AuditCategory;
import com.pesitwizard.server.entity.AuditEvent.AuditEventType;
import com.pesitwizard.server.entity.AuditEvent.AuditOutcome;
import com.pesitwizard.server.repository.AuditEventRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Tests")
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditService auditService;

    private AuditEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = AuditEvent.builder()
                .id(1L)
                .timestamp(Instant.now())
                .category(AuditCategory.TRANSFER)
                .eventType(AuditEventType.TRANSFER_STARTED)
                .outcome(AuditOutcome.SUCCESS)
                .username("testuser")
                .build();
    }

    @Nested
    @DisplayName("Logging Tests")
    class LoggingTests {

        @Test
        @DisplayName("Should log audit event")
        void shouldLogAuditEvent() {
            when(auditRepository.save(any(AuditEvent.class))).thenReturn(testEvent);

            AuditEvent result = auditService.log(AuditEvent.builder()
                    .category(AuditCategory.TRANSFER)
                    .eventType(AuditEventType.TRANSFER_STARTED)
                    .outcome(AuditOutcome.SUCCESS));

            assertNotNull(result);
            verify(auditRepository).save(any(AuditEvent.class));
        }

        @Test
        @DisplayName("Should log authentication success")
        void shouldLogAuthSuccess() {
            when(auditRepository.save(any(AuditEvent.class))).thenReturn(testEvent);

            auditService.logAuthSuccess("user1", "PASSWORD", "192.168.1.1", "session-123");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditRepository).save(captor.capture());

            AuditEvent saved = captor.getValue();
            assertEquals(AuditCategory.AUTHENTICATION, saved.getCategory());
            assertEquals(AuditEventType.LOGIN_SUCCESS, saved.getEventType());
            assertEquals(AuditOutcome.SUCCESS, saved.getOutcome());
        }

        @Test
        @DisplayName("Should log authentication failure")
        void shouldLogAuthFailure() {
            when(auditRepository.save(any(AuditEvent.class))).thenReturn(testEvent);

            auditService.logAuthFailure("user1", "PASSWORD", "192.168.1.1", "Invalid password");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditRepository).save(captor.capture());

            assertEquals(AuditOutcome.FAILURE, captor.getValue().getOutcome());
        }

        @Test
        @DisplayName("Should log access denied")
        void shouldLogAccessDenied() {
            when(auditRepository.save(any(AuditEvent.class))).thenReturn(testEvent);

            auditService.logAccessDenied("user1", "Partner", "DELETE", "192.168.1.1");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditRepository).save(captor.capture());

            assertEquals(AuditCategory.AUTHORIZATION, captor.getValue().getCategory());
            assertEquals(AuditOutcome.DENIED, captor.getValue().getOutcome());
        }

        @Test
        @DisplayName("Should log transfer started")
        void shouldLogTransferStarted() {
            when(auditRepository.save(any(AuditEvent.class))).thenReturn(testEvent);

            auditService.logTransferStarted("xfer-1", "partner-1", "file.txt", "RECEIVE", "user1", "192.168.1.1");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditRepository).save(captor.capture());

            assertEquals(AuditCategory.TRANSFER, captor.getValue().getCategory());
            assertEquals(AuditEventType.TRANSFER_STARTED, captor.getValue().getEventType());
        }

        @Test
        @DisplayName("Should log transfer completed")
        void shouldLogTransferCompleted() {
            when(auditRepository.save(any(AuditEvent.class))).thenReturn(testEvent);

            auditService.logTransferCompleted("xfer-1", "partner-1", "file.txt", 1024000L, 5000L);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditRepository).save(captor.capture());

            assertEquals(AuditEventType.TRANSFER_COMPLETED, captor.getValue().getEventType());
            assertEquals(1024000L, captor.getValue().getBytesTransferred());
        }

        @Test
        @DisplayName("Should log transfer failed")
        void shouldLogTransferFailed() {
            when(auditRepository.save(any(AuditEvent.class))).thenReturn(testEvent);

            auditService.logTransferFailed("xfer-1", "partner-1", "file.txt", "ERR_01", "Connection refused");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditRepository).save(captor.capture());

            assertEquals(AuditOutcome.FAILURE, captor.getValue().getOutcome());
            assertEquals("ERR_01", captor.getValue().getErrorCode());
        }

        @Test
        @DisplayName("Should log configuration change")
        void shouldLogConfigChange() {
            when(auditRepository.save(any(AuditEvent.class))).thenReturn(testEvent);

            auditService.logConfigChange(AuditEventType.PARTNER_CREATED, "Partner", "partner-1", "admin",
                    "Created partner");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditRepository).save(captor.capture());

            assertEquals(AuditCategory.CONFIGURATION, captor.getValue().getCategory());
        }

        @Test
        @DisplayName("Should log security event")
        void shouldLogSecurityEvent() {
            when(auditRepository.save(any(AuditEvent.class))).thenReturn(testEvent);

            auditService.logSecurityEvent(AuditEventType.CERTIFICATE_VALIDATION_FAILED, AuditOutcome.FAILURE,
                    "192.168.1.1", "Certificate expired");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditRepository).save(captor.capture());

            assertEquals(AuditCategory.SECURITY, captor.getValue().getCategory());
        }

        @Test
        @DisplayName("Should log API request with success outcome for 2xx")
        void shouldLogApiRequestSuccess() {
            when(auditRepository.save(any(AuditEvent.class))).thenReturn(testEvent);

            auditService.logApiRequest("admin", "GET", "/api/partners", 200, "192.168.1.1", 50L);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditRepository).save(captor.capture());

            assertEquals(AuditOutcome.SUCCESS, captor.getValue().getOutcome());
        }

        @Test
        @DisplayName("Should log API request with denied outcome for 401/403")
        void shouldLogApiRequestDenied() {
            when(auditRepository.save(any(AuditEvent.class))).thenReturn(testEvent);

            auditService.logApiRequest("user", "DELETE", "/api/partners/1", 403, "192.168.1.1", 10L);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditRepository).save(captor.capture());

            assertEquals(AuditOutcome.DENIED, captor.getValue().getOutcome());
        }
    }

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Should get recent events")
        void shouldGetRecentEvents() {
            Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
            when(auditRepository.findAll(any(Pageable.class))).thenReturn(page);

            Page<AuditEvent> result = auditService.getRecentEvents(0, 10);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Should get events by category")
        void shouldGetEventsByCategory() {
            Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
            when(auditRepository.findByCategoryOrderByTimestampDesc(any(), any())).thenReturn(page);

            Page<AuditEvent> result = auditService.getEventsByCategory(AuditCategory.TRANSFER, 0, 10);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Should get failures")
        void shouldGetFailures() {
            Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
            when(auditRepository.findFailures(any())).thenReturn(page);

            Page<AuditEvent> result = auditService.getFailures(0, 10);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should get security events")
        void shouldGetSecurityEvents() {
            Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
            when(auditRepository.findSecurityEvents(any())).thenReturn(page);

            Page<AuditEvent> result = auditService.getSecurityEvents(0, 10);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should get transfer events")
        void shouldGetTransferEvents() {
            Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
            when(auditRepository.findTransferEvents(any())).thenReturn(page);

            Page<AuditEvent> result = auditService.getTransferEvents(0, 10);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should get events for user")
        void shouldGetEventsForUser() {
            Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
            when(auditRepository.findByUsernameOrderByTimestampDesc(any(), any())).thenReturn(page);

            Page<AuditEvent> result = auditService.getEventsForUser("testuser", 0, 10);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should search events")
        void shouldSearchEvents() {
            Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
            when(auditRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);

            Page<AuditEvent> result = auditService.search(
                    AuditCategory.TRANSFER, AuditEventType.TRANSFER_STARTED, AuditOutcome.SUCCESS,
                    "testuser", "partner-1", "192.168.1.1",
                    Instant.now().minusSeconds(3600), Instant.now(), 0, 10);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should get audit statistics")
        void shouldGetAuditStatistics() {
            when(auditRepository.count()).thenReturn(100L);
            when(auditRepository.countFailuresSince(any(Instant.class))).thenReturn(5L);
            when(auditRepository.countByCategories(any(Instant.class))).thenReturn(List.of(
                    new Object[]{"TRANSFER", 50L},
                    new Object[]{"AUTHENTICATION", 30L}
            ));
            when(auditRepository.countByOutcomes(any(Instant.class))).thenReturn(List.of(
                    new Object[]{"SUCCESS", 90L},
                    new Object[]{"FAILURE", 10L}
            ));

            AuditService.AuditStatistics stats = auditService.getStatistics(24);

            assertEquals(24, stats.getPeriodHours());
            assertEquals(100L, stats.getTotalEvents());
            assertEquals(5L, stats.getFailureCount());
            assertNotNull(stats.getEventsByCategory());
            assertNotNull(stats.getEventsByOutcome());
        }
    }

    @Nested
    @DisplayName("Cleanup Tests")
    class CleanupTests {

        @Test
        @DisplayName("Should cleanup old events")
        void shouldCleanupOldEvents() {
            when(auditRepository.deleteOldEvents(any(Instant.class))).thenReturn(10);

            auditService.cleanupOldEvents();

            verify(auditRepository).deleteOldEvents(any(Instant.class));
        }

        @Test
        @DisplayName("Should not log when no events deleted")
        void shouldNotLogWhenNoEventsDeleted() {
            when(auditRepository.deleteOldEvents(any(Instant.class))).thenReturn(0);

            auditService.cleanupOldEvents();

            verify(auditRepository).deleteOldEvents(any(Instant.class));
        }
    }

    @Nested
    @DisplayName("AuditStatistics DTO Tests")
    class AuditStatisticsDtoTests {

        @Test
        @DisplayName("Should create and access AuditStatistics")
        void shouldCreateAndAccessAuditStatistics() {
            AuditService.AuditStatistics stats = new AuditService.AuditStatistics();
            stats.setPeriodHours(12);
            stats.setTotalEvents(500L);
            stats.setFailureCount(25L);
            stats.setEventsByCategory(Map.of("TRANSFER", 100L));
            stats.setEventsByOutcome(Map.of("SUCCESS", 475L));

            assertEquals(12, stats.getPeriodHours());
            assertEquals(500L, stats.getTotalEvents());
            assertEquals(25L, stats.getFailureCount());
            assertEquals(100L, stats.getEventsByCategory().get("TRANSFER"));
            assertEquals(475L, stats.getEventsByOutcome().get("SUCCESS"));
        }
    }
}
