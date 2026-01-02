package com.pesitwizard.server.handler;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduIO;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.cluster.ClusterProvider;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;
import com.pesitwizard.server.model.ValidationResult;
import com.pesitwizard.server.service.AuditService;
import com.pesitwizard.server.service.FpduResponseBuilder;
import com.pesitwizard.server.service.FpduValidator;
import com.pesitwizard.server.service.TransferTracker;
import com.pesitwizard.server.state.ServerState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles PeSIT protocol session for a single client connection.
 * Implements the server-side state machine for Hors-SIT profile.
 * 
 * This is a refactored version that delegates to specialized handlers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PesitSessionHandler {

    private final PesitServerProperties properties;
    private final ConnectionValidator connectionValidator;
    private final TransferOperationHandler transferOperationHandler;
    private final DataTransferHandler dataTransferHandler;
    private final MessageHandler messageHandler;
    private final TransferTracker transferTracker;
    private final AuditService auditService;
    private final ClusterProvider clusterProvider;
    private final FpduValidator fpduValidator;

    /**
     * Create a new session context
     */
    public SessionContext createSession(String remoteAddress) {
        return createSession(remoteAddress, properties.getServerId());
    }

    /**
     * Create a new session context with a specific server ID
     */
    public SessionContext createSession(String remoteAddress, String serverId) {
        SessionContext ctx = new SessionContext(UUID.randomUUID().toString());
        ctx.setRemoteAddress(remoteAddress);
        ctx.setServerConnectionId(generateConnectionId());
        ctx.setOurServerId(serverId);
        log.info("[{}] New session created from {} (server: {})", ctx.getSessionId(), remoteAddress, serverId);
        return ctx;
    }

    /**
     * Process an incoming FPDU and return the response
     */
    public byte[] processIncomingFpdu(SessionContext ctx, byte[] rawData, DataOutputStream out)
            throws IOException {
        ctx.touch();

        // Log raw data for debugging
        int[] phaseType = FpduIO.getPhaseAndType(rawData);
        if (phaseType != null) {
            log.debug("[{}] Raw FPDU: length={}, phase=0x{}, type=0x{}",
                    ctx.getSessionId(), rawData.length,
                    String.format("%02X", phaseType[0]), String.format("%02X", phaseType[1]));
        }

        // Check for DTF - needs special handling before parsing
        if (FpduIO.isDtf(rawData) && ctx.getState() == ServerState.TDE02B_RECEIVING_DATA) {
            byte[] data = FpduIO.extractDtfData(rawData);
            if (data.length > 0) {
                TransferContext transfer = ctx.getCurrentTransfer();
                if (transfer != null) {
                    try {
                        transfer.appendData(data);
                        log.info("[{}] DTF: received {} bytes, total: {} bytes",
                                ctx.getSessionId(), data.length, transfer.getBytesTransferred());
                    } catch (java.io.IOException e) {
                        log.error("[{}] DTF: error writing data: {}", ctx.getSessionId(), e.getMessage());
                        throw new RuntimeException("Failed to write transfer data", e);
                    }
                }
            }
            return null; // No response for DTF
        }

        // Parse FPDU
        FpduParser parser = new FpduParser(rawData);
        Fpdu fpdu = parser.parse();

        log.info("[{}] Received {} in state {}", ctx.getSessionId(), fpdu.getFpduType(), ctx.getState());

        // Process based on current state and FPDU type
        Fpdu response = processStateMachine(ctx, fpdu, out);

        if (response != null) {
            log.info("[{}] Sending {} -> state {}", ctx.getSessionId(), response.getFpduType(), ctx.getState());
            return FpduBuilder.buildFpdu(response);
        }

        return null;
    }

    /**
     * Main state machine processing
     */
    private Fpdu processStateMachine(SessionContext ctx, Fpdu fpdu, DataOutputStream out) throws IOException {
        FpduType type = fpdu.getFpduType();

        // Handle ABORT from any state
        if (type == FpduType.ABORT) {
            return handleAbort(ctx, fpdu);
        }

        return switch (ctx.getState()) {
            case CN01_REPOS -> handleCN01(ctx, fpdu);
            case CN03_CONNECTED -> handleCN03(ctx, fpdu);
            case MSG_RECEIVING -> messageHandler.handleMsgReceiving(ctx, fpdu);
            case SF03_FILE_SELECTED -> handleSF03(ctx, fpdu);
            case OF02_TRANSFER_READY -> handleOF02(ctx, fpdu, out);
            case TDE02B_RECEIVING_DATA -> dataTransferHandler.handleTDE02B(ctx, fpdu);
            case TDE07_WRITE_END -> dataTransferHandler.handleTDE07(ctx, fpdu);
            case TDL02B_SENDING_DATA -> dataTransferHandler.handleTDL02B(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in state {}", ctx.getSessionId(), type, ctx.getState());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * CN01 - REPOS: Waiting for CONNECT
     */
    private Fpdu handleCN01(SessionContext ctx, Fpdu fpdu) {
        if (fpdu.getFpduType() != FpduType.CONNECT) {
            log.warn("[{}] Expected CONNECT, got {}", ctx.getSessionId(), fpdu.getFpduType());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
        }

        // D3-304: Validate PI order in CONNECT
        FpduValidator.ValidationResult piOrderValidation = fpduValidator.validatePiOrder(fpdu);
        if (!piOrderValidation.valid()) {
            log.warn("[{}] PI order validation failed: {}", ctx.getSessionId(), piOrderValidation.message());
            return FpduResponseBuilder.buildAbort(ctx, piOrderValidation.errorCode(), piOrderValidation.message());
        }

        // Extract connection parameters
        extractConnectionParameters(ctx, fpdu);

        log.info("[{}] CONNECT from client '{}' to server '{}', version={}, accessType={}",
                ctx.getSessionId(), ctx.getClientIdentifier(), ctx.getServerIdentifier(),
                ctx.getProtocolVersion(), ctx.getAccessType());

        // Validate server name
        ValidationResult serverValidation = connectionValidator.validateServerName(ctx);
        if (!serverValidation.isValid()) {
            log.warn("[{}] Server validation failed: {}", ctx.getSessionId(), serverValidation.getMessage());
            return FpduResponseBuilder.buildRconnect(ctx, serverValidation.getDiagCode(),
                    serverValidation.getMessage());
        }

        // Validate protocol version
        ValidationResult versionValidation = connectionValidator.validateProtocolVersion(ctx);
        if (!versionValidation.isValid()) {
            log.warn("[{}] Version validation failed: {}", ctx.getSessionId(), versionValidation.getMessage());
            return FpduResponseBuilder.buildRconnect(ctx, versionValidation.getDiagCode(),
                    versionValidation.getMessage());
        }

        // Validate partner
        ValidationResult validation = connectionValidator.validatePartner(ctx, fpdu);
        if (!validation.isValid()) {
            log.warn("[{}] Partner validation failed: {}", ctx.getSessionId(), validation.getMessage());
            // Log authentication failure to audit
            auditService.logAuthFailure(
                    ctx.getClientIdentifier(),
                    "PESIT",
                    ctx.getRemoteAddress(),
                    validation.getMessage());
            // Track as failed transfer so it appears in transfer history
            // Format DiagnosticCode as hex: (code << 16) | reason
            int diagCodeValue = (validation.getDiagCode().getCode() << 16) | validation.getDiagCode().getReason();
            transferTracker.trackAuthenticationFailure(
                    ctx,
                    properties.getServerId(),
                    clusterProvider.getNodeName(),
                    String.format("0x%06X", diagCodeValue),
                    validation.getMessage());
            return FpduResponseBuilder.buildRconnect(ctx, validation.getDiagCode(), validation.getMessage());
        }

        // Transition to CONNECTED state
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        return FpduResponseBuilder.buildAconnect(ctx,
                properties.getProtocolVersion(),
                properties.isSyncPointsEnabled() && ctx.isSyncPointsEnabled(),
                properties.isResyncEnabled() && ctx.isResyncEnabled(),
                properties.getMaxEntitySize(),
                properties.getSyncIntervalKb());
    }

    /**
     * Extract connection parameters from CONNECT FPDU
     */
    private void extractConnectionParameters(SessionContext ctx, Fpdu fpdu) {
        ctx.setClientConnectionId(fpdu.getIdSrc());

        // PI 3 (Demandeur)
        ParameterValue pi3 = fpdu.getParameter(ParameterIdentifier.PI_03_DEMANDEUR);
        if (pi3 != null) {
            ctx.setClientIdentifier(new String(pi3.getValue(), StandardCharsets.ISO_8859_1).trim());
        }

        // PI 4 (Serveur)
        ParameterValue pi4 = fpdu.getParameter(ParameterIdentifier.PI_04_SERVEUR);
        if (pi4 != null) {
            ctx.setServerIdentifier(new String(pi4.getValue(), StandardCharsets.ISO_8859_1).trim());
        }

        // PI 6 (Version)
        ParameterValue pi6 = fpdu.getParameter(ParameterIdentifier.PI_06_VERSION);
        if (pi6 != null && pi6.getValue().length > 0) {
            byte[] versionBytes = pi6.getValue();
            int version = 0;
            for (byte b : versionBytes) {
                version = (version << 8) | (b & 0xFF);
            }
            ctx.setProtocolVersion(version);
        }

        // PI 22 (Access Type)
        ParameterValue pi22 = fpdu.getParameter(ParameterIdentifier.PI_22_TYPE_ACCES);
        if (pi22 != null && pi22.getValue().length >= 1) {
            ctx.setAccessType(pi22.getValue()[0] & 0xFF);
        }

        // PI 7 (Sync Points) - format: [interval_high, interval_low, window]
        ParameterValue pi7 = fpdu.getParameter(ParameterIdentifier.PI_07_SYNC_POINTS);
        if (pi7 != null && pi7.getValue() != null && pi7.getValue().length >= 2) {
            byte[] syncBytes = pi7.getValue();
            int intervalKb = ((syncBytes[0] & 0xFF) << 8) | (syncBytes[1] & 0xFF);
            ctx.setSyncPointsEnabled(true);
            ctx.setClientSyncIntervalKb(intervalKb);
            log.info("[{}] Client declared sync interval: {} KB", ctx.getSessionId(), intervalKb);
        } else {
            ctx.setSyncPointsEnabled(fpdu.hasParameter(ParameterIdentifier.PI_07_SYNC_POINTS));
            ctx.setClientSyncIntervalKb(0); // No sync interval declared
        }

        // PI 23 (Resync)
        ctx.setResyncEnabled(fpdu.hasParameter(ParameterIdentifier.PI_23_RESYNC));

        // PI 1 (CRC)
        ctx.setCrcEnabled(fpdu.hasParameter(ParameterIdentifier.PI_01_CRC));
    }

    /**
     * CN03 - CONNECTED: Waiting for CREATE, SELECT, MSG, or RELEASE
     */
    private Fpdu handleCN03(SessionContext ctx, Fpdu fpdu) throws IOException {
        return switch (fpdu.getFpduType()) {
            case CREATE -> transferOperationHandler.handleCreate(ctx, fpdu);
            case SELECT -> transferOperationHandler.handleSelect(ctx, fpdu);
            case MSG -> messageHandler.handleMsg(ctx, fpdu);
            case MSGDM -> messageHandler.handleMsgDm(ctx, fpdu);
            case RELEASE -> handleRelease(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in CN03", ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * SF03 - FILE SELECTED: Waiting for OPEN or DESELECT
     */
    private Fpdu handleSF03(SessionContext ctx, Fpdu fpdu) throws IOException {
        return switch (fpdu.getFpduType()) {
            case OPEN -> transferOperationHandler.handleOpen(ctx, fpdu);
            case DESELECT -> transferOperationHandler.handleDeselect(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in SF03", ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * OF02 - TRANSFER READY: Waiting for WRITE, READ, or CLOSE
     */
    private Fpdu handleOF02(SessionContext ctx, Fpdu fpdu, DataOutputStream out) throws IOException {
        return switch (fpdu.getFpduType()) {
            case WRITE -> dataTransferHandler.handleWrite(ctx, fpdu);
            case READ -> dataTransferHandler.handleRead(ctx, fpdu, out);
            case CLOSE -> transferOperationHandler.handleClose(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in OF02", ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * Handle RELEASE FPDU
     */
    private Fpdu handleRelease(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] RELEASE received, closing session", ctx.getSessionId());
        ctx.transitionTo(ServerState.CN01_REPOS);
        return FpduResponseBuilder.buildRelconf(ctx);
    }

    /**
     * Handle ABORT FPDU
     */
    private Fpdu handleAbort(SessionContext ctx, Fpdu fpdu) {
        log.warn("[{}] ABORT received", ctx.getSessionId());

        // Track transfer failure if there was an active transfer
        if (ctx.getTransferRecordId() != null) {
            ParameterValue pi2 = fpdu.getParameter(ParameterIdentifier.PI_02_DIAG);
            String errorCode = pi2 != null ? bytesToHex(pi2.getValue()) : "ABORT";
            transferTracker.trackTransferFailed(ctx, errorCode, "Transfer aborted by peer");
        }

        ctx.setAborted(true);
        ctx.transitionTo(ServerState.CN01_REPOS);
        return null; // No response for ABORT
    }

    /**
     * Convert bytes to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Generate a unique connection ID
     */
    private int generateConnectionId() {
        return (int) (System.currentTimeMillis() % 255) + 1;
    }
}
