package com.vectis.server.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vectis.server.entity.TransferRecord;
import com.vectis.server.entity.TransferRecord.TransferDirection;
import com.vectis.server.entity.TransferRecord.TransferStatus;

/**
 * Repository for transfer records.
 * Provides queries for transfer history, monitoring, and retry management.
 */
@Repository
public interface TransferRecordRepository extends JpaRepository<TransferRecord, Long> {

    /**
     * Find by unique transfer ID
     */
    Optional<TransferRecord> findByTransferId(String transferId);

    /**
     * Find all transfers for a session
     */
    List<TransferRecord> findBySessionIdOrderByStartedAtDesc(String sessionId);

    /**
     * Find all transfers for a partner
     */
    Page<TransferRecord> findByPartnerIdOrderByStartedAtDesc(String partnerId, Pageable pageable);

    /**
     * Find all transfers for a server instance
     */
    List<TransferRecord> findByServerIdOrderByStartedAtDesc(String serverId);

    /**
     * Find transfers by status
     */
    Page<TransferRecord> findByStatusOrderByStartedAtDesc(TransferStatus status, Pageable pageable);

    /**
     * Find transfers by direction
     */
    Page<TransferRecord> findByDirectionOrderByStartedAtDesc(TransferDirection direction, Pageable pageable);

    /**
     * Find active transfers (in progress)
     */
    @Query("SELECT t FROM TransferRecord t WHERE t.status IN ('INITIATED', 'IN_PROGRESS', 'PAUSED') ORDER BY t.startedAt DESC")
    List<TransferRecord> findActiveTransfers();

    /**
     * Find active transfers for a specific server
     */
    @Query("SELECT t FROM TransferRecord t WHERE t.serverId = :serverId AND t.status IN ('INITIATED', 'IN_PROGRESS', 'PAUSED') ORDER BY t.startedAt DESC")
    List<TransferRecord> findActiveTransfersByServerId(@Param("serverId") String serverId);

    /**
     * Find transfers that can be retried
     */
    @Query("SELECT t FROM TransferRecord t WHERE t.resumable = true AND t.retryCount < t.maxRetries AND t.status IN ('FAILED', 'INTERRUPTED') ORDER BY t.updatedAt ASC")
    List<TransferRecord> findRetryableTransfers();

    /**
     * Find transfers by filename pattern
     */
    @Query("SELECT t FROM TransferRecord t WHERE t.filename LIKE :pattern ORDER BY t.startedAt DESC")
    Page<TransferRecord> findByFilenamePattern(@Param("pattern") String pattern, Pageable pageable);

    /**
     * Find transfers within a time range
     */
    @Query("SELECT t FROM TransferRecord t WHERE t.startedAt BETWEEN :start AND :end ORDER BY t.startedAt DESC")
    Page<TransferRecord> findByTimeRange(@Param("start") Instant start, @Param("end") Instant end, Pageable pageable);

    /**
     * Find transfers by partner and status
     */
    Page<TransferRecord> findByPartnerIdAndStatusOrderByStartedAtDesc(
            String partnerId, TransferStatus status, Pageable pageable);

    /**
     * Find transfers by partner and direction
     */
    Page<TransferRecord> findByPartnerIdAndDirectionOrderByStartedAtDesc(
            String partnerId, TransferDirection direction, Pageable pageable);

    /**
     * Count transfers by status
     */
    long countByStatus(TransferStatus status);

    /**
     * Count transfers by partner
     */
    long countByPartnerId(String partnerId);

    /**
     * Count transfers by direction
     */
    long countByDirection(TransferDirection direction);

    /**
     * Count active transfers
     */
    @Query("SELECT COUNT(t) FROM TransferRecord t WHERE t.status IN ('INITIATED', 'IN_PROGRESS', 'PAUSED')")
    long countActiveTransfers();

    /**
     * Count active transfers for a server
     */
    @Query("SELECT COUNT(t) FROM TransferRecord t WHERE t.serverId = :serverId AND t.status IN ('INITIATED', 'IN_PROGRESS', 'PAUSED')")
    long countActiveTransfersByServerId(@Param("serverId") String serverId);

    /**
     * Get total bytes transferred
     */
    @Query("SELECT COALESCE(SUM(t.bytesTransferred), 0) FROM TransferRecord t WHERE t.status = 'COMPLETED'")
    long getTotalBytesTransferred();

    /**
     * Get total bytes transferred for a partner
     */
    @Query("SELECT COALESCE(SUM(t.bytesTransferred), 0) FROM TransferRecord t WHERE t.partnerId = :partnerId AND t.status = 'COMPLETED'")
    long getTotalBytesTransferredByPartner(@Param("partnerId") String partnerId);

    /**
     * Get transfer statistics by status
     */
    @Query("SELECT t.status, COUNT(t) FROM TransferRecord t GROUP BY t.status")
    List<Object[]> getTransferCountByStatus();

    /**
     * Get transfer statistics by partner
     */
    @Query("SELECT t.partnerId, COUNT(t), SUM(t.bytesTransferred) FROM TransferRecord t WHERE t.status = 'COMPLETED' GROUP BY t.partnerId ORDER BY COUNT(t) DESC")
    List<Object[]> getTransferStatsByPartner();

    /**
     * Get daily transfer statistics
     */
    @Query(value = "SELECT DATE(started_at) as day, COUNT(*) as count, SUM(bytes_transferred) as bytes " +
            "FROM transfer_records WHERE status = 'COMPLETED' AND started_at >= :since " +
            "GROUP BY DATE(started_at) ORDER BY day DESC", nativeQuery = true)
    List<Object[]> getDailyTransferStats(@Param("since") Instant since);

    /**
     * Find interrupted transfers for a node (for recovery after restart)
     */
    @Query("SELECT t FROM TransferRecord t WHERE t.nodeId = :nodeId AND t.status = 'IN_PROGRESS'")
    List<TransferRecord> findInterruptedTransfersByNode(@Param("nodeId") String nodeId);

    /**
     * Mark interrupted transfers as failed (for cleanup)
     */
    @Modifying
    @Query("UPDATE TransferRecord t SET t.status = 'INTERRUPTED', t.updatedAt = :now, t.errorMessage = 'Server shutdown' WHERE t.nodeId = :nodeId AND t.status = 'IN_PROGRESS'")
    int markInterruptedTransfers(@Param("nodeId") String nodeId, @Param("now") Instant now);

    /**
     * Delete old completed transfers (for cleanup)
     */
    @Modifying
    @Query("DELETE FROM TransferRecord t WHERE t.status = 'COMPLETED' AND t.completedAt < :before")
    int deleteOldCompletedTransfers(@Param("before") Instant before);

    /**
     * Find child transfers (retries)
     */
    List<TransferRecord> findByParentTransferIdOrderByStartedAtDesc(String parentTransferId);

    /**
     * Search transfers with multiple criteria
     */
    @Query("SELECT t FROM TransferRecord t WHERE " +
            "(:partnerId IS NULL OR t.partnerId = :partnerId) AND " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:direction IS NULL OR t.direction = :direction) AND " +
            "(:filename IS NULL OR t.filename LIKE :filename) AND " +
            "(:startDate IS NULL OR t.startedAt >= :startDate) AND " +
            "(:endDate IS NULL OR t.startedAt <= :endDate) " +
            "ORDER BY t.startedAt DESC")
    Page<TransferRecord> searchTransfers(
            @Param("partnerId") String partnerId,
            @Param("status") TransferStatus status,
            @Param("direction") TransferDirection direction,
            @Param("filename") String filename,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);
}
