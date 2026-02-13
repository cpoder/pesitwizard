package com.pesitwizard.client.pesit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.client.event.TransferEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferContext Tests")
class TransferContextTest {

    @Mock private TransferEventBus eventBus;

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
            assertThrows(
                    IllegalStateException.class,
                    () -> context.transition(ClientState.SF03_FILE_SELECTED));
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
    @DisplayName("Full Send State Cycle")
    class FullSendCycleTests {

        @Test
        @DisplayName("should enforce full send state cycle")
        void shouldEnforceFullSendStateCycle() {
            context.connectSent();
            assertEquals(ClientState.CN02A_CONNECT_PENDING, context.getState());
            context.connectAck();
            assertEquals(ClientState.CN03_CONNECTED, context.getState());
            context.createSent();
            assertEquals(ClientState.SF01A_CREATE_PENDING, context.getState());
            context.createAck();
            assertEquals(ClientState.SF03_FILE_SELECTED, context.getState());
            context.openSent();
            assertEquals(ClientState.OF01A_OPEN_PENDING, context.getState());
            context.openAck();
            assertEquals(ClientState.OF02_TRANSFER_READY, context.getState());
            context.writeSent();
            assertEquals(ClientState.TDE01A_WRITE_PENDING, context.getState());
            context.writeAck();
            assertEquals(ClientState.TDE02A_SENDING_DATA, context.getState());
            // Sync point cycle
            context.syncSent();
            assertEquals(ClientState.TDE03_SYNC_PENDING, context.getState());
            context.syncAckSend();
            assertEquals(ClientState.TDE02A_SENDING_DATA, context.getState());
            // End of data
            context.dtfEndSent();
            assertEquals(ClientState.TDE07_DATA_END, context.getState());
            context.transEndSent();
            assertEquals(ClientState.TDE08A_TRANS_END_PENDING, context.getState());
            context.transEndAck();
            assertEquals(ClientState.OF02_TRANSFER_READY, context.getState());
            context.closeSent();
            assertEquals(ClientState.OF03A_CLOSE_PENDING, context.getState());
            context.closeAck();
            assertEquals(ClientState.SF03_FILE_SELECTED, context.getState());
            context.deselectSent();
            assertEquals(ClientState.SF04A_DESELECT_PENDING, context.getState());
            context.deselectAck();
            assertEquals(ClientState.CN03_CONNECTED, context.getState());
            context.releaseSent();
            assertEquals(ClientState.CN04A_RELEASE_PENDING, context.getState());
            context.releaseAck();
            assertEquals(ClientState.CN01_REPOS, context.getState());
        }
    }

    @Nested
    @DisplayName("Full Receive State Cycle")
    class FullReceiveCycleTests {

        @Test
        @DisplayName("should enforce full receive state cycle")
        void shouldEnforceFullReceiveStateCycle() {
            context.connectSent();
            context.connectAck();
            context.selectSent();
            assertEquals(ClientState.SF02A_SELECT_PENDING, context.getState());
            context.selectAck();
            assertEquals(ClientState.SF03_FILE_SELECTED, context.getState());
            context.openSent();
            context.openAck();
            context.readSent();
            assertEquals(ClientState.TDL01A_READ_PENDING, context.getState());
            context.readAck();
            assertEquals(ClientState.TDL02A_RECEIVING_DATA, context.getState());
            // Sync point cycle during receive
            context.syncReceived();
            assertEquals(ClientState.TDL03_SYNC_ACK, context.getState());
            context.syncAckSentReceive();
            assertEquals(ClientState.TDL02A_RECEIVING_DATA, context.getState());
            // End of received data
            context.dataEndReceived();
            assertEquals(ClientState.TDL07_DATA_END, context.getState());
            context.transEndSent();
            assertEquals(ClientState.TDL08A_TRANS_END_PENDING, context.getState());
            context.transEndAck();
            assertEquals(ClientState.OF02_TRANSFER_READY, context.getState());
            // Cleanup
            context.closeSent();
            context.closeAck();
            context.deselectSent();
            context.deselectAck();
            context.releaseSent();
            context.releaseAck();
            assertEquals(ClientState.CN01_REPOS, context.getState());
        }

        @Test
        @DisplayName("should reject sync during send when in receive state")
        void shouldRejectSendSyncDuringReceive() {
            context.connectSent();
            context.connectAck();
            context.selectSent();
            context.selectAck();
            context.openSent();
            context.openAck();
            context.readSent();
            context.readAck();
            // In TDL02A_RECEIVING_DATA, cannot do send sync
            assertThrows(IllegalStateException.class, () -> context.syncSent());
        }

        @Test
        @DisplayName("should reject write when in receive path")
        void shouldRejectWriteInReceivePath() {
            context.connectSent();
            context.connectAck();
            context.selectSent();
            context.selectAck();
            context.openSent();
            context.openAck();
            // After OPEN ACK, can do READ or WRITE, but not both
            context.readSent();
            // In TDL01A_READ_PENDING, cannot do WRITE
            assertThrows(IllegalStateException.class, () -> context.writeSent());
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
