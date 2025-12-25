package com.pesitwizard.server.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.state.ServerState;

@DisplayName("MessageHandler Tests")
class MessageHandlerTest {

    private MessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MessageHandler();
    }

    @Test
    @DisplayName("handleMsg should return ACK for simple message")
    void handleMsgShouldReturnAckForSimpleMessage() {
        SessionContext ctx = new SessionContext("test-session");
        Fpdu fpdu = new Fpdu(FpduType.MSG);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_91_MESSAGE, "Hello".getBytes()));

        Fpdu response = handler.handleMsg(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_MSG, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsg should handle message without PI_91")
    void handleMsgShouldHandleMessageWithoutPi91() {
        SessionContext ctx = new SessionContext("test-session");
        Fpdu fpdu = new Fpdu(FpduType.MSG);

        Fpdu response = handler.handleMsg(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_MSG, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsg should extract filename from PGI_09")
    void handleMsgShouldExtractFilename() {
        SessionContext ctx = new SessionContext("test-session");
        Fpdu fpdu = new Fpdu(FpduType.MSG);

        // Create PGI_09 with PI_12 filename using varargs constructor
        ParameterValue pgi9 = new ParameterValue(ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                new ParameterValue(ParameterIdentifier.PI_12_NOM_FICHIER, "testfile.dat".getBytes()));
        fpdu.withParameter(pgi9);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_91_MESSAGE, "Test message".getBytes()));

        Fpdu response = handler.handleMsg(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_MSG, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsgDm should initialize message buffer and transition state")
    void handleMsgDmShouldInitializeBufferAndTransitionState() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.MSGDM);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_91_MESSAGE, "First segment".getBytes()));

        Fpdu response = handler.handleMsgDm(ctx, fpdu);

        assertNull(response); // No response for MSGDM
        assertEquals(ServerState.MSG_RECEIVING, ctx.getState());
        assertNotNull(ctx.getMessageBuffer());
        assertTrue(ctx.getMessageBuffer().toString().contains("First segment"));
    }

    @Test
    @DisplayName("handleMsgMm should append to message buffer")
    void handleMsgMmShouldAppendToBuffer() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setMessageBuffer(new StringBuilder("First"));

        Fpdu fpdu = new Fpdu(FpduType.MSGMM);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_91_MESSAGE, "Second".getBytes()));

        Fpdu response = handler.handleMsgMm(ctx, fpdu);

        assertNull(response); // No response for MSGMM
        assertEquals("FirstSecond", ctx.getMessageBuffer().toString());
    }

    @Test
    @DisplayName("handleMsgMm should return ABORT if no MSGDM received first")
    void handleMsgMmShouldReturnAbortIfNoMsgDm() {
        SessionContext ctx = new SessionContext("test-session");
        // No message buffer set - simulates MSGMM without MSGDM

        Fpdu fpdu = new Fpdu(FpduType.MSGMM);

        Fpdu response = handler.handleMsgMm(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsgFm should complete message and return ACK")
    void handleMsgFmShouldCompleteMessageAndReturnAck() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.MSG_RECEIVING);
        ctx.setMessageBuffer(new StringBuilder("First part "));
        ctx.setMessageFilename("testfile.dat");

        Fpdu fpdu = new Fpdu(FpduType.MSGFM);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_91_MESSAGE, "Final part".getBytes()));

        Fpdu response = handler.handleMsgFm(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_MSG, response.getFpduType());
        assertEquals(ServerState.CN03_CONNECTED, ctx.getState());
        assertNull(ctx.getMessageBuffer()); // Buffer should be cleared
        assertNull(ctx.getMessageFilename()); // Filename should be cleared
    }

    @Test
    @DisplayName("handleMsgFm should return ABORT if no MSGDM received first")
    void handleMsgFmShouldReturnAbortIfNoMsgDm() {
        SessionContext ctx = new SessionContext("test-session");
        // No message buffer set - simulates MSGFM without MSGDM

        Fpdu fpdu = new Fpdu(FpduType.MSGFM);

        Fpdu response = handler.handleMsgFm(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsgReceiving should dispatch MSGMM correctly")
    void handleMsgReceivingShouldDispatchMsgMm() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setMessageBuffer(new StringBuilder("Initial"));

        Fpdu fpdu = new Fpdu(FpduType.MSGMM);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_91_MESSAGE, "More".getBytes()));

        Fpdu response = handler.handleMsgReceiving(ctx, fpdu);

        assertNull(response);
        assertEquals("InitialMore", ctx.getMessageBuffer().toString());
    }

    @Test
    @DisplayName("handleMsgReceiving should dispatch MSGFM correctly")
    void handleMsgReceivingShouldDispatchMsgFm() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.MSG_RECEIVING);
        ctx.setMessageBuffer(new StringBuilder("Content"));

        Fpdu fpdu = new Fpdu(FpduType.MSGFM);

        Fpdu response = handler.handleMsgReceiving(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_MSG, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsgReceiving should return ABORT for unexpected FPDU type")
    void handleMsgReceivingShouldReturnAbortForUnexpectedType() {
        SessionContext ctx = new SessionContext("test-session");

        Fpdu fpdu = new Fpdu(FpduType.CONNECT);

        Fpdu response = handler.handleMsgReceiving(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsg should handle long message correctly")
    void handleMsgShouldHandleLongMessage() {
        SessionContext ctx = new SessionContext("test-session");
        Fpdu fpdu = new Fpdu(FpduType.MSG);

        // Create message longer than 100 chars to test truncation in logging
        String longMessage = "A".repeat(150);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_91_MESSAGE, longMessage.getBytes()));

        Fpdu response = handler.handleMsg(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_MSG, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsgDm should handle message without PI_91")
    void handleMsgDmShouldHandleMessageWithoutPi91() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.MSGDM);
        // No PI_91 parameter

        Fpdu response = handler.handleMsgDm(ctx, fpdu);

        assertNull(response);
        assertEquals(ServerState.MSG_RECEIVING, ctx.getState());
        assertNotNull(ctx.getMessageBuffer());
        assertEquals(0, ctx.getMessageBuffer().length());
    }

    @Test
    @DisplayName("handleMsgMm should handle message without PI_91")
    void handleMsgMmShouldHandleMessageWithoutPi91() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setMessageBuffer(new StringBuilder("Initial"));

        Fpdu fpdu = new Fpdu(FpduType.MSGMM);
        // No PI_91 parameter

        Fpdu response = handler.handleMsgMm(ctx, fpdu);

        assertNull(response);
        assertEquals("Initial", ctx.getMessageBuffer().toString());
    }

    @Test
    @DisplayName("handleMsgFm should handle message without PI_91")
    void handleMsgFmShouldHandleMessageWithoutPi91() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.MSG_RECEIVING);
        ctx.setMessageBuffer(new StringBuilder("Content"));

        Fpdu fpdu = new Fpdu(FpduType.MSGFM);
        // No PI_91 parameter

        Fpdu response = handler.handleMsgFm(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_MSG, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsgFm should handle long complete message")
    void handleMsgFmShouldHandleLongMessage() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.MSG_RECEIVING);
        ctx.setMessageBuffer(new StringBuilder("A".repeat(150)));
        ctx.setMessageFilename("testfile.dat");

        Fpdu fpdu = new Fpdu(FpduType.MSGFM);

        Fpdu response = handler.handleMsgFm(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_MSG, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsg should handle PGI_09 without PI_12")
    void handleMsgShouldHandlePgi09WithoutPi12() {
        SessionContext ctx = new SessionContext("test-session");
        Fpdu fpdu = new Fpdu(FpduType.MSG);

        // Create PGI_09 with PI_11 but not PI_12
        ParameterValue pgi9 = new ParameterValue(ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                new ParameterValue(ParameterIdentifier.PI_11_TYPE_FICHIER, 0));
        fpdu.withParameter(pgi9);

        Fpdu response = handler.handleMsg(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_MSG, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsgReceiving should return ABORT for unexpected FPDU type")
    void handleMsgReceivingShouldReturnAbortForUnexpectedFpdu() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.MSG_RECEIVING);
        ctx.setMessageBuffer(new StringBuilder());

        Fpdu fpdu = new Fpdu(FpduType.DTF); // Wrong type

        Fpdu response = handler.handleMsgReceiving(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleMsgMm should append segment to buffer")
    void handleMsgMmShouldAppendSegment() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.MSG_RECEIVING);
        ctx.setMessageBuffer(new StringBuilder("Initial"));

        Fpdu fpdu = new Fpdu(FpduType.MSGMM);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_91_MESSAGE, " More content".getBytes()));

        Fpdu response = handler.handleMsgMm(ctx, fpdu);

        // MSGMM returns null (no ack needed for middle segments)
        assertNull(response);
        // Verify buffer was appended
        assertEquals("Initial More content", ctx.getMessageBuffer().toString());
    }

    @Test
    @DisplayName("handleMsgDm should initialize message buffer")
    void handleMsgDmShouldInitializeBuffer() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.MSGDM);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_91_MESSAGE, "Start of message".getBytes()));

        Fpdu response = handler.handleMsgDm(ctx, fpdu);

        // MSGDM returns null (no ack needed until MSGFM)
        assertNull(response);
        assertEquals(ServerState.MSG_RECEIVING, ctx.getState());
        assertNotNull(ctx.getMessageBuffer());
        assertEquals("Start of message", ctx.getMessageBuffer().toString());
    }
}
