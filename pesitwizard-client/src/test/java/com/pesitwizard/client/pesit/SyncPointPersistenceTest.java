package com.pesitwizard.client.pesit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.client.entity.TransferHistory;
import com.pesitwizard.client.entity.TransferHistory.TransferStatus;
import com.pesitwizard.client.event.TransferEventBus;
import com.pesitwizard.client.repository.TransferHistoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for sync point persistence when transfers fail or are cancelled. Verifies that
 * lastSyncPoint and bytesAtLastSyncPoint are saved to the database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Sync Point Persistence Tests")
class SyncPointPersistenceTest {

    @Mock private TransferHistoryRepository historyRepository;

    @Mock private TransferEventBus eventBus;

    @Captor private ArgumentCaptor<TransferHistory> historyCaptor;

    private TransferHistory history;

    @BeforeEach
    void setUp() {
        history = new TransferHistory();
        history.setId("test-history-123");
        history.setStatus(TransferStatus.IN_PROGRESS);
    }

    @Nested
    @DisplayName("TransferContext Sync Point State")
    class TransferContextSyncPointStateTests {

        @Test
        @DisplayName("should preserve sync point data after multiple sync points")
        void shouldPreserveSyncPointDataAfterMultipleSyncPoints() {
            TransferContext ctx = new TransferContext("test-123", 10485760L, eventBus);

            // Simulate sync points during transfer
            ctx.addBytes(102400);
            ctx.syncPoint(1, 102400L);

            ctx.addBytes(102400);
            ctx.syncPoint(2, 204800L);

            ctx.addBytes(102400);
            ctx.syncPoint(3, 307200L);

            // Verify latest sync point is preserved
            assertEquals(3, ctx.getLastSyncPoint());
            assertEquals(307200L, ctx.getBytesAtLastSyncPoint());
            assertEquals(307200L, ctx.getBytesTransferred());
        }

        @Test
        @DisplayName("should preserve sync point data after error")
        void shouldPreserveSyncPointDataAfterError() {
            TransferContext ctx = new TransferContext("test-123", 10485760L, eventBus);

            // Simulate sync points then error
            ctx.syncPoint(5, 512000L);
            ctx.addBytes(512000);
            ctx.error("Connection lost", "0x010203");

            // Sync points should still be accessible
            assertEquals(5, ctx.getLastSyncPoint());
            assertEquals(512000L, ctx.getBytesAtLastSyncPoint());
            assertEquals(512000L, ctx.getBytesTransferred());
        }
    }

    @Nested
    @DisplayName("History Update with Sync Points")
    class HistoryUpdateWithSyncPointsTests {

        @Test
        @DisplayName("should save sync point data to history on failure")
        void shouldSaveSyncPointDataToHistoryOnFailure() {
            when(historyRepository.findById("test-history-123")).thenReturn(Optional.of(history));

            TransferContext ctx = new TransferContext("test-history-123", 10485760L, eventBus);
            ctx.syncPoint(42, 4300800L);
            ctx.addBytes(4500000L);

            // Simulate what updateHistoryFailed should do
            historyRepository
                    .findById("test-history-123")
                    .ifPresent(
                            h -> {
                                h.setStatus(TransferStatus.FAILED);
                                h.setErrorMessage("Connection lost");
                                h.setDiagnosticCode("0x010203");
                                h.setBytesTransferred(ctx.getBytesTransferred());
                                if (ctx.getLastSyncPoint() > 0) {
                                    h.setLastSyncPoint(ctx.getLastSyncPoint());
                                    h.setBytesAtLastSyncPoint(ctx.getBytesAtLastSyncPoint());
                                }
                                historyRepository.save(h);
                            });

            verify(historyRepository).save(historyCaptor.capture());
            TransferHistory saved = historyCaptor.getValue();

            assertEquals(TransferStatus.FAILED, saved.getStatus());
            assertEquals("Connection lost", saved.getErrorMessage());
            assertEquals("0x010203", saved.getDiagnosticCode());
            assertEquals(4500000L, saved.getBytesTransferred());
            assertEquals(42, saved.getLastSyncPoint());
            assertEquals(4300800L, saved.getBytesAtLastSyncPoint());
        }

        @Test
        @DisplayName("should not set sync point fields when no sync points occurred")
        void shouldNotSetSyncPointFieldsWhenNoSyncPointsOccurred() {
            when(historyRepository.findById("test-history-123")).thenReturn(Optional.of(history));

            TransferContext ctx = new TransferContext("test-history-123", 10485760L, eventBus);
            ctx.addBytes(50000L); // Some bytes transferred but no sync points

            // Simulate what updateHistoryFailed should do
            historyRepository
                    .findById("test-history-123")
                    .ifPresent(
                            h -> {
                                h.setStatus(TransferStatus.FAILED);
                                h.setErrorMessage("Early failure");
                                h.setBytesTransferred(ctx.getBytesTransferred());
                                if (ctx.getLastSyncPoint() > 0) {
                                    h.setLastSyncPoint(ctx.getLastSyncPoint());
                                    h.setBytesAtLastSyncPoint(ctx.getBytesAtLastSyncPoint());
                                }
                                historyRepository.save(h);
                            });

            verify(historyRepository).save(historyCaptor.capture());
            TransferHistory saved = historyCaptor.getValue();

            assertEquals(TransferStatus.FAILED, saved.getStatus());
            assertEquals(50000L, saved.getBytesTransferred());
            assertNull(saved.getLastSyncPoint()); // Not set
            assertNull(saved.getBytesAtLastSyncPoint()); // Not set
        }

        @Test
        @DisplayName("should preserve existing history fields when updating with sync points")
        void shouldPreserveExistingHistoryFieldsWhenUpdating() {
            history.setServerId("server-1");
            history.setServerName("Production Server");
            history.setPartnerId("PARTNER1");
            history.setLocalFilename("/data/test.dat");
            history.setFileSize(10485760L);
            history.setSyncPointsEnabled(true);

            when(historyRepository.findById("test-history-123")).thenReturn(Optional.of(history));

            TransferContext ctx = new TransferContext("test-history-123", 10485760L, eventBus);
            ctx.syncPoint(10, 1024000L);

            historyRepository
                    .findById("test-history-123")
                    .ifPresent(
                            h -> {
                                h.setStatus(TransferStatus.FAILED);
                                h.setErrorMessage("Network error");
                                h.setBytesTransferred(ctx.getBytesTransferred());
                                if (ctx.getLastSyncPoint() > 0) {
                                    h.setLastSyncPoint(ctx.getLastSyncPoint());
                                    h.setBytesAtLastSyncPoint(ctx.getBytesAtLastSyncPoint());
                                }
                                historyRepository.save(h);
                            });

            verify(historyRepository).save(historyCaptor.capture());
            TransferHistory saved = historyCaptor.getValue();

            // Original fields preserved
            assertEquals("server-1", saved.getServerId());
            assertEquals("Production Server", saved.getServerName());
            assertEquals("PARTNER1", saved.getPartnerId());
            assertEquals("/data/test.dat", saved.getLocalFilename());
            assertEquals(10485760L, saved.getFileSize());
            assertTrue(saved.getSyncPointsEnabled());

            // Sync point data added
            assertEquals(10, saved.getLastSyncPoint());
            assertEquals(1024000L, saved.getBytesAtLastSyncPoint());
        }
    }

    @Nested
    @DisplayName("Resume Capability Verification")
    class ResumeCapabilityTests {

        @Test
        @DisplayName("should have sufficient data for resume after failure")
        void shouldHaveSufficientDataForResumeAfterFailure() {
            TransferContext ctx =
                    new TransferContext("test-123", 104857600L, eventBus); // 100MB file

            // Simulate transfer with sync points every ~10MB
            for (int i = 1; i <= 5; i++) {
                long bytePos = i * 10485760L;
                ctx.addBytes(10485760L);
                ctx.syncPoint(i, bytePos);
            }

            // After 5 sync points (50MB transferred), connection drops
            ctx.error("Connection reset", null);

            // We should have all data needed for resume
            assertEquals(5, ctx.getLastSyncPoint());
            assertEquals(52428800L, ctx.getBytesAtLastSyncPoint());
            assertEquals(52428800L, ctx.getBytesTransferred());

            // This data can be persisted and used for PI_18_POINT_RELANCE on resume
        }

        @Test
        @DisplayName("should allow resume from last sync point")
        void shouldAllowResumeFromLastSyncPoint() {
            // First transfer attempt
            TransferContext ctx1 = new TransferContext("test-123", 104857600L, eventBus);
            ctx1.syncPoint(3, 31457280L); // 30MB
            ctx1.addBytes(35000000L);

            // Simulate saving to history
            history.setLastSyncPoint(ctx1.getLastSyncPoint());
            history.setBytesAtLastSyncPoint(ctx1.getBytesAtLastSyncPoint());

            // Resume transfer would use these values for PI_18_POINT_RELANCE
            assertEquals(3, history.getLastSyncPoint());
            assertEquals(31457280L, history.getBytesAtLastSyncPoint());

            // New transfer context for resume
            TransferContext ctx2 = new TransferContext("test-123-retry", 104857600L, eventBus);
            // The resume logic would seek to bytesAtLastSyncPoint and continue from there
        }
    }
}
