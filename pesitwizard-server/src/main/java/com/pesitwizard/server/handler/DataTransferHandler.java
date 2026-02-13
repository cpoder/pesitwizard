package com.pesitwizard.server.handler;

import com.pesitwizard.channel.PesitChannel;
import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduIO;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterParser;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;
import com.pesitwizard.server.service.FpduResponseBuilder;
import com.pesitwizard.server.service.FpduValidator;
import com.pesitwizard.server.service.TransferTracker;
import com.pesitwizard.server.state.ServerState;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Handles data transfer operations including WRITE, READ, DTF, and sync points. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataTransferHandler {

    private final PesitServerProperties properties;
    private final TransferTracker transferTracker;
    private final FpduValidator fpduValidator;

    /** Handle WRITE FPDU */
    public Fpdu handleWrite(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] WRITE: starting data reception", ctx.getSessionId());
        ctx.transitionTo(ServerState.TDE02B_RECEIVING_DATA);
        return FpduResponseBuilder.buildAckWrite(ctx, 0);
    }

    /** Handle READ FPDU - streams file data to client */
    public Fpdu handleRead(SessionContext ctx, Fpdu fpdu) throws IOException {
        PesitChannel channel = ctx.getChannel();
        TransferContext transfer = ctx.getCurrentTransfer();

        if (transfer == null || transfer.getLocalPath() == null) {
            log.error("[{}] READ: no file selected for transfer", ctx.getSessionId());
            return FpduResponseBuilder.buildAckRead(ctx, DiagnosticCode.D2_205);
        }

        Path filePath = transfer.getLocalPath();
        if (!Files.exists(filePath)) {
            log.error("[{}] READ: file not found: {}", ctx.getSessionId(), filePath);
            return FpduResponseBuilder.buildAckRead(ctx, DiagnosticCode.D2_205);
        }

        // Extract PI 18 (Restart Point)
        long restartPoint = extractRestartPoint(fpdu);
        if (restartPoint > 0) {
            transfer.setRestartPoint((int) restartPoint);
            log.info(
                    "[{}] READ: resuming from position {} for {}",
                    ctx.getSessionId(),
                    restartPoint,
                    filePath);
        } else {
            log.info("[{}] READ: starting data transmission for {}", ctx.getSessionId(), filePath);
        }

        // 1. Send ACK(READ)
        channel.writeFpdu(FpduResponseBuilder.buildAckRead(ctx, DiagnosticCode.D0_000));
        log.info("[{}] Sent ACK(READ)", ctx.getSessionId());

        // 2. Stream file data as DTF chunks (with RESYN retry loop)
        long currentStartPosition = restartPoint;
        int resyncAttempts = 0;
        int maxResyncAttempts = 10;

        while (true) {
            try {
                streamFileData(ctx, filePath, currentStartPosition);
                break; // Normal completion
            } catch (ResyncRequestedException e) {
                resyncAttempts++;
                if (resyncAttempts > maxResyncAttempts) {
                    log.error(
                            "[{}] Too many resync attempts ({}), aborting",
                            ctx.getSessionId(),
                            resyncAttempts);
                    channel.writeFpdu(FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_320));
                    return null;
                }
                currentStartPosition = e.getBytePosition();
                log.info(
                        "[{}] Restarting stream from byte {} after RESYN (attempt {})",
                        ctx.getSessionId(),
                        currentStartPosition,
                        resyncAttempts);
            }
        }

        // 3. Send DTF.END
        channel.writeFpdu(FpduResponseBuilder.buildDtfEnd(ctx));
        log.info("[{}] Sent DTF.END", ctx.getSessionId());

        // Transition to waiting for TRANS.END from client
        ctx.transitionTo(ServerState.TDL02B_SENDING_DATA);

        // Return null - we already sent all responses, now waiting for TRANS.END
        return null;
    }

    /** Stream file data to client using proper PeSIT article format. */
    private long streamFileData(SessionContext ctx, Path filePath, long startPosition)
            throws IOException {
        PesitChannel channel = ctx.getChannel();
        TransferContext transfer = ctx.getCurrentTransfer();
        int maxEntitySize = properties.getMaxEntitySize();
        int recordLength =
                transfer != null && transfer.getRecordLength() > 0
                        ? transfer.getRecordLength()
                        : 1024;

        // Sync point configuration
        boolean syncEnabled = ctx.isSyncPointsEnabled();
        int syncIntervalKb = ctx.getClientSyncIntervalKb();
        long syncIntervalBytes = syncIntervalKb * 1024L;
        long bytesSinceLastSync = 0;
        int syncPointNumber = transfer != null ? transfer.getCurrentSyncPoint() : 0;

        int articlesPerEntity = Math.max(1, (maxEntitySize - 6) / (2 + recordLength));

        long totalBytes = 0;
        int entityCount = 0;
        byte[] articleBuffer = new byte[recordLength];

        try (InputStream rawIn = Files.newInputStream(filePath);
                java.io.BufferedInputStream fileIn = new java.io.BufferedInputStream(rawIn)) {
            if (startPosition > 0) {
                long skipped = fileIn.skip(startPosition);
                log.info(
                        "[{}] READ: skipped {} bytes to resume position",
                        ctx.getSessionId(),
                        skipped);
            }

            long fileSize = Files.size(filePath) - startPosition;
            boolean hasMoreData = true;

            int bytesPerEntity = articlesPerEntity * recordLength;

            while (hasMoreData) {
                // Check if NEXT entity would exceed sync interval - send SYN BEFORE
                if (syncEnabled
                        && syncIntervalBytes > 0
                        && (bytesSinceLastSync + bytesPerEntity) > syncIntervalBytes) {
                    syncPointNumber++;
                    log.info(
                            "[{}] Sending SYN point {} at {} bytes (before next entity would exceed {} limit)",
                            ctx.getSessionId(),
                            syncPointNumber,
                            totalBytes,
                            syncIntervalBytes);

                    if (transfer != null) {
                        transfer.recordSyncPointPosition(
                                syncPointNumber, totalBytes + startPosition);
                    }

                    channel.writeFpdu(FpduResponseBuilder.buildSyn(ctx, syncPointNumber));

                    Fpdu ackOrResyn = readAndParseAckSyn(ctx, syncPointNumber);
                    if (ackOrResyn == null) {
                        throw new IOException("Timeout waiting for ACK_SYN");
                    }

                    if (ackOrResyn.getFpduType() == FpduType.RESYN) {
                        if (!ctx.isResyncEnabled()) {
                            throw new IOException("RESYN received but resync not negotiated");
                        }
                        int resyncPoint = ParameterParser.parsePI18RestartPoint(ackOrResyn);
                        long resyncBytePos =
                                transfer != null
                                        ? transfer.getSyncPointBytePosition(resyncPoint)
                                        : -1;
                        if (resyncBytePos < 0) {
                            throw new IOException("RESYN: unknown sync point " + resyncPoint);
                        }
                        log.info(
                                "[{}] RESYN: client requests rollback to sync point {} (byte {})",
                                ctx.getSessionId(),
                                resyncPoint,
                                resyncBytePos);

                        channel.writeFpdu(FpduResponseBuilder.buildAckResyn(ctx, resyncPoint));

                        throw new ResyncRequestedException(resyncPoint, resyncBytePos);
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

                    byte[] article =
                            (bytesRead == articleBuffer.length)
                                    ? articleBuffer
                                    : Arrays.copyOf(articleBuffer, bytesRead);

                    FpduType articleType;
                    boolean isFirstInEntity = (articlesInEntity == 0);
                    boolean isLastInEntity =
                            (i == articlesPerEntity - 1) || (totalBytes + bytesRead >= fileSize);

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

                    entityData.write((bytesRead >> 8) & 0xFF);
                    entityData.write(bytesRead & 0xFF);
                    entityData.write(article);

                    totalBytes += bytesRead;
                    bytesSinceLastSync += bytesRead;
                    articlesInEntity++;

                    log.debug(
                            "[{}] Article {}: {} {} bytes",
                            ctx.getSessionId(),
                            articlesInEntity,
                            articleType,
                            bytesRead);

                    if (isLastInEntity) break;
                }

                // Send the entity
                if (articlesInEntity > 0) {
                    byte[] data = entityData.toByteArray();
                    channel.writeFpduWithData(
                            FpduType.DTF, ctx.getClientConnectionId(), articlesInEntity, data);
                    entityCount++;
                    log.debug(
                            "[{}] Entity {}: {} articles, {} bytes",
                            ctx.getSessionId(),
                            entityCount,
                            articlesInEntity,
                            data.length);
                }
            }
        }

        log.info(
                "[{}] READ: sent {} bytes in {} entities, {} sync points",
                ctx.getSessionId(),
                totalBytes,
                entityCount,
                syncPointNumber);

        if (transfer != null) {
            transfer.setBytesTransferred(totalBytes);
            transfer.setRecordsTransferred(entityCount);
        }

        return totalBytes;
    }

    /** Read and parse ACK_SYN or RESYN response from client. */
    private Fpdu readAndParseAckSyn(SessionContext ctx, int expectedSyncPoint) throws IOException {
        PesitChannel channel = ctx.getChannel();
        byte[] data = channel.readFrame();
        if (data == null || data.length < 6) {
            log.warn(
                    "[{}] Invalid FPDU data while waiting for ACK_SYN: length={}",
                    ctx.getSessionId(),
                    data != null ? data.length : 0);
            return null;
        }

        FpduParser parser = new FpduParser(data, ctx.isEbcdicEncoding());
        Fpdu fpdu = parser.parse();

        if (fpdu.getFpduType() == FpduType.ACK_SYN) {
            int receivedSyncPoint = ParameterParser.parsePI20SyncNumber(fpdu);
            if (receivedSyncPoint != expectedSyncPoint) {
                log.warn(
                        "[{}] ACK_SYN sync point mismatch: expected {}, got {}",
                        ctx.getSessionId(),
                        expectedSyncPoint,
                        receivedSyncPoint);
            }
            return fpdu;
        }

        if (fpdu.getFpduType() == FpduType.RESYN) {
            log.info("[{}] Received RESYN instead of ACK_SYN", ctx.getSessionId());
            return fpdu;
        }

        log.warn(
                "[{}] Expected ACK_SYN or RESYN but got {}",
                ctx.getSessionId(),
                fpdu.getFpduType());
        return null;
    }

    /** TDE02B - RECEIVING DATA: Processing DTF, DTF.END, SYN, IDT */
    public Fpdu handleTDE02B(SessionContext ctx, Fpdu fpdu) throws IOException {
        return switch (fpdu.getFpduType()) {
            case DTF, DTFDA, DTFMA, DTFFA -> handleDtf(ctx, fpdu);
            case DTF_END -> handleDtfEnd(ctx, fpdu);
            case SYN -> handleSyn(ctx, fpdu);
            case RESYN -> handleResyn(ctx, fpdu);
            case IDT -> handleIdt(ctx, fpdu);
            default -> {
                log.warn(
                        "[{}] Unexpected FPDU {} in TDE02B",
                        ctx.getSessionId(),
                        fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /** TDL02B - SENDING DATA: Waiting for TRANS.END from client */
    public Fpdu handleTDL02B(SessionContext ctx, Fpdu fpdu) {
        return switch (fpdu.getFpduType()) {
            case TRANS_END -> handleTransEndFromClient(ctx, fpdu);
            default -> {
                log.warn(
                        "[{}] Unexpected FPDU {} in TDL02B (expected TRANS_END)",
                        ctx.getSessionId(),
                        fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /** TDE07 - WRITE END: Waiting for TRANS.END */
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

            transfer.closeOutputStream();

            log.info(
                    "[{}] TRANS.END: streaming transfer complete, {} bytes written to {}",
                    ctx.getSessionId(),
                    byteCount,
                    transfer.getLocalPath());
        }

        log.info(
                "[{}] TRANS.END: transfer complete, {} bytes, {} records",
                ctx.getSessionId(),
                byteCount,
                recordCount);

        transferTracker.trackTransferComplete(ctx);

        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);

        return FpduResponseBuilder.buildAckTransEnd(ctx, byteCount, recordCount);
    }

    /** Handle DTF (Data Transfer) FPDU - no response needed */
    private Fpdu handleDtf(SessionContext ctx, Fpdu fpdu) {
        TransferContext transfer = ctx.getCurrentTransfer();
        if (transfer == null) {
            log.warn("[{}] DTF: no active transfer context", ctx.getSessionId());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
        }

        byte[] data = fpdu.getData();
        int dataLength = data != null ? data.length : 0;

        // D2-220: Validate article length against announced record length
        FpduValidator.ValidationResult validation = fpduValidator.validateDtf(fpdu, transfer, data);
        if (!validation.valid()) {
            log.warn("[{}] DTF validation failed: {}", ctx.getSessionId(), validation.message());
            return FpduResponseBuilder.buildAbort(
                    ctx, validation.errorCode(), validation.message());
        }

        // Validate max entity size
        validation = fpduValidator.validateMaxEntitySize(data, transfer);
        if (!validation.valid()) {
            log.warn(
                    "[{}] DTF max entity size validation failed: {}",
                    ctx.getSessionId(),
                    validation.message());
            return FpduResponseBuilder.buildAbort(
                    ctx, validation.errorCode(), validation.message());
        }

        int clientSyncIntervalKb = ctx.getClientSyncIntervalKb();
        if (clientSyncIntervalKb > 0) {
            long newBytesSinceSync = transfer.getBytesSinceLastSync() + dataLength;
            transfer.setBytesSinceLastSync(newBytesSinceSync);
        }

        int articlesInFpdu = 1;
        if (data != null && data.length > 0) {
            try {
                boolean isMultiArticle = FpduIO.isMultiArticleDtf(fpdu);
                log.debug(
                        "[{}] {}: {} bytes, multiArticle={}",
                        ctx.getSessionId(),
                        fpdu.getFpduType(),
                        data.length,
                        isMultiArticle);

                if (isMultiArticle) {
                    java.util.List<byte[]> articles = FpduIO.extractArticles(data);
                    articlesInFpdu = articles.size();
                    int bytesWritten = 0;
                    for (byte[] article : articles) {
                        transfer.appendData(article);
                        bytesWritten += article.length;
                    }
                    log.debug(
                            "[{}] DTF: received {} bytes, wrote {} bytes ({} articles), total: {} bytes",
                            ctx.getSessionId(),
                            dataLength,
                            bytesWritten,
                            articlesInFpdu,
                            transfer.getBytesTransferred());
                } else {
                    transfer.appendData(data);
                    log.debug(
                            "[{}] DTF: received and wrote {} bytes, total: {} bytes",
                            ctx.getSessionId(),
                            dataLength,
                            transfer.getBytesTransferred());
                }
            } catch (java.io.IOException e) {
                log.error("[{}] DTF: error writing data: {}", ctx.getSessionId(), e.getMessage());
                return FpduResponseBuilder.buildAbort(
                        ctx, DiagnosticCode.D2_213, "Write error: " + e.getMessage());
            }
        } else {
            log.debug("[{}] DTF: received {} bytes (no data)", ctx.getSessionId(), dataLength);
            articlesInFpdu = 0;
        }
        transfer.setRecordsTransferred(transfer.getRecordsTransferred() + articlesInFpdu);
        return null; // No response for DTF
    }

    /** Handle DTF.END FPDU - no response needed */
    private Fpdu handleDtfEnd(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] DTF.END: end of data transfer", ctx.getSessionId());
        ctx.transitionTo(ServerState.TDE07_WRITE_END);
        return null;
    }

    /** Handle SYN (Synchronization Point) FPDU */
    private Fpdu handleSyn(SessionContext ctx, Fpdu fpdu) {
        int syncPoint = ParameterParser.parsePI20SyncNumber(fpdu);

        TransferContext transfer = ctx.getCurrentTransfer();
        if (transfer != null) {
            transfer.setCurrentSyncPoint(syncPoint);
            transfer.setBytesSinceLastSync(0);
            long bytesAtCheckpoint = transfer.getBytesTransferred();
            transfer.recordSyncPointPosition(syncPoint, bytesAtCheckpoint);
            transferTracker.trackSyncPoint(ctx, bytesAtCheckpoint);
            log.info(
                    "[{}] SYN: checkpoint {} at {} bytes",
                    ctx.getSessionId(),
                    syncPoint,
                    bytesAtCheckpoint);
        } else {
            log.info("[{}] SYN: sync point {} (no active transfer)", ctx.getSessionId(), syncPoint);
        }

        return FpduResponseBuilder.buildAckSyn(ctx, syncPoint);
    }

    /** Handle RESYN (Resynchronization) FPDU from client. */
    private Fpdu handleResyn(SessionContext ctx, Fpdu fpdu) {
        if (!ctx.isResyncEnabled()) {
            log.warn("[{}] RESYN received but resync not negotiated", ctx.getSessionId());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_306);
        }

        int requestedSyncPoint = ParameterParser.parsePI18RestartPoint(fpdu);
        log.info(
                "[{}] RESYN: client requests rollback to sync point {}",
                ctx.getSessionId(),
                requestedSyncPoint);

        TransferContext transfer = ctx.getCurrentTransfer();
        if (transfer == null) {
            log.error("[{}] RESYN: no active transfer context", ctx.getSessionId());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_218);
        }

        long bytePosition = transfer.getSyncPointBytePosition(requestedSyncPoint);
        if (bytePosition < 0) {
            log.warn(
                    "[{}] RESYN: unknown sync point {} (known: {})",
                    ctx.getSessionId(),
                    requestedSyncPoint,
                    transfer.getSyncPointPositions().keySet());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_228);
        }

        try {
            transfer.truncateAndReopen(bytePosition);
            transfer.setCurrentSyncPoint(requestedSyncPoint);
            log.info(
                    "[{}] RESYN: truncated to {} bytes (sync point {}), ready to receive",
                    ctx.getSessionId(),
                    bytePosition,
                    requestedSyncPoint);
        } catch (java.io.IOException e) {
            log.error(
                    "[{}] RESYN: failed to truncate file: {}", ctx.getSessionId(), e.getMessage());
            return FpduResponseBuilder.buildAbort(
                    ctx, DiagnosticCode.D2_218, "Resync failed: " + e.getMessage());
        }

        return FpduResponseBuilder.buildAckResyn(ctx, requestedSyncPoint);
    }

    /** Handle IDT (Interrupt Data Transfer) FPDU */
    private Fpdu handleIdt(SessionContext ctx, Fpdu fpdu) {
        TransferContext transfer = ctx.getCurrentTransfer();
        long bytesAtInterrupt = transfer != null ? transfer.getBytesTransferred() : 0;
        int syncPointAtInterrupt = transfer != null ? transfer.getCurrentSyncPoint() : 0;

        log.info(
                "[{}] IDT: transfer interrupted at {} bytes, sync point {}",
                ctx.getSessionId(),
                bytesAtInterrupt,
                syncPointAtInterrupt);

        if (transfer != null) {
            transfer.closeOutputStream();
        }

        transferTracker.trackTransferInterrupted(
                ctx,
                String.format(
                        "Client initiated IDT at %d bytes (sync point %d)",
                        bytesAtInterrupt, syncPointAtInterrupt));

        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);
        return FpduResponseBuilder.buildAckIdt(ctx);
    }

    /** Handle TRANS.END from client (after server sent file data) */
    private Fpdu handleTransEndFromClient(SessionContext ctx, Fpdu fpdu) {
        TransferContext transfer = ctx.getCurrentTransfer();
        long byteCount = transfer != null ? transfer.getBytesTransferred() : 0;
        int recordCount = transfer != null ? transfer.getRecordsTransferred() : 0;

        log.info(
                "[{}] TRANS.END received: {} bytes, {} records transferred",
                ctx.getSessionId(),
                byteCount,
                recordCount);

        transferTracker.trackTransferComplete(ctx);

        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);

        return FpduResponseBuilder.buildAckTransEnd(ctx, byteCount, recordCount);
    }

    /** Extract restart point from FPDU */
    private long extractRestartPoint(Fpdu fpdu) {
        return ParameterParser.parsePI18RestartPoint(fpdu);
    }

    /** Exception for data transfer errors with diagnostic code */
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

    /** Exception thrown when a RESYN is received during server READ (streamFileData). */
    static class ResyncRequestedException extends IOException {
        private final int syncPoint;
        private final long bytePosition;

        ResyncRequestedException(int syncPoint, long bytePosition) {
            super("RESYN to sync point " + syncPoint + " at byte " + bytePosition);
            this.syncPoint = syncPoint;
            this.bytePosition = bytePosition;
        }

        public int getSyncPoint() {
            return syncPoint;
        }

        public long getBytePosition() {
            return bytePosition;
        }
    }
}
