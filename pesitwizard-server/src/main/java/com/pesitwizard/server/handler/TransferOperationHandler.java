package com.pesitwizard.server.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Component;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.config.LogicalFileConfig;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.entity.TransferRecord.TransferDirection;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;
import com.pesitwizard.server.model.ValidationResult;
import com.pesitwizard.server.service.FileSystemService;
import com.pesitwizard.server.service.FpduResponseBuilder;
import com.pesitwizard.server.service.PathPlaceholderService;
import com.pesitwizard.server.service.TransferTracker;
import com.pesitwizard.server.state.ServerState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles file transfer operations: CREATE, SELECT, OPEN, CLOSE, DESELECT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferOperationHandler {

    private final PesitServerProperties properties;
    private final FileValidator fileValidator;
    private final TransferTracker transferTracker;
    private final PathPlaceholderService placeholderService;
    private final FileSystemService fileSystemService;

    /**
     * Handle CREATE FPDU
     */
    public Fpdu handleCreate(SessionContext ctx, Fpdu fpdu) throws IOException {
        log.debug("[{}] handleCreate: starting", ctx.getSessionId());
        TransferContext transfer = ctx.startTransfer();
        transfer.setWriteMode(true);

        // Extract file identification and attributes
        extractFileIdentification(fpdu, transfer);
        extractTransferAttributes(fpdu, transfer);
        extractLogicalAttributes(fpdu, transfer);

        // Validate logical file for CREATE (receive)
        ValidationResult fileValidation = fileValidator.validateForCreate(ctx, transfer);
        if (!fileValidation.isValid()) {
            log.warn("[{}] Logical file validation failed for CREATE: {}",
                    ctx.getSessionId(), fileValidation.getMessage());
            ctx.endTransfer();
            return FpduResponseBuilder.buildAckCreate(ctx, properties.getMaxEntitySize(), fileValidation.getDiagCode());
        }

        // Prepare local file path
        Path localPath = prepareReceivePath(ctx, transfer);
        if (localPath == null) {
            return FpduResponseBuilder.buildAckCreate(ctx, properties.getMaxEntitySize(), DiagnosticCode.D2_211);
        }
        transfer.setLocalPath(localPath);

        log.info("[{}] CREATE: file='{}', transferId={}, priority={}, localPath={}",
                ctx.getSessionId(), transfer.getFilename(), transfer.getTransferId(),
                transfer.getPriority(), transfer.getLocalPath());

        // Track transfer start
        transferTracker.trackTransferStart(ctx, properties.getServerId(), null,
                TransferDirection.RECEIVE, transfer.getFilename(), null,
                transfer.getLocalPath() != null ? transfer.getLocalPath().toString() : null);

        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);

        int maxSize = Math.min(properties.getMaxEntitySize(),
                transfer.getMaxEntitySize() > 0 ? transfer.getMaxEntitySize() : properties.getMaxEntitySize());
        log.debug("[{}] handleCreate: building ACK(CREATE) with maxEntitySize={}", ctx.getSessionId(), maxSize);
        return FpduResponseBuilder.buildAckCreate(ctx, maxSize, DiagnosticCode.D0_000);
    }

    /**
     * Handle SELECT FPDU
     */
    public Fpdu handleSelect(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] SELECT: entering handleSelect", ctx.getSessionId());
        TransferContext transfer = ctx.startTransfer();
        transfer.setWriteMode(false); // Read mode

        // Extract file identification
        extractFileIdentification(fpdu, transfer);
        extractTransferAttributes(fpdu, transfer);

        if (transfer.isRestart()) {
            log.info("[{}] SELECT: Transfer restart requested", ctx.getSessionId());
        }

        // Validate logical file for SELECT (send)
        ValidationResult fileValidation = fileValidator.validateForSelect(ctx, transfer);
        if (!fileValidation.isValid()) {
            log.warn("[{}] Logical file validation failed for SELECT: {}",
                    ctx.getSessionId(), fileValidation.getMessage());
            ctx.endTransfer();
            return FpduResponseBuilder.buildAckSelect(ctx, properties.getMaxEntitySize(), fileValidation.getDiagCode());
        }

        // Set file attributes from config (for ACK_SELECT)
        if (ctx.getLogicalFileConfig() != null) {
            if (ctx.getLogicalFileConfig().getRecordLength() > 0) {
                transfer.setRecordLength(ctx.getLogicalFileConfig().getRecordLength());
            }
            transfer.setRecordFormat(ctx.getLogicalFileConfig().getRecordFormat());
            transfer.setFileType(ctx.getLogicalFileConfig().getFileType());
        }

        // Determine file path
        Path filePath = prepareSendPath(ctx, transfer);

        // Check if file exists and is readable
        if (!Files.exists(filePath)) {
            log.warn("[{}] SELECT: file '{}' not found at {}",
                    ctx.getSessionId(), transfer.getFilename(), filePath);
            ctx.endTransfer();
            return FpduResponseBuilder.buildAckSelect(ctx, properties.getMaxEntitySize(), DiagnosticCode.D2_205);
        }
        if (!Files.isReadable(filePath)) {
            log.error("[{}] SELECT: access denied to file '{}' at {}",
                    ctx.getSessionId(), transfer.getFilename(), filePath);
            ctx.endTransfer();
            return FpduResponseBuilder.buildAckSelect(ctx, properties.getMaxEntitySize(), DiagnosticCode.D2_211);
        }

        transfer.setLocalPath(filePath);

        log.info("[{}] SELECT: file='{}', transferId={}, localPath={}",
                ctx.getSessionId(), transfer.getFilename(), transfer.getTransferId(), filePath);

        // Track transfer start
        try {
            long fileSize = Files.size(filePath);
            transferTracker.trackTransferStart(ctx, properties.getServerId(), null,
                    TransferDirection.SEND, transfer.getFilename(), fileSize, filePath.toString());
        } catch (IOException e) {
            log.warn("[{}] Could not get file size for tracking", ctx.getSessionId());
            transferTracker.trackTransferStart(ctx, properties.getServerId(), null,
                    TransferDirection.SEND, transfer.getFilename(), null, filePath.toString());
        }

        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);

        // Use client's maxEntitySize if provided, otherwise server default
        int maxEntitySize = transfer.getMaxEntitySize() > 0
                ? transfer.getMaxEntitySize()
                : properties.getMaxEntitySize();
        return FpduResponseBuilder.buildAckSelect(ctx, maxEntitySize, DiagnosticCode.D0_000);
    }

    /**
     * Handle OPEN (ORF) FPDU
     */
    public Fpdu handleOpen(SessionContext ctx, Fpdu fpdu) throws IOException {
        // Extract PI 21 (Compression)
        ParameterValue pi21 = fpdu.getParameter(ParameterIdentifier.PI_21_COMPRESSION);
        if (pi21 != null && pi21.getValue() != null && pi21.getValue().length > 0
                && ctx.getCurrentTransfer() != null) {
            ctx.getCurrentTransfer().setCompression(pi21.getValue()[0] & 0xFF);
        }

        // Open output stream for streaming writes (write mode only)
        TransferContext transfer = ctx.getCurrentTransfer();
        if (transfer != null && transfer.isWriteMode() && transfer.getLocalPath() != null) {
            transfer.openOutputStream();
            log.info("[{}] OPEN: streaming output opened to {}", ctx.getSessionId(), transfer.getLocalPath());
        } else {
            log.info("[{}] OPEN: file opened for transfer", ctx.getSessionId());
        }

        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);

        return FpduResponseBuilder.buildAckOpen(ctx);
    }

    /**
     * Handle CLOSE (CRF) FPDU
     */
    public Fpdu handleClose(SessionContext ctx, Fpdu fpdu) {
        // Close the output stream to flush data to disk
        TransferContext transfer = ctx.getCurrentTransfer();
        if (transfer != null) {
            transfer.closeOutputStream();
            log.info("[{}] CLOSE: file closed, {} bytes written", ctx.getSessionId(), transfer.getBytesTransferred());
        } else {
            log.info("[{}] CLOSE: file closed", ctx.getSessionId());
        }
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        return FpduResponseBuilder.buildAckClose(ctx);
    }

    /**
     * Handle DESELECT FPDU
     */
    public Fpdu handleDeselect(SessionContext ctx, Fpdu fpdu) {
        // Log diagnostic code if present
        ParameterValue pi2 = fpdu.getParameter(ParameterIdentifier.PI_02_DIAG);
        if (pi2 != null && pi2.getValue() != null) {
            byte[] diagBytes = pi2.getValue();
            String diagHex = bytesToHex(diagBytes);
            log.warn("[{}] DESELECT: diagnostic code={} (hex)", ctx.getSessionId(), diagHex);
        }
        log.info("[{}] DESELECT: file deselected", ctx.getSessionId());
        ctx.endTransfer();
        ctx.transitionTo(ServerState.CN03_CONNECTED);
        return FpduResponseBuilder.buildAckDeselect(ctx);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Extract file identification from FPDU
     */
    private void extractFileIdentification(Fpdu fpdu, TransferContext transfer) {
        ParameterValue pgi9 = fpdu.getParameter(ParameterGroupIdentifier.PGI_09_ID_FICHIER);
        if (pgi9 != null) {
            // PI 11 - File Type
            ParameterValue pi11 = pgi9.getParameter(ParameterIdentifier.PI_11_TYPE_FICHIER);
            if (pi11 != null && pi11.getValue() != null) {
                transfer.setFileType(parseNumeric(pi11.getValue()));
            }
            // PI 12 - Filename
            ParameterValue pi12 = pgi9.getParameter(ParameterIdentifier.PI_12_NOM_FICHIER);
            if (pi12 != null && pi12.getValue() != null) {
                transfer.setFilename(new String(pi12.getValue(), StandardCharsets.ISO_8859_1).trim());
            }
        }
    }

    /**
     * Extract transfer attributes from FPDU
     */
    private void extractTransferAttributes(Fpdu fpdu, TransferContext transfer) {
        // PI 13 (Transfer ID)
        ParameterValue pi13 = fpdu.getParameter(ParameterIdentifier.PI_13_ID_TRANSFERT);
        if (pi13 != null) {
            transfer.setTransferId(parseNumeric(pi13.getValue()));
        }

        // PI 17 (Priority)
        ParameterValue pi17 = fpdu.getParameter(ParameterIdentifier.PI_17_PRIORITE);
        if (pi17 != null && pi17.getValue().length >= 1) {
            transfer.setPriority(pi17.getValue()[0] & 0xFF);
        }

        // PI 25 (Max Entity Size)
        ParameterValue pi25 = fpdu.getParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE);
        if (pi25 != null) {
            transfer.setMaxEntitySize(parseNumeric(pi25.getValue()));
        }

        // PI 15 (Transfer Restart)
        ParameterValue pi15 = fpdu.getParameter(ParameterIdentifier.PI_15_TRANSFERT_RELANCE);
        if (pi15 != null && pi15.getValue() != null && pi15.getValue().length > 0) {
            transfer.setRestart(pi15.getValue()[0] == 1);
        }
    }

    /**
     * Extract logical attributes from FPDU
     */
    private void extractLogicalAttributes(Fpdu fpdu, TransferContext transfer) {
        ParameterValue pgi30 = fpdu.getParameter(ParameterGroupIdentifier.PGI_30_ATTR_LOGIQUES);
        if (pgi30 != null) {
            // PI 31 - Record Format
            ParameterValue pi31 = pgi30.getParameter(ParameterIdentifier.PI_31_FORMAT_ARTICLE);
            if (pi31 != null && pi31.getValue() != null && pi31.getValue().length > 0) {
                transfer.setRecordFormat(pi31.getValue()[0] & 0xFF);
            }
            // PI 32 - Record Length
            ParameterValue pi32 = pgi30.getParameter(ParameterIdentifier.PI_32_LONG_ARTICLE);
            if (pi32 != null && pi32.getValue() != null) {
                transfer.setRecordLength(parseNumeric(pi32.getValue()));
            }
            // PI 33 - File Organization
            ParameterValue pi33 = pgi30.getParameter(ParameterIdentifier.PI_33_ORG_FICHIER);
            if (pi33 != null && pi33.getValue() != null && pi33.getValue().length > 0) {
                transfer.setFileOrganization(pi33.getValue()[0] & 0xFF);
            }
        }
    }

    /**
     * Prepare receive path for incoming file
     */
    private Path prepareReceivePath(SessionContext ctx, TransferContext transfer) {
        Path receiveDir;
        String localFilename;

        LogicalFileConfig fileConfig = ctx.getLogicalFileConfig();
        if (fileConfig != null && fileConfig.getReceiveDirectory() != null) {
            receiveDir = fileSystemService.normalizePath(fileConfig.getReceiveDirectory());
            localFilename = placeholderService.resolvePath(
                    fileConfig.getReceiveFilenamePattern(),
                    PathPlaceholderService.PlaceholderContext.builder()
                            .partnerId(ctx.getClientIdentifier())
                            .virtualFile(transfer.getFilename())
                            .transferId((long) transfer.getTransferId())
                            .direction("RECEIVE")
                            .build());
        } else {
            receiveDir = fileSystemService.normalizePath(properties.getReceiveDirectory());
            localFilename = (transfer.getFilename() != null ? transfer.getFilename()
                    : "transfer_" + transfer.getTransferId())
                    + "_" + System.currentTimeMillis();
        }

        // Create receive directory
        var createResult = fileSystemService.createDirectories(receiveDir);
        if (!createResult.success()) {
            String errorDetail = String.format("Cannot access receive directory '%s': %s (permissions: %s)",
                    receiveDir, createResult.errorMessage(),
                    fileSystemService.getPermissionString(receiveDir.getParent()));
            log.error("[{}] {}", ctx.getSessionId(), errorDetail);
            return null;
        }

        return receiveDir.resolve(localFilename);
    }

    /**
     * Prepare send path for outgoing file
     */
    private Path prepareSendPath(SessionContext ctx, TransferContext transfer) {
        LogicalFileConfig fileConfig = ctx.getLogicalFileConfig();
        if (fileConfig != null && fileConfig.getSendDirectory() != null) {
            return Paths.get(fileConfig.getSendDirectory()).resolve(transfer.getFilename());
        }
        return Paths.get(properties.getSendDirectory()).resolve(transfer.getFilename());
    }

    /**
     * Parse numeric value from bytes (big-endian)
     */
    private int parseNumeric(byte[] bytes) {
        int value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }
}
