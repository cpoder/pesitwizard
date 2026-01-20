package com.pesitwizard.server.handler;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.service.FpduResponseBuilder;
import com.pesitwizard.server.state.ServerState;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles MSG (message) FPDUs including single messages and segmented messages.
 */
@Slf4j
@Component
public class MessageHandler {

    /**
     * Handle MSG FPDU - single message (fits in one FPDU)
     */
    public Fpdu handleMsg(SessionContext ctx, Fpdu fpdu) {
        // Extract message from PI_91
        ParameterValue pi91 = fpdu.getParameter(ParameterIdentifier.PI_91_MESSAGE);
        String message = null;
        if (pi91 != null && pi91.getValue() != null) {
            message = new String(pi91.getValue(), StandardCharsets.UTF_8);
        }

        // Extract file identification for logging
        String filename = extractFilename(fpdu);

        log.info("[{}] MSG received: file={}, message length={}",
                ctx.getSessionId(), filename, message != null ? message.length() : 0);

        if (message != null) {
            log.debug("[{}] Message content: {}", ctx.getSessionId(),
                    message.length() > 100 ? message.substring(0, 100) + "..." : message);
        }

        // Message received and logged - extend MessageHandler to add custom processing
        // (e.g., store to database, forward to external system, trigger workflow)

        // Stay in CN03 state after message
        return FpduResponseBuilder.buildAckMsg(ctx, DiagnosticCode.D0_000, null);
    }

    /**
     * Handle MSGDM FPDU - start of segmented message
     * Sets up context to receive MSGMM and MSGFM
     */
    public Fpdu handleMsgDm(SessionContext ctx, Fpdu fpdu) {
        // Extract file identification
        String filename = extractFilename(fpdu);

        // Initialize message buffer in context
        StringBuilder messageBuffer = new StringBuilder();

        // Extract first segment from PI_91
        ParameterValue pi91 = fpdu.getParameter(ParameterIdentifier.PI_91_MESSAGE);
        if (pi91 != null && pi91.getValue() != null) {
            messageBuffer.append(new String(pi91.getValue(), StandardCharsets.UTF_8));
        }

        // Store in context for subsequent segments
        ctx.setMessageBuffer(messageBuffer);
        ctx.setMessageFilename(filename);

        log.info("[{}] MSGDM received: file={}, first segment length={}",
                ctx.getSessionId(), filename, messageBuffer.length());

        // Transition to message receiving state
        ctx.transitionTo(ServerState.MSG_RECEIVING);

        // No response for MSGDM - wait for MSGMM/MSGFM
        return null;
    }

    /**
     * Handle MSGMM FPDU - middle segment of message
     */
    public Fpdu handleMsgMm(SessionContext ctx, Fpdu fpdu) {
        StringBuilder messageBuffer = ctx.getMessageBuffer();
        if (messageBuffer == null) {
            log.warn("[{}] MSGMM received without MSGDM", ctx.getSessionId());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
        }

        // Append segment from PI_91
        ParameterValue pi91 = fpdu.getParameter(ParameterIdentifier.PI_91_MESSAGE);
        if (pi91 != null && pi91.getValue() != null) {
            messageBuffer.append(new String(pi91.getValue(), StandardCharsets.UTF_8));
        }

        log.debug("[{}] MSGMM received: total length now={}", ctx.getSessionId(), messageBuffer.length());

        // No response for MSGMM - wait for more segments or MSGFM
        return null;
    }

    /**
     * Handle MSGFM FPDU - end of segmented message
     */
    public Fpdu handleMsgFm(SessionContext ctx, Fpdu fpdu) {
        StringBuilder messageBuffer = ctx.getMessageBuffer();
        if (messageBuffer == null) {
            log.warn("[{}] MSGFM received without MSGDM", ctx.getSessionId());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
        }

        // Append final segment from PI_91
        ParameterValue pi91 = fpdu.getParameter(ParameterIdentifier.PI_91_MESSAGE);
        if (pi91 != null && pi91.getValue() != null) {
            messageBuffer.append(new String(pi91.getValue(), StandardCharsets.UTF_8));
        }

        String fullMessage = messageBuffer.toString();
        String filename = ctx.getMessageFilename();

        log.info("[{}] MSGFM received: file={}, total message length={}",
                ctx.getSessionId(), filename, fullMessage.length());

        // Complete message received - extend MessageHandler to add custom processing
        log.debug("[{}] Complete message: {}", ctx.getSessionId(),
                fullMessage.length() > 100 ? fullMessage.substring(0, 100) + "..." : fullMessage);

        // Clear message buffer
        ctx.setMessageBuffer(null);
        ctx.setMessageFilename(null);

        // Return to connected state
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        return FpduResponseBuilder.buildAckMsg(ctx, DiagnosticCode.D0_000, null);
    }

    /**
     * MSG_RECEIVING state - waiting for MSGMM or MSGFM
     */
    public Fpdu handleMsgReceiving(SessionContext ctx, Fpdu fpdu) {
        return switch (fpdu.getFpduType()) {
            case MSGMM -> handleMsgMm(ctx, fpdu);
            case MSGFM -> handleMsgFm(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} while receiving message", ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * Extract filename from FPDU
     */
    private String extractFilename(Fpdu fpdu) {
        ParameterValue pgi9 = fpdu.getParameter(ParameterGroupIdentifier.PGI_09_ID_FICHIER);
        if (pgi9 != null) {
            ParameterValue pi12 = pgi9.getParameter(ParameterIdentifier.PI_12_NOM_FICHIER);
            if (pi12 != null && pi12.getValue() != null) {
                return new String(pi12.getValue(), StandardCharsets.UTF_8).trim();
            }
        }
        return "unknown";
    }
}
