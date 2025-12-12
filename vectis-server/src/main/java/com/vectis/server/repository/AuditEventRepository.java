package com.vectis.server.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vectis.server.entity.AuditEvent;
import com.vectis.server.entity.AuditEvent.AuditCategory;
import com.vectis.server.entity.AuditEvent.AuditEventType;
import com.vectis.server.entity.AuditEvent.AuditOutcome;

/**
 * Repository for audit events.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * Find by category
     */
    Page<AuditEvent> findByCategoryOrderByTimestampDesc(AuditCategory category, Pageable pageable);

    /**
     * Find by event type
     */
    Page<AuditEvent> findByEventTypeOrderByTimestampDesc(AuditEventType eventType, Pageable pageable);

    /**
     * Find by outcome
     */
    Page<AuditEvent> findByOutcomeOrderByTimestampDesc(AuditOutcome outcome, Pageable pageable);

    /**
     * Find by username
     */
    Page<AuditEvent> findByUsernameOrderByTimestampDesc(String username, Pageable pageable);

    /**
     * Find by session ID
     */
    List<AuditEvent> findBySessionIdOrderByTimestampDesc(String sessionId);

    /**
     * Find by transfer ID
     */
    List<AuditEvent> findByTransferIdOrderByTimestampDesc(String transferId);

    /**
     * Find by partner ID
     */
    Page<AuditEvent> findByPartnerIdOrderByTimestampDesc(String partnerId, Pageable pageable);

    /**
     * Find by resource
     */
    Page<AuditEvent> findByResourceTypeAndResourceIdOrderByTimestampDesc(
            String resourceType, String resourceId, Pageable pageable);

    /**
     * Find by time range
     */
    Page<AuditEvent> findByTimestampBetweenOrderByTimestampDesc(
            Instant start, Instant end, Pageable pageable);

    /**
     * Find failures
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.outcome IN ('FAILURE', 'DENIED', 'ERROR') ORDER BY a.timestamp DESC")
    Page<AuditEvent> findFailures(Pageable pageable);

    /**
     * Find security events
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.category = 'SECURITY' OR a.outcome = 'DENIED' ORDER BY a.timestamp DESC")
    Page<AuditEvent> findSecurityEvents(Pageable pageable);

    /**
     * Find authentication events for a user
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.category = 'AUTHENTICATION' AND a.username = :username ORDER BY a.timestamp DESC")
    Page<AuditEvent> findAuthenticationEventsForUser(@Param("username") String username, Pageable pageable);

    /**
     * Find transfer events
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.category = 'TRANSFER' ORDER BY a.timestamp DESC")
    Page<AuditEvent> findTransferEvents(Pageable pageable);

    /**
     * Search with multiple criteria
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
            "(:category IS NULL OR a.category = :category) AND " +
            "(:eventType IS NULL OR a.eventType = :eventType) AND " +
            "(:outcome IS NULL OR a.outcome = :outcome) AND " +
            "(:username IS NULL OR a.username = :username) AND " +
            "(:partnerId IS NULL OR a.partnerId = :partnerId) AND " +
            "(:clientIp IS NULL OR a.clientIp = :clientIp) AND " +
            "(:startTime IS NULL OR a.timestamp >= :startTime) AND " +
            "(:endTime IS NULL OR a.timestamp <= :endTime) " +
            "ORDER BY a.timestamp DESC")
    Page<AuditEvent> search(
            @Param("category") AuditCategory category,
            @Param("eventType") AuditEventType eventType,
            @Param("outcome") AuditOutcome outcome,
            @Param("username") String username,
            @Param("partnerId") String partnerId,
            @Param("clientIp") String clientIp,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable);

    /**
     * Count by category
     */
    long countByCategory(AuditCategory category);

    /**
     * Count by outcome
     */
    long countByOutcome(AuditOutcome outcome);

    /**
     * Count by category and time range
     */
    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.category = :category AND a.timestamp >= :since")
    long countByCategorySince(@Param("category") AuditCategory category, @Param("since") Instant since);

    /**
     * Count failures since
     */
    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.outcome IN ('FAILURE', 'DENIED', 'ERROR') AND a.timestamp >= :since")
    long countFailuresSince(@Param("since") Instant since);

    /**
     * Get event counts by category
     */
    @Query("SELECT a.category, COUNT(a) FROM AuditEvent a WHERE a.timestamp >= :since GROUP BY a.category")
    List<Object[]> countByCategories(@Param("since") Instant since);

    /**
     * Get event counts by outcome
     */
    @Query("SELECT a.outcome, COUNT(a) FROM AuditEvent a WHERE a.timestamp >= :since GROUP BY a.outcome")
    List<Object[]> countByOutcomes(@Param("since") Instant since);

    /**
     * Delete old events
     */
    @Modifying
    @Query("DELETE FROM AuditEvent a WHERE a.timestamp < :before")
    int deleteOldEvents(@Param("before") Instant before);
}
