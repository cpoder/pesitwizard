package com.pesitwizard.client.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.client.entity.FavoriteTransfer;
import com.pesitwizard.client.entity.ScheduledTransfer;
import com.pesitwizard.client.entity.ScheduledTransfer.RunStatus;
import com.pesitwizard.client.entity.ScheduledTransfer.ScheduleType;
import com.pesitwizard.client.entity.TransferHistory.TransferDirection;
import com.pesitwizard.client.repository.BusinessCalendarRepository;
import com.pesitwizard.client.repository.FavoriteTransferRepository;
import com.pesitwizard.client.repository.ScheduledTransferRepository;
import com.pesitwizard.security.SecretsService;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferSchedulerService Tests")
class TransferSchedulerServiceTest {

    private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");

    @Mock private ScheduledTransferRepository scheduleRepository;
    @Mock private FavoriteTransferRepository favoriteRepository;
    @Mock private BusinessCalendarRepository calendarRepository;
    @Mock private TransferService transferService;
    @Mock private PartnerService partnerService;
    @Mock private SecretsService secretsService;

    private TransferSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        schedulerService =
                new TransferSchedulerService(
                        scheduleRepository,
                        favoriteRepository,
                        calendarRepository,
                        transferService,
                        partnerService,
                        secretsService);
    }

    @Nested
    @DisplayName("Create Schedule - Initial Next Run Time Calculation")
    class CreateScheduleTests {

        @Test
        @DisplayName("DAILY schedule at 09:30 - should schedule for today if time not passed")
        void dailyScheduleShouldUseConfiguredTime_TodayIfNotPassed() {
            LocalTime targetTime = LocalTime.of(9, 30);
            ZonedDateTime now = ZonedDateTime.now(PARIS_ZONE);

            ZonedDateTime todayTarget = now.toLocalDate().atTime(targetTime).atZone(PARIS_ZONE);
            boolean timeNotPassedYet = now.isBefore(todayTarget);

            ScheduledTransfer schedule =
                    ScheduledTransfer.builder()
                            .name("Test Daily Schedule")
                            .scheduleType(ScheduleType.DAILY)
                            .dailyTime(targetTime)
                            .direction(TransferDirection.SEND)
                            .serverId("test-server")
                            .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            assertThat(result.getNextRunAt()).isNotNull();
            ZonedDateTime nextRun = result.getNextRunAt().atZone(PARIS_ZONE);
            assertThat(nextRun.toLocalTime()).isEqualTo(targetTime);

            if (timeNotPassedYet) {
                assertThat(nextRun.toLocalDate()).isEqualTo(now.toLocalDate());
            } else {
                assertThat(nextRun.toLocalDate()).isEqualTo(now.toLocalDate().plusDays(1));
            }
        }

        @Test
        @DisplayName("ONCE schedule - should use scheduledAt time")
        void onceScheduleShouldUseScheduledAt() {
            Instant scheduledAt = Instant.now().plus(2, ChronoUnit.HOURS);

            ScheduledTransfer schedule =
                    ScheduledTransfer.builder()
                            .name("Test Once Schedule")
                            .scheduleType(ScheduleType.ONCE)
                            .scheduledAt(scheduledAt)
                            .direction(TransferDirection.SEND)
                            .serverId("test-server")
                            .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            assertThat(result.getNextRunAt()).isEqualTo(scheduledAt);
        }

        @Test
        @DisplayName("INTERVAL schedule - should start after interval duration")
        void intervalScheduleShouldStartAfterInterval() {
            int intervalMinutes = 30;
            Instant before = Instant.now();

            ScheduledTransfer schedule =
                    ScheduledTransfer.builder()
                            .name("Test Interval Schedule")
                            .scheduleType(ScheduleType.INTERVAL)
                            .intervalMinutes(intervalMinutes)
                            .direction(TransferDirection.SEND)
                            .serverId("test-server")
                            .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            Instant expectedMin = before.plus(intervalMinutes - 1, ChronoUnit.MINUTES);
            Instant expectedMax = Instant.now().plus(intervalMinutes + 1, ChronoUnit.MINUTES);
            assertThat(result.getNextRunAt()).isAfter(expectedMin).isBefore(expectedMax);
        }

        @Test
        @DisplayName("WEEKLY schedule - should use configured day of week and time")
        void weeklyScheduleShouldUseConfiguredDayAndTime() {
            LocalTime targetTime = LocalTime.of(14, 0);
            int targetDayOfWeek = 3;

            ScheduledTransfer schedule =
                    ScheduledTransfer.builder()
                            .name("Test Weekly Schedule")
                            .scheduleType(ScheduleType.WEEKLY)
                            .dailyTime(targetTime)
                            .dayOfWeek(targetDayOfWeek)
                            .direction(TransferDirection.SEND)
                            .serverId("test-server")
                            .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            assertThat(result.getNextRunAt()).isNotNull();
            ZonedDateTime nextRun = result.getNextRunAt().atZone(PARIS_ZONE);
            assertThat(nextRun.getDayOfWeek().getValue()).isEqualTo(targetDayOfWeek);
            assertThat(nextRun.toLocalTime()).isEqualTo(targetTime);
        }

        @Test
        @DisplayName("MONTHLY schedule - should use configured day of month and time")
        void monthlyScheduleShouldUseConfiguredDayAndTime() {
            LocalTime targetTime = LocalTime.of(10, 0);
            int targetDayOfMonth = 15;

            ScheduledTransfer schedule =
                    ScheduledTransfer.builder()
                            .name("Test Monthly Schedule")
                            .scheduleType(ScheduleType.MONTHLY)
                            .dailyTime(targetTime)
                            .dayOfMonth(targetDayOfMonth)
                            .direction(TransferDirection.SEND)
                            .serverId("test-server")
                            .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            assertThat(result.getNextRunAt()).isNotNull();
            ZonedDateTime nextRun = result.getNextRunAt().atZone(PARIS_ZONE);
            assertThat(nextRun.getDayOfMonth()).isEqualTo(targetDayOfMonth);
            assertThat(nextRun.toLocalTime()).isEqualTo(targetTime);
        }

        @Test
        @DisplayName("HOURLY schedule - should start at next hour boundary")
        void hourlyScheduleShouldStartAtNextHour() {
            ScheduledTransfer schedule =
                    ScheduledTransfer.builder()
                            .name("Test Hourly Schedule")
                            .scheduleType(ScheduleType.HOURLY)
                            .direction(TransferDirection.SEND)
                            .serverId("test-server")
                            .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            assertThat(result.getNextRunAt()).isNotNull();
            ZonedDateTime nextRun = result.getNextRunAt().atZone(PARIS_ZONE);
            assertThat(nextRun.getMinute()).isZero();
        }

        @Test
        @DisplayName("CRON schedule - should use cron expression")
        void cronScheduleShouldUseCronExpression() {
            ScheduledTransfer schedule =
                    ScheduledTransfer.builder()
                            .name("Test Cron Schedule")
                            .scheduleType(ScheduleType.CRON)
                            .cronExpression("0 0 6 * * *")
                            .direction(TransferDirection.SEND)
                            .serverId("test-server")
                            .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            assertThat(result.getNextRunAt()).isNotNull();
            ZonedDateTime nextRun = result.getNextRunAt().atZone(PARIS_ZONE);
            assertThat(nextRun.getHour()).isEqualTo(6);
            assertThat(nextRun.getMinute()).isZero();
        }

        @Test
        @DisplayName("Schedule with nextRunAt already set - should keep existing value")
        void shouldKeepExistingNextRunAt() {
            Instant existingNextRun = Instant.now().plus(5, ChronoUnit.DAYS);

            ScheduledTransfer schedule =
                    ScheduledTransfer.builder()
                            .name("Test Schedule")
                            .scheduleType(ScheduleType.DAILY)
                            .dailyTime(LocalTime.of(9, 30))
                            .nextRunAt(existingNextRun)
                            .direction(TransferDirection.SEND)
                            .serverId("test-server")
                            .build();

            when(scheduleRepository.save(any(ScheduledTransfer.class)))
                    .thenAnswer(i -> i.getArgument(0));

            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            assertThat(result.getNextRunAt()).isEqualTo(existingNextRun);
        }

        @Test
        void createSchedule_encryptsPassword() {
            ScheduledTransfer schedule =
                    ScheduledTransfer.builder()
                            .name("test")
                            .scheduleType(ScheduleType.ONCE)
                            .scheduledAt(Instant.now().plus(1, ChronoUnit.HOURS))
                            .direction(TransferDirection.SEND)
                            .serverId("srv1")
                            .password("secret")
                            .build();

            when(secretsService.isEncrypted("secret")).thenReturn(false);
            when(secretsService.encryptForStorage("secret", "schedule", "test", "password"))
                    .thenReturn("ENC:xxx");
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ScheduledTransfer result = schedulerService.createSchedule(schedule);

            assertThat(result.getPassword()).isEqualTo("ENC:xxx");
        }
    }

    @Nested
    @DisplayName("CRUD Operations")
    class CrudTests {

        @Test
        void getAllSchedules() {
            when(scheduleRepository.findAllByOrderByNextRunAtAsc()).thenReturn(List.of());

            assertThat(schedulerService.getAllSchedules()).isEmpty();
        }

        @Test
        void getSchedule_found() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setName("test");
            when(scheduleRepository.findById("1")).thenReturn(Optional.of(schedule));

            assertThat(schedulerService.getSchedule("1")).isPresent();
        }

        @Test
        void getSchedule_notFound() {
            when(scheduleRepository.findById("missing")).thenReturn(Optional.empty());

            assertThat(schedulerService.getSchedule("missing")).isEmpty();
        }

        @Test
        void updateSchedule_found() {
            ScheduledTransfer existing = new ScheduledTransfer();
            existing.setId("1");
            existing.setName("old");
            existing.setScheduleType(ScheduleType.ONCE);

            ScheduledTransfer updated = new ScheduledTransfer();
            updated.setName("new");
            updated.setScheduleType(ScheduleType.DAILY);
            updated.setEnabled(true);

            when(scheduleRepository.findById("1")).thenReturn(Optional.of(existing));
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<ScheduledTransfer> result = schedulerService.updateSchedule("1", updated);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("new");
        }

        @Test
        void updateSchedule_notFound() {
            when(scheduleRepository.findById("missing")).thenReturn(Optional.empty());

            assertThat(schedulerService.updateSchedule("missing", new ScheduledTransfer()))
                    .isEmpty();
        }

        @Test
        void deleteSchedule() {
            schedulerService.deleteSchedule("1");

            verify(scheduleRepository).deleteById("1");
        }

        @Test
        void toggleEnabled_enableToDisable() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setEnabled(true);
            schedule.setScheduleType(ScheduleType.DAILY);

            when(scheduleRepository.findById("1")).thenReturn(Optional.of(schedule));
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<ScheduledTransfer> result = schedulerService.toggleEnabled("1");

            assertThat(result).isPresent();
            assertThat(result.get().isEnabled()).isFalse();
        }

        @Test
        void toggleEnabled_disableToEnable_calculatesNextRun() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setEnabled(false);
            schedule.setScheduleType(ScheduleType.DAILY);
            schedule.setNextRunAt(null);

            when(scheduleRepository.findById("1")).thenReturn(Optional.of(schedule));
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<ScheduledTransfer> result = schedulerService.toggleEnabled("1");

            assertThat(result).isPresent();
            assertThat(result.get().isEnabled()).isTrue();
            assertThat(result.get().getNextRunAt()).isNotNull();
        }

        @Test
        void toggleEnabled_notFound() {
            when(scheduleRepository.findById("missing")).thenReturn(Optional.empty());

            assertThat(schedulerService.toggleEnabled("missing")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Execute Schedule")
    class ExecuteTests {

        @Test
        void executeSchedule_sendSuccess() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setPartnerId("PART1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.ONCE);

            when(partnerService.resolvePassword("PART1")).thenReturn("pwd");
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.executeSchedule(schedule);

            assertThat(schedule.getLastRunStatus()).isEqualTo(RunStatus.SUCCESS);
            verify(transferService).sendFile(any());
        }

        @Test
        void executeSchedule_receiveSuccess() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.RECEIVE);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.ONCE);
            schedule.setPassword("ENC:xxx");

            when(secretsService.decryptFromStorage("ENC:xxx")).thenReturn("secret");
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.executeSchedule(schedule);

            assertThat(schedule.getLastRunStatus()).isEqualTo(RunStatus.SUCCESS);
            verify(transferService).receiveFile(any());
        }

        @Test
        void executeSchedule_failure_marksFailed() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.ONCE);

            when(partnerService.resolvePassword(any())).thenReturn(null);
            when(transferService.sendFile(any()))
                    .thenThrow(new RuntimeException("Connection refused"));
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.executeSchedule(schedule);

            assertThat(schedule.getLastRunStatus()).isEqualTo(RunStatus.FAILED);
            assertThat(schedule.getLastRunError()).contains("Connection refused");
        }

        @Test
        void executeSchedule_onceType_disablesAfterRun() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.ONCE);

            when(partnerService.resolvePassword(any())).thenReturn(null);
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.executeSchedule(schedule);

            assertThat(schedule.isEnabled()).isFalse();
            assertThat(schedule.getNextRunAt()).isNull();
        }

        @Test
        void executeSchedule_intervalType_setsNextRun() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.INTERVAL);
            schedule.setIntervalMinutes(60);

            when(partnerService.resolvePassword(any())).thenReturn(null);
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.executeSchedule(schedule);

            assertThat(schedule.getNextRunAt()).isNotNull();
            assertThat(schedule.getNextRunAt()).isAfter(Instant.now().plus(55, ChronoUnit.MINUTES));
        }

        @Test
        void executeSchedule_hourlyType_setsNextRun() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.HOURLY);

            when(partnerService.resolvePassword(any())).thenReturn(null);
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.executeSchedule(schedule);

            assertThat(schedule.getNextRunAt()).isNotNull();
        }

        @Test
        void executeSchedule_dailyType_setsNextRun() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.DAILY);

            when(partnerService.resolvePassword(any())).thenReturn(null);
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.executeSchedule(schedule);

            assertThat(schedule.getNextRunAt()).isNotNull();
        }

        @Test
        void executeSchedule_weeklyType_setsNextRun() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.WEEKLY);
            schedule.setDayOfWeek(3);

            when(partnerService.resolvePassword(any())).thenReturn(null);
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.executeSchedule(schedule);

            assertThat(schedule.getNextRunAt()).isNotNull();
        }

        @Test
        void executeSchedule_monthlyType_setsNextRun() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.MONTHLY);
            schedule.setDayOfMonth(15);

            when(partnerService.resolvePassword(any())).thenReturn(null);
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.executeSchedule(schedule);

            assertThat(schedule.getNextRunAt()).isNotNull();
        }

        @Test
        void executeSchedule_cronType_setsNextRun() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.CRON);
            schedule.setCronExpression("0 0 * * * *");

            when(partnerService.resolvePassword(any())).thenReturn(null);
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.executeSchedule(schedule);

            assertThat(schedule.getNextRunAt()).isNotNull();
        }

        @Test
        void executeSchedule_invalidCron_fallsBack() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.CRON);
            schedule.setCronExpression("invalid");

            when(partnerService.resolvePassword(any())).thenReturn(null);
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.executeSchedule(schedule);

            assertThat(schedule.getNextRunAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Process Due Schedules")
    class ProcessDueTests {

        @Test
        void processDueSchedules_executesDueSchedules() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("due");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.ONCE);

            when(scheduleRepository.findDueSchedules(any())).thenReturn(List.of(schedule));
            when(partnerService.resolvePassword(any())).thenReturn(null);
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            schedulerService.processDueSchedules();

            verify(transferService).sendFile(any());
        }

        @Test
        void processDueSchedules_noDueSchedules() {
            when(scheduleRepository.findDueSchedules(any())).thenReturn(List.of());

            schedulerService.processDueSchedules();

            verify(transferService, never()).sendFile(any());
        }

        @Test
        void processDueSchedules_handlesException() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("bad");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setScheduleType(ScheduleType.ONCE);

            when(scheduleRepository.findDueSchedules(any())).thenReturn(List.of(schedule));
            when(scheduleRepository.save(any())).thenThrow(new RuntimeException("DB error"));

            // Should not propagate exception
            schedulerService.processDueSchedules();
        }
    }

    @Nested
    @DisplayName("Create From Favorite")
    class CreateFromFavoriteTests {

        @Test
        void createFromFavorite_found() {
            FavoriteTransfer fav = new FavoriteTransfer();
            fav.setId("f1");
            fav.setName("My Favorite");
            fav.setServerId("srv1");
            fav.setServerName("Server1");
            fav.setPartnerId("PART1");
            fav.setDirection(TransferDirection.SEND);
            fav.setFilename("file.txt");

            when(favoriteRepository.findById("f1")).thenReturn(Optional.of(fav));
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<ScheduledTransfer> result =
                    schedulerService.createFromFavorite("f1", ScheduleType.INTERVAL, null, 30);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).contains("My Favorite");
            assertThat(result.get().getServerId()).isEqualTo("srv1");
        }

        @Test
        void createFromFavorite_notFound() {
            when(favoriteRepository.findById("missing")).thenReturn(Optional.empty());

            assertThat(
                            schedulerService.createFromFavorite(
                                    "missing", ScheduleType.ONCE, null, null))
                    .isEmpty();
        }

        @Test
        void createFromFavorite_onceType_usesScheduledAt() {
            Instant scheduledAt = Instant.now().plus(1, ChronoUnit.HOURS);
            FavoriteTransfer fav = new FavoriteTransfer();
            fav.setId("f1");
            fav.setName("My Favorite");
            fav.setServerId("srv1");
            fav.setDirection(TransferDirection.SEND);
            fav.setFilename("file.txt");

            when(favoriteRepository.findById("f1")).thenReturn(Optional.of(fav));
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<ScheduledTransfer> result =
                    schedulerService.createFromFavorite("f1", ScheduleType.ONCE, scheduledAt, null);

            assertThat(result).isPresent();
            assertThat(result.get().getNextRunAt()).isEqualTo(scheduledAt);
        }
    }

    @Nested
    @DisplayName("Run Now")
    class RunNowTests {

        @Test
        void runNow_found() {
            ScheduledTransfer schedule = new ScheduledTransfer();
            schedule.setId("1");
            schedule.setName("test");
            schedule.setServerId("srv1");
            schedule.setDirection(TransferDirection.SEND);
            schedule.setFilename("file.txt");
            schedule.setScheduleType(ScheduleType.DAILY);

            when(scheduleRepository.findById("1")).thenReturn(Optional.of(schedule));
            when(partnerService.resolvePassword(any())).thenReturn(null);
            when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<ScheduledTransfer> result = schedulerService.runNow("1");

            assertThat(result).isPresent();
            verify(transferService).sendFile(any());
        }

        @Test
        void runNow_notFound() {
            when(scheduleRepository.findById("missing")).thenReturn(Optional.empty());

            assertThat(schedulerService.runNow("missing")).isEmpty();
        }
    }
}
