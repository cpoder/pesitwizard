package com.pesitwizard.server.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Database-backed record of which cluster node owns each PeSIT server instance.
 * The unique constraint on {@code serverId} (which is the primary key) ensures
 * that only one node can own a server at a time, even when multiple nodes
 * attempt concurrent acquisition.
 */
@Entity
@Table(name = "server_ownership")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerOwnershipRecord {

    /** The server identifier (unique, also primary key) */
    @Id
    @Column(nullable = false, length = 64)
    private String serverId;

    /** The cluster node that owns this server */
    @Column(nullable = false, length = 128)
    private String ownerNode;

    /** When ownership was acquired */
    @Column(nullable = false)
    private Instant acquiredAt;
}
