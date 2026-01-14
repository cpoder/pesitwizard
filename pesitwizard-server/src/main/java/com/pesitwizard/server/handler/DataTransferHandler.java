package com.pesitwizard.server.handler;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
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
import com.pesitwizard.fpdu.FpduParser;
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
    public Fpdu handleRead(SessionContext ctx, Fpdu fpdu, DataInputStream in, DataOutputStream out) throws IOException {
        TransferContext transfer = ctx.getCurrentTransfer();

        if (transfer == null || transfer.getLocalPath() == null) {
            log.error("[{}] READ: no file selected for transfer", ctx.getSessionId());
            return FpduResponseBuilder.buildNackRead(ctx, DiagnosticCode.D2_205, "No file selected");
        }

        Path filePath = transfer.getLocalPath();
        if (!Files.exists(filePath)) {
            log.error("[{}] READ: file not found: {}", ctx.getSessionId(), filePath);
            return FpduResponseBuilder.buildNackRead(ctx, DiagnosticCode.D2_205, "File not found");
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
        long totalBytes = streamFileData(ctx, filePath, restartPoint, in, out);

        // 3. Send DTF.END
        FpduIO.writeFpdu(out, FpduResponseBuilder.buildDtfEnd(ctx));
        log.info("[{}] Sent DTF.END", ctx.getSessionId());

        // Transition to waiting for TRANS.END from client
        ctx.transitionTo(ServerState.TDL02B_SENDING_DATA);

        // Return null - we already sent all responses, now waiting for TRANS.END
        return null;
    }

    /**
     * Stream file data to client using proper PeSIT article format.
     * 
     * Structure:
     * - An ENTITY (FPDU) contains multiple ARTICLES
     * - Each article = recordLength bytes, prefixed with 2-byte length
     * - Within an entity: DTFDA (first) + DTFMA* (middle) + DTFFA (last)
     * - SYN points are sent between entities (after DTFFA, before next DTFDA)
     */
    private long streamFileData(SessionContext ctx, Path filePath, long startPosition, DataInputStream in,
            DataOutputStream out) throws IOException {
        TransferContext transfer = ctx.getCurrentTransfer();
        int maxEntitySize = properties.getMaxEntitySize();
        int recordLength = transfer != null && transfer.getRecordLength() > 0
                ? transfer.getRecordLength()
                : 1024;

        // Sync point configuration
        boolean syncEnabled = ctx.isSyncPointsEnabled();
        int syncIntervalKb = ctx.getClientSyncIntervalKb();
        long syncIntervalBytes = syncIntervalKb * 1024L;
        long bytesSinceLastSync = 0;
        int syncPointNumber = transfer != null ? transfer.getCurrentSyncPoint() : 0;

        // Calculate articles per entity: each article needs 6 (header) + 2 (length
        // prefix) + recordLength
        // But header is per FPDU, so: maxEntitySize = 6 + N * (2 + recordLength)
        int articlesPerEntity = Math.max(1, (maxEntitySize - 6) / (2 + recordLength));

        long totalBytes = 0;
        int entityCount = 0;
        byte[] articleBuffer = new byte[recordLength];

        try (InputStream rawIn = Files.newInputStream(filePath);
                java.io.BufferedInputStream fileIn = new java.io.BufferedInputStream(rawIn)) {
            if (startPosition > 0) {
                long skipped = fileIn.skip(startPosition);
                log.info("[{}] READ: skipped {} bytes to resume position", ctx.getSessionId(), skipped);
            }

            long fileSize = Files.size(filePath) - startPosition;
            boolean hasMoreData = true;

            // Estimate bytes per entity for sync point calculation
            int bytesPerEntity = articlesPerEntity * recordLength;

            while (hasMoreData) {
                // Check if NEXT entity would exceed sync interval - send SYN BEFORE
                if (syncEnabled && syncIntervalBytes > 0
                        && (bytesSinceLastSync + bytesPerEntity) > syncIntervalBytes) {
                    syncPointNumber++;
                    log.info("[{}] Sending SYN point {} at {} bytes (before next entity would exceed {} limit)",
                            ctx.getSessionId(), syncPointNumber, totalBytes, syncIntervalBytes);

                    FpduIO.writeFpdu(out, FpduResponseBuilder.buildSyn(ctx, syncPointNumber));

                    Fpdu ackSyn = readAndParseAckSyn(ctx, in, syncPointNumber);
                    if (ackSyn == null) {
                        throw new IOException("Timeout waiting for ACK_SYN");
                    }

                    if (transfer != null) {
                        transfer.setCurrentSyncPoint(syncPointNumber);
                        transfer.setBytesSinceLastSync(0);
                    }
                    bytesSinceLastSync = 0;
                    log.info("[{}] SYN point {} acknowledged", ctx.getSessionId(), syncPointNumber);
                }

                // Build one entity with multiple articles
                ByteArrayOutputStream entityData = new ByteArrayOutputStream();
                int articlesInEntity = 0;

                for (int i = 0; i < articlesPerEntity && hasMoreData; i++) {
                    int bytesRead = fileIn.read(articleBuffer);
                    if (bytesRead == -1) {
                        hasMoreData = false;
                        break;
                    }

                    byte[] article = (bytesRead == articleBuffer.length)
                            ? articleBuffer
                            : Arrays.copyOf(articleBuffer, bytesRead);

                    // Determine article type within entity
                    FpduType articleType;
                    boolean isFirstInEntity = (articlesInEntity == 0);
                    boolean isLastInEntity = (i == articlesPerEntity - 1) || (totalBytes + bytesRead >= fileSize);

                    // Peek to see if more data
                    if (!isLastInEntity) {
                        fileIn.mark(1);
                        int peek = fileIn.read();
                        if (peek == -1) {
                            isLastInEntity = true;
                            hasMoreData = false;
                        } else {
                            fileIn.reset();
                        }
                    }

                    if (isFirstInEntity && isLastInEntity) {
                        articleType = FpduType.DTF;
                    } else if (isFirstInEntity) {
                        articleType = FpduType.DTFDA;
                    } else if (isLastInEntity) {
                        articleType = FpduType.DTFFA;
                    } else {
                        articleType = FpduType.DTFMA;
                    }

                    // Write article with 2-byte length prefix
                    entityData.write((bytesRead >> 8) & 0xFF);
                    entityData.write(bytesRead & 0xFF);
                    entityData.write(article);

                    totalBytes += bytesRead;
                    bytesSinceLastSync += bytesRead;
                    articlesInEntity++;

                    log.debug("[{}] Article {}: {} {} bytes", ctx.getSessionId(), articlesInEntity, articleType,
                            bytesRead);

                    if (isLastInEntity)
                        break;
                }

                // Send the entity if we have articles - always DTF for multi-articles
                if (articlesInEntity > 0) {
                    byte[] data = entityData.toByteArray();
                    // Multi-article DTF: idSrc = number of articles
                    FpduIO.writeFpduWithData(out, FpduType.DTF, ctx.getClientConnectionId(), articlesInEntity, data);
                    entityCount++;
                    log.debug("[{}] Entity {}: {} articles, {} bytes",
                            ctx.getSessionId(), entityCount, articlesInEntity, data.length);
                }
            }
        }

        log.info("[{}] READ: sent {} bytes in {} entities, {} sync points",
                ctx.getSessionId(), totalBytes, entityCount, syncPointNumber);

        if (transfer != null) {
            transfer.setBytesTransferred(totalBytes);
            transfer.setRecordsTransferred(entityCount);
        }

        return totalBytes;
    }

    /**
     * Read and parse ACK_SYN response from client
     */
    private Fpdu readAndParseAckSyn(SessionContext ctx, DataInputStream in, int expectedSyncPoint) throws IOException {
        // Read FPDU length
        int length = in.readUnsignedShort();
        if (length <= 0 || length > 65535) {
            log.warn("[{}] Invalid FPDU length while waiting for ACK_SYN: {}", ctx.getSessionId(), length);
            return null;
        }

        byte[] data = new byte[length];
        in.readFully(data);

        FpduParser parser = new FpduParser(data, ctx.isEbcdicEncoding());
        Fpdu fpdu = parser.parse();

        if (fpdu.getFpduType() != FpduType.ACK_SYN) {
            log.warn("[{}] Expected ACK_SYN but got {}", ctx.getSessionId(), fpdu.getFpduType());
            return null;
        }

        // Verify sync point number
        ParameterValue pi20 = fpdu.getParameter(ParameterIdentifier.PI_20_NUM_SYNC);
        if (pi20 != null) {
            int receivedSyncPoint = parseNumeric(pi20.getValue());
            if (receivedSyncPoint != expectedSyncPoint) {
                log.warn("[{}] ACK_SYN sync point mismatch: expected {}, got {}",
                        ctx.getSessionId(), expectedSyncPoint, receivedSyncPoint);
            }
        }

        return fpdu;
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

        // Track bytes since last sync (for logging only - client sends SYN after data,
        // not before)
        int clientSyncIntervalKb = ctx.getClientSyncIntervalKb();
        if (clientSyncIntervalKb > 0) {
            long newBytesSinceSync = transfer.getBytesSinceLastSync() + dataLength;
            transfer.setBytesSinceLastSync(newBytesSinceSync);
        }

        // Write data to output stream
        // Only DTF (type 0x00) can have multi-article format with 2-byte length
        // prefixes
        // DTFDA/DTFMA/DTFFA are article segments - no prefixes, write data as-is
        if (data != null && data.length > 0) {
            try {
                // Only check for multi-article if this is a DTF (not DTFDA/DTFMA/DTFFA)
                boolean isDtfType = fpdu.getFpduType() == com.pesitwizard.fpdu.FpduType.DTF;
                boolean isMultiArticle = isDtfType && looksLikeMultiArticle(data);
                log.debug("[{}] {}: {} bytes, multiArticle={}",
                        ctx.getSessionId(), fpdu.getFpduType(), data.length, isMultiArticle);
                if (isMultiArticle) {
                    // Extract articles from multi-article format
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(data);
                    int bytesWritten = 0;
                    while (buffer.remaining() >= 2) {
                        int articleLen = buffer.getShort() & 0xFFFF;
                        if (articleLen == 0 || articleLen > buffer.remaining()) {
                            break;
                        }
                        byte[] articleData = new byte[articleLen];
                        buffer.get(articleData);
                        transfer.appendData(articleData);
                        bytesWritten += articleLen;
                    }
                    log.debug("[{}] DTF: received {} bytes, wrote {} bytes (multi-article), total: {} bytes",
                            ctx.getSessionId(), dataLength, bytesWritten, transfer.getBytesTransferred());
                } else {
                    // Raw data - write as-is
                    transfer.appendData(data);
                    log.debug("[{}] DTF: received and wrote {} bytes, total: {} bytes",
                            ctx.getSessionId(), dataLength, transfer.getBytesTransferred());
                }
            } catch (java.io.IOException e) {
                log.error("[{}] DTF: error writing data: {}", ctx.getSessionId(), e.getMessage());
                return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_213, "Write error: " + e.getMessage());
            }
        } else {
            log.debug("[{}] DTF: received {} bytes (no data)", ctx.getSessionId(), dataLength);
        }
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
     * Detect if data looks like multi-article format with 2-byte length prefixes.
     * Check if first 2 bytes are a valid article length that fits in the data.
     */
    private boolean looksLikeMultiArticle(byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }
        int firstLen = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        // Valid if: length > 0, length <= remaining data, and length is reasonable (<
        // 64KB)
        return firstLen > 0 && firstLen <= data.length - 2 && firstLen < 65535;
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
