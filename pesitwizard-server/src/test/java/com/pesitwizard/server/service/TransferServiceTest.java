package com.pesitwizard.server.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.pesitwizard.server.entity.TransferRecord;
import com.pesitwizard.server.entity.TransferRecord.TransferDirection;
import com.pesitwizard.server.entity.TransferRecord.TransferStatus;
import com.pesitwizard.server.repository.TransferRecordRepository;
import com.pesitwizard.server.service.TransferService.TransferStatistics;

/**
 * Tests for TransferService
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TransferServiceTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRecordRepository transferRepository;

    @BeforeEach
    void setUp() {
        transferRepository.deleteAll();
    }

    @Test
    @DisplayName("Create and complete a transfer")
    void testCreateAndCompleteTransfer() {
        // Create transfer
        TransferRecord transfer = transferService.createTransfer(
                "session-1", "server-1", "node-1",
                "PARTNER_A", "TEST_FILE.dat",
                TransferDirection.RECEIVE, "192.168.1.100");

        assertNotNull(transfer.getId());
        assertNotNull(transfer.getTransferId());
        assertEquals(TransferStatus.INITIATED, transfer.getStatus());
        assertEquals("PARTNER_A", transfer.getPartnerId());
        assertEquals("TEST_FILE.dat", transfer.getFilename());
        assertEquals(TransferDirection.RECEIVE, transfer.getDirection());

        // Start transfer
        transfer = transferService.startTransfer(transfer.getTransferId(), 1024L, "/data/received/test.dat");
        assertEquals(TransferStatus.IN_PROGRESS, transfer.getStatus());
        assertEquals(1024L, transfer.getFileSize());

        // Update progress
        transfer = transferService.updateProgress(transfer.getTransferId(), 512L);
        assertEquals(512L, transfer.getBytesTransferred());
        assertEquals(50, transfer.getProgressPercent());

        // Complete transfer
        transfer = transferService.completeTransfer(transfer.getTransferId(), "abc123checksum");
        assertEquals(TransferStatus.COMPLETED, transfer.getStatus());
        assertNotNull(transfer.getCompletedAt());
        assertEquals("abc123checksum", transfer.getChecksum());
    }

    @Test
    @DisplayName("Fail a transfer")
    void testFailTransfer() {
        TransferRecord transfer = transferService.createTransfer(
                "session-2", "server-1", "node-1",
                "PARTNER_B", "FAIL_FILE.dat",
                TransferDirection.SEND, "192.168.1.101");

        transferService.startTransfer(transfer.getTransferId(), 2048L, "/data/send/fail.dat");

        transfer = transferService.failTransfer(transfer.getTransferId(), "E001", "Connection lost");

        assertEquals(TransferStatus.FAILED, transfer.getStatus());
        assertEquals("E001", transfer.getErrorCode());
        assertEquals("Connection lost", transfer.getErrorMessage());
        assertNotNull(transfer.getCompletedAt());
    }

    @Test
    @DisplayName("Pause and resume transfer")
    void testPauseAndResumeTransfer() {
        TransferRecord transfer = transferService.createTransfer(
                "session-3", "server-1", "node-1",
                "PARTNER_C", "PAUSE_FILE.dat",
                TransferDirection.RECEIVE, "192.168.1.102");

        transferService.startTransfer(transfer.getTransferId(), 4096L, "/data/received/pause.dat");
        transferService.updateProgress(transfer.getTransferId(), 1024L);

        // Pause
        transfer = transferService.pauseTransfer(transfer.getTransferId());
        assertEquals(TransferStatus.PAUSED, transfer.getStatus());

        // Resume
        transfer = transferService.resumeTransfer(transfer.getTransferId());
        assertEquals(TransferStatus.IN_PROGRESS, transfer.getStatus());
    }

    @Test
    @DisplayName("Retry a failed transfer")
    void testRetryTransfer() {
        // Create and fail a transfer
        TransferRecord original = transferService.createTransfer(
                "session-4", "server-1", "node-1",
                "PARTNER_D", "RETRY_FILE.dat",
                TransferDirection.RECEIVE, "192.168.1.103");

        transferService.startTransfer(original.getTransferId(), 8192L, "/data/received/retry.dat");
        transferService.updateProgress(original.getTransferId(), 2048L);
        transferService.recordSyncPoint(original.getTransferId(), 2048L);
        transferService.failTransfer(original.getTransferId(), "E002", "Timeout");

        // Retry
        TransferRecord retry = transferService.retryTransfer(original.getTransferId());

        assertNotNull(retry);
        assertNotEquals(original.getTransferId(), retry.getTransferId());
        assertEquals(original.getTransferId(), retry.getParentTransferId());
        assertEquals(TransferStatus.INITIATED, retry.getStatus());
        assertEquals(2048L, retry.getBytesTransferred()); // Resumed from sync point
        assertEquals(1, retry.getRetryCount());

        // Original should be marked as RETRY_PENDING
        original = transferService.getTransferOrThrow(original.getTransferId());
        assertEquals(TransferStatus.RETRY_PENDING, original.getStatus());
    }

    @Test
    @DisplayName("Cannot retry beyond max retries")
    void testMaxRetries() {
        TransferRecord transfer = transferService.createTransfer(
                "session-5", "server-1", "node-1",
                "PARTNER_E", "MAX_RETRY.dat",
                TransferDirection.RECEIVE, "192.168.1.104");

        transferService.startTransfer(transfer.getTransferId(), 1024L, "/data/received/max.dat");
        transferService.failTransfer(transfer.getTransferId(), "E003", "Error 1");

        // Retry 3 times (default max)
        String currentId = transfer.getTransferId();
        for (int i = 0; i < 3; i++) {
            TransferRecord retry = transferService.retryTransfer(currentId);
            transferService.startTransfer(retry.getTransferId(), 1024L, "/data/received/max.dat");
            transferService.failTransfer(retry.getTransferId(), "E003", "Error " + (i + 2));
            currentId = retry.getTransferId();
        }

        // 4th retry should fail
        final String finalId = currentId;
        assertThrows(IllegalStateException.class, () -> transferService.retryTransfer(finalId));
    }

    @Test
    @DisplayName("Query active transfers")
    void testQueryActiveTransfers() {
        // Create multiple transfers
        TransferRecord t1 = transferService.createTransfer(
                "session-6", "server-1", "node-1",
                "PARTNER_F", "ACTIVE_1.dat",
                TransferDirection.RECEIVE, "192.168.1.105");
        transferService.startTransfer(t1.getTransferId(), 1024L, "/data/1.dat");

        TransferRecord t2 = transferService.createTransfer(
                "session-7", "server-1", "node-1",
                "PARTNER_F", "ACTIVE_2.dat",
                TransferDirection.SEND, "192.168.1.106");
        transferService.startTransfer(t2.getTransferId(), 2048L, "/data/2.dat");

        TransferRecord t3 = transferService.createTransfer(
                "session-8", "server-2", "node-2",
                "PARTNER_G", "COMPLETED.dat",
                TransferDirection.RECEIVE, "192.168.1.107");
        transferService.startTransfer(t3.getTransferId(), 512L, "/data/3.dat");
        transferService.completeTransfer(t3.getTransferId(), null);

        // Query active
        List<TransferRecord> active = transferService.getActiveTransfers();
        assertEquals(2, active.size());

        // Query by server
        List<TransferRecord> server1Active = transferService.getActiveTransfersByServer("server-1");
        assertEquals(2, server1Active.size());

        List<TransferRecord> server2Active = transferService.getActiveTransfersByServer("server-2");
        assertEquals(0, server2Active.size());
    }

    @Test
    @DisplayName("Search transfers with filters")
    void testSearchTransfers() {
        // Create transfers with different attributes
        for (int i = 0; i < 5; i++) {
            TransferRecord t = transferService.createTransfer(
                    "session-" + (10 + i), "server-1", "node-1",
                    "PARTNER_H", "SEARCH_" + i + ".dat",
                    i % 2 == 0 ? TransferDirection.RECEIVE : TransferDirection.SEND,
                    "192.168.1." + (110 + i));
            transferService.startTransfer(t.getTransferId(), 1024L * (i + 1), "/data/search" + i + ".dat");
            if (i < 3) {
                transferService.completeTransfer(t.getTransferId(), null);
            }
        }

        // Search by partner
        Page<TransferRecord> byPartner = transferService.getTransfersByPartner("PARTNER_H", 0, 10);
        assertEquals(5, byPartner.getTotalElements());

        // Search by status
        Page<TransferRecord> completed = transferService.getTransfersByStatus(TransferStatus.COMPLETED, 0, 10);
        assertEquals(3, completed.getTotalElements());

        // Search by direction
        Page<TransferRecord> receives = transferService.searchTransfers(
                null, null, TransferDirection.RECEIVE, null, null, null, 0, 10);
        assertEquals(3, receives.getTotalElements());
    }

    @Test
    @DisplayName("Get transfer statistics")
    void testTransferStatistics() {
        // Create some transfers
        for (int i = 0; i < 10; i++) {
            TransferRecord t = transferService.createTransfer(
                    "session-" + (20 + i), "server-1", "node-1",
                    "PARTNER_I", "STATS_" + i + ".dat",
                    TransferDirection.RECEIVE, "192.168.1." + (120 + i));
            transferService.startTransfer(t.getTransferId(), 1000L, "/data/stats" + i + ".dat");
            transferService.updateProgress(t.getTransferId(), 1000L);

            if (i < 7) {
                transferService.completeTransfer(t.getTransferId(), null);
            } else if (i < 9) {
                transferService.failTransfer(t.getTransferId(), "E004", "Test error");
            }
            // Leave last one in progress
        }

        TransferStatistics stats = transferService.getStatistics();

        assertEquals(10, stats.getTotalTransfers());
        assertEquals(1, stats.getActiveTransfers());
        assertEquals(7, stats.getCompletedTransfers());
        assertEquals(2, stats.getFailedTransfers());
        assertEquals(7000L, stats.getTotalBytesTransferred()); // Only completed transfers
    }

    @Test
    @DisplayName("Record sync points")
    void testSyncPoints() {
        TransferRecord transfer = transferService.createTransfer(
                "session-30", "server-1", "node-1",
                "PARTNER_J", "SYNC_FILE.dat",
                TransferDirection.RECEIVE, "192.168.1.130");

        transferService.startTransfer(transfer.getTransferId(), 10000L, "/data/sync.dat");

        // Record multiple sync points
        transferService.updateProgress(transfer.getTransferId(), 2000L);
        transferService.recordSyncPoint(transfer.getTransferId(), 2000L);

        transferService.updateProgress(transfer.getTransferId(), 4000L);
        transferService.recordSyncPoint(transfer.getTransferId(), 4000L);

        transferService.updateProgress(transfer.getTransferId(), 6000L);
        transferService.recordSyncPoint(transfer.getTransferId(), 6000L);

        // Verify sync point recorded
        TransferRecord updated = transferService.getTransfer(transfer.getTransferId()).orElseThrow();
        assertEquals(6000L, updated.getLastSyncPoint());
    }

    @Test
    @DisplayName("Interrupt and resume transfer")
    void testInterruptAndResumeTransfer() {
        TransferRecord transfer = transferService.createTransfer(
                "session-32", "server-1", "node-1",
                "PARTNER_L", "INTERRUPT_FILE.dat",
                TransferDirection.RECEIVE, "192.168.1.132");

        transferService.startTransfer(transfer.getTransferId(), 8000L, "/data/interrupt.dat");
        transferService.updateProgress(transfer.getTransferId(), 3000L);
        transferService.recordSyncPoint(transfer.getTransferId(), 3000L);

        transfer = transferService.interruptTransfer(transfer.getTransferId(), "Network disconnected");

        assertEquals(TransferStatus.INTERRUPTED, transfer.getStatus());
        assertTrue(transfer.canRetry());

        // Resume
        transfer = transferService.resumeTransfer(transfer.getTransferId());
        assertEquals(TransferStatus.IN_PROGRESS, transfer.getStatus());
    }

    @Test
    @DisplayName("Cancel transfer")
    void testCancelTransfer() {
        TransferRecord transfer = transferService.createTransfer(
                "session-33", "server-1", "node-1",
                "PARTNER_M", "CANCEL_FILE.dat",
                TransferDirection.SEND, "192.168.1.133");

        transferService.startTransfer(transfer.getTransferId(), 5000L, "/data/cancel.dat");
        transferService.updateProgress(transfer.getTransferId(), 1000L);

        transfer = transferService.cancelTransfer(transfer.getTransferId(), "User cancelled");

        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());
        assertEquals("User cancelled", transfer.getErrorMessage());
        assertNotNull(transfer.getCompletedAt());
    }

    @Test
    @DisplayName("Get retryable transfers")
    void testGetRetryableTransfers() {
        // Create an interrupted transfer
        TransferRecord transfer = transferService.createTransfer(
                "session-35", "server-1", "node-1",
                "PARTNER_O", "RETRYABLE_FILE.dat",
                TransferDirection.RECEIVE, "192.168.1.135");

        transferService.startTransfer(transfer.getTransferId(), 10000L, "/data/retryable.dat");
        transferService.updateProgress(transfer.getTransferId(), 3000L);
        transferService.recordSyncPoint(transfer.getTransferId(), 3000L);
        transferService.interruptTransfer(transfer.getTransferId(), "Network error");

        List<TransferRecord> retryable = transferService.getRetryableTransfers();
        assertTrue(retryable.stream().anyMatch(t -> t.getTransferId().equals(transfer.getTransferId())));
    }

    @Test
    @DisplayName("Get transfer or throw exception")
    void testGetTransferOrThrow() {
        assertThrows(IllegalArgumentException.class, () -> {
            transferService.getTransfer("non-existent-id").orElseThrow(
                    () -> new IllegalArgumentException("Transfer not found"));
        });
    }

    @Test
    @DisplayName("Get active transfers")
    void testGetActiveTransfers() {
        // Create an active transfer
        TransferRecord transfer = transferService.createTransfer(
                "session-36", "server-1", "node-1",
                "PARTNER_P", "ACTIVE_FILE.dat",
                TransferDirection.SEND, "192.168.1.136");

        transferService.startTransfer(transfer.getTransferId(), 5000L, "/data/active.dat");

        List<TransferRecord> active = transferService.getActiveTransfers();
        assertTrue(active.stream().anyMatch(t -> t.getTransferId().equals(transfer.getTransferId())));
    }
}
