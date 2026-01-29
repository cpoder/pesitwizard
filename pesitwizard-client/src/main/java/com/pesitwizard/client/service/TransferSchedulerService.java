package com.pesitwizard.client.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pesitwizard.client.dto.TransferRequest;
import com.pesitwizard.client.entity.ScheduledTransfer;
import com.pesitwizard.client.entity.ScheduledTransfer.RunStatus;
import com.pesitwizard.client.entity.ScheduledTransfer.ScheduleType;
import com.pesitwizard.client.entity.TransferHistory.TransferDirection;
import com.pesitwizard.client.repository.BusinessCalendarRepository;
import com.pesitwizard.client.repository.FavoriteTransferRepository;
import com.pesitwizard.client.repository.ScheduledTransferRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing and executing scheduled transfers
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSchedulerService {

    private final ScheduledTransferRepository scheduleRepository;
    private final FavoriteTransferRepository favoriteRepository;
    private final BusinessCalendarRepository calendarRepository;
    private final TransferService transferService;

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Paris");

    /**
     * Check for due schedules every minute
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processDueSchedules() {
        List<ScheduledTransfer> dueSchedules = scheduleRepository.findDueSchedules(Instant.now());

        for (ScheduledTransfer schedule : dueSchedules) {
            try {
                // Check if we should skip due to working days constraint
                if (schedule.isWorkingDaysOnly() && !isWorkingDay(schedule)) {
                    log.info("Skipping schedule {} - not a working day", schedule.getName());
                    skipToNextWorkingDay(schedule);
                    continue;
                }

                log.info("Executing scheduled transfer: {}", schedule.getName());
                executeSchedule(schedule);
            } catch (Exception e) {
                log.error("Failed to execute schedule {}: {}", schedule.getId(), e.getMessage());
            }
        }
    }

    /**
     * Check if today is a working day for this schedule
     */
    private boolean isWorkingDay(ScheduledTransfer schedule) {
        if (schedule.getCalendarId() == null) {
            // Default: Mon-Fri are working days
            int dayOfWeek = LocalDate.now(DEFAULT_ZONE).getDayOfWeek().getValue();
            return dayOfWeek >= 1 && dayOfWeek <= 5;
        }

        return calendarRepository.findById(schedule.getCalendarId())
                .map(cal -> cal.isWorkingDay(LocalDate.now(ZoneId.of(cal.getTimezone()))))
                .orElse(true);
    }

    /**
     * Skip to next working day
     */
    private void skipToNextWorkingDay(ScheduledTransfer schedule) {
        calculateNextRunTime(schedule);
        scheduleRepository.save(schedule);
    }

    /**
     * Execute a scheduled transfer
     */
    @Transactional
    public void executeSchedule(ScheduledTransfer schedule) {
        schedule.setLastRunStatus(RunStatus.RUNNING);
        scheduleRepository.save(schedule);

        try {
            // Use filename if available, fallback to deprecated localPath
            String filename = schedule.getFilename() != null ? schedule.getFilename() : schedule.getLocalPath();
            TransferRequest request = TransferRequest.builder()
                    .server(schedule.getServerId())
                    .partnerId(schedule.getPartnerId())
                    .filename(filename)
                    .sourceConnectionId(schedule.getSourceConnectionId())
                    .destinationConnectionId(schedule.getDestinationConnectionId())
                    .remoteFilename(schedule.getRemoteFilename())
                    .virtualFile(schedule.getVirtualFile())
                    .transferConfig(schedule.getTransferConfigId())
                    .build();

            if (schedule.getDirection() == TransferDirection.SEND) {
                transferService.sendFile(request);
            } else if (schedule.getDirection() == TransferDirection.RECEIVE) {
                transferService.receiveFile(request);
            }

            schedule.markSuccess();
            log.info("Scheduled transfer {} completed successfully", schedule.getName());

        } catch (Exception e) {
            schedule.markFailed(e.getMessage());
            log.error("Scheduled transfer {} failed: {}", schedule.getName(), e.getMessage());
        }

        // Calculate next run time
        calculateNextRunTime(schedule);
        scheduleRepository.save(schedule);
    }

    /**
     * Calculate the next run time based on schedule type
     */
    private void calculateNextRunTime(ScheduledTransfer schedule) {
        ZonedDateTime now = ZonedDateTime.now(DEFAULT_ZONE);

        switch (schedule.getScheduleType()) {
            case ONCE -> {
                // Disable after single run
                schedule.setEnabled(false);
                schedule.setNextRunAt(null);
            }
            case INTERVAL -> {
                if (schedule.getIntervalMinutes() != null && schedule.getIntervalMinutes() > 0) {
                    Instant next = now.toInstant().plus(schedule.getIntervalMinutes(), ChronoUnit.MINUTES);
                    schedule.setNextRunAt(adjustForWorkingDays(schedule, next));
                }
            }
            case HOURLY -> {
                Instant next = now.toInstant().plus(1, ChronoUnit.HOURS);
                schedule.setNextRunAt(adjustForWorkingDays(schedule, next));
            }
            case DAILY -> {
                // Use dailyTime if set, otherwise same time tomorrow
                LocalTime time = schedule.getDailyTime() != null ? schedule.getDailyTime() : now.toLocalTime();
                ZonedDateTime nextRun = now.plusDays(1).with(time);
                schedule.setNextRunAt(adjustForWorkingDays(schedule, nextRun.toInstant()));
            }
            case WEEKLY -> {
                // Find next occurrence of the specified day of week
                int targetDay = schedule.getDayOfWeek() != null ? schedule.getDayOfWeek()
                        : now.getDayOfWeek().getValue();
                ZonedDateTime nextRun = now.plusWeeks(1);
                // Adjust to target day of week
                int currentDay = nextRun.getDayOfWeek().getValue();
                int daysToAdd = (targetDay - currentDay + 7) % 7;
                if (daysToAdd == 0)
                    daysToAdd = 7; // If same day, go to next week
                nextRun = now.plusDays(daysToAdd);
                if (schedule.getDailyTime() != null) {
                    nextRun = nextRun.with(schedule.getDailyTime());
                }
                schedule.setNextRunAt(adjustForWorkingDays(schedule, nextRun.toInstant()));
            }
            case MONTHLY -> {
                // Find next occurrence of the specified day of month
                int targetDayOfMonth = schedule.getDayOfMonth() != null ? schedule.getDayOfMonth()
                        : now.getDayOfMonth();
                ZonedDateTime nextRun = now.plusMonths(1)
                        .withDayOfMonth(Math.min(targetDayOfMonth, now.plusMonths(1).toLocalDate().lengthOfMonth()));
                if (schedule.getDailyTime() != null) {
                    nextRun = nextRun.with(schedule.getDailyTime());
                }
                schedule.setNextRunAt(adjustForWorkingDays(schedule, nextRun.toInstant()));
            }
            case CRON -> {
                // Use Spring's CronExpression for proper cron parsing
                if (schedule.getCronExpression() != null) {
                    try {
                        CronExpression cron = CronExpression.parse(schedule.getCronExpression());
                        ZonedDateTime next = cron.next(now);
                        if (next != null) {
                            schedule.setNextRunAt(adjustForWorkingDays(schedule, next.toInstant()));
                        }
                    } catch (Exception e) {
                        log.error("Invalid cron expression: {}", schedule.getCronExpression());
                        schedule.setNextRunAt(now.plusDays(1).toInstant());
                    }
                } else {
                    schedule.setNextRunAt(now.plusDays(1).toInstant());
                }
            }
        }
    }

    /**
     * Adjust next run time to skip non-working days if required
     */
    private Instant adjustForWorkingDays(ScheduledTransfer schedule, Instant proposedTime) {
        if (!schedule.isWorkingDaysOnly()) {
            return proposedTime;
        }

        ZoneId zone = DEFAULT_ZONE;
        if (schedule.getCalendarId() != null) {
            zone = calendarRepository.findById(schedule.getCalendarId())
                    .map(cal -> ZoneId.of(cal.getTimezone()))
                    .orElse(DEFAULT_ZONE);
        }

        LocalDate date = proposedTime.atZone(zone).toLocalDate();
        LocalTime time = proposedTime.atZone(zone).toLocalTime();

        // Find next working day (max 14 days ahead to avoid infinite loop)
        for (int i = 0; i < 14; i++) {
            if (isWorkingDayForDate(schedule, date)) {
                return date.atTime(time).atZone(zone).toInstant();
            }
            date = date.plusDays(1);
        }

        return proposedTime; // Fallback
    }

    /**
     * Check if a specific date is a working day
     */
    private boolean isWorkingDayForDate(ScheduledTransfer schedule, LocalDate date) {
        if (schedule.getCalendarId() == null) {
            int dayOfWeek = date.getDayOfWeek().getValue();
            return dayOfWeek >= 1 && dayOfWeek <= 5;
        }

        return calendarRepository.findById(schedule.getCalendarId())
                .map(cal -> cal.isWorkingDay(date))
                .orElse(true);
    }

    /**
     * Get all schedules
     */
    public List<ScheduledTransfer> getAllSchedules() {
        return scheduleRepository.findAllByOrderByNextRunAtAsc();
    }

    /**
     * Get a schedule by ID
     */
    public Optional<ScheduledTransfer> getSchedule(String id) {
        return scheduleRepository.findById(id);
    }

    /**
     * Create a new schedule
     */
    @Transactional
    public ScheduledTransfer createSchedule(ScheduledTransfer schedule) {
        // Set initial next run time based on schedule type and configured time
        if (schedule.getNextRunAt() == null) {
            Instant initialRunTime = calculateInitialNextRunTime(schedule);
            schedule.setNextRunAt(initialRunTime);
        }
        log.info("Creating schedule: {} - next run at {}", schedule.getName(), schedule.getNextRunAt());
        return scheduleRepository.save(schedule);
    }

    /**
     * Calculate the initial next run time when creating a new schedule.
     * For DAILY, WEEKLY, MONTHLY: uses the configured time (dailyTime) instead of NOW.
     */
    private Instant calculateInitialNextRunTime(ScheduledTransfer schedule) {
        ZonedDateTime now = ZonedDateTime.now(DEFAULT_ZONE);

        switch (schedule.getScheduleType()) {
            case ONCE -> {
                // Use the explicitly scheduled time
                return schedule.getScheduledAt() != null ? schedule.getScheduledAt() : now.toInstant();
            }
            case INTERVAL -> {
                // Start after the interval duration
                if (schedule.getIntervalMinutes() != null && schedule.getIntervalMinutes() > 0) {
                    return now.toInstant().plus(schedule.getIntervalMinutes(), ChronoUnit.MINUTES);
                }
                return now.toInstant();
            }
            case HOURLY -> {
                // Start at the next hour boundary
                return now.plusHours(1).truncatedTo(ChronoUnit.HOURS).toInstant();
            }
            case DAILY -> {
                // Use dailyTime - if the time has passed today, schedule for tomorrow
                LocalTime targetTime = schedule.getDailyTime() != null ? schedule.getDailyTime() : LocalTime.of(9, 0);
                ZonedDateTime targetDateTime = now.toLocalDate().atTime(targetTime).atZone(DEFAULT_ZONE);
                if (targetDateTime.isBefore(now) || targetDateTime.equals(now)) {
                    // Time already passed today, schedule for tomorrow
                    targetDateTime = targetDateTime.plusDays(1);
                }
                return adjustForWorkingDays(schedule, targetDateTime.toInstant());
            }
            case WEEKLY -> {
                // Use dayOfWeek and dailyTime
                int targetDay = schedule.getDayOfWeek() != null ? schedule.getDayOfWeek() : 1; // Default Monday
                LocalTime targetTime = schedule.getDailyTime() != null ? schedule.getDailyTime() : LocalTime.of(9, 0);

                int currentDay = now.getDayOfWeek().getValue();
                int daysToAdd = (targetDay - currentDay + 7) % 7;
                ZonedDateTime targetDateTime = now.toLocalDate().plusDays(daysToAdd).atTime(targetTime).atZone(DEFAULT_ZONE);

                // If same day but time has passed, go to next week
                if (daysToAdd == 0 && (targetDateTime.isBefore(now) || targetDateTime.equals(now))) {
                    targetDateTime = targetDateTime.plusWeeks(1);
                }
                return adjustForWorkingDays(schedule, targetDateTime.toInstant());
            }
            case MONTHLY -> {
                // Use dayOfMonth and dailyTime
                int targetDayOfMonth = schedule.getDayOfMonth() != null ? schedule.getDayOfMonth() : 1;
                LocalTime targetTime = schedule.getDailyTime() != null ? schedule.getDailyTime() : LocalTime.of(9, 0);

                LocalDate targetDate = now.toLocalDate().withDayOfMonth(
                    Math.min(targetDayOfMonth, now.toLocalDate().lengthOfMonth()));
                ZonedDateTime targetDateTime = targetDate.atTime(targetTime).atZone(DEFAULT_ZONE);

                // If the day has passed this month, go to next month
                if (targetDateTime.isBefore(now) || targetDateTime.equals(now)) {
                    LocalDate nextMonth = now.toLocalDate().plusMonths(1);
                    targetDate = nextMonth.withDayOfMonth(Math.min(targetDayOfMonth, nextMonth.lengthOfMonth()));
                    targetDateTime = targetDate.atTime(targetTime).atZone(DEFAULT_ZONE);
                }
                return adjustForWorkingDays(schedule, targetDateTime.toInstant());
            }
            case CRON -> {
                // Use Spring's CronExpression
                if (schedule.getCronExpression() != null) {
                    try {
                        CronExpression cron = CronExpression.parse(schedule.getCronExpression());
                        ZonedDateTime next = cron.next(now);
                        if (next != null) {
                            return adjustForWorkingDays(schedule, next.toInstant());
                        }
                    } catch (Exception e) {
                        log.error("Invalid cron expression: {}", schedule.getCronExpression());
                    }
                }
                return now.plusDays(1).toInstant();
            }
            default -> {
                return now.toInstant();
            }
        }
    }

    /**
     * Create a schedule from a favorite
     */
    @Transactional
    public Optional<ScheduledTransfer> createFromFavorite(String favoriteId, ScheduleType type,
            Instant scheduledAt, Integer intervalMinutes) {
        return favoriteRepository.findById(favoriteId)
                .map(favorite -> {
                    String filename = favorite.getFilename() != null ? favorite.getFilename() : favorite.getLocalPath();
                    ScheduledTransfer schedule = ScheduledTransfer.builder()
                            .name("Schedule: " + favorite.getName())
                            .favoriteId(favoriteId)
                            .serverId(favorite.getServerId())
                            .serverName(favorite.getServerName())
                            .partnerId(favorite.getPartnerId())
                            .direction(favorite.getDirection())
                            .filename(filename)
                            .sourceConnectionId(favorite.getSourceConnectionId())
                            .destinationConnectionId(favorite.getDestinationConnectionId())
                            .remoteFilename(favorite.getRemoteFilename())
                            .virtualFile(favorite.getVirtualFile())
                            .transferConfigId(favorite.getTransferConfigId())
                            .scheduleType(type)
                            .scheduledAt(scheduledAt)
                            .intervalMinutes(intervalMinutes)
                            .build();

                    // Set next run time
                    if (type == ScheduleType.ONCE && scheduledAt != null) {
                        schedule.setNextRunAt(scheduledAt);
                    } else if (type == ScheduleType.INTERVAL && intervalMinutes != null) {
                        schedule.setNextRunAt(Instant.now().plus(intervalMinutes, ChronoUnit.MINUTES));
                    } else {
                        schedule.setNextRunAt(Instant.now());
                    }

                    log.info("Creating schedule from favorite: {}", favorite.getName());
                    return scheduleRepository.save(schedule);
                });
    }

    /**
     * Update a schedule
     */
    @Transactional
    public Optional<ScheduledTransfer> updateSchedule(String id, ScheduledTransfer updated) {
        return scheduleRepository.findById(id)
                .map(existing -> {
                    existing.setName(updated.getName());
                    existing.setDescription(updated.getDescription());
                    existing.setScheduleType(updated.getScheduleType());
                    existing.setScheduledAt(updated.getScheduledAt());
                    existing.setIntervalMinutes(updated.getIntervalMinutes());
                    existing.setCronExpression(updated.getCronExpression());
                    existing.setEnabled(updated.isEnabled());

                    // Recalculate next run if needed
                    if (updated.isEnabled() && existing.getNextRunAt() == null) {
                        calculateNextRunTime(existing);
                    }

                    log.info("Updated schedule: {}", existing.getName());
                    return scheduleRepository.save(existing);
                });
    }

    /**
     * Delete a schedule
     */
    @Transactional
    public void deleteSchedule(String id) {
        log.info("Deleting schedule: {}", id);
        scheduleRepository.deleteById(id);
    }

    /**
     * Toggle schedule enabled/disabled
     */
    @Transactional
    public Optional<ScheduledTransfer> toggleEnabled(String id) {
        return scheduleRepository.findById(id)
                .map(schedule -> {
                    schedule.setEnabled(!schedule.isEnabled());
                    if (schedule.isEnabled() && schedule.getNextRunAt() == null) {
                        calculateNextRunTime(schedule);
                    }
                    log.info("Schedule {} {}", schedule.getName(),
                            schedule.isEnabled() ? "enabled" : "disabled");
                    return scheduleRepository.save(schedule);
                });
    }

    /**
     * Run a schedule immediately
     */
    @Transactional
    public Optional<ScheduledTransfer> runNow(String id) {
        return scheduleRepository.findById(id)
                .map(schedule -> {
                    executeSchedule(schedule);
                    return schedule;
                });
    }
}
