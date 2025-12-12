package com.vectis.server.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for database backup and recovery operations.
 * Supports H2 database backup and file-based backup management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    @Value("${pesit.backup.directory:./backups}")
    private String backupDirectory;

    @Value("${pesit.backup.retention-days:30}")
    private int retentionDays;

    @Value("${pesit.backup.max-backups:10}")
    private int maxBackups;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ========== Backup Operations ==========

    /**
     * Create a database backup
     */
    public BackupResult createBackup(String description) throws IOException {
        Path backupDir = ensureBackupDirectory();
        String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
        String backupName = "pesit_backup_" + timestamp;

        BackupResult result = new BackupResult();
        result.setBackupName(backupName);
        result.setTimestamp(Instant.now());
        result.setDescription(description);

        try {
            if (isH2Database()) {
                // H2 database backup
                Path backupFile = backupDir.resolve(backupName + ".zip");
                createH2Backup(backupFile);
                result.setBackupPath(backupFile.toString());
                result.setBackupType("H2_DATABASE");
                result.setSizeBytes(Files.size(backupFile));
            } else {
                // For other databases, create a metadata file
                // Actual backup should be done via pg_dump, mysqldump, etc.
                Path metadataFile = backupDir.resolve(backupName + ".meta");
                createBackupMetadata(metadataFile, description);
                result.setBackupPath(metadataFile.toString());
                result.setBackupType("METADATA_ONLY");
                result.setSizeBytes(Files.size(metadataFile));
                result.setMessage(
                        "Database backup requires external tool (pg_dump, mysqldump). Metadata file created.");
            }

            result.setSuccess(true);
            log.info("Backup created: {} ({})", backupName, result.getBackupType());

            // Cleanup old backups
            cleanupOldBackups();

        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Backup failed: " + e.getMessage());
            log.error("Backup failed: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Create H2 database backup using BACKUP TO command
     */
    private void createH2Backup(Path backupFile) throws Exception {
        // H2 BACKUP TO creates a ZIP file with the database
        // This requires a JDBC connection to execute
        // For now, we'll copy the database files directly

        String dbPath = extractH2DbPath();
        if (dbPath != null) {
            Path dbFile = Path.of(dbPath + ".mv.db");
            if (Files.exists(dbFile)) {
                Files.copy(dbFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
                log.debug("H2 database file copied to: {}", backupFile);
            } else {
                // Try trace file
                Path traceFile = Path.of(dbPath + ".trace.db");
                if (Files.exists(traceFile)) {
                    Files.copy(traceFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Create backup metadata file
     */
    private void createBackupMetadata(Path metadataFile, String description) throws IOException {
        String metadata = String.format("""
                # PeSIT Server Backup Metadata
                timestamp=%s
                description=%s
                datasource=%s

                # To restore this backup:
                # 1. Stop the PeSIT server
                # 2. Restore the database using appropriate tool:
                #    - PostgreSQL: pg_restore -d pesit backup.dump
                #    - MySQL: mysql pesit < backup.sql
                # 3. Start the PeSIT server
                """,
                Instant.now().toString(),
                description != null ? description : "Manual backup",
                datasourceUrl.replaceAll("password=[^&]*", "password=***"));

        Files.writeString(metadataFile, metadata);
    }

    /**
     * Scheduled automatic backup
     */
    @Scheduled(cron = "${pesit.backup.schedule:0 0 1 * * ?}") // Default: 1 AM daily
    public void scheduledBackup() {
        try {
            BackupResult result = createBackup("Scheduled automatic backup");
            if (result.isSuccess()) {
                log.info("Scheduled backup completed: {}", result.getBackupName());
            }
        } catch (Exception e) {
            log.error("Scheduled backup failed: {}", e.getMessage());
        }
    }

    // ========== Restore Operations ==========

    /**
     * List available backups
     */
    public List<BackupInfo> listBackups() throws IOException {
        Path backupDir = Path.of(backupDirectory);
        if (!Files.exists(backupDir)) {
            return List.of();
        }

        List<BackupInfo> backups = new ArrayList<>();

        try (Stream<Path> files = Files.list(backupDir)) {
            files.filter(p -> p.toString().endsWith(".zip") || p.toString().endsWith(".meta"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .forEach(path -> {
                        try {
                            BackupInfo info = new BackupInfo();
                            info.setName(path.getFileName().toString());
                            info.setPath(path.toString());
                            info.setSizeBytes(Files.size(path));
                            info.setCreatedAt(Files.getLastModifiedTime(path).toInstant());
                            info.setType(path.toString().endsWith(".zip") ? "H2_DATABASE" : "METADATA");
                            backups.add(info);
                        } catch (IOException e) {
                            log.warn("Error reading backup info: {}", path, e);
                        }
                    });
        }

        return backups;
    }

    /**
     * Restore from backup (H2 only)
     */
    public RestoreResult restoreBackup(String backupName) throws IOException {
        RestoreResult result = new RestoreResult();
        result.setBackupName(backupName);
        result.setTimestamp(Instant.now());

        Path backupFile = Path.of(backupDirectory, backupName);
        if (!Files.exists(backupFile)) {
            result.setSuccess(false);
            result.setMessage("Backup file not found: " + backupName);
            return result;
        }

        if (!isH2Database()) {
            result.setSuccess(false);
            result.setMessage("Automatic restore only supported for H2 database. " +
                    "Use appropriate database tool for restore.");
            return result;
        }

        try {
            String dbPath = extractH2DbPath();
            if (dbPath != null) {
                Path dbFile = Path.of(dbPath + ".mv.db");

                // Create backup of current database before restore
                if (Files.exists(dbFile)) {
                    Path preRestoreBackup = dbFile.resolveSibling(
                            dbFile.getFileName() + ".pre_restore_" +
                                    LocalDateTime.now().format(BACKUP_DATE_FORMAT));
                    Files.copy(dbFile, preRestoreBackup);
                    log.info("Created pre-restore backup: {}", preRestoreBackup);
                }

                // Restore
                Files.copy(backupFile, dbFile, StandardCopyOption.REPLACE_EXISTING);
                result.setSuccess(true);
                result.setMessage("Database restored from backup. Restart required.");
                log.info("Database restored from: {}", backupName);
            } else {
                result.setSuccess(false);
                result.setMessage("Could not determine database path");
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Restore failed: " + e.getMessage());
            log.error("Restore failed: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Delete a backup
     */
    public boolean deleteBackup(String backupName) throws IOException {
        Path backupFile = Path.of(backupDirectory, backupName);
        if (Files.exists(backupFile)) {
            Files.delete(backupFile);
            log.info("Deleted backup: {}", backupName);
            return true;
        }
        return false;
    }

    // ========== Cleanup ==========

    /**
     * Clean up old backups based on retention policy
     */
    public int cleanupOldBackups() throws IOException {
        Path backupDir = Path.of(backupDirectory);
        if (!Files.exists(backupDir)) {
            return 0;
        }

        List<Path> backups;
        try (Stream<Path> files = Files.list(backupDir)) {
            backups = files
                    .filter(p -> p.toString().endsWith(".zip") || p.toString().endsWith(".meta"))
                    .sorted(Comparator.comparing((Path p) -> {
                        try {
                            return Files.getLastModifiedTime(p).toInstant();
                        } catch (IOException e) {
                            return Instant.MIN;
                        }
                    }).reversed())
                    .toList();
        }

        int deleted = 0;
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60 * 60);

        for (int i = 0; i < backups.size(); i++) {
            Path backup = backups.get(i);
            boolean shouldDelete = false;

            // Delete if exceeds max backups
            if (i >= maxBackups) {
                shouldDelete = true;
            }

            // Delete if older than retention period
            try {
                if (Files.getLastModifiedTime(backup).toInstant().isBefore(cutoff)) {
                    shouldDelete = true;
                }
            } catch (IOException e) {
                // Ignore
            }

            if (shouldDelete) {
                try {
                    Files.delete(backup);
                    deleted++;
                    log.debug("Deleted old backup: {}", backup.getFileName());
                } catch (IOException e) {
                    log.warn("Failed to delete backup: {}", backup, e);
                }
            }
        }

        if (deleted > 0) {
            log.info("Cleaned up {} old backups", deleted);
        }

        return deleted;
    }

    // ========== Helper Methods ==========

    private Path ensureBackupDirectory() throws IOException {
        Path dir = Path.of(backupDirectory);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            log.info("Created backup directory: {}", dir);
        }
        return dir;
    }

    private boolean isH2Database() {
        return datasourceUrl != null && datasourceUrl.contains("h2:");
    }

    private String extractH2DbPath() {
        if (datasourceUrl == null)
            return null;

        // jdbc:h2:file:./data/pesit -> ./data/pesit
        // jdbc:h2:./data/pesit -> ./data/pesit
        String url = datasourceUrl;
        if (url.contains("h2:file:")) {
            url = url.substring(url.indexOf("h2:file:") + 8);
        } else if (url.contains("h2:")) {
            url = url.substring(url.indexOf("h2:") + 3);
        }

        // Remove parameters
        if (url.contains(";")) {
            url = url.substring(0, url.indexOf(";"));
        }
        if (url.contains("?")) {
            url = url.substring(0, url.indexOf("?"));
        }

        return url;
    }

    // ========== DTOs ==========

    @lombok.Data
    public static class BackupResult {
        private boolean success;
        private String backupName;
        private String backupPath;
        private String backupType;
        private Long sizeBytes;
        private Instant timestamp;
        private String description;
        private String message;
    }

    @lombok.Data
    public static class RestoreResult {
        private boolean success;
        private String backupName;
        private Instant timestamp;
        private String message;
    }

    @lombok.Data
    public static class BackupInfo {
        private String name;
        private String path;
        private String type;
        private Long sizeBytes;
        private Instant createdAt;
    }
}
