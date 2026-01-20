package com.pesitwizard.server.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import com.pesitwizard.server.entity.AuditEvent;
import com.pesitwizard.server.entity.AuditEvent.AuditCategory;
import com.pesitwizard.server.entity.AuditEvent.AuditEventType;
import com.pesitwizard.server.entity.AuditEvent.AuditOutcome;
import com.pesitwizard.server.service.AuditService;
import com.pesitwizard.server.service.AuditService.AuditStatistics;

@WebMvcTest(AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuditController Tests")
class AuditControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AuditService auditService;

        private AuditEvent testEvent;

        @BeforeEach
        void setUp() {
                testEvent = AuditEvent.builder()
                                .category(AuditCategory.TRANSFER)
                                .eventType(AuditEventType.TRANSFER_STARTED)
                                .outcome(AuditOutcome.SUCCESS)
                                .username("testuser")
                                .timestamp(Instant.now())
                                .build();
        }

        @Test
        @DisplayName("should search audit events")
        void shouldSearchEvents() throws Exception {
                Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
                when(auditService.search(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                                .thenReturn(page);

                mockMvc.perform(get("/api/v1/audit"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("should get recent events")
        void shouldGetRecentEvents() throws Exception {
                Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
                when(auditService.getRecentEvents(anyInt(), anyInt())).thenReturn(page);

                mockMvc.perform(get("/api/v1/audit/recent"))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should get events by category")
        void shouldGetEventsByCategory() throws Exception {
                Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
                when(auditService.getEventsByCategory(any(AuditCategory.class), anyInt(), anyInt())).thenReturn(page);

                mockMvc.perform(get("/api/v1/audit/category/{category}", "TRANSFER")
                                .param("category", "TRANSFER"))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should get failures")
        void shouldGetFailures() throws Exception {
                testEvent = AuditEvent.builder()
                                .category(AuditCategory.TRANSFER)
                                .eventType(AuditEventType.TRANSFER_FAILED)
                                .outcome(AuditOutcome.FAILURE)
                                .build();
                Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
                when(auditService.getFailures(anyInt(), anyInt())).thenReturn(page);

                mockMvc.perform(get("/api/v1/audit/failures"))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should get security events")
        void shouldGetSecurityEvents() throws Exception {
                Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
                when(auditService.getSecurityEvents(anyInt(), anyInt())).thenReturn(page);

                mockMvc.perform(get("/api/v1/audit/security"))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should get transfer events")
        void shouldGetTransferEvents() throws Exception {
                Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
                when(auditService.getTransferEvents(anyInt(), anyInt())).thenReturn(page);

                mockMvc.perform(get("/api/v1/audit/transfers"))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should get events for user")
        void shouldGetEventsForUser() throws Exception {
                Page<AuditEvent> page = new PageImpl<>(List.of(testEvent));
                when(auditService.getEventsForUser(eq("testuser"), anyInt(), anyInt())).thenReturn(page);

                mockMvc.perform(get("/api/v1/audit/user/{username}", "testuser")
                                .param("username", "testuser"))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should get audit statistics")
        void shouldGetStatistics() throws Exception {
                AuditStatistics stats = new AuditStatistics();
                stats.setTotalEvents(100);
                stats.setFailureCount(5);
                when(auditService.getStatistics(24)).thenReturn(stats);

                mockMvc.perform(get("/api/v1/audit/stats"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalEvents").value(100));
        }

}
