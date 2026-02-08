package com.pesitwizard.backup;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupServiceTest {

    @TempDir
    Path tempDir;

    private BackupConfig config;
    private BackupService service;

    @BeforeEach
    void setUp() {
        config = BackupConfig.builder()
                .backupDirectory(tempDir.toString())
                .backupPrefix("test")
                .retentionDays(30)
                .maxBackups(5)
                .datasourceUrl("jdbc:h2:file:" + tempDir.resolve("testdb").toString())
                .build();
        service = new BackupService(config);
    }

    // --- detectDatabaseType ---

    @Test
    void detectDatabaseType_h2() {
        assertThat(service.detectDatabaseType()).isEqualTo(DatabaseType.H2);
    }

    @Test
    void detectDatabaseType_postgresql() {
        config = BackupConfig.builder()
                .backupDirectory(tempDir.toString())
                .datasourceUrl("jdbc:postgresql://localhost:5432/mydb")
                .build();
        service = new BackupService(config);
        assertThat(service.detectDatabaseType()).isEqualTo(DatabaseType.POSTGRESQL);
    }

    @Test
    void detectDatabaseType_unknown() {
        config = BackupConfig.builder()
                .backupDirectory(tempDir.toString())
                .datasourceUrl("jdbc:mysql://localhost/mydb")
                .build();
        service = new BackupService(config);
        assertThat(service.detectDatabaseType()).isEqualTo(DatabaseType.UNKNOWN);
    }

    @Test
    void detectDatabaseType_nullUrl() {
        config = BackupConfig.builder()
                .backupDirectory(tempDir.toString())
                .datasourceUrl(null)
                .build();
        service = new BackupService(config);
        assertThat(service.detectDatabaseType()).isEqualTo(DatabaseType.UNKNOWN);
    }

    // --- createBackup (H2 path) ---

    @Test
    void createBackup_h2_copiesDbFile() throws IOException {
        // Create a fake H2 database file
        Path dbFile = tempDir.resolve("testdb.mv.db");
        Files.writeString(dbFile, "fake-h2-data");

        BackupResult result = service.createBackup("Test backup");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBackupType()).isEqualTo("H2");
        assertThat(result.getBackupName()).startsWith("test_");
        assertThat(result.getDescription()).isEqualTo("Test backup");
        assertThat(result.getSizeBytes()).isGreaterThan(0);
        assertThat(result.getBackupPath()).endsWith(".zip");
        assertThat(Files.exists(Path.of(result.getBackupPath()))).isTrue();
    }

    @Test
    void createBackup_nullDescription_defaultsToManual() throws IOException {
        Path dbFile = tempDir.resolve("testdb.mv.db");
        Files.writeString(dbFile, "fake-h2-data");

        BackupResult result = service.createBackup(null);

        assertThat(result.getDescription()).isEqualTo("Manual backup");
    }

    @Test
    void createBackup_unknownDb_createsMetadata() {
        config = BackupConfig.builder()
                .backupDirectory(tempDir.toString())
                .backupPrefix("test")
                .datasourceUrl("jdbc:mysql://localhost/mydb")
                .maxBackups(5)
                .retentionDays(30)
                .build();
        service = new BackupService(config);

        BackupResult result = service.createBackup("Metadata only");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBackupType()).isEqualTo("METADATA");
        assertThat(result.getBackupPath()).endsWith(".meta");
    }

    // --- listBackups ---

    @Test
    void listBackups_emptyDirectory() {
        List<BackupInfo> list = service.listBackups();
        assertThat(list).isEmpty();
    }

    @Test
    void listBackups_returnsZipAndDumpFiles() throws IOException {
        Files.writeString(tempDir.resolve("backup_20260101.zip"), "zip-data");
        Files.writeString(tempDir.resolve("backup_20260102.dump"), "dump-data");
        Files.writeString(tempDir.resolve("backup_20260102.dump.meta"), "A description");
        Files.writeString(tempDir.resolve("unrelated.txt"), "ignored");

        List<BackupInfo> list = service.listBackups();

        assertThat(list).hasSize(2);
        assertThat(list).extracting(BackupInfo::getType).containsExactlyInAnyOrder("H2", "POSTGRESQL");
    }

    @Test
    void listBackups_nonexistentDirectory() {
        config = BackupConfig.builder()
                .backupDirectory(tempDir.resolve("nonexistent").toString())
                .build();
        service = new BackupService(config);

        List<BackupInfo> list = service.listBackups();
        assertThat(list).isEmpty();
    }

    @Test
    void listBackups_readsMetaDescription() throws IOException {
        Files.writeString(tempDir.resolve("b1.zip"), "data");
        Files.writeString(tempDir.resolve("b1.zip.meta"), "My description");

        List<BackupInfo> list = service.listBackups();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getDescription()).isEqualTo("My description");
    }

    // --- deleteBackup ---

    @Test
    void deleteBackup_existingFile() throws IOException {
        Path f = tempDir.resolve("test.zip");
        Path m = tempDir.resolve("test.zip.meta");
        Files.writeString(f, "data");
        Files.writeString(m, "desc");

        boolean deleted = service.deleteBackup("test.zip");

        assertThat(deleted).isTrue();
        assertThat(Files.exists(f)).isFalse();
        assertThat(Files.exists(m)).isFalse();
    }

    @Test
    void deleteBackup_nonexistentFile() {
        boolean deleted = service.deleteBackup("missing.zip");
        assertThat(deleted).isFalse();
    }

    // --- restoreBackup ---

    @Test
    void restoreBackup_fileNotFound() {
        RestoreResult result = service.restoreBackup("missing.zip");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Not found");
    }

    @Test
    void restoreBackup_unsupportedFormat() throws IOException {
        Files.writeString(tempDir.resolve("bad.txt"), "data");
        RestoreResult result = service.restoreBackup("bad.txt");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Unsupported format");
    }

    @Test
    void restoreBackup_h2() throws IOException {
        // Create the "db" file and a backup to restore from
        Path dbFile = tempDir.resolve("testdb.mv.db");
        Files.writeString(dbFile, "original-data");
        Path backupFile = tempDir.resolve("restore.zip");
        Files.writeString(backupFile, "backup-data");

        RestoreResult result = service.restoreBackup("restore.zip");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("H2 restored");
        // Original should have been backed up and replaced
        assertThat(Files.readString(dbFile)).isEqualTo("backup-data");
    }

    // --- cleanupOldBackups ---

    @Test
    void cleanupOldBackups_respectsMaxBackups() throws IOException {
        config = BackupConfig.builder()
                .backupDirectory(tempDir.toString())
                .maxBackups(2)
                .retentionDays(365)
                .build();
        service = new BackupService(config);

        for (int i = 0; i < 5; i++) {
            Path f = tempDir.resolve("backup_" + i + ".zip");
            Files.writeString(f, "data" + i);
            // Ensure distinct modification times
            f.toFile().setLastModified(System.currentTimeMillis() - (4 - i) * 60000L);
        }

        int deleted = service.cleanupOldBackups();

        assertThat(deleted).isEqualTo(3);
        // 2 newest should remain
        long remaining = Files.list(tempDir).filter(p -> p.toString().endsWith(".zip")).count();
        assertThat(remaining).isEqualTo(2);
    }

    @Test
    void cleanupOldBackups_nonexistentDirectory() {
        config = BackupConfig.builder()
                .backupDirectory(tempDir.resolve("nonexistent").toString())
                .maxBackups(5)
                .retentionDays(30)
                .build();
        service = new BackupService(config);

        int deleted = service.cleanupOldBackups();
        assertThat(deleted).isEqualTo(0);
    }

    // --- validateBackupFilename (path traversal - S3-03) ---

    @Test
    void deleteBackup_rejectsPathTraversal_dotdot() {
        assertThatThrownBy(() -> service.deleteBackup("../../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void deleteBackup_rejectsPathTraversal_slash() {
        assertThatThrownBy(() -> service.deleteBackup("/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void deleteBackup_rejectsPathTraversal_backslash() {
        assertThatThrownBy(() -> service.deleteBackup("..\\windows\\system32"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void deleteBackup_rejectsEmptyFilename() {
        assertThatThrownBy(() -> service.deleteBackup(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void deleteBackup_rejectsNullFilename() {
        assertThatThrownBy(() -> service.deleteBackup(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void restoreBackup_rejectsPathTraversal() {
        assertThatThrownBy(() -> service.restoreBackup("../secret.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    // --- Encryption (BL-23) ---

    @Test
    void createBackup_withEncryption_encryptsFile() throws IOException {
        Path dbFile = tempDir.resolve("testdb.mv.db");
        Files.writeString(dbFile, "fake-h2-data");

        config = BackupConfig.builder()
                .backupDirectory(tempDir.toString())
                .backupPrefix("enc")
                .datasourceUrl("jdbc:h2:file:" + tempDir.resolve("testdb"))
                .maxBackups(5)
                .retentionDays(30)
                .encryptionKey("my-secret-key-12345")
                .build();
        BackupService encService = new BackupService(config);

        BackupResult result = encService.createBackup("Encrypted backup");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isEncrypted()).isTrue();
        assertThat(result.getBackupPath()).endsWith(".enc");
        assertThat(Files.exists(Path.of(result.getBackupPath()))).isTrue();
        // Original unencrypted file should be deleted
        String unencryptedPath = result.getBackupPath().replace(".enc", "");
        assertThat(Files.exists(Path.of(unencryptedPath))).isFalse();
    }

    @Test
    void restoreBackup_withEncryption_decryptsAndRestores() throws IOException {
        Path dbFile = tempDir.resolve("testdb.mv.db");
        Files.writeString(dbFile, "original-data");

        config = BackupConfig.builder()
                .backupDirectory(tempDir.toString())
                .backupPrefix("enc")
                .datasourceUrl("jdbc:h2:file:" + tempDir.resolve("testdb"))
                .maxBackups(5)
                .retentionDays(30)
                .encryptionKey("my-secret-key-12345")
                .build();
        BackupService encService = new BackupService(config);

        // Create encrypted backup
        BackupResult backup = encService.createBackup("Test");
        assertThat(backup.isSuccess()).isTrue();

        // Modify db
        Files.writeString(dbFile, "modified-data");

        // Restore from encrypted backup
        String encFilename = Path.of(backup.getBackupPath()).getFileName().toString();
        RestoreResult result = encService.restoreBackup(encFilename);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.readString(dbFile)).isEqualTo("original-data");
    }

    @Test
    void restoreBackup_encryptedWithoutKey_fails() throws IOException {
        // Create encrypted backup with key
        Path dbFile = tempDir.resolve("testdb.mv.db");
        Files.writeString(dbFile, "data");

        config = BackupConfig.builder()
                .backupDirectory(tempDir.toString())
                .backupPrefix("enc")
                .datasourceUrl("jdbc:h2:file:" + tempDir.resolve("testdb"))
                .maxBackups(5)
                .retentionDays(30)
                .encryptionKey("my-secret-key-12345")
                .build();
        BackupService encService = new BackupService(config);
        BackupResult backup = encService.createBackup("Test");

        // Try restoring without key
        BackupService noKeyService = new BackupService(BackupConfig.builder()
                .backupDirectory(tempDir.toString())
                .datasourceUrl("jdbc:h2:file:" + tempDir.resolve("testdb"))
                .maxBackups(5)
                .retentionDays(30)
                .build());
        String encFilename = Path.of(backup.getBackupPath()).getFileName().toString();
        RestoreResult result = noKeyService.restoreBackup(encFilename);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("no encryption key");
    }

    @Test
    void isEncryptionEnabled_withKey() {
        config = BackupConfig.builder()
                .backupDirectory(tempDir.toString())
                .encryptionKey("test-key")
                .build();
        BackupService encService = new BackupService(config);
        assertThat(encService.isEncryptionEnabled()).isTrue();
    }

    @Test
    void isEncryptionEnabled_withoutKey() {
        assertThat(service.isEncryptionEnabled()).isFalse();
    }

    @Test
    void listBackups_includesEncryptedFiles() throws IOException {
        Files.writeString(tempDir.resolve("backup_1.zip"), "plain");
        Files.writeString(tempDir.resolve("backup_2.zip.enc"), "encrypted");

        List<BackupInfo> list = service.listBackups();

        assertThat(list).hasSize(2);
        BackupInfo plain = list.stream().filter(i -> !i.isEncrypted()).findFirst().orElseThrow();
        BackupInfo encrypted = list.stream().filter(BackupInfo::isEncrypted).findFirst().orElseThrow();
        assertThat(plain.getFilename()).isEqualTo("backup_1.zip");
        assertThat(encrypted.getFilename()).isEqualTo("backup_2.zip.enc");
        assertThat(encrypted.getType()).isEqualTo("H2");
    }
}
