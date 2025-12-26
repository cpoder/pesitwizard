package com.pesitwizard.server.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.service.TransferTracker;
import com.pesitwizard.server.state.ServerState;

@ExtendWith(MockitoExtension.class)
@DisplayName("PesitSessionHandler Tests")
class PesitSessionHandlerTest {

    @Mock
    private PesitServerProperties properties;

    @Mock
    private ConnectionValidator connectionValidator;

    @Mock
    private TransferOperationHandler transferOperationHandler;

    @Mock
    private DataTransferHandler dataTransferHandler;

    @Mock
    private MessageHandler messageHandler;

    @Mock
    private TransferTracker transferTracker;

    private PesitSessionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PesitSessionHandler(properties, connectionValidator,
                transferOperationHandler, dataTransferHandler, messageHandler, transferTracker);
        lenient().when(properties.getServerId()).thenReturn("TEST_SERVER");
    }

    @Test
    @DisplayName("createSession should create new session with remote address")
    void createSessionShouldCreateNewSession() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        assertNotNull(ctx);
        assertNotNull(ctx.getSessionId());
        assertEquals("192.168.1.100", ctx.getRemoteAddress());
        assertEquals("TEST_SERVER", ctx.getOurServerId());
        assertEquals(ServerState.CN01_REPOS, ctx.getState());
    }

    @Test
    @DisplayName("createSession with serverId should use provided serverId")
    void createSessionWithServerIdShouldUseProvidedId() {
        SessionContext ctx = handler.createSession("192.168.1.100", "CUSTOM_SERVER");

        assertNotNull(ctx);
        assertEquals("CUSTOM_SERVER", ctx.getOurServerId());
    }

    @Test
    @DisplayName("createSession should generate unique connection ID")
    void createSessionShouldGenerateConnectionId() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        assertTrue(ctx.getServerConnectionId() > 0);
        assertTrue(ctx.getServerConnectionId() <= 256);
    }

    @Test
    @DisplayName("createSession should generate unique session IDs")
    void createSessionShouldGenerateUniqueSessionIds() {
        SessionContext ctx1 = handler.createSession("192.168.1.100");
        SessionContext ctx2 = handler.createSession("192.168.1.101");

        assertNotEquals(ctx1.getSessionId(), ctx2.getSessionId());
    }

    @Test
    @DisplayName("session should start in CN01_REPOS state")
    void sessionShouldStartInReposState() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        assertEquals(ServerState.CN01_REPOS, ctx.getState());
    }

    @Test
    @DisplayName("session should transition through states correctly")
    void sessionShouldTransitionThroughStates() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        assertEquals(ServerState.CN01_REPOS, ctx.getState());

        ctx.transitionTo(ServerState.CN03_CONNECTED);
        assertEquals(ServerState.CN03_CONNECTED, ctx.getState());

        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        assertEquals(ServerState.SF03_FILE_SELECTED, ctx.getState());

        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);
        assertEquals(ServerState.OF02_TRANSFER_READY, ctx.getState());
    }

    @Test
    @DisplayName("session should handle abort flag")
    void sessionShouldHandleAbortFlag() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        assertFalse(ctx.isAborted());

        ctx.setAborted(true);
        assertTrue(ctx.isAborted());
    }

    @Test
    @DisplayName("session should track touch time")
    void sessionShouldTrackTouchTime() throws InterruptedException {
        SessionContext ctx = handler.createSession("192.168.1.100");
        java.time.Instant initialTime = ctx.getLastActivityTime();

        Thread.sleep(10);
        ctx.touch();

        assertTrue(ctx.getLastActivityTime().isAfter(initialTime));
    }

    @Test
    @DisplayName("session should support transfer context lifecycle")
    void sessionShouldSupportTransferContext() {
        SessionContext ctx = handler.createSession("192.168.1.100");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        // Initially no transfer
        assertNull(ctx.getCurrentTransfer());

        // Start transfer
        var transfer = ctx.startTransfer();
        assertNotNull(transfer);
        assertNotNull(ctx.getCurrentTransfer());

        // End transfer
        ctx.endTransfer();
        assertNull(ctx.getCurrentTransfer());
    }

    @Test
    @DisplayName("session should track partner config")
    void sessionShouldTrackPartnerConfig() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        assertNull(ctx.getPartnerConfig());

        com.pesitwizard.server.config.PartnerConfig partner = new com.pesitwizard.server.config.PartnerConfig();
        partner.setId("PARTNER1");
        ctx.setPartnerConfig(partner);

        assertNotNull(ctx.getPartnerConfig());
        assertEquals("PARTNER1", ctx.getPartnerConfig().getId());
    }

    @Test
    @DisplayName("session should track logical file config")
    void sessionShouldTrackLogicalFileConfig() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        assertNull(ctx.getLogicalFileConfig());

        com.pesitwizard.server.config.LogicalFileConfig fileConfig = com.pesitwizard.server.config.LogicalFileConfig
                .builder()
                .id("FILE1")
                .enabled(true)
                .build();
        ctx.setLogicalFileConfig(fileConfig);

        assertNotNull(ctx.getLogicalFileConfig());
        assertEquals("FILE1", ctx.getLogicalFileConfig().getId());
    }

    @Test
    @DisplayName("session should track protocol options")
    void sessionShouldTrackProtocolOptions() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        // Default values
        assertFalse(ctx.isSyncPointsEnabled());
        assertFalse(ctx.isResyncEnabled());
        assertFalse(ctx.isCrcEnabled());

        // Set options
        ctx.setSyncPointsEnabled(true);
        ctx.setResyncEnabled(true);
        ctx.setCrcEnabled(true);

        assertTrue(ctx.isSyncPointsEnabled());
        assertTrue(ctx.isResyncEnabled());
        assertTrue(ctx.isCrcEnabled());
    }

    @Test
    @DisplayName("session should track client and server identifiers")
    void sessionShouldTrackIdentifiers() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        ctx.setClientIdentifier("CLIENT_ID");
        ctx.setServerIdentifier("SERVER_ID");
        ctx.setClientConnectionId(100);

        assertEquals("CLIENT_ID", ctx.getClientIdentifier());
        assertEquals("SERVER_ID", ctx.getServerIdentifier());
        assertEquals(100, ctx.getClientConnectionId());
    }

    @Test
    @DisplayName("session should track protocol version and access type")
    void sessionShouldTrackProtocolVersionAndAccessType() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        ctx.setProtocolVersion(2);
        ctx.setAccessType(1);

        assertEquals(2, ctx.getProtocolVersion());
        assertEquals(1, ctx.getAccessType());
    }

    @Test
    @DisplayName("session should support message buffer for segmented messages")
    void sessionShouldSupportMessageBuffer() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        assertNull(ctx.getMessageBuffer());
        assertNull(ctx.getMessageFilename());

        ctx.setMessageBuffer(new StringBuilder("Test message"));
        ctx.setMessageFilename("test.dat");

        assertNotNull(ctx.getMessageBuffer());
        assertEquals("Test message", ctx.getMessageBuffer().toString());
        assertEquals("test.dat", ctx.getMessageFilename());
    }

    @Test
    @DisplayName("session should track transfer record ID")
    void sessionShouldTrackTransferRecordId() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        assertNull(ctx.getTransferRecordId());
        ctx.setTransferRecordId("transfer-123");
        assertEquals("transfer-123", ctx.getTransferRecordId());
    }
}
