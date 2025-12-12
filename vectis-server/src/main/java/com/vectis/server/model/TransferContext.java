package com.vectis.server.model;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;

import lombok.Data;

/**
 * Context for a file transfer operation
 */
@Data
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
    
    /** Total bytes transferred */
    private long bytesTransferred;
    
    /** Total records transferred */
    private int recordsTransferred;
    
    /** Buffer for accumulating received data */
    private ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
    
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
        this.bytesTransferred = 0;
        this.recordsTransferred = 0;
        this.dataBuffer = new ByteArrayOutputStream();
        this.startTime = null;
        this.endTime = null;
        this.clientId = null;
        this.bankId = null;
    }
    
    /**
     * Append data to the buffer
     */
    public void appendData(byte[] data) {
        dataBuffer.writeBytes(data);
        bytesTransferred += data.length;
        recordsTransferred++;
    }
    
    /**
     * Get all accumulated data
     */
    public byte[] getData() {
        return dataBuffer.toByteArray();
    }
}
