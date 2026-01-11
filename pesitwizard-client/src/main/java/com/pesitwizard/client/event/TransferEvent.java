package com.pesitwizard.client.event;

import java.time.Instant;

import com.pesitwizard.client.pesit.ClientState;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferEvent {

    public enum EventType {
        STATE_CHANGE,
        PROGRESS,
        SYNC_POINT,
        ERROR,
        COMPLETED,
        CANCELLED
    }

    private final String transferId;
    private final EventType type;
    private final Instant timestamp;

    // State change
    private final ClientState previousState;
    private final ClientState currentState;

    // Progress
    private final long bytesTransferred;
    private final long totalBytes;
    private final int percentComplete;

    // Sync point
    private final int syncPointNumber;
    private final long syncPointBytePosition;

    // Error
    private final String errorMessage;
    private final String diagnosticCode;

    public static TransferEvent stateChange(String transferId, ClientState from, ClientState to) {
        return TransferEvent.builder()
                .transferId(transferId)
                .type(EventType.STATE_CHANGE)
                .timestamp(Instant.now())
                .previousState(from)
                .currentState(to)
                .build();
    }

    public static TransferEvent progress(String transferId, long bytes, long total) {
        int pct = total > 0 ? (int) ((bytes * 100) / total) : 0;
        return TransferEvent.builder()
                .transferId(transferId)
                .type(EventType.PROGRESS)
                .timestamp(Instant.now())
                .bytesTransferred(bytes)
                .totalBytes(total)
                .percentComplete(pct)
                .build();
    }

    public static TransferEvent syncPoint(String transferId, int syncNum, long bytePos) {
        return TransferEvent.builder()
                .transferId(transferId)
                .type(EventType.SYNC_POINT)
                .timestamp(Instant.now())
                .syncPointNumber(syncNum)
                .syncPointBytePosition(bytePos)
                .build();
    }

    public static TransferEvent error(String transferId, String message, String diagCode) {
        return TransferEvent.builder()
                .transferId(transferId)
                .type(EventType.ERROR)
                .timestamp(Instant.now())
                .errorMessage(message)
                .diagnosticCode(diagCode)
                .build();
    }

    public static TransferEvent completed(String transferId, long totalBytes) {
        return TransferEvent.builder()
                .transferId(transferId)
                .type(EventType.COMPLETED)
                .timestamp(Instant.now())
                .bytesTransferred(totalBytes)
                .totalBytes(totalBytes)
                .percentComplete(100)
                .build();
    }

    public static TransferEvent cancelled(String transferId) {
        return TransferEvent.builder()
                .transferId(transferId)
                .type(EventType.CANCELLED)
                .timestamp(Instant.now())
                .build();
    }
}
