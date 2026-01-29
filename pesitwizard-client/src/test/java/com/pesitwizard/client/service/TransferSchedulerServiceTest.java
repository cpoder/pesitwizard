package com.pesitwizard.client.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.client.entity.ScheduledTransfer;
import com.pesitwizard.client.entity.ScheduledTransfer.ScheduleType;
import com.pesitwizard.client.entity.TransferHistory.TransferDirection;
import com.pesitwizard.client.repository.BusinessCalendarRepository;
import com.pesitwizard.client.repository.FavoriteTransferRepository;
import com.pesitwizard.client.repository.ScheduledTransferRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferSchedulerService Tests")
class TransferSchedulerServiceTest {

    private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");

    @Mock
    private ScheduledTransferRepository scheduleRepository;
    @Mock
    private FavoriteTransferRepository favoriteRepository;
    @Mock
    private BusinessCalendarRepository calendarRepository;
    @Mock
    private TransferService transferService;

    private TransferSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        schedulerService = new TransferSchedulerService(
                scheduleRepository,
                favoriteRepository,
                calendarRepository,
                transferService);
    }

    @Nested
    @DisplayName("Create Schedule - Initial Next Run Time Calculation")
    class CreateScheduleTests {

        @Test
        @DisplayName("DAILY schedule at 09:30 - should schedule for today if time not passed")
        void dailyScheduleShouldUseConfiguredTime_TodayIfNotPassed() {
            // Given: Current time is 08:00, schedule time is 09:30
            LocalTime targetTime = LocalTime.of(9, 30);
            ZonedDateTime now = ZonedDateTime.now(PARIS_ZONE);

            // Only run this test logic if it's before 09:30 in Paris timezone
            // Otherwise, the behavior would be to schedule for tomorrow
            ZonedDateTime todayTarget = now.toLocalDate().atTime(targetTime).atZone(PARIS_ZONE);
            boolean timeNotPassedYet = now.isBefore(todayTarget);

            ScheduledTransfer schedule = ScheduledTransfer.builder()
                    .name("Test Daily Schedule")
                    .scheduleType(ScheduleType.DAILY)
                    .dailyTime(targetTime)
                    .direction(TransferDirection.SEND)
                    .serverId("test-server")
                    .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            // When
            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            // Then
            assertThat(result.getNextRunAt()).isNotNull();
            ZonedDateTime nextRun = result.getNextRunAt().atZone(PARIS_ZONE);

            // Verify time is 09:30
            assertThat(nextRun.toLocalTime()).isEqualTo(targetTime);

            if (timeNotPassedYet) {
                // Should be scheduled for today
                assertThat(nextRun.toLocalDate()).isEqualTo(now.toLocalDate());
            } else {
                // Should be scheduled for tomorrow
                assertThat(nextRun.toLocalDate()).isEqualTo(now.toLocalDate().plusDays(1));
            }
        }

        @Test
        @DisplayName("DAILY schedule - should NOT use current time + 24h (regression test)")
        void dailyScheduleShouldNotUseCurrent24hOffset() {
            // This is the main regression test for the bug
            // Before fix: nextRunAt was set to Instant.now() which means immediate execution
            // After fix: nextRunAt should be set to the next occurrence of dailyTime

            LocalTime targetTime = LocalTime.of(9, 30);
            ZonedDateTime now = ZonedDateTime.now(PARIS_ZONE);

            ScheduledTransfer schedule = ScheduledTransfer.builder()
                    .name("Test Daily Schedule")
                    .scheduleType(ScheduleType.DAILY)
                    .dailyTime(targetTime)
                    .direction(TransferDirection.SEND)
                    .serverId("test-server")
                    .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            // When
            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            // Then - should NOT be scheduled for NOW (within 1 minute tolerance)
            ZonedDateTime nextRun = result.getNextRunAt().atZone(PARIS_ZONE);

            // The next run time should be at exactly 09:30, not at current time
            assertThat(nextRun.toLocalTime()).isEqualTo(targetTime);

            // Should NOT be within the next few minutes (unless it happens to be 09:30 right now)
            if (!now.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                    .equals(targetTime.truncatedTo(ChronoUnit.MINUTES))) {
                // If current time is not 09:30, then nextRun should not be "now"
                long minutesDiff = Math.abs(ChronoUnit.MINUTES.between(now, nextRun));
                assertThat(minutesDiff).isGreaterThan(5); // At least 5 minutes difference
            }
        }

        @Test
        @DisplayName("ONCE schedule - should use scheduledAt time")
        void onceScheduleShouldUseScheduledAt() {
            Instant scheduledAt = Instant.now().plus(2, ChronoUnit.HOURS);

            ScheduledTransfer schedule = ScheduledTransfer.builder()
                    .name("Test Once Schedule")
                    .scheduleType(ScheduleType.ONCE)
                    .scheduledAt(scheduledAt)
                    .direction(TransferDirection.SEND)
                    .serverId("test-server")
                    .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            // When
            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            // Then
            assertThat(result.getNextRunAt()).isEqualTo(scheduledAt);
        }

        @Test
        @DisplayName("INTERVAL schedule - should start after interval duration")
        void intervalScheduleShouldStartAfterInterval() {
            int intervalMinutes = 30;
            Instant before = Instant.now();

            ScheduledTransfer schedule = ScheduledTransfer.builder()
                    .name("Test Interval Schedule")
                    .scheduleType(ScheduleType.INTERVAL)
                    .intervalMinutes(intervalMinutes)
                    .direction(TransferDirection.SEND)
                    .serverId("test-server")
                    .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            // When
            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            // Then - should be approximately 30 minutes from now
            Instant expectedMin = before.plus(intervalMinutes - 1, ChronoUnit.MINUTES);
            Instant expectedMax = Instant.now().plus(intervalMinutes + 1, ChronoUnit.MINUTES);

            assertThat(result.getNextRunAt())
                    .isAfter(expectedMin)
                    .isBefore(expectedMax);
        }

        @Test
        @DisplayName("WEEKLY schedule - should use configured day of week and time")
        void weeklyScheduleShouldUseConfiguredDayAndTime() {
            LocalTime targetTime = LocalTime.of(14, 0);
            int targetDayOfWeek = 3; // Wednesday

            ScheduledTransfer schedule = ScheduledTransfer.builder()
                    .name("Test Weekly Schedule")
                    .scheduleType(ScheduleType.WEEKLY)
                    .dailyTime(targetTime)
                    .dayOfWeek(targetDayOfWeek)
                    .direction(TransferDirection.SEND)
                    .serverId("test-server")
                    .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            // When
            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            // Then
            assertThat(result.getNextRunAt()).isNotNull();
            ZonedDateTime nextRun = result.getNextRunAt().atZone(PARIS_ZONE);

            // Should be on Wednesday
            assertThat(nextRun.getDayOfWeek().getValue()).isEqualTo(targetDayOfWeek);
            // Should be at 14:00
            assertThat(nextRun.toLocalTime()).isEqualTo(targetTime);
        }

        @Test
        @DisplayName("MONTHLY schedule - should use configured day of month and time")
        void monthlyScheduleShouldUseConfiguredDayAndTime() {
            LocalTime targetTime = LocalTime.of(10, 0);
            int targetDayOfMonth = 15;

            ScheduledTransfer schedule = ScheduledTransfer.builder()
                    .name("Test Monthly Schedule")
                    .scheduleType(ScheduleType.MONTHLY)
                    .dailyTime(targetTime)
                    .dayOfMonth(targetDayOfMonth)
                    .direction(TransferDirection.SEND)
                    .serverId("test-server")
                    .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            // When
            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            // Then
            assertThat(result.getNextRunAt()).isNotNull();
            ZonedDateTime nextRun = result.getNextRunAt().atZone(PARIS_ZONE);

            // Should be on the 15th
            assertThat(nextRun.getDayOfMonth()).isEqualTo(targetDayOfMonth);
            // Should be at 10:00
            assertThat(nextRun.toLocalTime()).isEqualTo(targetTime);
        }

        @Test
        @DisplayName("HOURLY schedule - should start at next hour boundary")
        void hourlyScheduleShouldStartAtNextHour() {
            ZonedDateTime now = ZonedDateTime.now(PARIS_ZONE);

            ScheduledTransfer schedule = ScheduledTransfer.builder()
                    .name("Test Hourly Schedule")
                    .scheduleType(ScheduleType.HOURLY)
                    .direction(TransferDirection.SEND)
                    .serverId("test-server")
                    .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            // When
            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            // Then - should be at the next hour boundary
            assertThat(result.getNextRunAt()).isNotNull();
            ZonedDateTime nextRun = result.getNextRunAt().atZone(PARIS_ZONE);

            // Minutes should be 0 (hour boundary)
            assertThat(nextRun.getMinute()).isZero();
            // Should be at least the next hour
            assertThat(nextRun.getHour()).isGreaterThanOrEqualTo((now.getHour() + 1) % 24);
        }

        @Test
        @DisplayName("CRON schedule - should use cron expression")
        void cronScheduleShouldUseCronExpression() {
            // Every day at 06:00
            String cronExpression = "0 0 6 * * *";

            ScheduledTransfer schedule = ScheduledTransfer.builder()
                    .name("Test Cron Schedule")
                    .scheduleType(ScheduleType.CRON)
                    .cronExpression(cronExpression)
                    .direction(TransferDirection.SEND)
                    .serverId("test-server")
                    .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            // When
            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            // Then
            assertThat(result.getNextRunAt()).isNotNull();
            ZonedDateTime nextRun = result.getNextRunAt().atZone(PARIS_ZONE);

            // Should be at 06:00
            assertThat(nextRun.getHour()).isEqualTo(6);
            assertThat(nextRun.getMinute()).isZero();
        }

        @Test
        @DisplayName("Schedule with nextRunAt already set - should keep existing value")
        void shouldKeepExistingNextRunAt() {
            Instant existingNextRun = Instant.now().plus(5, ChronoUnit.DAYS);

            ScheduledTransfer schedule = ScheduledTransfer.builder()
                    .name("Test Schedule")
                    .scheduleType(ScheduleType.DAILY)
                    .dailyTime(LocalTime.of(9, 30))
                    .nextRunAt(existingNextRun) // Already set
                    .direction(TransferDirection.SEND)
                    .serverId("test-server")
                    .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            // When
            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            // Then - should keep the existing value
            assertThat(result.getNextRunAt()).isEqualTo(existingNextRun);
        }
    }
}
