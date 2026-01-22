package com.pesitwizard.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.server.entity.TransferRecord;
import com.pesitwizard.server.entity.TransferRecord.TransferDirection;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferTracker Tests")
class TransferTrackerTest {

    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransferTracker transferTracker;

    private SessionContext sessionContext;

    @BeforeEach
    void setUp() {
        sessionContext = new SessionContext("session-123");
        sessionContext.setClientIdentifier("partner-1");
        sessionContext.setRemoteAddress("192.168.1.100");
    }

    @Nested
    @DisplayName("Track Transfer Start")
    class TrackTransferStartTests {

        @Test
        @DisplayName("should track transfer start and store transfer ID in session")
        void shouldTrackTransferStart() {
            TransferRecord record = new TransferRecord();
            record.setTransferId("transfer-123");

            when(transferService.createTransfer(anyString(), anyString(), anyString(),
                    anyString(), anyString(), any(TransferDirection.class), anyString()))
                    .thenReturn(record);

            transferTracker.trackTransferStart(sessionContext, "server-1", "node-1",
                    TransferDirection.RECEIVE, "test.dat", 1024L, "/data/test.dat");

            assertEquals("transfer-123", sessionContext.getTransferRecordId());
            verify(transferService).createTransfer(eq("session-123"), eq("server-1"), eq("node-1"),
                    eq("partner-1"), eq("test.dat"), eq(TransferDirection.RECEIVE), eq("192.168.1.100"));
            verify(transferService).startTransfer("transfer-123", 1024L, "/data/test.dat");
        }

        @Test
        @DisplayName("should handle exception gracefully when tracking start fails")
        void shouldHandleExceptionWhenTrackingStartFails() {
            when(transferService.createTransfer(anyString(), anyString(), anyString(),
                    anyString(), anyString(), any(TransferDirection.class), anyString()))
                    .thenThrow(new RuntimeException("Database error"));

            // Should not throw exception
            assertDoesNotThrow(() -> transferTracker.trackTransferStart(sessionContext, "server-1",
                    "node-1", TransferDirection.RECEIVE, "test.dat", 1024L, "/data/test.dat"));
        }
    }

    @Nested
    @DisplayName("Track Progress")
    class TrackProgressTests {

        @Test
        @DisplayName("should track progress when transfer ID is set")
        void shouldTrackProgress() {
            sessionContext.setTransferRecordId("transfer-123");

            transferTracker.trackProgress(sessionContext, 512L);

            verify(transferService).updateProgress("transfer-123", 512L);
        }

        @Test
        @DisplayName("should skip progress tracking when no transfer ID")
        void shouldSkipProgressWhenNoTransferId() {
            transferTracker.trackProgress(sessionContext, 512L);

            verifyNoInteractions(transferService);
        }

        @Test
        @DisplayName("should handle exception gracefully when tracking progress fails")
        void shouldHandleExceptionWhenTrackingProgressFails() {
            sessionContext.setTransferRecordId("transfer-123");
            doThrow(new RuntimeException("Update failed")).when(transferService).updateProgress(anyString(), anyLong());

            assertDoesNotThrow(() -> transferTracker.trackProgress(sessionContext, 512L));
        }
    }

    @Nested
    @DisplayName("Track Sync Point")
    class TrackSyncPointTests {

        @Test
        @DisplayName("should track sync point")
        void shouldTrackSyncPoint() {
            sessionContext.setTransferRecordId("transfer-123");

            transferTracker.trackSyncPoint(sessionContext, 1000L);

            verify(transferService).recordSyncPoint("transfer-123", 1000L);
        }

        @Test
        @DisplayName("should skip sync point tracking when no transfer ID")
        void shouldSkipSyncPointWhenNoTransferId() {
            transferTracker.trackSyncPoint(sessionContext, 1000L);

            verifyNoInteractions(transferService);
        }
    }

    @Nested
    @DisplayName("Track Transfer Complete")
    class TrackTransferCompleteTests {

        @Test
        @DisplayName("should track transfer completion")
        void shouldTrackCompletion() {
            sessionContext.setTransferRecordId("transfer-123");

            transferTracker.trackTransferComplete(sessionContext);

            verify(transferService).completeTransfer(eq("transfer-123"), any());
            assertNull(sessionContext.getTransferRecordId());
        }

        @Test
        @DisplayName("should track completion with transfer context data")
        void shouldTrackCompletionWithTransferContext() {
            sessionContext.setTransferRecordId("transfer-123");
            TransferContext transferContext = new TransferContext();
            // Set bytes transferred directly since appendData now requires streaming setup
            transferContext.setBytesTransferred(4L);
            sessionContext.setCurrentTransfer(transferContext);

            transferTracker.trackTransferComplete(sessionContext);

            verify(transferService).updateProgress("transfer-123", 4L);
            verify(transferService).completeTransfer(eq("transfer-123"), any());
        }

        @Test
        @DisplayName("should skip completion when no transfer ID")
        void shouldSkipCompletionWhenNoTransferId() {
            transferTracker.trackTransferComplete(sessionContext);

            verifyNoInteractions(transferService);
        }
    }

    @Nested
    @DisplayName("Track Transfer Failed")
    class TrackTransferFailedTests {

        @Test
        @DisplayName("should track transfer failure")
        void shouldTrackFailure() {
            sessionContext.setTransferRecordId("transfer-123");

            transferTracker.trackTransferFailed(sessionContext, "ERR001", "File not found");

            verify(transferService).failTransfer("transfer-123", "ERR001", "File not found");
            assertNull(sessionContext.getTransferRecordId());
        }

        @Test
        @DisplayName("should skip failure tracking when no transfer ID")
        void shouldSkipFailureWhenNoTransferId() {
            transferTracker.trackTransferFailed(sessionContext, "ERR001", "Error");

            verifyNoInteractions(transferService);
        }
    }

    @Nested
    @DisplayName("Track Transfer Cancelled")
    class TrackTransferCancelledTests {

        @Test
        @DisplayName("should track transfer cancellation")
        void shouldTrackCancellation() {
            sessionContext.setTransferRecordId("transfer-123");

            transferTracker.trackTransferCancelled(sessionContext, "User requested");

            verify(transferService).cancelTransfer("transfer-123", "User requested");
            assertNull(sessionContext.getTransferRecordId());
        }

        @Test
        @DisplayName("should skip cancellation tracking when no transfer ID")
        void shouldSkipCancellationWhenNoTransferId() {
            transferTracker.trackTransferCancelled(sessionContext, "User requested");

            verifyNoInteractions(transferService);
        }
    }

    @Nested
    @DisplayName("Track Transfer Interrupted")
    class TrackTransferInterruptedTests {

        @Test
        @DisplayName("should track transfer interruption and keep transfer ID for resume")
        void shouldTrackInterruption() {
            sessionContext.setTransferRecordId("transfer-123");

            transferTracker.trackTransferInterrupted(sessionContext, "Connection lost");

            verify(transferService).interruptTransfer("transfer-123", "Connection lost");
            // Transfer ID should NOT be cleared for potential resume
            assertEquals("transfer-123", sessionContext.getTransferRecordId());
        }

        @Test
        @DisplayName("should skip interruption tracking when no transfer ID")
        void shouldSkipInterruptionWhenNoTransferId() {
            transferTracker.trackTransferInterrupted(sessionContext, "Connection lost");

            verifyNoInteractions(transferService);
        }
    }

    @Nested
    @DisplayName("Mark Interrupted Transfers")
    class MarkInterruptedTransfersTests {

        @Test
        @DisplayName("should mark interrupted transfers for node")
        void shouldMarkInterruptedTransfers() {
            when(transferService.markInterruptedTransfers("node-1")).thenReturn(5);

            int count = transferTracker.markInterruptedTransfers("node-1");

            assertEquals(5, count);
            verify(transferService).markInterruptedTransfers("node-1");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle exception when sync point tracking fails")
        void shouldHandleExceptionWhenSyncPointFails() {
            sessionContext.setTransferRecordId("transfer-123");
            doThrow(new RuntimeException("Database error")).when(transferService).recordSyncPoint(anyString(),
                    anyLong());

            assertDoesNotThrow(() -> transferTracker.trackSyncPoint(sessionContext, 1000L));
        }

        @Test
        @DisplayName("should handle exception when completion fails")
        void shouldHandleExceptionWhenCompletionFails() {
            sessionContext.setTransferRecordId("transfer-123");
            doThrow(new RuntimeException("Database error")).when(transferService).completeTransfer(anyString(), any());

            assertDoesNotThrow(() -> transferTracker.trackTransferComplete(sessionContext));
        }

        @Test
        @DisplayName("should handle exception when failure tracking fails")
        void shouldHandleExceptionWhenFailureTrackingFails() {
            sessionContext.setTransferRecordId("transfer-123");
            doThrow(new RuntimeException("Database error")).when(transferService).failTransfer(anyString(), anyString(),
                    anyString());

            assertDoesNotThrow(() -> transferTracker.trackTransferFailed(sessionContext, "ERR001", "Error"));
        }

        @Test
        @DisplayName("should handle exception when cancellation tracking fails")
        void shouldHandleExceptionWhenCancellationFails() {
            sessionContext.setTransferRecordId("transfer-123");
            doThrow(new RuntimeException("Database error")).when(transferService).cancelTransfer(anyString(),
                    anyString());

            assertDoesNotThrow(() -> transferTracker.trackTransferCancelled(sessionContext, "User requested"));
        }

        @Test
        @DisplayName("should handle exception when interruption tracking fails")
        void shouldHandleExceptionWhenInterruptionFails() {
            sessionContext.setTransferRecordId("transfer-123");
            doThrow(new RuntimeException("Database error")).when(transferService).interruptTransfer(anyString(),
                    anyString());

            assertDoesNotThrow(() -> transferTracker.trackTransferInterrupted(sessionContext, "Connection lost"));
        }

        @Test
        @DisplayName("should track completion with bytesTransferred but no data")
        void shouldTrackCompletionWithBytesButNoData() {
            sessionContext.setTransferRecordId("transfer-123");
            TransferContext transferContext = new TransferContext();
            transferContext.setBytesTransferred(1024);
            sessionContext.setCurrentTransfer(transferContext);

            transferTracker.trackTransferComplete(sessionContext);

            verify(transferService).updateProgress("transfer-123", 1024L);
            verify(transferService).completeTransfer(eq("transfer-123"), isNull());
        }

        @Test
        @DisplayName("should track completion with null transfer context")
        void shouldTrackCompletionWithNullTransferContext() {
            sessionContext.setTransferRecordId("transfer-123");
            sessionContext.setCurrentTransfer(null);

            transferTracker.trackTransferComplete(sessionContext);

            verify(transferService).completeTransfer(eq("transfer-123"), isNull());
        }
    }

    @Nested
    @DisplayName("Track Authentication Failure")
    class TrackAuthenticationFailureTests {

        @Test
        @DisplayName("should track authentication failure")
        void shouldTrackAuthenticationFailure() {
            TransferRecord record = new TransferRecord();
            record.setTransferId("transfer-auth-fail");

            when(transferService.createTransfer(anyString(), anyString(), anyString(),
                    anyString(), eq("AUTH_FAILURE"), any(TransferDirection.class), anyString()))
                    .thenReturn(record);

            transferTracker.trackAuthenticationFailure(sessionContext, "server-1", "node-1",
                    "AUTH001", "Invalid credentials");

            verify(transferService).createTransfer(eq("session-123"), eq("server-1"), eq("node-1"),
                    eq("partner-1"), eq("AUTH_FAILURE"), eq(TransferDirection.RECEIVE), eq("192.168.1.100"));
            verify(transferService).failTransfer("transfer-auth-fail", "AUTH001", "Invalid credentials");
        }

        @Test
        @DisplayName("should handle exception when tracking auth failure fails")
        void shouldHandleExceptionWhenAuthFailureTrackingFails() {
            when(transferService.createTransfer(anyString(), anyString(), anyString(),
                    anyString(), anyString(), any(TransferDirection.class), anyString()))
                    .thenThrow(new RuntimeException("Database error"));

            assertDoesNotThrow(() -> transferTracker.trackAuthenticationFailure(
                    sessionContext, "server-1", "node-1", "AUTH001", "Invalid credentials"));
        }
    }
}
