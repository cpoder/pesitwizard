package com.vectis.server.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vectis.server.entity.TransferRecord;
import com.vectis.server.entity.TransferRecord.TransferDirection;
import com.vectis.server.entity.TransferRecord.TransferStatus;
import com.vectis.server.repository.TransferRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing file transfers.
 * Provides transfer tracking, retry management, and statistics.
 */
@SuppressWarnings("null") // Spring Data JPA save() never returns null
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRecordRepository transferRepository;

    // ========== Transfer Lifecycle ==========

    /**
     * Create a new transfer record
     */
    @Transactional
    public TransferRecord createTransfer(String sessionId, String serverId, String nodeId,
            String partnerId, String filename, TransferDirection direction, String remoteAddress) {

        TransferRecord transfer = TransferRecord.builder()
                .transferId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .serverId(serverId)
                .nodeId(nodeId)
                .partnerId(partnerId)
                .filename(filename)
                .direction(direction)
                .status(TransferStatus.INITIATED)
                .remoteAddress(remoteAddress)
                .startedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        transfer = transferRepository.save(transfer);
        log.info("[{}] Transfer created: {} {} from partner {}",
                transfer.getTransferId(), direction, filename, partnerId);

        return transfer;
    }

    /**
     * Start a transfer (set to IN_PROGRESS)
     */
    @Transactional
    public TransferRecord startTransfer(String transferId, Long fileSize, String localPath) {
        TransferRecord transfer = getTransferOrThrow(transferId);

        transfer.setStatus(TransferStatus.IN_PROGRESS);
        transfer.setFileSize(fileSize);
        transfer.setLocalPath(localPath);
        transfer.setUpdatedAt(Instant.now());

        transfer = transferRepository.save(transfer);
        log.info("[{}] Transfer started: {} bytes to {}", transferId, fileSize, localPath);

        return transfer;
    }

    /**
     * Update transfer progress
     */
    @Transactional
    public TransferRecord updateProgress(String transferId, long bytesTransferred) {
        TransferRecord transfer = getTransferOrThrow(transferId);

        transfer.updateProgress(bytesTransferred);
        transfer = transferRepository.save(transfer);

        if (log.isDebugEnabled()) {
            log.debug("[{}] Progress: {} bytes ({}%)",
                    transferId, bytesTransferred, transfer.getProgressPercent());
        }

        return transfer;
    }

    /**
     * Record a sync point
     */
    @Transactional
    public TransferRecord recordSyncPoint(String transferId, long position) {
        TransferRecord transfer = getTransferOrThrow(transferId);

        transfer.setLastSyncPoint(position);
        transfer.setSyncPointCount(transfer.getSyncPointCount() + 1);
        transfer.setUpdatedAt(Instant.now());
        transfer = transferRepository.save(transfer);

        log.debug("[{}] Sync point recorded at position {}", transferId, position);

        return transfer;
    }

    /**
     * Pause a transfer
     */
    @Transactional
    public TransferRecord pauseTransfer(String transferId) {
        TransferRecord transfer = getTransferOrThrow(transferId);

        if (transfer.getStatus() != TransferStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot pause transfer in status: " + transfer.getStatus());
        }

        transfer.setStatus(TransferStatus.PAUSED);
        transfer.setUpdatedAt(Instant.now());
        transfer = transferRepository.save(transfer);

        log.info("[{}] Transfer paused at {} bytes", transferId, transfer.getBytesTransferred());

        return transfer;
    }

    /**
     * Resume a paused transfer
     */
    @Transactional
    public TransferRecord resumeTransfer(String transferId) {
        TransferRecord transfer = getTransferOrThrow(transferId);

        if (transfer.getStatus() != TransferStatus.PAUSED &&
                transfer.getStatus() != TransferStatus.INTERRUPTED) {
            throw new IllegalStateException("Cannot resume transfer in status: " + transfer.getStatus());
        }

        transfer.setStatus(TransferStatus.IN_PROGRESS);
        transfer.setUpdatedAt(Instant.now());
        transfer = transferRepository.save(transfer);

        log.info("[{}] Transfer resumed from {} bytes", transferId, transfer.getBytesTransferred());

        return transfer;
    }

    /**
     * Complete a transfer successfully
     */
    @Transactional
    public TransferRecord completeTransfer(String transferId, String checksum) {
        TransferRecord transfer = getTransferOrThrow(transferId);

        transfer.markCompleted();
        if (checksum != null) {
            transfer.setChecksum(checksum);
        }
        transfer = transferRepository.save(transfer);

        log.info("[{}] Transfer completed: {} bytes in {}ms, speed: {} bytes/sec",
                transferId, transfer.getBytesTransferred(),
                transfer.getCompletedAt().toEpochMilli() - transfer.getStartedAt().toEpochMilli(),
                transfer.getTransferSpeed());

        return transfer;
    }

    /**
     * Mark a transfer as failed
     */
    @Transactional
    public TransferRecord failTransfer(String transferId, String errorCode, String errorMessage) {
        TransferRecord transfer = getTransferOrThrow(transferId);

        transfer.markFailed(errorCode, errorMessage);
        transfer = transferRepository.save(transfer);

        log.warn("[{}] Transfer failed: {} - {}", transferId, errorCode, errorMessage);

        return transfer;
    }

    /**
     * Cancel a transfer
     */
    @Transactional
    public TransferRecord cancelTransfer(String transferId, String reason) {
        TransferRecord transfer = getTransferOrThrow(transferId);

        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setCompletedAt(Instant.now());
        transfer.setUpdatedAt(Instant.now());
        transfer.setErrorMessage(reason);
        transfer = transferRepository.save(transfer);

        log.info("[{}] Transfer cancelled: {}", transferId, reason);

        return transfer;
    }

    /**
     * Interrupt a transfer (can be resumed)
     */
    @Transactional
    public TransferRecord interruptTransfer(String transferId, String reason) {
        TransferRecord transfer = getTransferOrThrow(transferId);

        transfer.setStatus(TransferStatus.INTERRUPTED);
        transfer.setUpdatedAt(Instant.now());
        transfer.setErrorMessage(reason);
        transfer = transferRepository.save(transfer);

        log.info("[{}] Transfer interrupted: {}", transferId, reason);

        return transfer;
    }

    // ========== Retry Management ==========

    /**
     * Create a retry transfer
     */
    @Transactional
    public TransferRecord retryTransfer(String originalTransferId) {
        TransferRecord original = getTransferOrThrow(originalTransferId);

        if (!original.canRetry()) {
            throw new IllegalStateException("Transfer cannot be retried: " +
                    (original.getRetryCount() >= original.getMaxRetries() ? "max retries exceeded" : "not resumable"));
        }

        // Increment retry count on original
        original.setRetryCount(original.getRetryCount() + 1);
        original.setStatus(TransferStatus.RETRY_PENDING);
        original.setUpdatedAt(Instant.now());
        transferRepository.save(original);

        // Create new transfer as child
        TransferRecord retry = TransferRecord.builder()
                .transferId(UUID.randomUUID().toString())
                .sessionId(original.getSessionId())
                .serverId(original.getServerId())
                .nodeId(original.getNodeId())
                .partnerId(original.getPartnerId())
                .filename(original.getFilename())
                .direction(original.getDirection())
                .status(TransferStatus.INITIATED)
                .remoteAddress(original.getRemoteAddress())
                .localPath(original.getLocalPath())
                .fileSize(original.getFileSize())
                .bytesTransferred(original.getLastSyncPoint()) // Resume from last sync point
                .lastSyncPoint(original.getLastSyncPoint())
                .parentTransferId(originalTransferId)
                .retryCount(original.getRetryCount())
                .maxRetries(original.getMaxRetries())
                .startedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        retry = transferRepository.save(retry);
        log.info("[{}] Retry transfer created from {} (attempt {})",
                retry.getTransferId(), originalTransferId, retry.getRetryCount());

        return retry;
    }

    /**
     * Get retryable transfers
     */
    public List<TransferRecord> getRetryableTransfers() {
        return transferRepository.findRetryableTransfers();
    }

    // ========== Query Methods ==========

    /**
     * Get transfer by ID
     */
    public Optional<TransferRecord> getTransfer(String transferId) {
        return transferRepository.findByTransferId(transferId);
    }

    /**
     * Get transfer or throw exception
     */
    public TransferRecord getTransferOrThrow(String transferId) {
        return transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));
    }

    /**
     * Get transfers for a session
     */
    public List<TransferRecord> getTransfersBySession(String sessionId) {
        return transferRepository.findBySessionIdOrderByStartedAtDesc(sessionId);
    }

    /**
     * Get transfers for a partner
     */
    public Page<TransferRecord> getTransfersByPartner(String partnerId, int page, int size) {
        return transferRepository.findByPartnerIdOrderByStartedAtDesc(partnerId, PageRequest.of(page, size));
    }

    /**
     * Get transfers by status
     */
    public Page<TransferRecord> getTransfersByStatus(TransferStatus status, int page, int size) {
        return transferRepository.findByStatusOrderByStartedAtDesc(status, PageRequest.of(page, size));
    }

    /**
     * Get active transfers
     */
    public List<TransferRecord> getActiveTransfers() {
        return transferRepository.findActiveTransfers();
    }

    /**
     * Get active transfers for a server
     */
    public List<TransferRecord> getActiveTransfersByServer(String serverId) {
        return transferRepository.findActiveTransfersByServerId(serverId);
    }

    /**
     * Search transfers with multiple criteria
     */
    public Page<TransferRecord> searchTransfers(String partnerId, TransferStatus status,
            TransferDirection direction, String filename, Instant startDate, Instant endDate,
            int page, int size) {
        return transferRepository.searchTransfers(partnerId, status, direction, filename,
                startDate, endDate, PageRequest.of(page, size));
    }

    /**
     * Get all transfers with pagination
     */
    public Page<TransferRecord> getAllTransfers(Pageable pageable) {
        return transferRepository.findAll(pageable);
    }

    /**
     * Get retry history for a transfer
     */
    public List<TransferRecord> getRetryHistory(String transferId) {
        return transferRepository.findByParentTransferIdOrderByStartedAtDesc(transferId);
    }

    // ========== Statistics ==========

    /**
     * Get transfer statistics
     */
    public TransferStatistics getStatistics() {
        TransferStatistics stats = new TransferStatistics();

        stats.setTotalTransfers(transferRepository.count());
        stats.setActiveTransfers(transferRepository.countActiveTransfers());
        stats.setCompletedTransfers(transferRepository.countByStatus(TransferStatus.COMPLETED));
        stats.setFailedTransfers(transferRepository.countByStatus(TransferStatus.FAILED));
        stats.setTotalBytesTransferred(transferRepository.getTotalBytesTransferred());
        stats.setSendTransfers(transferRepository.countByDirection(TransferDirection.SEND));
        stats.setReceiveTransfers(transferRepository.countByDirection(TransferDirection.RECEIVE));

        // Status breakdown
        Map<String, Long> statusCounts = new HashMap<>();
        for (Object[] row : transferRepository.getTransferCountByStatus()) {
            statusCounts.put(row[0].toString(), (Long) row[1]);
        }
        stats.setStatusBreakdown(statusCounts);

        return stats;
    }

    /**
     * Get statistics for a partner
     */
    public PartnerTransferStatistics getPartnerStatistics(String partnerId) {
        PartnerTransferStatistics stats = new PartnerTransferStatistics();
        stats.setPartnerId(partnerId);
        stats.setTotalTransfers(transferRepository.countByPartnerId(partnerId));
        stats.setTotalBytesTransferred(transferRepository.getTotalBytesTransferredByPartner(partnerId));

        // Get recent transfers
        Page<TransferRecord> recent = transferRepository.findByPartnerIdOrderByStartedAtDesc(
                partnerId, PageRequest.of(0, 10));
        stats.setRecentTransfers(recent.getContent());

        return stats;
    }

    /**
     * Get daily statistics
     */
    public List<DailyTransferStats> getDailyStatistics(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> results = transferRepository.getDailyTransferStats(since);

        return results.stream()
                .map(row -> new DailyTransferStats(
                        row[0].toString(),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue()))
                .toList();
    }

    // ========== Cleanup ==========

    /**
     * Mark interrupted transfers for a node (called on startup)
     */
    @Transactional
    public int markInterruptedTransfers(String nodeId) {
        int count = transferRepository.markInterruptedTransfers(nodeId, Instant.now());
        if (count > 0) {
            log.warn("Marked {} interrupted transfers for node {}", count, nodeId);
        }
        return count;
    }

    /**
     * Clean up old completed transfers
     */
    @Transactional
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    public void cleanupOldTransfers() {
        // Keep completed transfers for 90 days by default
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        int deleted = transferRepository.deleteOldCompletedTransfers(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} old completed transfers", deleted);
        }
    }

    // ========== Statistics DTOs ==========

    @lombok.Data
    public static class TransferStatistics {
        private long totalTransfers;
        private long activeTransfers;
        private long completedTransfers;
        private long failedTransfers;
        private long totalBytesTransferred;
        private long sendTransfers;
        private long receiveTransfers;
        private Map<String, Long> statusBreakdown;
    }

    @lombok.Data
    public static class PartnerTransferStatistics {
        private String partnerId;
        private long totalTransfers;
        private long totalBytesTransferred;
        private List<TransferRecord> recentTransfers;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class DailyTransferStats {
        private String date;
        private long count;
        private long bytes;
    }
}
