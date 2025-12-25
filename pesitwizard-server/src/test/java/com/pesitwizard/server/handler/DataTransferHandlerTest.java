package com.pesitwizard.server.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;
import com.pesitwizard.server.service.TransferTracker;
import com.pesitwizard.server.state.ServerState;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataTransferHandler Tests")
class DataTransferHandlerTest {

    @Mock
    private PesitServerProperties properties;

    @Mock
    private TransferTracker transferTracker;

    private DataTransferHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DataTransferHandler(properties, transferTracker);
    }

    @Test
    @DisplayName("handleWrite should transition to receiving state and return ACK")
    void handleWriteShouldTransitionToReceivingState() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);
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

        Fpdu fpdu = new Fpdu(FpduType.DTF);

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNull(response); // No response for DTF
        assertEquals(1, transfer.getRecordsTransferred());
    }

    @Test
    @DisplayName("handleTDE02B should dispatch DTF_END correctly")
    void handleTDE02BShouldDispatchDtfEnd() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.TDE02B_RECEIVING_DATA);

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
        ctx.transitionTo(ServerState.TDE02B_RECEIVING_DATA);

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
        ctx.transitionTo(ServerState.TDL02B_SENDING_DATA);
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
        ctx.transitionTo(ServerState.TDE07_WRITE_END);
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
        ctx.transitionTo(ServerState.TDE07_WRITE_END);
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

        Fpdu fpdu = new Fpdu(FpduType.DTFDA);

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNull(response);
        assertEquals(1, transfer.getRecordsTransferred());
    }

    @Test
    @DisplayName("handleTDE02B should handle DTFMA correctly")
    void handleTDE02BShouldHandleDtfma() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = ctx.startTransfer();
        transfer.setRecordsTransferred(0);

        Fpdu fpdu = new Fpdu(FpduType.DTFMA);

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNull(response);
        assertEquals(1, transfer.getRecordsTransferred());
    }

    @Test
    @DisplayName("handleTDE02B should handle DTFFA correctly")
    void handleTDE02BShouldHandleDtffa() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = ctx.startTransfer();
        transfer.setRecordsTransferred(0);

        Fpdu fpdu = new Fpdu(FpduType.DTFFA);

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNull(response);
        assertEquals(1, transfer.getRecordsTransferred());
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
        ctx.transitionTo(ServerState.TDL02B_SENDING_DATA);
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
        transfer.setRecordsTransferred(5);

        Fpdu fpdu = new Fpdu(FpduType.DTF);
        byte[] data = new byte[100];
        fpdu.setData(data);

        Fpdu response = handler.handleTDE02B(ctx, fpdu);

        assertNull(response);
        assertEquals(6, transfer.getRecordsTransferred());
    }
}
