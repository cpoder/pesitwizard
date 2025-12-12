package com.vectis.client.dto;

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

    /** Local file path (for send) or destination path (for receive) */
    @NotBlank(message = "Local path is required")
    private String localPath;

    /** Remote filename */
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
}
