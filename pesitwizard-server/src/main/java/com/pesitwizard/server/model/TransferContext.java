package com.pesitwizard.server.model;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Context for a file transfer operation.
 * Supports streaming writes directly to disk to avoid memory issues with large
 * files.
 */
@Data
@Slf4j
public class TransferContext {

    /** Transfer identifier (PI 13) */
    private int transferId;

    /** File type (PI 11) */
    private int fileType;

    /** Virtual filename (PI 12) */
    private String filename;

    /** Local file path where data is stored */
    private Path localPath;

    /** Transfer priority (PI 17) */
    private int priority;

    /** Data code: 0=ASCII, 1=EBCDIC, 2=BINARY (PI 16) */
    private int dataCode;

    /** Record format (PI 31) */
    private int recordFormat;

    /** Record length (PI 32) */
    private int recordLength;

    /** File organization (PI 33) */
    private int fileOrganization;

    /** Maximum entity size (PI 25) */
    private int maxEntitySize;

    /** Compression mode (PI 21) */
    private int compression;

    /** Is this a write (receive) or read (send) operation */
    private boolean writeMode;

    /** Is this a restart of a previous transfer */
    private boolean restart;

    /** Restart point (PI 18) */
    private int restartPoint;

    /** Current sync point number */
    private int currentSyncPoint;

    /** Bytes transferred since last sync point (for D2-222 validation) */
    private long bytesSinceLastSync;

    /** Total bytes transferred */
    private long bytesTransferred;

    /** Total records transferred */
    private int recordsTransferred;

    /** Output stream for streaming writes directly to disk */
    private OutputStream fileOutputStream;

    /** Map of sync point number → byte position for RESYN rollback */
    private final Map<Integer, Long> syncPointPositions = new ConcurrentHashMap<>();

    /** Transfer start time */
    private Instant startTime;

    /** Transfer end time */
    private Instant endTime;

    /** Client identifier (PI 61) - for store and forward */
    private String clientId;

    /** Bank identifier (PI 62) - for store and forward */
    private String bankId;

    /**
     * Reset the transfer context for a new transfer
     */
    public void reset() {
        closeOutputStream();
        this.transferId = 0;
        this.fileType = 0;
        this.filename = null;
        this.localPath = null;
        this.priority = 0;
        this.dataCode = 0;
        this.recordFormat = 0;
        this.recordLength = 0;
        this.fileOrganization = 0;
        this.maxEntitySize = 0;
        this.compression = 0;
        this.writeMode = false;
        this.restart = false;
        this.restartPoint = 0;
        this.currentSyncPoint = 0;
        this.bytesSinceLastSync = 0;
        this.bytesTransferred = 0;
        this.recordsTransferred = 0;
        this.fileOutputStream = null;
        this.syncPointPositions.clear();
        this.startTime = null;
        this.endTime = null;
        this.clientId = null;
        this.bankId = null;
    }

    /**
     * Open the output stream for streaming writes.
     * Must be called after localPath is set.
     */
    public void openOutputStream() throws IOException {
        if (localPath == null) {
            throw new IllegalStateException("localPath must be set before opening output stream");
        }
        // Ensure parent directory exists
        Files.createDirectories(localPath.getParent());
        // Use buffered output stream with 64KB buffer for better performance
        this.fileOutputStream = new BufferedOutputStream(new FileOutputStream(localPath.toFile()), 64 * 1024);
        log.debug("Opened streaming output to {}", localPath);
    }

    /**
     * Append data directly to file (streaming - no memory buffering).
     */
    public void appendData(byte[] data) throws IOException {
        if (fileOutputStream == null) {
            throw new IllegalStateException("Output stream not opened. Call openOutputStream() first.");
        }
        fileOutputStream.write(data);
        bytesTransferred += data.length;
        // Note: recordsTransferred is managed by the caller (DataTransferHandler)
    }

    /**
     * Close the output stream and flush data to disk.
     */
    public void closeOutputStream() {
        if (fileOutputStream != null) {
            try {
                fileOutputStream.flush();
                fileOutputStream.close();
                log.debug("Closed streaming output, total bytes: {}", bytesTransferred);
            } catch (IOException e) {
                log.error("Error closing output stream: {}", e.getMessage());
            }
            fileOutputStream = null;
        }
    }

    /**
     * Record a sync point with its byte position for later RESYN rollback.
     */
    public void recordSyncPointPosition(int syncPoint, long bytePosition) {
        syncPointPositions.put(syncPoint, bytePosition);
    }

    /**
     * Look up the byte position for a given sync point number.
     * Returns -1 if the sync point is unknown.
     */
    public long getSyncPointBytePosition(int syncPoint) {
        return syncPointPositions.getOrDefault(syncPoint, -1L);
    }

    /**
     * Truncate the file to the given byte position and reopen the output stream for appending.
     * Used during RESYN rollback to discard data after the sync point.
     */
    public void truncateAndReopen(long bytePosition) throws IOException {
        closeOutputStream();
        if (localPath == null || !Files.exists(localPath)) {
            throw new IllegalStateException("Cannot truncate: file does not exist");
        }
        // Truncate the file
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(localPath.toFile(), "rw")) {
            raf.setLength(bytePosition);
        }
        // Reset bytes transferred to the truncated position
        this.bytesTransferred = bytePosition;
        this.bytesSinceLastSync = 0;
        // Reopen for appending
        this.fileOutputStream = new BufferedOutputStream(new FileOutputStream(localPath.toFile(), true), 64 * 1024);
        log.debug("Truncated {} to {} bytes and reopened for appending", localPath, bytePosition);
    }

    /**
     * @deprecated Use streaming with appendData() and closeOutputStream() instead.
     *             This method is kept for backward compatibility but should not be
     *             used for large files.
     */
    @Deprecated
    public byte[] getData() {
        // For backward compatibility - read from file if it exists
        if (localPath != null && Files.exists(localPath)) {
            try {
                return Files.readAllBytes(localPath);
            } catch (IOException e) {
                log.error("Error reading file data: {}", e.getMessage());
                return new byte[0];
            }
        }
        return new byte[0];
    }
}
