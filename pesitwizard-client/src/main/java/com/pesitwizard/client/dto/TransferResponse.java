package com.pesitwizard.client.dto;

import com.pesitwizard.client.entity.TransferHistory.TransferDirection;
import com.pesitwizard.client.entity.TransferHistory.TransferStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for transfer response/result */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponse {

    private String transferId;
    private String correlationId;
    private TransferDirection direction;
    private TransferStatus status;

    private String serverName;
    private String localFilename;
    private String remoteFilename;

    private Long fileSize;
    private Long bytesTransferred;
    private String checksum;

    private String errorMessage;
    private String diagnosticCode;

    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;

    private String traceId;
    private String spanId;
}
