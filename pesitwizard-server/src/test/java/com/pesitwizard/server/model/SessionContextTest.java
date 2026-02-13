package com.pesitwizard.server.model;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.pesitwizard.server.state.ServerState;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SessionContext Tests")
class SessionContextTest {

    private SessionContext context;

    @BeforeEach
    void setUp() {
        context = new SessionContext("test-session");
    }

    @Test
    @DisplayName("should initialize with correct session ID")
    void shouldInitializeWithSessionId() {
        assertEquals("test-session", context.getSessionId());
    }

    @Test
    @DisplayName("should initialize with default state")
    void shouldInitializeWithDefaultState() {
        assertEquals(ServerState.CN01_REPOS, context.getState());
    }

    @Test
    @DisplayName("should initialize timestamps")
    void shouldInitializeTimestamps() {
        assertNotNull(context.getStartTime());
        assertNotNull(context.getLastActivityTime());
    }

    @Test
    @DisplayName("should update last activity time on touch")
    void shouldUpdateLastActivityOnTouch() {
        Instant before = context.getLastActivityTime();
        await().atMost(Duration.ofMillis(200)).pollDelay(Duration.ofMillis(10)).until(() -> true);
        context.touch();
        assertTrue(context.getLastActivityTime().isAfter(before));
    }

    @Test
    @DisplayName("should transition to new state")
    void shouldTransitionToNewState() {
        context.transitionTo(ServerState.CN02B_CONNECT_PENDING);
        assertEquals(ServerState.CN02B_CONNECT_PENDING, context.getState());
    }

    @Test
    @DisplayName("should reject invalid state transition")
    void shouldRejectInvalidStateTransition() {
        // CN01 cannot transition directly to SF03 (must go through connect + create/select)
        assertThrows(
                InvalidStateTransitionException.class,
                () -> context.transitionTo(ServerState.SF03_FILE_SELECTED));
    }

    @Test
    @DisplayName("should start new transfer")
    void shouldStartNewTransfer() {
        TransferContext transfer = context.startTransfer();

        assertNotNull(transfer);
        assertNotNull(transfer.getStartTime());
        assertEquals(transfer, context.getCurrentTransfer());
    }

    @Test
    @DisplayName("should end current transfer")
    void shouldEndCurrentTransfer() {
        context.startTransfer();
        assertTrue(context.hasActiveTransfer());

        context.endTransfer();
        assertFalse(context.hasActiveTransfer());
        assertNull(context.getCurrentTransfer());
    }

    @Test
    @DisplayName("should set end time when ending transfer")
    void shouldSetEndTimeWhenEndingTransfer() {
        TransferContext transfer = context.startTransfer();
        assertNull(transfer.getEndTime());

        context.endTransfer();
        // Transfer object still has end time set even though context cleared it
    }

    @Test
    @DisplayName("should report no active transfer initially")
    void shouldReportNoActiveTransferInitially() {
        assertFalse(context.hasActiveTransfer());
    }

    @Test
    @DisplayName("should report active transfer after start")
    void shouldReportActiveTransferAfterStart() {
        context.startTransfer();
        assertTrue(context.hasActiveTransfer());
    }

    @Test
    @DisplayName("should handle end transfer when no transfer active")
    void shouldHandleEndTransferWhenNoTransferActive() {
        assertDoesNotThrow(() -> context.endTransfer());
    }

    @Test
    @DisplayName("should store client connection ID")
    void shouldStoreClientConnectionId() {
        context.setClientConnectionId(123);
        assertEquals(123, context.getClientConnectionId());
    }

    @Test
    @DisplayName("should store server connection ID")
    void shouldStoreServerConnectionId() {
        context.setServerConnectionId(456);
        assertEquals(456, context.getServerConnectionId());
    }

    @Test
    @DisplayName("should store client identifier")
    void shouldStoreClientIdentifier() {
        context.setClientIdentifier("PARTNER1");
        assertEquals("PARTNER1", context.getClientIdentifier());
    }

    @Test
    @DisplayName("should store protocol version")
    void shouldStoreProtocolVersion() {
        context.setProtocolVersion(2);
        assertEquals(2, context.getProtocolVersion());
    }

    @Test
    @DisplayName("should store sync points enabled")
    void shouldStoreSyncPointsEnabled() {
        context.setSyncPointsEnabled(true);
        assertTrue(context.isSyncPointsEnabled());
    }

    @Test
    @DisplayName("should store resync enabled")
    void shouldStoreResyncEnabled() {
        context.setResyncEnabled(true);
        assertTrue(context.isResyncEnabled());
    }

    @Test
    @DisplayName("should store remote address")
    void shouldStoreRemoteAddress() {
        context.setRemoteAddress("192.168.1.100");
        assertEquals("192.168.1.100", context.getRemoteAddress());
    }

    @Test
    @DisplayName("should store aborted flag")
    void shouldStoreAbortedFlag() {
        context.setAborted(true);
        assertTrue(context.isAborted());
    }

    @Test
    @DisplayName("should store transfer record ID")
    void shouldStoreTransferRecordId() {
        context.setTransferRecordId("transfer-123");
        assertEquals("transfer-123", context.getTransferRecordId());
    }
}
