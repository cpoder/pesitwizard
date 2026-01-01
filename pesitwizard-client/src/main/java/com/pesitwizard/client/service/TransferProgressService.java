package com.pesitwizard.client.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for sending real-time transfer progress updates via WebSocket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferProgressService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Send progress update to WebSocket subscribers.
     * Clients subscribe to /topic/transfer/{transferId}/progress
     */
    public void sendProgress(String transferId, long bytesTransferred, long fileSize, int syncPoint) {
        if (transferId == null) {
            return;
        }

        TransferProgressMessage message = new TransferProgressMessage(
                transferId,
                bytesTransferred,
                fileSize,
                fileSize > 0 ? (int) ((bytesTransferred * 100) / fileSize) : 0,
                syncPoint,
                "IN_PROGRESS",
                null);

        String destination = "/topic/transfer/" + transferId + "/progress";
        messagingTemplate.convertAndSend(destination, message);
        log.debug("WebSocket progress: {} - {}% ({}/{})",
                transferId, message.percentage(), bytesTransferred, fileSize);
    }

    /**
     * Send transfer completion notification.
     */
    public void sendComplete(String transferId, long bytesTransferred, long fileSize) {
        if (transferId == null) {
            return;
        }

        TransferProgressMessage message = new TransferProgressMessage(
                transferId,
                bytesTransferred,
                fileSize,
                100,
                0,
                "COMPLETED",
                null);

        String destination = "/topic/transfer/" + transferId + "/progress";
        messagingTemplate.convertAndSend(destination, message);
        log.info("WebSocket: transfer {} completed ({} bytes)", transferId, bytesTransferred);
    }

    /**
     * Send transfer failure notification.
     */
    public void sendFailed(String transferId, String errorMessage) {
        if (transferId == null) {
            return;
        }

        TransferProgressMessage message = new TransferProgressMessage(
                transferId,
                0,
                0,
                0,
                0,
                "FAILED",
                errorMessage);

        String destination = "/topic/transfer/" + transferId + "/progress";
        messagingTemplate.convertAndSend(destination, message);
        log.info("WebSocket: transfer {} failed - {}", transferId, errorMessage);
    }

    /**
     * Progress message record for WebSocket communication.
     */
    public record TransferProgressMessage(
            String transferId,
            long bytesTransferred,
            long fileSize,
            int percentage,
            int lastSyncPoint,
            String status,
            String errorMessage) {
    }
}
