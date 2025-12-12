package com.vectis.server.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vectis.server.entity.FileChecksum;
import com.vectis.server.entity.FileChecksum.HashAlgorithm;
import com.vectis.server.entity.FileChecksum.TransferDirection;
import com.vectis.server.entity.FileChecksum.VerificationStatus;

/**
 * Repository for file checksum operations.
 */
@Repository
public interface FileChecksumRepository extends JpaRepository<FileChecksum, Long> {

    /**
     * Find by checksum hash
     */
    List<FileChecksum> findByChecksumHash(String checksumHash);

    /**
     * Find by checksum hash and algorithm
     */
    Optional<FileChecksum> findByChecksumHashAndAlgorithm(String checksumHash, HashAlgorithm algorithm);

    /**
     * Find by transfer ID
     */
    Optional<FileChecksum> findByTransferId(String transferId);

    /**
     * Find by filename
     */
    List<FileChecksum> findByFilename(String filename);

    /**
     * Find by partner ID
     */
    Page<FileChecksum> findByPartnerIdOrderByCreatedAtDesc(String partnerId, Pageable pageable);

    /**
     * Find by status
     */
    Page<FileChecksum> findByStatusOrderByCreatedAtDesc(VerificationStatus status, Pageable pageable);

    /**
     * Find duplicates (same hash, multiple entries)
     */
    @Query("SELECT f FROM FileChecksum f WHERE f.checksumHash IN " +
            "(SELECT f2.checksumHash FROM FileChecksum f2 GROUP BY f2.checksumHash HAVING COUNT(f2) > 1) " +
            "ORDER BY f.checksumHash, f.createdAt")
    List<FileChecksum> findDuplicates();

    /**
     * Find files with duplicate count > 0
     */
    List<FileChecksum> findByDuplicateCountGreaterThanOrderByDuplicateCountDesc(int count);

    /**
     * Find pending verifications
     */
    List<FileChecksum> findByStatusOrderByCreatedAtAsc(VerificationStatus status);

    /**
     * Find by direction
     */
    Page<FileChecksum> findByDirectionOrderByCreatedAtDesc(TransferDirection direction, Pageable pageable);

    /**
     * Check if checksum exists
     */
    boolean existsByChecksumHashAndAlgorithm(String checksumHash, HashAlgorithm algorithm);

    /**
     * Count by status
     */
    long countByStatus(VerificationStatus status);

    /**
     * Count duplicates
     */
    @Query("SELECT COUNT(DISTINCT f.checksumHash) FROM FileChecksum f WHERE f.duplicateCount > 0")
    long countDistinctDuplicates();

    /**
     * Find files verified before a certain date (for re-verification)
     */
    List<FileChecksum> findByVerifiedAtBeforeAndStatusOrderByVerifiedAtAsc(
            Instant before, VerificationStatus status);

    /**
     * Find files by partner and time range
     */
    @Query("SELECT f FROM FileChecksum f WHERE f.partnerId = :partnerId " +
            "AND f.createdAt >= :startTime AND f.createdAt <= :endTime " +
            "ORDER BY f.createdAt DESC")
    List<FileChecksum> findByPartnerAndTimeRange(
            @Param("partnerId") String partnerId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    /**
     * Get total bytes by partner
     */
    @Query("SELECT SUM(f.fileSize) FROM FileChecksum f WHERE f.partnerId = :partnerId")
    Long sumFileSizeByPartnerId(@Param("partnerId") String partnerId);

    /**
     * Get checksum statistics
     */
    @Query("SELECT f.status, COUNT(f) FROM FileChecksum f GROUP BY f.status")
    List<Object[]> countByStatusGrouped();

    /**
     * Search by filename pattern
     */
    @Query("SELECT f FROM FileChecksum f WHERE f.filename LIKE %:pattern% ORDER BY f.createdAt DESC")
    Page<FileChecksum> searchByFilename(@Param("pattern") String pattern, Pageable pageable);
}
