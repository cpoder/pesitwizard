package com.vectis.server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vectis.server.entity.SecretEntry;
import com.vectis.server.entity.SecretEntry.SecretScope;
import com.vectis.server.entity.SecretEntry.SecretType;

/**
 * Repository for secret management.
 */
@Repository
public interface SecretRepository extends JpaRepository<SecretEntry, Long> {

    /**
     * Find by name
     */
    Optional<SecretEntry> findByName(String name);

    /**
     * Find active secret by name
     */
    Optional<SecretEntry> findByNameAndActiveTrue(String name);

    /**
     * Find by type
     */
    List<SecretEntry> findBySecretTypeOrderByNameAsc(SecretType type);

    /**
     * Find by scope
     */
    List<SecretEntry> findByScopeOrderByNameAsc(SecretScope scope);

    /**
     * Find global secrets
     */
    @Query("SELECT s FROM SecretEntry s WHERE s.scope = 'GLOBAL' AND s.active = true ORDER BY s.name")
    List<SecretEntry> findActiveGlobalSecrets();

    /**
     * Find secrets for a partner
     */
    @Query("SELECT s FROM SecretEntry s WHERE (s.scope = 'GLOBAL' OR (s.scope = 'PARTNER' AND s.partnerId = :partnerId)) AND s.active = true ORDER BY s.name")
    List<SecretEntry> findActiveSecretsForPartner(@Param("partnerId") String partnerId);

    /**
     * Find secrets for a server
     */
    @Query("SELECT s FROM SecretEntry s WHERE (s.scope = 'GLOBAL' OR (s.scope = 'SERVER' AND s.serverId = :serverId)) AND s.active = true ORDER BY s.name")
    List<SecretEntry> findActiveSecretsForServer(@Param("serverId") String serverId);

    /**
     * Find by partner ID
     */
    List<SecretEntry> findByPartnerIdAndActiveTrue(String partnerId);

    /**
     * Find by server ID
     */
    List<SecretEntry> findByServerIdAndActiveTrue(String serverId);

    /**
     * Check if name exists
     */
    boolean existsByName(String name);

    /**
     * Find expired secrets
     */
    @Query("SELECT s FROM SecretEntry s WHERE s.active = true AND s.expiresAt IS NOT NULL AND s.expiresAt < CURRENT_TIMESTAMP")
    List<SecretEntry> findExpiredSecrets();

    /**
     * Count by type
     */
    long countBySecretType(SecretType type);

    /**
     * Count active secrets
     */
    long countByActiveTrue();
}
