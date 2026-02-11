package com.pesitwizard.client.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * PeSIT partner configuration with optional encrypted password.
 * Partners are pre-configured client identifiers that can be selected
 * from a dropdown in the transfer form instead of typing manually.
 */
@Entity
@Table(name = "partners", indexes = {
        @Index(name = "idx_partner_partner_id", columnList = "partnerId", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** PeSIT partner identifier (PI_03 DEMANDEUR) — must be unique */
    @Column(nullable = false, unique = true)
    private String partnerId;

    /** User-friendly description */
    private String description;

    /** Encrypted PeSIT partner password (encrypted via SecretsService) */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;

    /** Computed field indicating whether a password is configured */
    @JsonProperty("passwordConfigured")
    public boolean isPasswordConfigured() {
        return password != null && !password.isEmpty();
    }

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
}
