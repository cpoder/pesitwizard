package com.pesitwizard.client.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Virtual file definition for PeSIT transfers. Virtual files are logical file identifiers (PI_12)
 * configured on the server. Pre-configuring them here allows dropdown selection in the transfer
 * form.
 */
@Entity
@Table(
        name = "client_virtual_files",
        indexes = {
            @Index(name = "idx_client_virtual_file_name", columnList = "name", unique = true)
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VirtualFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Virtual file name / PeSIT PI_12 identifier (e.g. "DATA_FILE") */
    @Column(nullable = false, unique = true)
    private String name;

    /** User-friendly description */
    private String description;

    /** Transfer direction this virtual file is used for */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Direction direction = Direction.BOTH;

    /** Default record length (PI_32) for this virtual file, if known */
    private Integer recordLength;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum Direction {
        SEND,
        RECEIVE,
        BOTH
    }
}
