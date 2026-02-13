package com.pesitwizard.client.pesit;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.session.PesitSession;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes data as DTF FPDUs to a PeSIT session, respecting entity size limits.
 *
 * <p>Handles automatic chunking of data based on negotiated PI_25 (max entity size) and optional
 * multi-article DTF (DTFMA) for variable-length records.
 */
@Slf4j
public class FpduWriter {
    private static final int FPDU_HEADER_SIZE =
            6; // len(2) + phase(1) + type(1) + idDst(1) + idSrc(1)
    private static final int ARTICLE_PREFIX_SIZE = 2; // 2-byte length prefix per article in DTFMA

    private final PesitSession session;
    private final int serverConnectionId;
    private final int maxEntitySize; // PI_25 negotiated value
    private final int recordLength; // PI_32 article/record size (0 = variable)
    private final boolean useMultiArticle; // Use DTFMA for variable records

    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private final ConcurrentHashMap<Integer, Long> confirmedSyncPoints = new ConcurrentHashMap<>();

    /** Create a writer for fixed-length records (simple DTF). */
    public FpduWriter(PesitSession session, int serverConnectionId, int maxEntitySize) {
        this(session, serverConnectionId, maxEntitySize, 0, false);
    }

    /**
     * Create a writer with full configuration.
     *
     * @param session PeSIT session
     * @param serverConnectionId Server connection ID for DTF FPDUs
     * @param maxEntitySize Negotiated PI_25 (max FPDU size)
     * @param recordLength PI_32 record/article length (0 = variable)
     * @param useMultiArticle If true, use DTFMA for variable records
     */
    public FpduWriter(
            PesitSession session,
            int serverConnectionId,
            int maxEntitySize,
            int recordLength,
            boolean useMultiArticle) {
        this.session = session;
        this.serverConnectionId = serverConnectionId;
        this.maxEntitySize = maxEntitySize > 0 ? maxEntitySize : 4096; // Default 4KB
        this.recordLength = recordLength;
        this.useMultiArticle = useMultiArticle && recordLength > 0;

        log.debug(
                "FpduWriter created: maxEntitySize={}, recordLength={}, useMultiArticle={}",
                this.maxEntitySize,
                this.recordLength,
                this.useMultiArticle);
    }

    /** Get maximum data size per DTF FPDU, capped at 65536. */
    public int getMaxDataPerDtf() {
        return Math.min(maxEntitySize - FPDU_HEADER_SIZE, 65536);
    }

    /** Get total bytes sent so far. */
    public long getTotalBytesSent() {
        return totalBytesSent.get();
    }

    /** Record a confirmed sync point (after receiving ACK_SYN). */
    public void confirmSyncPoint(int syncNumber) {
        confirmedSyncPoints.put(syncNumber, totalBytesSent.get());
    }

    /** Get the byte position at a confirmed sync point. Returns -1 if unknown. */
    public long getBytesAtSyncPoint(int syncNumber) {
        return confirmedSyncPoints.getOrDefault(syncNumber, -1L);
    }

    /** Get the last confirmed sync point byte position, or 0 if none. */
    public long getLastConfirmedSyncPosition() {
        return confirmedSyncPoints.values().stream().mapToLong(Long::longValue).max().orElse(0L);
    }

    /**
     * Write data from an InputStream, automatically chunking into multiple DTF FPDUs.
     *
     * @param inputStream Source data
     * @param callback Optional callback for progress/sync points
     * @return Total bytes written
     */
    public long writeFromStream(InputStream inputStream, WriteCallback callback)
            throws IOException {
        int maxDataPerDtf = getMaxDataPerDtf();
        byte[] buffer = new byte[maxDataPerDtf];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            byte[] chunk =
                    bytesRead == buffer.length
                            ? buffer
                            : java.util.Arrays.copyOf(buffer, bytesRead);
            writeDtf(chunk);

            if (callback != null) {
                callback.onChunkSent(bytesRead, totalBytesSent.get());
            }
        }

        return totalBytesSent.get();
    }

    /**
     * Write a single chunk of data as DTF. If chunk exceeds maxEntitySize, it will be split into
     * multiple DTF FPDUs.
     */
    public void writeDtf(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return;
        }

        int maxDataPerDtf = getMaxDataPerDtf();

        if (data.length <= maxDataPerDtf) {
            // Single DTF is sufficient
            sendSingleDtf(data);
        } else {
            // Split into multiple DTF FPDUs
            int offset = 0;
            while (offset < data.length) {
                int chunkSize = Math.min(maxDataPerDtf, data.length - offset);
                byte[] chunk = new byte[chunkSize];
                System.arraycopy(data, offset, chunk, 0, chunkSize);
                sendSingleDtf(chunk);
                offset += chunkSize;
            }
        }
    }

    /**
     * Write multiple articles as a single DTFMA FPDU if they fit, otherwise split across multiple
     * DTFMAs.
     */
    public void writeMultiArticle(List<byte[]> articles) throws IOException {
        if (articles == null || articles.isEmpty()) {
            return;
        }

        int maxDataPerDtf = getMaxDataPerDtf();
        List<byte[]> currentBatch = new ArrayList<>();
        int currentBatchSize = 0;

        for (byte[] article : articles) {
            int articleTotalSize = ARTICLE_PREFIX_SIZE + article.length;

            if (currentBatchSize + articleTotalSize > maxDataPerDtf) {
                // Flush current batch
                if (!currentBatch.isEmpty()) {
                    sendMultiArticleDtf(currentBatch);
                    currentBatch.clear();
                    currentBatchSize = 0;
                }

                // If single article exceeds limit, split it
                if (articleTotalSize > maxDataPerDtf) {
                    log.warn(
                            "Article size {} exceeds max entity size {}, sending as simple DTF",
                            article.length,
                            maxEntitySize);
                    writeDtf(article);
                    continue;
                }
            }

            currentBatch.add(article);
            currentBatchSize += articleTotalSize;
        }

        // Flush remaining
        if (!currentBatch.isEmpty()) {
            sendMultiArticleDtf(currentBatch);
        }
    }

    /** Send a single DTF FPDU with the given data. */
    private void sendSingleDtf(byte[] data) throws IOException {
        Fpdu dtfFpdu = new Fpdu(FpduType.DTF).withIdDst(serverConnectionId);
        try {
            session.sendFpduWithData(dtfFpdu, data);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sending DTF", e);
        }
        long newTotal = totalBytesSent.addAndGet(data.length);
        log.debug("Sent DTF: {} bytes, total: {}", data.length, newTotal);
    }

    /** Send a DTFMA (multi-article DTF) FPDU. */
    private void sendMultiArticleDtf(List<byte[]> articles) throws IOException {
        byte[] dtfmaData =
                FpduBuilder.buildMultiArticleDtf(serverConnectionId, articles, maxEntitySize);
        if (dtfmaData != null) {
            session.sendRawFpdu(dtfmaData);
            int dataSize = articles.stream().mapToInt(a -> a.length).sum();
            long newTotal = totalBytesSent.addAndGet(dataSize);
            log.debug(
                    "Sent DTFMA: {} articles, {} bytes data, total: {}",
                    articles.size(),
                    dataSize,
                    newTotal);
        } else {
            // Fallback: send as individual DTFs
            log.warn("DTFMA too large, falling back to individual DTFs");
            for (byte[] article : articles) {
                sendSingleDtf(article);
            }
        }
    }

    /** Callback interface for write progress. */
    public interface WriteCallback {
        void onChunkSent(int chunkSize, long totalSent);
    }
}
