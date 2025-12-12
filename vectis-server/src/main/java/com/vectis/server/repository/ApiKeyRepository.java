package com.vectis.server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vectis.server.entity.ApiKey;

/**
 * Repository for API key management.
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /**
     * Find by key hash
     */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * Find by name
     */
    Optional<ApiKey> findByName(String name);

    /**
     * Find by key prefix
     */
    List<ApiKey> findByKeyPrefix(String keyPrefix);

    /**
     * Find all active keys
     */
    List<ApiKey> findByActiveTrue();

    /**
     * Find active key by hash
     */
    @Query("SELECT k FROM ApiKey k WHERE k.keyHash = :hash AND k.active = true")
    Optional<ApiKey> findActiveByKeyHash(@Param("hash") String hash);

    /**
     * Find by partner ID
     */
    List<ApiKey> findByPartnerIdAndActiveTrue(String partnerId);

    /**
     * Check if name exists
     */
    boolean existsByName(String name);

    /**
     * Check if key hash exists
     */
    boolean existsByKeyHash(String keyHash);
}
