package com.vectis.client.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vectis.client.entity.TransferHistory;
import com.vectis.client.entity.TransferHistory.TransferDirection;
import com.vectis.client.entity.TransferHistory.TransferStatus;

@Repository
public interface TransferHistoryRepository extends JpaRepository<TransferHistory, String> {

    Page<TransferHistory> findByServerId(String serverId, Pageable pageable);

    Page<TransferHistory> findByStatus(TransferStatus status, Pageable pageable);

    Page<TransferHistory> findByDirection(TransferDirection direction, Pageable pageable);

    Page<TransferHistory> findByInitiatedBy(String initiatedBy, Pageable pageable);

    List<TransferHistory> findByStartedAtBetween(Instant start, Instant end);

    List<TransferHistory> findByStatus(TransferStatus status);

    long countByStatus(TransferStatus status);

    @Query("SELECT COALESCE(SUM(h.bytesTransferred), 0) FROM TransferHistory h WHERE h.status = :status AND h.startedAt >= :since")
    Long sumBytesTransferredSince(@Param("status") TransferStatus status, @Param("since") Instant since);

    List<TransferHistory> findByCorrelationId(String correlationId);

    List<TransferHistory> findByTraceId(String traceId);
}
