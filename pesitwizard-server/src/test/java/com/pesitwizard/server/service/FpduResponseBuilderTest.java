package com.pesitwizard.server.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;

@DisplayName("FpduResponseBuilder Tests")
class FpduResponseBuilderTest {

    private SessionContext sessionContext;

    @BeforeEach
    void setUp() {
        sessionContext = new SessionContext("session-123");
        sessionContext.setClientConnectionId(1);
        sessionContext.setServerConnectionId(2);
    }

    @Nested
    @DisplayName("Connection Responses")
    class ConnectionResponseTests {

        @Test
        @DisplayName("should build ACONNECT response")
        void shouldBuildAconnect() {
            Fpdu response = FpduResponseBuilder.buildAconnect(sessionContext, 2, true, true, 4096, 32);

            assertEquals(FpduType.ACONNECT, response.getFpduType());
            assertEquals(1, response.getIdDst());
            assertEquals(2, response.getIdSrc());
            // Verify PI 06 (protocol version) - ACONNECT doesn't have PI_25
            assertNotNull(response.getParameter(ParameterIdentifier.PI_06_VERSION));
        }

        @Test
        @DisplayName("should build ACONNECT without options")
        void shouldBuildAconnectWithoutOptions() {
            Fpdu response = FpduResponseBuilder.buildAconnect(sessionContext, 2, false, false, 4096, 32);

            assertEquals(FpduType.ACONNECT, response.getFpduType());
        }

        @Test
        @DisplayName("should build RCONNECT response")
        void shouldBuildRconnect() {
            Fpdu response = FpduResponseBuilder.buildRconnect(sessionContext,
                    DiagnosticCode.D3_301, "Authentication failed");

            assertEquals(FpduType.RCONNECT, response.getFpduType());
            assertEquals(1, response.getIdDst());
        }

        @Test
        @DisplayName("should build RCONNECT without message")
        void shouldBuildRconnectWithoutMessage() {
            Fpdu response = FpduResponseBuilder.buildRconnect(sessionContext,
                    DiagnosticCode.D3_301, null);

            assertEquals(FpduType.RCONNECT, response.getFpduType());
        }

        @Test
        @DisplayName("should build RELCONF response")
        void shouldBuildRelconf() {
            Fpdu response = FpduResponseBuilder.buildRelconf(sessionContext);

            assertEquals(FpduType.RELCONF, response.getFpduType());
            assertEquals(1, response.getIdDst());
            assertEquals(2, response.getIdSrc());
        }
    }

    @Nested
    @DisplayName("File Operation Responses")
    class FileOperationTests {

        @Test
        @DisplayName("should build ACK_CREATE response")
        void shouldBuildAckCreate() {
            Fpdu response = FpduResponseBuilder.buildAckCreate(sessionContext, 4096);

            assertEquals(FpduType.ACK_CREATE, response.getFpduType());
            assertEquals(1, response.getIdDst());
        }

        @Test
        @DisplayName("should build NACK_CREATE response")
        void shouldBuildNackCreate() {
            Fpdu response = FpduResponseBuilder.buildNackCreate(sessionContext,
                    DiagnosticCode.D2_205, "File not found");

            assertEquals(FpduType.ACK_CREATE, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_SELECT response")
        void shouldBuildAckSelect() {
            TransferContext transfer = new TransferContext();
            transfer.setFilename("test.dat");
            transfer.setTransferId(1);
            sessionContext.setCurrentTransfer(transfer);

            Fpdu response = FpduResponseBuilder.buildAckSelect(sessionContext, 4096);

            assertEquals(FpduType.ACK_SELECT, response.getFpduType());
        }

        @Test
        @DisplayName("should build NACK_SELECT response")
        void shouldBuildNackSelect() {
            Fpdu response = FpduResponseBuilder.buildNackSelect(sessionContext,
                    DiagnosticCode.D2_205, "File not found");

            assertEquals(FpduType.ACK_SELECT, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_OPEN response")
        void shouldBuildAckOpen() {
            Fpdu response = FpduResponseBuilder.buildAckOpen(sessionContext);

            assertEquals(FpduType.ACK_OPEN, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_CLOSE response")
        void shouldBuildAckClose() {
            Fpdu response = FpduResponseBuilder.buildAckClose(sessionContext);

            assertEquals(FpduType.ACK_CLOSE, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_DESELECT response")
        void shouldBuildAckDeselect() {
            Fpdu response = FpduResponseBuilder.buildAckDeselect(sessionContext);

            assertEquals(FpduType.ACK_DESELECT, response.getFpduType());
        }
    }

    @Nested
    @DisplayName("Transfer Responses")
    class TransferTests {

        @Test
        @DisplayName("should build ACK_WRITE response")
        void shouldBuildAckWrite() {
            Fpdu response = FpduResponseBuilder.buildAckWrite(sessionContext, 1024);

            assertEquals(FpduType.ACK_WRITE, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_READ response")
        void shouldBuildAckRead() {
            Fpdu response = FpduResponseBuilder.buildAckRead(sessionContext);

            assertEquals(FpduType.ACK_READ, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_TRANS_END response")
        void shouldBuildAckTransEnd() {
            Fpdu response = FpduResponseBuilder.buildAckTransEnd(sessionContext, 2048, 10);

            assertEquals(FpduType.ACK_TRANS_END, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_TRANS_END without counts")
        void shouldBuildAckTransEndWithoutCounts() {
            Fpdu response = FpduResponseBuilder.buildAckTransEnd(sessionContext, 0, 0);

            assertEquals(FpduType.ACK_TRANS_END, response.getFpduType());
        }

        @Test
        @DisplayName("should build DTF response")
        void shouldBuildDtf() {
            byte[] data = new byte[] { 1, 2, 3, 4, 5 };
            Fpdu response = FpduResponseBuilder.buildDtf(sessionContext, data);

            assertEquals(FpduType.DTF, response.getFpduType());
            assertArrayEquals(data, response.getData());
        }

        @Test
        @DisplayName("should build DTF_END response")
        void shouldBuildDtfEnd() {
            Fpdu response = FpduResponseBuilder.buildDtfEnd(sessionContext);

            assertEquals(FpduType.DTF_END, response.getFpduType());
        }

        @Test
        @DisplayName("should build TRANS_END response")
        void shouldBuildTransEnd() {
            Fpdu response = FpduResponseBuilder.buildTransEnd(sessionContext, 4096, 20);

            assertEquals(FpduType.TRANS_END, response.getFpduType());
        }
    }

    @Nested
    @DisplayName("Sync Point Responses")
    class SyncPointTests {

        @Test
        @DisplayName("should build ACK_SYN response")
        void shouldBuildAckSyn() {
            Fpdu response = FpduResponseBuilder.buildAckSyn(sessionContext, 5);

            assertEquals(FpduType.ACK_SYN, response.getFpduType());
        }
    }

    @Nested
    @DisplayName("Message Responses")
    class MessageTests {

        @Test
        @DisplayName("should build ACK_MSG response")
        void shouldBuildAckMsg() {
            Fpdu response = FpduResponseBuilder.buildAckMsg(sessionContext, "Response message");

            assertEquals(FpduType.ACK_MSG, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_MSG without message")
        void shouldBuildAckMsgWithoutMessage() {
            Fpdu response = FpduResponseBuilder.buildAckMsg(sessionContext, null);

            assertEquals(FpduType.ACK_MSG, response.getFpduType());
        }

        @Test
        @DisplayName("should build NACK_MSG response")
        void shouldBuildNackMsg() {
            Fpdu response = FpduResponseBuilder.buildNackMsg(sessionContext,
                    DiagnosticCode.D2_299, "Error message");

            assertEquals(FpduType.ACK_MSG, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_IDT response")
        void shouldBuildAckIdt() {
            Fpdu response = FpduResponseBuilder.buildAckIdt(sessionContext);

            assertEquals(FpduType.ACK_IDT, response.getFpduType());
        }
    }

    @Nested
    @DisplayName("Abort Responses")
    class AbortTests {

        @Test
        @DisplayName("should build ABORT response")
        void shouldBuildAbort() {
            Fpdu response = FpduResponseBuilder.buildAbort(sessionContext, DiagnosticCode.D1_100);

            assertEquals(FpduType.ABORT, response.getFpduType());
            assertEquals(1, response.getIdDst());
            assertEquals(2, response.getIdSrc());
        }

        @Test
        @DisplayName("should build ABORT response with message")
        void shouldBuildAbortWithMessage() {
            Fpdu response = FpduResponseBuilder.buildAbort(sessionContext,
                    DiagnosticCode.D1_100, "Fatal error");

            assertEquals(FpduType.ABORT, response.getFpduType());
        }

        @Test
        @DisplayName("should build ABORT without message")
        void shouldBuildAbortWithNullMessage() {
            Fpdu response = FpduResponseBuilder.buildAbort(sessionContext,
                    DiagnosticCode.D1_100, null);

            assertEquals(FpduType.ABORT, response.getFpduType());
        }

        @Test
        @DisplayName("should build ABORT with empty message")
        void shouldBuildAbortWithEmptyMessage() {
            Fpdu response = FpduResponseBuilder.buildAbort(sessionContext,
                    DiagnosticCode.D1_100, "");

            assertEquals(FpduType.ABORT, response.getFpduType());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should build RCONNECT with empty message")
        void shouldBuildRconnectWithEmptyMessage() {
            Fpdu response = FpduResponseBuilder.buildRconnect(sessionContext,
                    DiagnosticCode.D3_301, "");

            assertEquals(FpduType.RCONNECT, response.getFpduType());
        }

        @Test
        @DisplayName("should build NACK_CREATE with empty message")
        void shouldBuildNackCreateWithEmptyMessage() {
            Fpdu response = FpduResponseBuilder.buildNackCreate(sessionContext,
                    DiagnosticCode.D2_205, "");

            assertEquals(FpduType.ACK_CREATE, response.getFpduType());
        }

        @Test
        @DisplayName("should build NACK_SELECT with empty message")
        void shouldBuildNackSelectWithEmptyMessage() {
            Fpdu response = FpduResponseBuilder.buildNackSelect(sessionContext,
                    DiagnosticCode.D2_205, "");

            assertEquals(FpduType.ACK_SELECT, response.getFpduType());
        }

        @Test
        @DisplayName("should build NACK_SELECT with null message")
        void shouldBuildNackSelectWithNullMessage() {
            Fpdu response = FpduResponseBuilder.buildNackSelect(sessionContext,
                    DiagnosticCode.D2_205, null);

            assertEquals(FpduType.ACK_SELECT, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_MSG with empty message")
        void shouldBuildAckMsgWithEmptyMessage() {
            Fpdu response = FpduResponseBuilder.buildAckMsg(sessionContext, "");

            assertEquals(FpduType.ACK_MSG, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_SELECT with null transfer context")
        void shouldBuildAckSelectWithNullTransfer() {
            sessionContext.setCurrentTransfer(null);

            Fpdu response = FpduResponseBuilder.buildAckSelect(sessionContext, 4096);

            assertEquals(FpduType.ACK_SELECT, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_SELECT with transfer but null local path")
        void shouldBuildAckSelectWithNullLocalPath() {
            TransferContext transfer = new TransferContext();
            transfer.setFilename("test.dat");
            transfer.setTransferId(1);
            transfer.setLocalPath(null);
            sessionContext.setCurrentTransfer(transfer);

            Fpdu response = FpduResponseBuilder.buildAckSelect(sessionContext, 4096);

            assertEquals(FpduType.ACK_SELECT, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_TRANS_END with byte count and record count")
        void shouldBuildAckTransEnd() {
            Fpdu response = FpduResponseBuilder.buildAckTransEnd(sessionContext, 8192, 50);

            assertEquals(FpduType.ACK_TRANS_END, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_WRITE")
        void shouldBuildAckWrite() {
            Fpdu response = FpduResponseBuilder.buildAckWrite(sessionContext, 0);

            assertEquals(FpduType.ACK_WRITE, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_READ")
        void shouldBuildAckRead() {
            Fpdu response = FpduResponseBuilder.buildAckRead(sessionContext);

            assertEquals(FpduType.ACK_READ, response.getFpduType());
        }

        @Test
        @DisplayName("should build RELCONF")
        void shouldBuildRelconf() {
            Fpdu response = FpduResponseBuilder.buildRelconf(sessionContext);

            assertEquals(FpduType.RELCONF, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_CLOSE")
        void shouldBuildAckClose() {
            Fpdu response = FpduResponseBuilder.buildAckClose(sessionContext);

            assertEquals(FpduType.ACK_CLOSE, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_DESELECT")
        void shouldBuildAckDeselect() {
            Fpdu response = FpduResponseBuilder.buildAckDeselect(sessionContext);

            assertEquals(FpduType.ACK_DESELECT, response.getFpduType());
        }

        @Test
        @DisplayName("should build ACK_TRANS_END without counts when zero")
        void shouldBuildAckTransEndWithoutCounts() {
            Fpdu response = FpduResponseBuilder.buildAckTransEnd(sessionContext, 0, 0);

            assertEquals(FpduType.ACK_TRANS_END, response.getFpduType());
        }

        @Test
        @DisplayName("should build DTF response")
        void shouldBuildDtf() {
            byte[] data = "test data".getBytes();
            Fpdu response = FpduResponseBuilder.buildDtf(sessionContext, data);

            assertEquals(FpduType.DTF, response.getFpduType());
        }

        @Test
        @DisplayName("should build DTF_END response")
        void shouldBuildDtfEnd() {
            Fpdu response = FpduResponseBuilder.buildDtfEnd(sessionContext);

            assertEquals(FpduType.DTF_END, response.getFpduType());
        }
    }
}
