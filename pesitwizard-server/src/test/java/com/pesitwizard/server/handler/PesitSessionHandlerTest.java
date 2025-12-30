package com.pesitwizard.server.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.SelectMessageBuilder;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.ValidationResult;
import com.pesitwizard.server.service.FpduResponseBuilder;
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

    @Mock
    private com.pesitwizard.server.service.AuditService auditService;

    @Mock
    private com.pesitwizard.server.cluster.ClusterProvider clusterProvider;

    private PesitSessionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PesitSessionHandler(properties, connectionValidator,
                transferOperationHandler, dataTransferHandler, messageHandler, transferTracker, auditService,
                clusterProvider);
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

    @Test
    @DisplayName("processIncomingFpdu should throw for invalid FPDU data")
    void processIncomingFpduShouldThrowForInvalidData() {
        SessionContext ctx = handler.createSession("192.168.1.100");
        byte[] invalidData = new byte[] { 0x00, 0x01, 0x02 }; // Too short to be valid

        assertThrows(IllegalArgumentException.class,
                () -> handler.processIncomingFpdu(ctx, invalidData, null));
    }

    @Nested
    @DisplayName("CN01 State - CONNECT Handling")
    class CN01StateTests {

        @Test
        @DisplayName("should accept valid CONNECT and transition to CN03")
        void shouldAcceptValidConnect() throws Exception {
            SessionContext ctx = handler.createSession("192.168.1.100");
            assertEquals(ServerState.CN01_REPOS, ctx.getState());

            // Mock successful validations
            when(connectionValidator.validateServerName(any())).thenReturn(ValidationResult.ok());
            when(connectionValidator.validateProtocolVersion(any())).thenReturn(ValidationResult.ok());
            when(connectionValidator.validatePartner(any(), any())).thenReturn(ValidationResult.ok());
            when(properties.getProtocolVersion()).thenReturn(2);
            when(properties.isSyncPointsEnabled()).thenReturn(false);
            when(properties.isResyncEnabled()).thenReturn(false);

            // Build valid CONNECT FPDU
            Fpdu connectFpdu = new ConnectMessageBuilder()
                    .demandeur("TEST_CLIENT")
                    .serveur("TEST_SERVER")
                    .writeAccess()
                    .build(1);
            byte[] rawData = FpduBuilder.buildFpdu(connectFpdu);

            byte[] response = handler.processIncomingFpdu(ctx, rawData, null);

            assertNotNull(response);
            assertEquals(ServerState.CN03_CONNECTED, ctx.getState());
            assertEquals("TEST_CLIENT", ctx.getClientIdentifier());
            assertEquals("TEST_SERVER", ctx.getServerIdentifier());
        }

        @Test
        @DisplayName("should reject CONNECT when server validation fails")
        void shouldRejectConnectWhenServerValidationFails() throws Exception {
            SessionContext ctx = handler.createSession("192.168.1.100");

            when(connectionValidator.validateServerName(any()))
                    .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D1_100, "Unknown server"));

            Fpdu connectFpdu = new ConnectMessageBuilder()
                    .demandeur("CLIENT")
                    .serveur("WRONG_SERVER")
                    .build(1);
            byte[] rawData = FpduBuilder.buildFpdu(connectFpdu);

            byte[] response = handler.processIncomingFpdu(ctx, rawData, null);

            assertNotNull(response);
            assertEquals(ServerState.CN01_REPOS, ctx.getState()); // Should stay in CN01
        }

        @Test
        @DisplayName("should reject CONNECT when partner validation fails")
        void shouldRejectConnectWhenPartnerValidationFails() throws Exception {
            SessionContext ctx = handler.createSession("192.168.1.100");

            when(connectionValidator.validateServerName(any())).thenReturn(ValidationResult.ok());
            when(connectionValidator.validateProtocolVersion(any())).thenReturn(ValidationResult.ok());
            when(connectionValidator.validatePartner(any(), any()))
                    .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D1_100, "Unknown partner"));

            Fpdu connectFpdu = new ConnectMessageBuilder()
                    .demandeur("UNKNOWN_PARTNER")
                    .serveur("TEST_SERVER")
                    .build(1);
            byte[] rawData = FpduBuilder.buildFpdu(connectFpdu);

            byte[] response = handler.processIncomingFpdu(ctx, rawData, null);

            assertNotNull(response);
            assertEquals(ServerState.CN01_REPOS, ctx.getState());
        }
    }

    @Nested
    @DisplayName("CN03 State - Connected")
    class CN03StateTests {

        private SessionContext connectedCtx;

        @BeforeEach
        void setupConnectedState() {
            connectedCtx = handler.createSession("192.168.1.100");
            connectedCtx.transitionTo(ServerState.CN03_CONNECTED);
        }

        @Test
        @DisplayName("should delegate CREATE to TransferOperationHandler")
        void shouldDelegateCreate() throws Exception {
            Fpdu ackCreate = FpduResponseBuilder.buildAckCreate(connectedCtx, 4096);
            when(transferOperationHandler.handleCreate(any(), any())).thenReturn(ackCreate);

            Fpdu createFpdu = new CreateMessageBuilder()
                    .filename("TESTFILE")
                    .transferId(1)
                    .variableFormat()
                    .build(1);
            byte[] rawData = FpduBuilder.buildFpdu(createFpdu);

            byte[] response = handler.processIncomingFpdu(connectedCtx, rawData, null);

            assertNotNull(response);
            verify(transferOperationHandler).handleCreate(eq(connectedCtx), any(Fpdu.class));
        }

        @Test
        @DisplayName("should delegate SELECT to TransferOperationHandler")
        void shouldDelegateSelect() throws Exception {
            connectedCtx.startTransfer().setFilename("TESTFILE");
            Fpdu ackSelect = FpduResponseBuilder.buildAckSelect(connectedCtx, 4096);
            when(transferOperationHandler.handleSelect(any(), any())).thenReturn(ackSelect);

            Fpdu selectFpdu = new SelectMessageBuilder()
                    .filename("TESTFILE")
                    .transferId(1)
                    .build(1);
            byte[] rawData = FpduBuilder.buildFpdu(selectFpdu);

            byte[] response = handler.processIncomingFpdu(connectedCtx, rawData, null);

            assertNotNull(response);
            verify(transferOperationHandler).handleSelect(eq(connectedCtx), any(Fpdu.class));
        }

        @Test
        @DisplayName("should handle RELEASE and return RELCONF")
        void shouldHandleRelease() throws Exception {
            Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                    .withParameter(new com.pesitwizard.fpdu.ParameterValue(
                            com.pesitwizard.fpdu.ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0 }));
            byte[] rawData = FpduBuilder.buildFpdu(releaseFpdu);

            byte[] response = handler.processIncomingFpdu(connectedCtx, rawData, null);

            assertNotNull(response);
            assertEquals(ServerState.CN01_REPOS, connectedCtx.getState());
        }
    }

    @Nested
    @DisplayName("OF02 State - Transfer Ready")
    class OF02StateTests {

        private SessionContext transferReadyCtx;
        private DataOutputStream outputStream;

        @BeforeEach
        void setupTransferReadyState() {
            transferReadyCtx = handler.createSession("192.168.1.100");
            transferReadyCtx.transitionTo(ServerState.OF02_TRANSFER_READY);
            outputStream = new DataOutputStream(new ByteArrayOutputStream());
        }

        @Test
        @DisplayName("should delegate WRITE to DataTransferHandler")
        void shouldDelegateWrite() throws Exception {
            Fpdu ackWrite = FpduResponseBuilder.buildAckWrite(transferReadyCtx, 0);
            when(dataTransferHandler.handleWrite(any(), any())).thenReturn(ackWrite);

            Fpdu writeFpdu = new Fpdu(FpduType.WRITE)
                    .withParameter(new com.pesitwizard.fpdu.ParameterValue(
                            com.pesitwizard.fpdu.ParameterIdentifier.PI_18_POINT_RELANCE, 0));
            byte[] rawData = FpduBuilder.buildFpdu(writeFpdu);

            byte[] response = handler.processIncomingFpdu(transferReadyCtx, rawData, outputStream);

            assertNotNull(response);
            verify(dataTransferHandler).handleWrite(eq(transferReadyCtx), any(Fpdu.class));
        }

        @Test
        @DisplayName("should delegate CLOSE to TransferOperationHandler")
        void shouldDelegateClose() throws Exception {
            Fpdu ackClose = FpduResponseBuilder.buildAckClose(transferReadyCtx);
            when(transferOperationHandler.handleClose(any(), any())).thenReturn(ackClose);

            Fpdu closeFpdu = new Fpdu(FpduType.CLOSE)
                    .withParameter(new com.pesitwizard.fpdu.ParameterValue(
                            com.pesitwizard.fpdu.ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0 }));
            byte[] rawData = FpduBuilder.buildFpdu(closeFpdu);

            byte[] response = handler.processIncomingFpdu(transferReadyCtx, rawData, outputStream);

            assertNotNull(response);
            verify(transferOperationHandler).handleClose(eq(transferReadyCtx), any(Fpdu.class));
        }
    }

    @Nested
    @DisplayName("ABORT Handling")
    class AbortHandlingTests {

        @Test
        @DisplayName("should handle ABORT from any state and mark session aborted")
        void shouldHandleAbortFromAnyState() throws Exception {
            SessionContext ctx = handler.createSession("192.168.1.100");
            ctx.transitionTo(ServerState.CN03_CONNECTED);

            Fpdu abortFpdu = new Fpdu(FpduType.ABORT)
                    .withParameter(new com.pesitwizard.fpdu.ParameterValue(
                            com.pesitwizard.fpdu.ParameterIdentifier.PI_02_DIAG, new byte[] { 0x03, 0x11 }));
            byte[] rawData = FpduBuilder.buildFpdu(abortFpdu);

            byte[] response = handler.processIncomingFpdu(ctx, rawData, null);

            assertNull(response); // No response for ABORT
            assertTrue(ctx.isAborted());
            assertEquals(ServerState.CN01_REPOS, ctx.getState());
        }

        @Test
        @DisplayName("should track transfer failure on ABORT with active transfer")
        void shouldTrackTransferFailureOnAbort() throws Exception {
            SessionContext ctx = handler.createSession("192.168.1.100");
            ctx.transitionTo(ServerState.TDE02B_RECEIVING_DATA);
            ctx.setTransferRecordId("transfer-123");

            Fpdu abortFpdu = new Fpdu(FpduType.ABORT)
                    .withParameter(new com.pesitwizard.fpdu.ParameterValue(
                            com.pesitwizard.fpdu.ParameterIdentifier.PI_02_DIAG, new byte[] { 0x02, 0x05 }));
            byte[] rawData = FpduBuilder.buildFpdu(abortFpdu);

            handler.processIncomingFpdu(ctx, rawData, null);

            verify(transferTracker).trackTransferFailed(eq(ctx), anyString(), anyString());
            assertTrue(ctx.isAborted());
        }
    }
}
