package com.pesitwizard.server.backup;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("BackupService Tests")
class BackupServiceTest {

    private BackupService backupService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        backupService = new BackupService();
        ReflectionTestUtils.setField(backupService, "backupDirectory", tempDir.toString());
        ReflectionTestUtils.setField(backupService, "retentionDays", 30);
        ReflectionTestUtils.setField(backupService, "maxBackups", 5);
    }

    @Nested
    @DisplayName("Backup Creation Tests")
    class CreateBackupTests {

        @Test
        @DisplayName("Should create backup with description")
        void shouldCreateBackupWithDescription() throws IOException {
            ReflectionTestUtils.setField(backupService, "datasourceUrl", "jdbc:postgresql://localhost/pesit");

            BackupService.BackupResult result = backupService.createBackup("Test backup");

            assertTrue(result.isSuccess());
            assertNotNull(result.getBackupName());
            assertTrue(result.getBackupName().startsWith("pesit_backup_"));
            assertNotNull(result.getTimestamp());
            assertEquals("Test backup", result.getDescription());
        }

        @Test
        @DisplayName("Should create H2 database backup")
        void shouldCreateH2Backup() throws IOException {
            // Create a fake H2 database file
            Path dbFile = tempDir.resolve("testdb.mv.db");
            Files.writeString(dbFile, "fake database content");

            ReflectionTestUtils.setField(backupService, "datasourceUrl",
                    "jdbc:h2:file:" + tempDir.resolve("testdb").toString());

            BackupService.BackupResult result = backupService.createBackup("H2 backup");

            assertTrue(result.isSuccess());
            assertEquals("H2_DATABASE", result.getBackupType());
        }

        @Test
        @DisplayName("Should create metadata backup for non-H2 database")
        void shouldCreateMetadataBackup() throws IOException {
            ReflectionTestUtils.setField(backupService, "datasourceUrl",
                    "jdbc:postgresql://localhost:5432/pesit?password=secret");

            BackupService.BackupResult result = backupService.createBackup("PostgreSQL backup");

            assertTrue(result.isSuccess());
            assertEquals("METADATA_ONLY", result.getBackupType());
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().contains("external tool"));
        }
    }

    @Nested
    @DisplayName("Backup Listing Tests")
    class ListBackupTests {

        @Test
        @DisplayName("Should list empty backups when directory is empty")
        void shouldListEmptyBackups() throws IOException {
            List<BackupService.BackupInfo> backups = backupService.listBackups();

            assertTrue(backups.isEmpty());
        }

        @Test
        @DisplayName("Should list backups from directory")
        void shouldListBackups() throws IOException {
            // Create some backup files
            Files.writeString(tempDir.resolve("pesit_backup_20240101_120000.zip"), "backup1");
            Files.writeString(tempDir.resolve("pesit_backup_20240102_120000.zip"), "backup2");
            Files.writeString(tempDir.resolve("pesit_backup_20240103_120000.meta"), "backup3");

            List<BackupService.BackupInfo> backups = backupService.listBackups();

            assertEquals(3, backups.size());
        }

        @Test
        @DisplayName("Should return correct backup types")
        void shouldReturnCorrectTypes() throws IOException {
            Files.writeString(tempDir.resolve("backup.zip"), "zip content");
            Files.writeString(tempDir.resolve("backup.meta"), "meta content");

            List<BackupService.BackupInfo> backups = backupService.listBackups();

            assertTrue(backups.stream().anyMatch(b -> "H2_DATABASE".equals(b.getType())));
            assertTrue(backups.stream().anyMatch(b -> "METADATA".equals(b.getType())));
        }

        @Test
        @DisplayName("Should handle non-existent backup directory")
        void shouldHandleNonExistentDirectory() throws IOException {
            ReflectionTestUtils.setField(backupService, "backupDirectory",
                    tempDir.resolve("nonexistent").toString());

            List<BackupService.BackupInfo> backups = backupService.listBackups();

            assertTrue(backups.isEmpty());
        }
    }

    @Nested
    @DisplayName("Backup Restore Tests")
    class RestoreBackupTests {

        @Test
        @DisplayName("Should fail restore for non-existent backup")
        void shouldFailForNonExistent() throws IOException {
            BackupService.RestoreResult result = backupService.restoreBackup("nonexistent.zip");

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("Should fail restore for non-H2 database")
        void shouldFailForNonH2() throws IOException {
            Files.writeString(tempDir.resolve("backup.zip"), "content");
            ReflectionTestUtils.setField(backupService, "datasourceUrl",
                    "jdbc:postgresql://localhost/pesit");

            BackupService.RestoreResult result = backupService.restoreBackup("backup.zip");

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("H2 database"));
        }

        @Test
        @DisplayName("Should restore H2 database backup")
        void shouldRestoreH2Backup() throws IOException {
            // Create backup file
            Files.writeString(tempDir.resolve("backup.zip"), "restored content");

            // Create target db file
            Path dbDir = tempDir.resolve("db");
            Files.createDirectories(dbDir);
            Path dbFile = dbDir.resolve("pesit.mv.db");
            Files.writeString(dbFile, "original content");

            ReflectionTestUtils.setField(backupService, "datasourceUrl",
                    "jdbc:h2:file:" + dbDir.resolve("pesit").toString());

            BackupService.RestoreResult result = backupService.restoreBackup("backup.zip");

            assertTrue(result.isSuccess());
            assertEquals("restored content", Files.readString(dbFile));
        }
    }

    @Nested
    @DisplayName("Backup Deletion Tests")
    class DeleteBackupTests {

        @Test
        @DisplayName("Should delete existing backup")
        void shouldDeleteBackup() throws IOException {
            Path backupFile = tempDir.resolve("to_delete.zip");
            Files.writeString(backupFile, "content");

            boolean deleted = backupService.deleteBackup("to_delete.zip");

            assertTrue(deleted);
            assertFalse(Files.exists(backupFile));
        }

        @Test
        @DisplayName("Should return false for non-existent backup")
        void shouldReturnFalseForNonExistent() throws IOException {
            boolean deleted = backupService.deleteBackup("nonexistent.zip");

            assertFalse(deleted);
        }
    }

    @Nested
    @DisplayName("Backup Cleanup Tests")
    class CleanupTests {

        @Test
        @DisplayName("Should cleanup old backups exceeding max count")
        void shouldCleanupExceedingMax() throws IOException {
            // Create more backups than max (5)
            for (int i = 0; i < 8; i++) {
                Path backup = tempDir.resolve("backup_" + String.format("%02d", i) + ".zip");
                Files.writeString(backup, "content " + i);
            }

            int deleted = backupService.cleanupOldBackups();

            assertEquals(3, deleted); // 8 - 5 = 3 deleted
        }

        @Test
        @DisplayName("Should not cleanup when under max count")
        void shouldNotCleanupUnderMax() throws IOException {
            // Create fewer backups than max
            for (int i = 0; i < 3; i++) {
                Files.writeString(tempDir.resolve("backup_" + i + ".zip"), "content");
            }

            int deleted = backupService.cleanupOldBackups();

            assertEquals(0, deleted);
        }

        @Test
        @DisplayName("Should handle empty backup directory")
        void shouldHandleEmptyDirectory() throws IOException {
            int deleted = backupService.cleanupOldBackups();

            assertEquals(0, deleted);
        }

        @Test
        @DisplayName("Should handle non-existent backup directory")
        void shouldHandleNonExistentDirectory() throws IOException {
            ReflectionTestUtils.setField(backupService, "backupDirectory",
                    tempDir.resolve("nonexistent").toString());

            int deleted = backupService.cleanupOldBackups();

            assertEquals(0, deleted);
        }
    }

    @Nested
    @DisplayName("Scheduled Backup Tests")
    class ScheduledBackupTests {

        @Test
        @DisplayName("Should execute scheduled backup")
        void shouldExecuteScheduledBackup() {
            ReflectionTestUtils.setField(backupService, "datasourceUrl",
                    "jdbc:postgresql://localhost/pesit");

            // Should not throw
            assertDoesNotThrow(() -> backupService.scheduledBackup());
        }
    }
}
