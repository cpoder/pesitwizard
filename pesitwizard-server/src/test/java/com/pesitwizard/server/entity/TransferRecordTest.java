package com.pesitwizard.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.pesitwizard.server.entity.TransferRecord.TransferDirection;
import com.pesitwizard.server.entity.TransferRecord.TransferStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TransferRecord Tests")
class TransferRecordTest {

    private TransferRecord record;

    @BeforeEach
    void setUp() {
        record =
                TransferRecord.builder()
                        .transferId("test-transfer-123")
                        .sessionId("session-456")
                        .serverId("server-1")
                        .partnerId("PARTNER_A")
                        .filename("TEST.DAT")
                        .direction(TransferDirection.RECEIVE)
                        .startedAt(Instant.now().minusSeconds(60))
                        .updatedAt(Instant.now())
                        .build();
    }

    @Test
    @DisplayName("should have default values")
    void shouldHaveDefaultValues() {
        assertEquals(TransferStatus.INITIATED, record.getStatus());
        assertEquals(0L, record.getFileSize());
        assertEquals(0L, record.getBytesTransferred());
        assertEquals(0, record.getProgressPercent());
        assertEquals(0L, record.getLastSyncPoint());
        assertEquals(0, record.getSyncPointCount());
        assertEquals(0, record.getRetryCount());
        assertEquals(3, record.getMaxRetries());
        assertEquals(2, record.getProtocolVersion());
        assertTrue(record.getResumable());
    }

    @Test
    @DisplayName("should update progress correctly")
    void shouldUpdateProgress() {
        record.setFileSize(1000L);

        record.updateProgress(500L);

        assertEquals(500L, record.getBytesTransferred());
        assertEquals(50, record.getProgressPercent());
        assertNotNull(record.getUpdatedAt());
    }

    @Test
    @DisplayName("should update progress with zero file size")
    void shouldUpdateProgressWithZeroFileSize() {
        record.setFileSize(0L);

        record.updateProgress(100L);

        assertEquals(100L, record.getBytesTransferred());
        assertEquals(0, record.getProgressPercent()); // Can't calculate percent with 0 size
    }

    @Test
    @DisplayName("should mark as completed")
    void shouldMarkAsCompleted() {
        record.markCompleted();

        assertEquals(TransferStatus.COMPLETED, record.getStatus());
        assertEquals(100, record.getProgressPercent());
        assertNotNull(record.getCompletedAt());
        assertNotNull(record.getUpdatedAt());
    }

    @Test
    @DisplayName("should mark as failed")
    void shouldMarkAsFailed() {
        record.markFailed("E001", "Connection lost");

        assertEquals(TransferStatus.FAILED, record.getStatus());
        assertEquals("E001", record.getErrorCode());
        assertEquals("Connection lost", record.getErrorMessage());
        assertNotNull(record.getCompletedAt());
    }

    @Test
    @DisplayName("should calculate transfer speed")
    void shouldCalculateTransferSpeed() {
        record.setStartedAt(Instant.now().minusSeconds(10));
        record.setBytesTransferred(10000L);

        Long speed = record.getTransferSpeed();

        assertNotNull(speed);
        assertTrue(speed > 0);
        // ~1000 bytes/sec expected
        assertTrue(speed >= 900 && speed <= 1100);
    }

    @Test
    @DisplayName("should return zero speed when no bytes transferred")
    void shouldReturnZeroSpeedWhenNoBytesTransferred() {
        record.setBytesTransferred(0L);

        assertEquals(0L, record.getTransferSpeed());
    }

    @Test
    @DisplayName("should return zero speed when start time is null")
    void shouldReturnZeroSpeedWhenStartTimeNull() {
        record.setStartedAt(null);
        record.setBytesTransferred(1000L);

        assertEquals(0L, record.getTransferSpeed());
    }

    @Test
    @DisplayName("should calculate estimated time remaining")
    void shouldCalculateEstimatedTimeRemaining() {
        record.setStartedAt(Instant.now().minusSeconds(10));
        record.setFileSize(20000L);
        record.setBytesTransferred(10000L);

        Long remaining = record.getEstimatedTimeRemaining();

        assertNotNull(remaining);
        // Half the file remaining, ~10 seconds expected
        assertTrue(remaining >= 8 && remaining <= 12);
    }

    @Test
    @DisplayName("should return null estimated time when file size is zero")
    void shouldReturnNullEstimatedTimeWhenFileSizeZero() {
        record.setFileSize(0L);

        assertNull(record.getEstimatedTimeRemaining());
    }

    @Test
    @DisplayName("should return null estimated time when speed is zero")
    void shouldReturnNullEstimatedTimeWhenSpeedZero() {
        record.setFileSize(1000L);
        record.setBytesTransferred(0L);

        assertNull(record.getEstimatedTimeRemaining());
    }

    @Test
    @DisplayName("canRetry should return true for failed resumable transfer")
    void canRetryShouldReturnTrueForFailedResumable() {
        record.setStatus(TransferStatus.FAILED);
        record.setResumable(true);
        record.setRetryCount(0);

        assertTrue(record.canRetry());
    }

    @Test
    @DisplayName("canRetry should return true for interrupted resumable transfer")
    void canRetryShouldReturnTrueForInterruptedResumable() {
        record.setStatus(TransferStatus.INTERRUPTED);
        record.setResumable(true);
        record.setRetryCount(1);

        assertTrue(record.canRetry());
    }

    @Test
    @DisplayName("canRetry should return false when max retries exceeded")
    void canRetryShouldReturnFalseWhenMaxRetriesExceeded() {
        record.setStatus(TransferStatus.FAILED);
        record.setResumable(true);
        record.setRetryCount(3);
        record.setMaxRetries(3);

        assertFalse(record.canRetry());
    }

    @Test
    @DisplayName("canRetry should return false when not resumable")
    void canRetryShouldReturnFalseWhenNotResumable() {
        record.setStatus(TransferStatus.FAILED);
        record.setResumable(false);
        record.setRetryCount(0);

        assertFalse(record.canRetry());
    }

    @Test
    @DisplayName("canRetry should return false for completed transfer")
    void canRetryShouldReturnFalseForCompleted() {
        record.setStatus(TransferStatus.COMPLETED);
        record.setResumable(true);
        record.setRetryCount(0);

        assertFalse(record.canRetry());
    }

    @Test
    @DisplayName("should store all transfer attributes")
    void shouldStoreAllAttributes() {
        TransferRecord tr =
                TransferRecord.builder()
                        .transferId("tr-1")
                        .sessionId("sess-1")
                        .serverId("srv-1")
                        .nodeId("node-1")
                        .direction(TransferDirection.SEND)
                        .status(TransferStatus.IN_PROGRESS)
                        .partnerId("PARTNER_B")
                        .filename("DATA.TXT")
                        .localPath("/data/DATA.TXT")
                        .fileSize(5000L)
                        .bytesTransferred(2500L)
                        .progressPercent(50)
                        .lastSyncPoint(2000L)
                        .syncPointCount(2)
                        .retryCount(1)
                        .maxRetries(5)
                        .startedAt(Instant.now())
                        .remoteAddress("192.168.1.100")
                        .protocolVersion(2)
                        .accessType(1)
                        .checksum("abc123")
                        .checksumAlgorithm("MD5")
                        .resumable(false)
                        .parentTransferId("parent-1")
                        .metadata("{\"key\":\"value\"}")
                        .updatedAt(Instant.now())
                        .build();

        assertEquals("tr-1", tr.getTransferId());
        assertEquals("sess-1", tr.getSessionId());
        assertEquals("srv-1", tr.getServerId());
        assertEquals("node-1", tr.getNodeId());
        assertEquals(TransferDirection.SEND, tr.getDirection());
        assertEquals(TransferStatus.IN_PROGRESS, tr.getStatus());
        assertEquals("PARTNER_B", tr.getPartnerId());
        assertEquals("DATA.TXT", tr.getFilename());
        assertEquals("/data/DATA.TXT", tr.getLocalPath());
        assertEquals(5000L, tr.getFileSize());
        assertEquals(2500L, tr.getBytesTransferred());
        assertEquals(50, tr.getProgressPercent());
        assertEquals(2000L, tr.getLastSyncPoint());
        assertEquals(2, tr.getSyncPointCount());
        assertEquals(1, tr.getRetryCount());
        assertEquals(5, tr.getMaxRetries());
        assertEquals("192.168.1.100", tr.getRemoteAddress());
        assertEquals(2, tr.getProtocolVersion());
        assertEquals(1, tr.getAccessType());
        assertEquals("abc123", tr.getChecksum());
        assertEquals("MD5", tr.getChecksumAlgorithm());
        assertFalse(tr.getResumable());
        assertEquals("parent-1", tr.getParentTransferId());
        assertEquals("{\"key\":\"value\"}", tr.getMetadata());
    }
}
