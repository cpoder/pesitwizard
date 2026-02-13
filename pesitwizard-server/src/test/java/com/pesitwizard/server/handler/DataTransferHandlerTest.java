package com.pesitwizard.server.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;
import com.pesitwizard.server.service.FpduValidator;
import com.pesitwizard.server.service.FpduValidator.ValidationResult;
import com.pesitwizard.server.service.TransferTracker;
import com.pesitwizard.server.state.ServerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataTransferHandler Tests")
class DataTransferHandlerTest {

    @Mock private PesitServerProperties properties;

    @Mock private TransferTracker transferTracker;

    @Mock private FpduValidator fpduValidator;

    private DataTransferHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DataTransferHandler(properties, transferTracker, fpduValidator);

        // Default stubs for validator - return OK for all validations
        lenient()
                .when(fpduValidator.validateDtf(any(), any(), any()))
                .thenReturn(ValidationResult.ok());
        lenient()
                .when(fpduValidator.validateMaxEntitySize(any(), any()))
                .thenReturn(ValidationResult.ok());
    }

    @Test
    @DisplayName("handleWrite should transition to receiving state and return ACK")
    void handleWriteShouldTransitionToReceivingState() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.OF02_TRANSFER_READY);
        Fpdu fpdu = new Fpdu(FpduType.WRITE);

        Fpdu response = handler.handleWrite(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_WRITE, response.getFpduType());
        assertEquals(ServerState.TDE02B_RECEIVING_DATA, ctx.getState());
    }

    @Test
    @DisplayName("handleTDE02B should dispatch DTF correctly")
    void handleTDE02BShouldDispatchDtf() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = ctx.startTransfer();
        transfer.setRecordsTransferred(0);
        // Open output stream to a temp file
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test", ".dat");
        transfer.setLocalPath(tempFile);
        transfer.openOutputStream();

        Fpdu fpdu = new Fpdu(FpduType.DTF);
        fpdu.setData(new byte[100]); // DTF with data counts as 1 record

        try {
            Fpdu response = handler.handleTDE02B(ctx, fpdu);

            assertNull(response); // No response for DTF
            assertEquals(1, transfer.getRecordsTransferred());
        } finally {
            transfer.closeOutputStream();
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("handleTDE02B should dispatch DTF_END correctly")
    void handleTDE02BShouldDispatchDtfEnd() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.TDE02B_RECEIVING_DATA);

        Fpdu fpdu = new Fpdu(FpduType.DTF_END);

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNull(response); // No response for DTF_END
        assertEquals(ServerState.TDE07_WRITE_END, ctx.getState());
    }

    @Test
    @DisplayName("handleTDE02B should handle SYN and return ACK_SYN")
    void handleTDE02BShouldHandleSyn() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = ctx.startTransfer();
        transfer.setBytesTransferred(1000);

        Fpdu fpdu = new Fpdu(FpduType.SYN);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_20_NUM_SYNC, 5));

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_SYN, response.getFpduType());
        assertEquals(5, transfer.getCurrentSyncPoint());
        verify(transferTracker).trackSyncPoint(ctx, 1000);
    }

    @Test
    @DisplayName("handleTDE02B should handle IDT and return ACK_IDT")
    void handleTDE02BShouldHandleIdt() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.TDE02B_RECEIVING_DATA);

        Fpdu fpdu = new Fpdu(FpduType.IDT);

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_IDT, response.getFpduType());
        assertEquals(ServerState.OF02_TRANSFER_READY, ctx.getState());
    }

    @Test
    @DisplayName("handleTDE02B should return ABORT for unexpected FPDU")
    void handleTDE02BShouldReturnAbortForUnexpected() throws Exception {
        SessionContext ctx = new SessionContext("test-session");

        Fpdu fpdu = new Fpdu(FpduType.CONNECT);

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleTDL02B should handle TRANS_END and return ACK")
    void handleTDL02BShouldHandleTransEnd() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.TDL02B_SENDING_DATA);
        TransferContext transfer = ctx.startTransfer();
        transfer.setBytesTransferred(2048);
        transfer.setRecordsTransferred(10);

        Fpdu fpdu = new Fpdu(FpduType.TRANS_END);

        Fpdu response = handler.handleTDL02B(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_TRANS_END, response.getFpduType());
        assertEquals(ServerState.OF02_TRANSFER_READY, ctx.getState());
        verify(transferTracker).trackTransferComplete(ctx);
    }

    @Test
    @DisplayName("handleTDL02B should return ABORT for unexpected FPDU")
    void handleTDL02BShouldReturnAbortForUnexpected() {
        SessionContext ctx = new SessionContext("test-session");

        Fpdu fpdu = new Fpdu(FpduType.WRITE);

        Fpdu response = handler.handleTDL02B(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleTDE07 should handle TRANS_END and complete transfer")
    void handleTDE07ShouldHandleTransEnd() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.TDE07_WRITE_END);
        TransferContext transfer = ctx.startTransfer();
        transfer.setBytesTransferred(1024);
        transfer.setRecordsTransferred(5);
        // No data to write - empty transfer

        Fpdu fpdu = new Fpdu(FpduType.TRANS_END);

        Fpdu response = handler.handleTDE07(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_TRANS_END, response.getFpduType());
        assertEquals(ServerState.OF02_TRANSFER_READY, ctx.getState());
        verify(transferTracker).trackTransferComplete(ctx);
    }

    @Test
    @DisplayName("handleTDE07 should return ABORT for non TRANS_END FPDU")
    void handleTDE07ShouldReturnAbortForNonTransEnd() throws Exception {
        SessionContext ctx = new SessionContext("test-session");

        Fpdu fpdu = new Fpdu(FpduType.WRITE);

        Fpdu response = handler.handleTDE07(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleTDE07 should handle null transfer gracefully")
    void handleTDE07ShouldHandleNullTransfer() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.TDE07_WRITE_END);
        // No transfer started

        Fpdu fpdu = new Fpdu(FpduType.TRANS_END);

        Fpdu response = handler.handleTDE07(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_TRANS_END, response.getFpduType());
    }

    @Test
    @DisplayName("handleTDE02B should handle DTFDA correctly")
    void handleTDE02BShouldHandleDtfda() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = ctx.startTransfer();
        transfer.setRecordsTransferred(0);
        // Open output stream to a temp file
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test", ".dat");
        transfer.setLocalPath(tempFile);
        transfer.openOutputStream();

        Fpdu fpdu = new Fpdu(FpduType.DTFDA);
        fpdu.setData(new byte[50]); // DTFDA with data counts as 1 record

        try {
            Fpdu response = handler.handleTDE02B(ctx, fpdu);

            assertNull(response);
            assertEquals(1, transfer.getRecordsTransferred());
        } finally {
            transfer.closeOutputStream();
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("handleTDE02B should handle DTFMA correctly")
    void handleTDE02BShouldHandleDtfma() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = ctx.startTransfer();
        transfer.setRecordsTransferred(0);
        // Open output stream to a temp file
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test", ".dat");
        transfer.setLocalPath(tempFile);
        transfer.openOutputStream();

        Fpdu fpdu = new Fpdu(FpduType.DTFMA);
        fpdu.setData(new byte[50]); // DTFMA with data counts as 1 record

        try {
            Fpdu response = handler.handleTDE02B(ctx, fpdu);

            assertNull(response);
            assertEquals(1, transfer.getRecordsTransferred());
        } finally {
            transfer.closeOutputStream();
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("handleTDE02B should handle DTFFA correctly")
    void handleTDE02BShouldHandleDtffa() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = ctx.startTransfer();
        transfer.setRecordsTransferred(0);
        // Open output stream to a temp file
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test", ".dat");
        transfer.setLocalPath(tempFile);
        transfer.openOutputStream();

        Fpdu fpdu = new Fpdu(FpduType.DTFFA);
        fpdu.setData(new byte[50]); // DTFFA with data counts as 1 record

        try {
            Fpdu response = handler.handleTDE02B(ctx, fpdu);

            assertNull(response);
            assertEquals(1, transfer.getRecordsTransferred());
        } finally {
            transfer.closeOutputStream();
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("handleTDE02B should handle SYN without sync point number")
    void handleTDE02BShouldHandleSynWithoutNumber() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = ctx.startTransfer();
        transfer.setBytesTransferred(500);

        Fpdu fpdu = new Fpdu(FpduType.SYN);
        // No PI_20 parameter

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_SYN, response.getFpduType());
    }

    @Test
    @DisplayName("handleTDL02B should handle null transfer gracefully")
    void handleTDL02BShouldHandleNullTransfer() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.TDL02B_SENDING_DATA);
        // No transfer started

        Fpdu fpdu = new Fpdu(FpduType.TRANS_END);

        Fpdu response = handler.handleTDL02B(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_TRANS_END, response.getFpduType());
    }

    @Test
    @DisplayName("handleDtf should increment record count")
    void handleDtfShouldIncrementRecordCount() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = ctx.startTransfer();
        int initialRecords = transfer.getRecordsTransferred();
        // Open output stream to a temp file
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test", ".dat");
        transfer.setLocalPath(tempFile);
        transfer.openOutputStream();

        Fpdu fpdu = new Fpdu(FpduType.DTF);
        byte[] data = new byte[100];
        fpdu.setData(data);

        try {
            Fpdu response = handler.handleTDE02B(ctx, fpdu);
            assertNull(response);
            assertEquals(initialRecords + 1, transfer.getRecordsTransferred());
        } finally {
            transfer.closeOutputStream();
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("handleRead should return NACK_READ when no transfer context")
    void handleReadShouldReturnAbortWhenNoTransfer() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.OF02_TRANSFER_READY);
        // No transfer started

        Fpdu fpdu = new Fpdu(FpduType.READ);

        Fpdu response = handler.handleRead(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_READ, response.getFpduType()); // NACK is ACK with error diag
    }

    @Test
    @DisplayName("handleRead should return NACK_READ when local path is null")
    void handleReadShouldReturnAbortWhenLocalPathNull() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.OF02_TRANSFER_READY);
        TransferContext transfer = ctx.startTransfer();
        transfer.setLocalPath(null);

        Fpdu fpdu = new Fpdu(FpduType.READ);

        Fpdu response = handler.handleRead(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_READ, response.getFpduType()); // NACK is ACK with error diag
    }

    @Test
    @DisplayName("handleRead should return NACK_READ when file does not exist")
    void handleReadShouldReturnAbortWhenFileNotExists() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.OF02_TRANSFER_READY);
        TransferContext transfer = ctx.startTransfer();
        transfer.setLocalPath(java.nio.file.Path.of("/non/existent/file.txt"));

        Fpdu fpdu = new Fpdu(FpduType.READ);

        Fpdu response = handler.handleRead(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_READ, response.getFpduType()); // NACK is ACK with error diag
    }

    @Test
    @DisplayName("handleRead should stream file and return null on success")
    void handleReadShouldStreamFileOnSuccess() throws Exception {
        // Create temp file
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test", ".dat");
        java.nio.file.Files.writeString(tempFile, "Test content");

        try {
            SessionContext ctx = new SessionContext("test-session");
            ctx.setState(ServerState.OF02_TRANSFER_READY);
            TransferContext transfer = ctx.startTransfer();
            transfer.setLocalPath(tempFile);

            when(properties.getMaxEntitySize()).thenReturn(4096);

            // Set up a channel on the context for streaming
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(baos);
            ctx.setChannel(
                    new com.pesitwizard.channel.TcpPesitChannel(
                            new java.io.DataInputStream(
                                    new java.io.ByteArrayInputStream(new byte[0])),
                            out));

            Fpdu fpdu = new Fpdu(FpduType.READ);

            Fpdu response = handler.handleRead(ctx, fpdu);

            assertNull(response); // Success returns null
            assertEquals(ServerState.TDL02B_SENDING_DATA, ctx.getState());
            assertTrue(transfer.getBytesTransferred() > 0);
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("handleRead should handle restart point")
    void handleReadShouldHandleRestartPoint() throws Exception {
        // Create temp file with content
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test", ".dat");
        java.nio.file.Files.writeString(tempFile, "Test content for restart");

        try {
            SessionContext ctx = new SessionContext("test-session");
            ctx.setState(ServerState.OF02_TRANSFER_READY);
            TransferContext transfer = ctx.startTransfer();
            transfer.setLocalPath(tempFile);

            when(properties.getMaxEntitySize()).thenReturn(4096);

            // Set up a channel on the context for streaming
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(baos);
            ctx.setChannel(
                    new com.pesitwizard.channel.TcpPesitChannel(
                            new java.io.DataInputStream(
                                    new java.io.ByteArrayInputStream(new byte[0])),
                            out));

            // Add restart point parameter
            Fpdu fpdu = new Fpdu(FpduType.READ);
            fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_18_POINT_RELANCE, 5));

            Fpdu response = handler.handleRead(ctx, fpdu);

            assertNull(response);
            assertEquals(5, transfer.getRestartPoint());
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("handleTDE07 should write data to file and track completion")
    void handleTDE07ShouldWriteDataAndTrackCompletion() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("test");
        java.nio.file.Path tempFile = tempDir.resolve("output.dat");

        try {
            SessionContext ctx = new SessionContext("test-session");
            ctx.setState(ServerState.TDE07_WRITE_END);
            TransferContext transfer = ctx.startTransfer();
            transfer.setLocalPath(tempFile);
            transfer.openOutputStream();
            transfer.appendData("Test data".getBytes());
            transfer.closeOutputStream();

            Fpdu fpdu = new Fpdu(FpduType.TRANS_END);

            Fpdu response = handler.handleTDE07(ctx, fpdu);

            assertNotNull(response);
            assertEquals(FpduType.ACK_TRANS_END, response.getFpduType());
            assertEquals(ServerState.OF02_TRANSFER_READY, ctx.getState());
            verify(transferTracker).trackTransferComplete(ctx);
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    @Test
    @DisplayName("handleTDE07 should return ABORT for unexpected FPDU type")
    void handleTDE07ShouldReturnAbortForUnexpectedFpdu() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.TDE07_WRITE_END);
        ctx.startTransfer();

        Fpdu fpdu = new Fpdu(FpduType.DTF); // Wrong type

        Fpdu response = handler.handleTDE07(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    // ===== RESYN Tests =====

    @Test
    @DisplayName("handleTDE02B should dispatch RESYN correctly")
    void handleTDE02BShouldDispatchResyn() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.TDE02B_RECEIVING_DATA);
        ctx.setResyncEnabled(true);
        TransferContext transfer = ctx.startTransfer();

        // Write some data to a temp file
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("resyn-test", ".dat");
        transfer.setLocalPath(tempFile);
        transfer.openOutputStream();
        transfer.appendData(new byte[500]); // 500 bytes

        // Record a sync point at 500 bytes (sync point 1)
        transfer.recordSyncPointPosition(1, 500);
        transfer.setCurrentSyncPoint(1);

        // Write more data
        transfer.appendData(new byte[300]); // total 800 bytes

        try {
            // Client sends RESYN requesting rollback to sync point 1
            Fpdu fpdu = new Fpdu(FpduType.RESYN);
            fpdu.withParameter(
                    new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}));
            fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_18_POINT_RELANCE, 1));

            Fpdu response = handler.handleTDE02B(ctx, fpdu);

            assertNotNull(response);
            assertEquals(FpduType.ACK_RESYN, response.getFpduType());
            // Verify file was truncated to 500 bytes
            assertEquals(500, transfer.getBytesTransferred());
            assertEquals(1, transfer.getCurrentSyncPoint());
        } finally {
            transfer.closeOutputStream();
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("handleTDE02B should reject RESYN when not negotiated")
    void handleTDE02BShouldRejectResynWhenNotNegotiated() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.TDE02B_RECEIVING_DATA);
        ctx.setResyncEnabled(false); // RESYN not negotiated

        Fpdu fpdu = new Fpdu(FpduType.RESYN);
        fpdu.withParameter(
                new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}));
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_18_POINT_RELANCE, 1));

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleTDE02B should reject RESYN for unknown sync point")
    void handleTDE02BShouldRejectResynForUnknownSyncPoint() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.TDE02B_RECEIVING_DATA);
        ctx.setResyncEnabled(true);
        TransferContext transfer = ctx.startTransfer();

        // No sync points recorded

        Fpdu fpdu = new Fpdu(FpduType.RESYN);
        fpdu.withParameter(
                new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}));
        fpdu.withParameter(
                new ParameterValue(
                        ParameterIdentifier.PI_18_POINT_RELANCE, 5)); // Unknown sync point

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("SYN should record sync point position for RESYN rollback")
    void synShouldRecordSyncPointPosition() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = ctx.startTransfer();
        transfer.setBytesTransferred(2048);

        Fpdu fpdu = new Fpdu(FpduType.SYN);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_20_NUM_SYNC, 3));

        handler.handleTDE02B(ctx, fpdu);

        // Verify the sync point byte position was recorded
        assertEquals(2048, transfer.getSyncPointBytePosition(3));
    }

    @Test
    @DisplayName("handleTDE02B should reject RESYN with no transfer context")
    void handleTDE02BShouldRejectResynWithNoTransfer() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setState(ServerState.TDE02B_RECEIVING_DATA);
        ctx.setResyncEnabled(true);
        // No transfer started

        Fpdu fpdu = new Fpdu(FpduType.RESYN);
        fpdu.withParameter(
                new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}));
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_18_POINT_RELANCE, 1));

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }
}
