package com.pesitwizard.server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.pesitwizard.server.entity.ServerOwnershipRecord;

/**
 * Repository for database-backed server ownership records.
 * Used to serialize concurrent ownership acquisition across cluster nodes.
 */
@Repository
public interface ServerOwnershipRepository extends JpaRepository<ServerOwnershipRecord, String> {

    Optional<ServerOwnershipRecord> findByServerId(String serverId);

    List<ServerOwnershipRecord> findByOwnerNode(String ownerNode);

    @Modifying
    @Query("DELETE FROM ServerOwnershipRecord r WHERE r.serverId = :serverId AND r.ownerNode = :ownerNode")
    int deleteByServerIdAndOwnerNode(@Param("serverId") String serverId, @Param("ownerNode") String ownerNode);

    @Modifying
    @Query("DELETE FROM ServerOwnershipRecord r WHERE r.ownerNode = :ownerNode")
    int deleteByOwnerNode(@Param("ownerNode") String ownerNode);
}
