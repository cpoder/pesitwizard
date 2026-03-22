package com.pesitwizard.client.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Scheduled transfer configuration */
@Entity
@Table(
        name = "scheduled_transfers",
        indexes = {
            @Index(name = "idx_scheduled_enabled", columnList = "enabled"),
            @Index(name = "idx_scheduled_next_run", columnList = "nextRunAt")
        })
@SuppressWarnings("deprecation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** User-friendly name */
    @Column(nullable = false)
    private String name;

    /** Optional description */
    private String description;

    /** Reference to favorite (optional - can schedule without favorite) */
    private String favoriteId;

    /** Server ID */
    @Column(nullable = false)
    private String serverId;

    /** Server name for display */
    private String serverName;

    /** Partner ID */
    private String partnerId;

    /** Transfer direction */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferHistory.TransferDirection direction;

    /** Source storage connection ID (null = local filesystem) */
    private String sourceConnectionId;

    /** Destination storage connection ID (null = local filesystem) */
    private String destinationConnectionId;

    /** Filename (relative path on connector, or local path) */
    private String filename;

    /**
     * @deprecated Use filename instead
     */
    @Deprecated private String localPath;

    /** Remote filename (virtual file ID) */
    private String remoteFilename;

    /** Virtual file */
    private String virtualFile;

    /** Transfer config ID */
    private String transferConfigId;

    /** Encrypted PeSIT partner password (encrypted via SecretsService) */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;

    /** Computed field indicating whether a password is configured */
    @JsonProperty("passwordConfigured")
    public boolean isPasswordConfigured() {
        return password != null && !password.isEmpty();
    }

    /** Schedule type */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScheduleType scheduleType = ScheduleType.ONCE;

    /** Cron expression for CRON type */
    private String cronExpression;

    /** Interval in minutes for INTERVAL type */
    private Integer intervalMinutes;

    /** Time of day for DAILY schedule (HH:mm) */
    private LocalTime dailyTime;

    /** Day of week for WEEKLY schedule (1=Monday, 7=Sunday) */
    private Integer dayOfWeek;

    /** Day of month for MONTHLY schedule (1-31) */
    private Integer dayOfMonth;

    /** Business calendar ID (for working days only) */
    private String calendarId;

    /** Only run on working days */
    @Builder.Default private boolean workingDaysOnly = false;

    /** Scheduled time for ONCE type */
    private Instant scheduledAt;

    /** Next scheduled run time */
    private Instant nextRunAt;

    /** Last run time */
    private Instant lastRunAt;

    /** Last run status */
    @Enumerated(EnumType.STRING)
    private RunStatus lastRunStatus;

    /** Last run error message */
    @Column(length = 2000)
    private String lastRunError;

    /** Number of successful runs */
    @Builder.Default private int successCount = 0;

    /** Number of failed runs */
    @Builder.Default private int failureCount = 0;

    /** Whether this schedule is enabled */
    @Builder.Default private boolean enabled = true;

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

    public enum ScheduleType {
        ONCE, // Run once at scheduled time
        INTERVAL, // Run every N minutes
        CRON, // Run on cron schedule
        DAILY, // Run daily at specific time
        HOURLY, // Run every hour
        WEEKLY, // Run weekly on specific days
        MONTHLY // Run monthly on specific day
    }

    public enum RunStatus {
        SUCCESS,
        FAILED,
        RUNNING
    }

    public void markSuccess() {
        this.lastRunAt = Instant.now();
        this.lastRunStatus = RunStatus.SUCCESS;
        this.lastRunError = null;
        this.successCount++;
    }

    public void markFailed(String error) {
        this.lastRunAt = Instant.now();
        this.lastRunStatus = RunStatus.FAILED;
        this.lastRunError = error;
        this.failureCount++;
    }
}
