package com.pesitwizard.server.backup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.pesitwizard.backup.BackupInfo;
import com.pesitwizard.backup.BackupResult;
import com.pesitwizard.backup.BackupService;
import com.pesitwizard.backup.RestoreResult;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BackupServiceAdapter Tests")
class BackupServiceAdapterTest {

    @Mock
    private BackupService backupService;

    private BackupServiceAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new BackupServiceAdapter();
        setField(adapter, "backupDirectory", "./test-backups");
        setField(adapter, "retentionDays", 7);
        setField(adapter, "maxBackups", 5);
        setField(adapter, "datasourceUrl", "jdbc:h2:mem:test");
        setField(adapter, "dbUser", "sa");
        setField(adapter, "dbPassword", "");

        // Initialize adapter then inject mock
        adapter.init();
        setField(adapter, "backupService", backupService);
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    @Nested
    @DisplayName("Backup Operations")
    class BackupOperations {

        @Test
        @DisplayName("Should create backup")
        void shouldCreateBackup() {
            BackupResult expectedResult = new BackupResult();
            expectedResult.setSuccess(true);
            expectedResult.setBackupName("backup-001.zip");
            when(backupService.createBackup("Test backup")).thenReturn(expectedResult);

            BackupResult result = adapter.createBackup("Test backup");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getBackupName()).isEqualTo("backup-001.zip");
        }

        @Test
        @DisplayName("Should list backups")
        void shouldListBackups() {
            BackupInfo info1 = new BackupInfo();
            info1.setFilename("backup1.zip");
            BackupInfo info2 = new BackupInfo();
            info2.setFilename("backup2.zip");
            List<BackupInfo> expectedList = List.of(info1, info2);
            when(backupService.listBackups()).thenReturn(expectedList);

            List<BackupInfo> result = adapter.listBackups();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should restore backup")
        void shouldRestoreBackup() {
            RestoreResult expectedResult = new RestoreResult();
            expectedResult.setSuccess(true);
            when(backupService.restoreBackup("backup1.zip")).thenReturn(expectedResult);

            RestoreResult result = adapter.restoreBackup("backup1.zip");

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should delete backup")
        void shouldDeleteBackup() {
            when(backupService.deleteBackup("backup1.zip")).thenReturn(true);

            boolean result = adapter.deleteBackup("backup1.zip");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should cleanup old backups")
        void shouldCleanupOldBackups() {
            when(backupService.cleanupOldBackups()).thenReturn(3);

            int result = adapter.cleanupOldBackups();

            assertThat(result).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Scheduled Backup")
    class ScheduledBackupTests {

        @Test
        @DisplayName("Should run scheduled backup successfully")
        void shouldRunScheduledBackupSuccessfully() {
            BackupResult successResult = new BackupResult();
            successResult.setSuccess(true);
            successResult.setBackupName("scheduled-backup.zip");
            when(backupService.createBackup("Scheduled automatic backup")).thenReturn(successResult);

            assertThatCode(() -> adapter.scheduledBackup()).doesNotThrowAnyException();
            verify(backupService).createBackup("Scheduled automatic backup");
        }

        @Test
        @DisplayName("Should handle scheduled backup failure")
        void shouldHandleScheduledBackupFailure() {
            when(backupService.createBackup("Scheduled automatic backup"))
                    .thenThrow(new RuntimeException("Backup failed"));

            assertThatCode(() -> adapter.scheduledBackup()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle unsuccessful backup result")
        void shouldHandleUnsuccessfulBackupResult() {
            BackupResult failedResult = new BackupResult();
            failedResult.setSuccess(false);
            failedResult.setMessage("Disk full");
            when(backupService.createBackup("Scheduled automatic backup")).thenReturn(failedResult);

            assertThatCode(() -> adapter.scheduledBackup()).doesNotThrowAnyException();
        }
    }
}
