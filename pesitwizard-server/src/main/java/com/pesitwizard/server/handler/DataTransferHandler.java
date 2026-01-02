package com.pesitwizard.server.handler;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduIO;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;
import com.pesitwizard.server.service.FpduResponseBuilder;
import com.pesitwizard.server.service.FpduValidator;
import com.pesitwizard.server.service.TransferTracker;
import com.pesitwizard.server.state.ServerState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles data transfer operations including WRITE, READ, DTF, and sync points.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataTransferHandler {

    private final PesitServerProperties properties;
    private final TransferTracker transferTracker;
    private final FpduValidator fpduValidator;

    /**
     * Handle WRITE FPDU
     */
    public Fpdu handleWrite(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] WRITE: starting data reception", ctx.getSessionId());
        ctx.transitionTo(ServerState.TDE02B_RECEIVING_DATA);
        return FpduResponseBuilder.buildAckWrite(ctx, 0);
    }

    /**
     * Handle READ FPDU - streams file data to client
     */
    public Fpdu handleRead(SessionContext ctx, Fpdu fpdu, DataOutputStream out) throws IOException {
        TransferContext transfer = ctx.getCurrentTransfer();

        if (transfer == null || transfer.getLocalPath() == null) {
            log.error("[{}] READ: no file selected for transfer", ctx.getSessionId());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_205, "No file selected");
        }

        Path filePath = transfer.getLocalPath();
        if (!Files.exists(filePath)) {
            log.error("[{}] READ: file not found: {}", ctx.getSessionId(), filePath);
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_205, "File not found");
        }

        // Extract PI 18 (Restart Point)
        long restartPoint = extractRestartPoint(fpdu);
        if (restartPoint > 0) {
            transfer.setRestartPoint((int) restartPoint);
            log.info("[{}] READ: resuming from position {} for {}", ctx.getSessionId(), restartPoint, filePath);
        } else {
            log.info("[{}] READ: starting data transmission for {}", ctx.getSessionId(), filePath);
        }

        // 1. Send ACK(READ)
        FpduIO.writeFpdu(out, FpduResponseBuilder.buildAckRead(ctx));
        log.info("[{}] Sent ACK(READ)", ctx.getSessionId());

        // 2. Stream file data as DTF chunks
        long totalBytes = streamFileData(ctx, filePath, restartPoint, out);

        // 3. Send DTF.END
        FpduIO.writeFpdu(out, FpduResponseBuilder.buildDtfEnd(ctx));
        log.info("[{}] Sent DTF.END", ctx.getSessionId());

        // Transition to waiting for TRANS.END from client
        ctx.transitionTo(ServerState.TDL02B_SENDING_DATA);

        // Return null - we already sent all responses, now waiting for TRANS.END
        return null;
    }

    /**
     * Stream file data to client
     */
    private long streamFileData(SessionContext ctx, Path filePath, long startPosition, DataOutputStream out)
            throws IOException {
        int maxChunkSize = properties.getMaxEntitySize();
        long totalBytes = 0;
        int recordCount = 0;
        byte[] buffer = new byte[maxChunkSize];

        try (InputStream fileIn = Files.newInputStream(filePath)) {
            // Skip to restart point if resuming transfer
            if (startPosition > 0) {
                long skipped = fileIn.skip(startPosition);
                log.info("[{}] READ: skipped {} bytes to resume position", ctx.getSessionId(), skipped);
            }

            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                byte[] chunk = (bytesRead == buffer.length) ? buffer : Arrays.copyOf(buffer, bytesRead);
                FpduIO.writeFpduWithData(out, FpduType.DTF, ctx.getClientConnectionId(), 0, chunk);

                totalBytes += bytesRead;
                recordCount++;

                log.debug("[{}] DTF: sent {} bytes, total: {} bytes",
                        ctx.getSessionId(), bytesRead, totalBytes);
            }
        }

        log.info("[{}] READ: sent {} bytes in {} DTF chunk(s)", ctx.getSessionId(), totalBytes, recordCount);

        // Store transfer stats
        TransferContext transfer = ctx.getCurrentTransfer();
        if (transfer != null) {
            transfer.setBytesTransferred(totalBytes);
            transfer.setRecordsTransferred(recordCount);
        }

        return totalBytes;
    }

    /**
     * TDE02B - RECEIVING DATA: Processing DTF, DTF.END, SYN, IDT
     */
    public Fpdu handleTDE02B(SessionContext ctx, Fpdu fpdu) throws IOException {
        return switch (fpdu.getFpduType()) {
            case DTF, DTFDA, DTFMA, DTFFA -> handleDtf(ctx, fpdu);
            case DTF_END -> handleDtfEnd(ctx, fpdu);
            case SYN -> handleSyn(ctx, fpdu);
            case IDT -> handleIdt(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in TDE02B", ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * TDL02B - SENDING DATA: Waiting for TRANS.END from client
     */
    public Fpdu handleTDL02B(SessionContext ctx, Fpdu fpdu) {
        return switch (fpdu.getFpduType()) {
            case TRANS_END -> handleTransEndFromClient(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in TDL02B (expected TRANS_END)",
                        ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * TDE07 - WRITE END: Waiting for TRANS.END
     */
    public Fpdu handleTDE07(SessionContext ctx, Fpdu fpdu) throws IOException {
        if (fpdu.getFpduType() != FpduType.TRANS_END) {
            log.warn("[{}] Expected TRANS.END, got {}", ctx.getSessionId(), fpdu.getFpduType());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
        }

        TransferContext transfer = ctx.getCurrentTransfer();
        long byteCount = 0;
        int recordCount = 0;

        if (transfer != null) {
            byteCount = transfer.getBytesTransferred();
            recordCount = transfer.getRecordsTransferred();

            // With streaming, data is already on disk via appendData() calls
            // Just ensure the output stream is closed and flushed
            transfer.closeOutputStream();

            log.info("[{}] TRANS.END: streaming transfer complete, {} bytes written to {}",
                    ctx.getSessionId(), byteCount, transfer.getLocalPath());
        }

        log.info("[{}] TRANS.END: transfer complete, {} bytes, {} records",
                ctx.getSessionId(), byteCount, recordCount);

        // Track transfer completion
        transferTracker.trackTransferComplete(ctx);

        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);

        return FpduResponseBuilder.buildAckTransEnd(ctx, byteCount, recordCount);
    }

    /**
     * Write received data to file
     */
    private void writeReceivedData(SessionContext ctx, TransferContext transfer) throws IOException {
        try {
            // Ensure parent directory exists
            Path parentDir = transfer.getLocalPath().getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.write(transfer.getLocalPath(), transfer.getData());
            log.info("[{}] TRANS.END: wrote {} bytes to {}",
                    ctx.getSessionId(), transfer.getData().length, transfer.getLocalPath());
        } catch (java.nio.file.FileSystemException e) {
            // Disk full or permission error
            log.error("[{}] TRANS.END: failed to write file: {}", ctx.getSessionId(), e.getMessage());
            ctx.transitionTo(ServerState.OF02_TRANSFER_READY);
            if (e.getMessage() != null && e.getMessage().contains("No space")) {
                throw new DataTransferException(DiagnosticCode.D2_219, "Disk full: " + e.getMessage());
            }
            throw new DataTransferException(DiagnosticCode.D2_213, "File write error: " + e.getMessage());
        }
    }

    /**
     * Handle DTF (Data Transfer) FPDU - no response needed
     * Validates article length against announced record length (D2-220)
     * Validates data without sync point (D2-222)
     */
    private Fpdu handleDtf(SessionContext ctx, Fpdu fpdu) {
        TransferContext transfer = ctx.getCurrentTransfer();
        if (transfer == null) {
            log.warn("[{}] DTF: no active transfer context", ctx.getSessionId());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
        }

        // Get data payload from FPDU
        byte[] data = fpdu.getData();
        int dataLength = data != null ? data.length : 0;

        // D2-220: Validate article length against announced record length
        FpduValidator.ValidationResult validation = fpduValidator.validateDtf(fpdu, transfer, data);
        if (!validation.valid()) {
            log.warn("[{}] DTF validation failed: {}", ctx.getSessionId(), validation.message());
            return FpduResponseBuilder.buildAbort(ctx, validation.errorCode(), validation.message());
        }

        // Validate max entity size
        validation = fpduValidator.validateMaxEntitySize(data, transfer);
        if (!validation.valid()) {
            log.warn("[{}] DTF max entity size validation failed: {}", ctx.getSessionId(), validation.message());
            return FpduResponseBuilder.buildAbort(ctx, validation.errorCode(), validation.message());
        }

        // D2-222: Validate that client respects its declared sync point interval
        int clientSyncIntervalKb = ctx.getClientSyncIntervalKb();
        if (clientSyncIntervalKb > 0) {
            long syncIntervalBytes = clientSyncIntervalKb * 1024L;
            long newBytesSinceSync = transfer.getBytesSinceLastSync() + dataLength;
            validation = fpduValidator.validateDataWithoutSyncPoint(transfer, newBytesSinceSync, syncIntervalBytes);
            if (!validation.valid()) {
                log.warn("[{}] DTF D2-222: client exceeded declared sync interval ({} KB): {} bytes without sync",
                        ctx.getSessionId(), clientSyncIntervalKb, newBytesSinceSync);
                return FpduResponseBuilder.buildAbort(ctx, validation.errorCode(), validation.message());
            }
            transfer.setBytesSinceLastSync(newBytesSinceSync);
        }

        log.debug("[{}] DTF: received {} bytes", ctx.getSessionId(), dataLength);
        transfer.setRecordsTransferred(transfer.getRecordsTransferred() + 1);
        return null; // No response for DTF
    }

    /**
     * Handle DTF.END FPDU - no response needed
     */
    private Fpdu handleDtfEnd(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] DTF.END: end of data transfer", ctx.getSessionId());
        ctx.transitionTo(ServerState.TDE07_WRITE_END);
        return null; // No response for DTF.END
    }

    /**
     * Handle SYN (Synchronization Point) FPDU
     */
    private Fpdu handleSyn(SessionContext ctx, Fpdu fpdu) {
        ParameterValue pi20 = fpdu.getParameter(ParameterIdentifier.PI_20_NUM_SYNC);
        int syncPoint = 0;
        if (pi20 != null) {
            syncPoint = parseNumeric(pi20.getValue());
        }

        TransferContext transfer = ctx.getCurrentTransfer();
        if (transfer != null) {
            transfer.setCurrentSyncPoint(syncPoint);
            // Reset bytes since last sync for D2-222 tracking
            transfer.setBytesSinceLastSync(0);
            long bytesAtCheckpoint = transfer.getBytesTransferred();
            transferTracker.trackSyncPoint(ctx, bytesAtCheckpoint);
            log.info("[{}] SYN: checkpoint {} at {} bytes",
                    ctx.getSessionId(), syncPoint, bytesAtCheckpoint);
        } else {
            log.info("[{}] SYN: sync point {} (no active transfer)", ctx.getSessionId(), syncPoint);
        }

        return FpduResponseBuilder.buildAckSyn(ctx, syncPoint);
    }

    /**
     * Handle IDT (Interrupt Data Transfer) FPDU
     * Tracks the interruption in database for potential resume.
     */
    private Fpdu handleIdt(SessionContext ctx, Fpdu fpdu) {
        TransferContext transfer = ctx.getCurrentTransfer();
        long bytesAtInterrupt = transfer != null ? transfer.getBytesTransferred() : 0;
        int syncPointAtInterrupt = transfer != null ? transfer.getCurrentSyncPoint() : 0;

        log.info("[{}] IDT: transfer interrupted at {} bytes, sync point {}",
                ctx.getSessionId(), bytesAtInterrupt, syncPointAtInterrupt);

        // Close output stream to flush data written so far
        if (transfer != null) {
            transfer.closeOutputStream();
        }

        // Track interruption in database - transfer can be resumed
        transferTracker.trackTransferInterrupted(ctx,
                String.format("Client initiated IDT at %d bytes (sync point %d)",
                        bytesAtInterrupt, syncPointAtInterrupt));

        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);
        return FpduResponseBuilder.buildAckIdt(ctx);
    }

    /**
     * Handle TRANS.END from client (after server sent file data)
     */
    private Fpdu handleTransEndFromClient(SessionContext ctx, Fpdu fpdu) {
        TransferContext transfer = ctx.getCurrentTransfer();
        long byteCount = transfer != null ? transfer.getBytesTransferred() : 0;
        int recordCount = transfer != null ? transfer.getRecordsTransferred() : 0;

        log.info("[{}] TRANS.END received: {} bytes, {} records transferred",
                ctx.getSessionId(), byteCount, recordCount);

        // Track transfer completion for SEND transfers
        transferTracker.trackTransferComplete(ctx);

        // Transition back to file open state (ready for CLOSE)
        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);

        return FpduResponseBuilder.buildAckTransEnd(ctx, byteCount, recordCount);
    }

    /**
     * Extract restart point from FPDU
     */
    private long extractRestartPoint(Fpdu fpdu) {
        ParameterValue pi18 = fpdu.getParameter(ParameterIdentifier.PI_18_POINT_RELANCE);
        if (pi18 != null) {
            return parseNumeric(pi18.getValue());
        }
        return 0;
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

    /**
     * Exception for data transfer errors with diagnostic code
     */
    public static class DataTransferException extends IOException {
        private final DiagnosticCode diagnosticCode;

        public DataTransferException(DiagnosticCode code, String message) {
            super(message);
            this.diagnosticCode = code;
        }

        public DiagnosticCode getDiagnosticCode() {
            return diagnosticCode;
        }
    }
}
