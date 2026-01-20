package com.pesitwizard.server.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import com.pesitwizard.server.entity.TransferRecord;
import com.pesitwizard.server.entity.TransferRecord.TransferDirection;
import com.pesitwizard.server.entity.TransferRecord.TransferStatus;
import com.pesitwizard.server.service.TransferService;
import com.pesitwizard.server.service.TransferService.PartnerTransferStatistics;
import com.pesitwizard.server.service.TransferService.TransferStatistics;

@WebMvcTest(TransferController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TransferController Tests")
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    private TransferRecord testTransfer;

    @BeforeEach
    void setUp() {
        testTransfer = new TransferRecord();
        testTransfer.setTransferId("transfer-123");
        testTransfer.setPartnerId("partner-1");
        testTransfer.setFilename("test.dat");
        testTransfer.setStatus(TransferStatus.IN_PROGRESS);
        testTransfer.setDirection(TransferDirection.RECEIVE);
        testTransfer.setStartedAt(Instant.now());
    }

    @Nested
    @DisplayName("List & Search")
    class ListAndSearchTests {

        @Test
        @DisplayName("should list transfers with pagination")
        void shouldListTransfers() throws Exception {
            Page<TransferRecord> page = new PageImpl<>(List.of(testTransfer));
            when(transferService.getAllTransfers(any(PageRequest.class))).thenReturn(page);

            mockMvc.perform(get("/api/v1/transfers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].transferId").value("transfer-123"));
        }

        @Test
        @DisplayName("should search transfers with filters")
        void shouldSearchTransfers() throws Exception {
            Page<TransferRecord> page = new PageImpl<>(List.of(testTransfer));
            when(transferService.searchTransfers(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/transfers/search")
                    .param("partnerId", "partner-1")
                    .param("status", "IN_PROGRESS"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should get active transfers")
        void shouldGetActiveTransfers() throws Exception {
            when(transferService.getActiveTransfers()).thenReturn(List.of(testTransfer));

            mockMvc.perform(get("/api/v1/transfers/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));
        }

        @Test
        @DisplayName("should get active transfers by server")
        void shouldGetActiveTransfersByServer() throws Exception {
            when(transferService.getActiveTransfersByServer("server-1")).thenReturn(List.of(testTransfer));

            mockMvc.perform(get("/api/v1/transfers/active/server/server-1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should get transfers by status")
        void shouldGetTransfersByStatus() throws Exception {
            Page<TransferRecord> page = new PageImpl<>(List.of(testTransfer));
            when(transferService.getTransfersByStatus(eq(TransferStatus.IN_PROGRESS), anyInt(), anyInt()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/transfers/status/IN_PROGRESS"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should get transfers by partner")
        void shouldGetTransfersByPartner() throws Exception {
            Page<TransferRecord> page = new PageImpl<>(List.of(testTransfer));
            when(transferService.getTransfersByPartner(eq("partner-1"), anyInt(), anyInt())).thenReturn(page);

            mockMvc.perform(get("/api/v1/transfers/partner/partner-1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should get retryable transfers")
        void shouldGetRetryableTransfers() throws Exception {
            testTransfer.setStatus(TransferStatus.FAILED);
            when(transferService.getRetryableTransfers()).thenReturn(List.of(testTransfer));

            mockMvc.perform(get("/api/v1/transfers/retryable"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Single Transfer")
    class SingleTransferTests {

        @Test
        @DisplayName("should get transfer by ID")
        void shouldGetTransferById() throws Exception {
            when(transferService.getTransfer("transfer-123")).thenReturn(Optional.of(testTransfer));

            mockMvc.perform(get("/api/v1/transfers/transfer-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transferId").value("transfer-123"));
        }

        @Test
        @DisplayName("should return 404 for non-existent transfer")
        void shouldReturn404ForNonExistent() throws Exception {
            when(transferService.getTransfer("non-existent")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/transfers/non-existent"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should get transfers by session")
        void shouldGetTransfersBySession() throws Exception {
            when(transferService.getTransfersBySession("session-1")).thenReturn(List.of(testTransfer));

            mockMvc.perform(get("/api/v1/transfers/session/session-1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should get retry history")
        void shouldGetRetryHistory() throws Exception {
            when(transferService.getRetryHistory("transfer-123")).thenReturn(List.of(testTransfer));

            mockMvc.perform(get("/api/v1/transfers/transfer-123/retries"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Transfer Actions")
    class TransferActionsTests {

        @Test
        @DisplayName("should cancel transfer")
        void shouldCancelTransfer() throws Exception {
            testTransfer.setStatus(TransferStatus.CANCELLED);
            when(transferService.cancelTransfer(eq("transfer-123"), anyString())).thenReturn(testTransfer);

            mockMvc.perform(post("/api/v1/transfers/transfer-123/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("should return 404 when cancelling non-existent transfer")
        void shouldReturn404WhenCancellingNonExistent() throws Exception {
            when(transferService.cancelTransfer(eq("non-existent"), anyString()))
                    .thenThrow(new IllegalArgumentException("Not found"));

            mockMvc.perform(post("/api/v1/transfers/non-existent/cancel"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should pause transfer")
        void shouldPauseTransfer() throws Exception {
            testTransfer.setStatus(TransferStatus.PAUSED);
            when(transferService.pauseTransfer("transfer-123")).thenReturn(testTransfer);

            mockMvc.perform(post("/api/v1/transfers/transfer-123/pause"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAUSED"));
        }

        @Test
        @DisplayName("should resume transfer")
        void shouldResumeTransfer() throws Exception {
            testTransfer.setStatus(TransferStatus.IN_PROGRESS);
            when(transferService.resumeTransfer("transfer-123")).thenReturn(testTransfer);

            mockMvc.perform(post("/api/v1/transfers/transfer-123/resume"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should retry failed transfer")
        void shouldRetryFailedTransfer() throws Exception {
            TransferRecord newTransfer = new TransferRecord();
            newTransfer.setTransferId("transfer-456");
            when(transferService.retryTransfer("transfer-123")).thenReturn(newTransfer);

            mockMvc.perform(post("/api/v1/transfers/transfer-123/retry"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transferId").value("transfer-456"));
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("should get transfer statistics")
        void shouldGetStatistics() throws Exception {
            TransferStatistics stats = new TransferStatistics();
            stats.setTotalTransfers(100);
            stats.setCompletedTransfers(95);
            stats.setFailedTransfers(3);
            stats.setActiveTransfers(2);
            when(transferService.getStatistics()).thenReturn(stats);

            mockMvc.perform(get("/api/v1/transfers/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTransfers").value(100));
        }

        @Test
        @DisplayName("should get partner statistics")
        void shouldGetPartnerStatistics() throws Exception {
            PartnerTransferStatistics stats = new PartnerTransferStatistics();
            stats.setPartnerId("partner-1");
            stats.setTotalTransfers(50);
            when(transferService.getPartnerStatistics("partner-1")).thenReturn(stats);

            mockMvc.perform(get("/api/v1/transfers/stats/partner/partner-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.partnerId").value("partner-1"));
        }

        @Test
        @DisplayName("should get daily statistics")
        void shouldGetDailyStatistics() throws Exception {
            when(transferService.getDailyStatistics(30)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/transfers/stats/daily"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Admin")
    class AdminTests {

        @Test
        @DisplayName("should trigger cleanup")
        void shouldTriggerCleanup() throws Exception {
            doNothing().when(transferService).cleanupOldTransfers();

            mockMvc.perform(delete("/api/v1/transfers/cleanup"))
                    .andExpect(status().isOk());

            verify(transferService).cleanupOldTransfers();
        }
    }
}
