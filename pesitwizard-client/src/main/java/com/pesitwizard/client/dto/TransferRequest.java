package com.pesitwizard.client.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for initiating a file transfer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequest {

    /** Server name or ID to use */
    @NotBlank(message = "Server is required")
    private String server;

    /** Partner ID (PI_03 DEMANDEUR) - identifies this client to the server */
    @NotBlank(message = "Partner ID is required")
    private String partnerId;

    /** Password for authentication (PI_05 CONTROLE_ACCES) - optional */
    private String password;

    /**
     * Source connection ID for reading the file (null = local filesystem).
     * For SEND: where to read the file from.
     * For RECEIVE: not used (file comes from PeSIT server).
     */
    private String sourceConnectionId;

    /**
     * Destination connection ID for writing the file (null = local filesystem).
     * For SEND: not used (file goes to PeSIT server).
     * For RECEIVE: where to write the received file.
     */
    private String destinationConnectionId;

    /** Filename (relative path on the connector, or local path if no connector) */
    @NotBlank(message = "Filename is required")
    private String filename;

    /**
     * @deprecated Use filename instead. Kept for backward compatibility.
     */
    @Deprecated
    private String localPath;

    /** Remote filename (virtual file ID on PeSIT server) */
    @NotBlank(message = "Remote filename is required")
    private String remoteFilename;

    /**
     * Virtual file name (logical file identifier on server, optional - defaults to
     * remoteFilename)
     */
    private String virtualFile;

    /** File type: 0=binary, 1=text, 2=structured (optional, default binary) */
    private Integer fileType;

    /** Transfer config name or ID (optional, uses default if not specified) */
    private String transferConfig;

    /** Correlation ID for tracing (optional, auto-generated if not provided) */
    private String correlationId;

    /** Override chunk size (optional) */
    private Integer chunkSize;

    /** Override compression (optional) */
    private Boolean compressionEnabled;

    /** Override priority (optional) */
    private Integer priority;

    /** Enable sync points for restart capability (PI_07) */
    private Boolean syncPointsEnabled;

    /**
     * Sync point interval in bytes. If null, auto-calculated based on file size:
     * - Files < 1MB: no sync points
     * - Files 1-10MB: every 256KB
     * - Files 10-100MB: every 1MB
     * - Files > 100MB: every 5MB
     */
    private Long syncPointIntervalBytes;

    /** Enable resynchronization capability (PI_23) */
    private Boolean resyncEnabled;

    /**
     * Resume from a previous interrupted transfer.
     * If set, this is the transfer ID to resume from.
     */
    private String resumeFromTransferId;

    /**
     * Record/article length (PI_32) - max size of a single article/record.
     * This is configured per virtual file on the server and must be known in
     * advance.
     * If null, uses config default (typically 506 bytes).
     * Note: PI_32 (article) and PI_25 (entity) are INDEPENDENT limits.
     */
    private Integer recordLength;
}
