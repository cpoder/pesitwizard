package com.pesitwizard.server.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

import com.pesitwizard.server.entity.TransferRecord;
import com.pesitwizard.server.entity.TransferRecord.TransferDirection;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Component for tracking transfers in the database.
 * Integrates with PesitSessionHandler to persist transfer records.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferTracker {

    private final TransferService transferService;

    /**
     * Track the start of a new transfer (CREATE/ACREATE)
     */
    public void trackTransferStart(SessionContext ctx, String serverId, String nodeId,
            TransferDirection direction, String filename, Long fileSize, String localPath) {

        try {
            TransferRecord record = transferService.createTransfer(
                    ctx.getSessionId(),
                    serverId,
                    nodeId,
                    ctx.getClientIdentifier(),
                    filename,
                    direction,
                    ctx.getRemoteAddress());

            // Start the transfer with file info
            transferService.startTransfer(record.getTransferId(), fileSize, localPath);

            // Store the transfer ID in the session context
            ctx.setTransferRecordId(record.getTransferId());

            log.debug("[{}] Transfer tracking started: {}", ctx.getSessionId(), record.getTransferId());

        } catch (Exception e) {
            log.error("[{}] Failed to track transfer start: {}", ctx.getSessionId(), e.getMessage());
            // Don't fail the transfer if tracking fails
        }
    }

    /**
     * Track transfer progress (DTF data received/sent)
     */
    public void trackProgress(SessionContext ctx, long bytesTransferred) {
        String transferId = ctx.getTransferRecordId();
        if (transferId == null) {
            return;
        }

        try {
            transferService.updateProgress(transferId, bytesTransferred);
        } catch (Exception e) {
            log.debug("[{}] Failed to track progress: {}", ctx.getSessionId(), e.getMessage());
        }
    }

    /**
     * Track sync point acknowledgment
     */
    public void trackSyncPoint(SessionContext ctx, long position) {
        String transferId = ctx.getTransferRecordId();
        if (transferId == null) {
            return;
        }

        try {
            transferService.recordSyncPoint(transferId, position);
        } catch (Exception e) {
            log.debug("[{}] Failed to track sync point: {}", ctx.getSessionId(), e.getMessage());
        }
    }

    /**
     * Track successful transfer completion (DTFDA/DTFFA acknowledged)
     */
    public void trackTransferComplete(SessionContext ctx) {
        String transferId = ctx.getTransferRecordId();
        if (transferId == null) {
            return;
        }

        try {
            // Calculate checksum and get bytes transferred from transfer context
            String checksum = null;
            long bytesTransferred = 0;
            TransferContext transfer = ctx.getCurrentTransfer();
            if (transfer != null) {
                bytesTransferred = transfer.getBytesTransferred();
                byte[] data = transfer.getData();
                if (data != null && data.length > 0) {
                    // Use actual data length if available (for RECEIVE)
                    if (bytesTransferred == 0) {
                        bytesTransferred = data.length;
                    }
                    checksum = calculateChecksum(data);
                }
            }

            // Update final bytes transferred before completing
            if (bytesTransferred > 0) {
                transferService.updateProgress(transferId, bytesTransferred);
            }

            transferService.completeTransfer(transferId, checksum);
            ctx.setTransferRecordId(null);

            log.debug("[{}] Transfer tracking completed: {} ({} bytes)",
                    ctx.getSessionId(), transferId, bytesTransferred);

        } catch (Exception e) {
            log.error("[{}] Failed to track transfer completion: {}", ctx.getSessionId(), e.getMessage());
        }
    }

    /**
     * Track transfer failure
     */
    public void trackTransferFailed(SessionContext ctx, String errorCode, String errorMessage) {
        String transferId = ctx.getTransferRecordId();
        if (transferId == null) {
            return;
        }

        try {
            transferService.failTransfer(transferId, errorCode, errorMessage);
            ctx.setTransferRecordId(null);

            log.debug("[{}] Transfer tracking failed: {} - {}", ctx.getSessionId(), errorCode, errorMessage);

        } catch (Exception e) {
            log.error("[{}] Failed to track transfer failure: {}", ctx.getSessionId(), e.getMessage());
        }
    }

    /**
     * Track transfer cancellation
     */
    public void trackTransferCancelled(SessionContext ctx, String reason) {
        String transferId = ctx.getTransferRecordId();
        if (transferId == null) {
            return;
        }

        try {
            transferService.cancelTransfer(transferId, reason);
            ctx.setTransferRecordId(null);

            log.debug("[{}] Transfer tracking cancelled: {}", ctx.getSessionId(), reason);

        } catch (Exception e) {
            log.error("[{}] Failed to track transfer cancellation: {}", ctx.getSessionId(), e.getMessage());
        }
    }

    /**
     * Track transfer interruption (can be resumed)
     */
    public void trackTransferInterrupted(SessionContext ctx, String reason) {
        String transferId = ctx.getTransferRecordId();
        if (transferId == null) {
            return;
        }

        try {
            transferService.interruptTransfer(transferId, reason);
            // Don't clear transferRecordId - it can be used for resume

            log.debug("[{}] Transfer tracking interrupted: {}", ctx.getSessionId(), reason);

        } catch (Exception e) {
            log.error("[{}] Failed to track transfer interruption: {}", ctx.getSessionId(), e.getMessage());
        }
    }

    /**
     * Calculate SHA-256 checksum of data
     */
    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available for checksum calculation");
            return null;
        }
    }

    /**
     * Mark all in-progress transfers for a node as interrupted (called on shutdown)
     */
    public int markInterruptedTransfers(String nodeId) {
        return transferService.markInterruptedTransfers(nodeId);
    }

    /**
     * Track authentication failure as a failed transfer.
     * This creates a transfer record with FAILED status for connections that
     * fail authentication before any transfer operation starts.
     */
    public void trackAuthenticationFailure(SessionContext ctx, String serverId, String nodeId,
            String errorCode, String errorMessage) {
        try {
            // Create a transfer record for the failed authentication
            TransferRecord record = transferService.createTransfer(
                    ctx.getSessionId(),
                    serverId,
                    nodeId,
                    ctx.getClientIdentifier(),
                    "AUTH_FAILURE", // pseudo-filename to identify auth failures
                    TransferDirection.RECEIVE, // default direction
                    ctx.getRemoteAddress());

            // Immediately fail it with the auth error
            transferService.failTransfer(record.getTransferId(), errorCode, errorMessage);

            log.info("[{}] Authentication failure tracked: {} - {}",
                    ctx.getSessionId(), errorCode, errorMessage);

        } catch (Exception e) {
            log.error("[{}] Failed to track authentication failure: {}", ctx.getSessionId(), e.getMessage());
        }
    }
}
