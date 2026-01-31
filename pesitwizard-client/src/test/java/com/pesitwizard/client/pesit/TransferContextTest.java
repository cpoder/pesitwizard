package com.pesitwizard.client.pesit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.client.event.TransferEventBus;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferContext Tests")
class TransferContextTest {

    @Mock
    private TransferEventBus eventBus;

    private TransferContext context;

    @BeforeEach
    void setUp() {
        context = new TransferContext("test-transfer-123", 1000L, eventBus);
    }

    @Nested
    @DisplayName("Sync Point Tracking")
    class SyncPointTests {

        @Test
        @DisplayName("should initialize with zero sync point")
        void shouldInitializeWithZeroSyncPoint() {
            assertEquals(0, context.getLastSyncPoint());
            assertEquals(0, context.getBytesAtLastSyncPoint());
        }

        @Test
        @DisplayName("should track sync point and byte position")
        void shouldTrackSyncPointAndBytePosition() {
            context.syncPoint(1, 102400L);

            assertEquals(1, context.getLastSyncPoint());
            assertEquals(102400L, context.getBytesAtLastSyncPoint());
        }

        @Test
        @DisplayName("should update sync point on subsequent calls")
        void shouldUpdateSyncPointOnSubsequentCalls() {
            context.syncPoint(1, 102400L);
            context.syncPoint(2, 204800L);
            context.syncPoint(3, 307200L);

            assertEquals(3, context.getLastSyncPoint());
            assertEquals(307200L, context.getBytesAtLastSyncPoint());
        }

        @Test
        @DisplayName("should emit sync point event via event bus")
        void shouldEmitSyncPointEvent() {
            context.syncPoint(5, 512000L);

            verify(eventBus).syncPoint("test-transfer-123", 5, 512000L);
        }

        @Test
        @DisplayName("should handle null event bus gracefully")
        void shouldHandleNullEventBusGracefully() {
            TransferContext ctxNoEventBus = new TransferContext("test-id", null);

            // Should not throw
            assertDoesNotThrow(() -> ctxNoEventBus.syncPoint(1, 1000L));
            assertEquals(1, ctxNoEventBus.getLastSyncPoint());
            assertEquals(1000L, ctxNoEventBus.getBytesAtLastSyncPoint());
        }
    }

    @Nested
    @DisplayName("Bytes Tracking")
    class BytesTrackingTests {

        @Test
        @DisplayName("should track bytes transferred")
        void shouldTrackBytesTransferred() {
            context.addBytes(100);
            context.addBytes(200);

            assertEquals(300, context.getBytesTransferred());
        }

        @Test
        @DisplayName("should provide total bytes")
        void shouldProvideTotalBytes() {
            assertEquals(1000L, context.getTotalBytes());
        }
    }

    @Nested
    @DisplayName("State Transitions")
    class StateTransitionTests {

        @Test
        @DisplayName("should start in CN01_REPOS state")
        void shouldStartInReposState() {
            assertEquals(ClientState.CN01_REPOS, context.getState());
        }

        @Test
        @DisplayName("should transition through connect states")
        void shouldTransitionThroughConnectStates() {
            context.connectSent();
            assertEquals(ClientState.CN02A_CONNECT_PENDING, context.getState());

            context.connectAck();
            assertEquals(ClientState.CN03_CONNECTED, context.getState());
        }

        @Test
        @DisplayName("should reject invalid transitions")
        void shouldRejectInvalidTransitions() {
            // Cannot go from REPOS to FILE_SELECTED directly
            assertThrows(IllegalStateException.class, () ->
                context.transition(ClientState.SF03_FILE_SELECTED));
        }
    }

    @Nested
    @DisplayName("Constructor Variations")
    class ConstructorTests {

        @Test
        @DisplayName("should create context with transfer ID only")
        void shouldCreateContextWithTransferIdOnly() {
            TransferContext simpleCtx = new TransferContext("simple-123", eventBus);

            assertEquals("simple-123", simpleCtx.getTransferId());
            assertEquals(0, simpleCtx.getTotalBytes());
            assertEquals(0, simpleCtx.getLastSyncPoint());
            assertEquals(0, simpleCtx.getBytesAtLastSyncPoint());
        }

        @Test
        @DisplayName("should create context with file size")
        void shouldCreateContextWithFileSize() {
            TransferContext sizedCtx = new TransferContext("sized-123", 1048576L, eventBus);

            assertEquals("sized-123", sizedCtx.getTransferId());
            assertEquals(1048576L, sizedCtx.getTotalBytes());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should set error state and emit event")
        void shouldSetErrorStateAndEmitEvent() {
            context.error("Connection failed", "0x010203");

            assertEquals(ClientState.ERROR, context.getState());
            verify(eventBus).error("test-transfer-123", "Connection failed", "0x010203");
        }

        @Test
        @DisplayName("should preserve sync points on error")
        void shouldPreserveSyncPointsOnError() {
            context.syncPoint(5, 512000L);
            context.error("Transfer interrupted", null);

            // Sync points should still be accessible after error
            assertEquals(5, context.getLastSyncPoint());
            assertEquals(512000L, context.getBytesAtLastSyncPoint());
        }
    }

    @Nested
    @DisplayName("Completion and Cancellation")
    class CompletionTests {

        @Test
        @DisplayName("should emit completed event")
        void shouldEmitCompletedEvent() {
            context.addBytes(1000);
            context.completed();

            verify(eventBus).completed("test-transfer-123", 1000L);
        }

        @Test
        @DisplayName("should emit cancelled event")
        void shouldEmitCancelledEvent() {
            context.cancelled();

            verify(eventBus).cancelled("test-transfer-123");
        }
    }
}
