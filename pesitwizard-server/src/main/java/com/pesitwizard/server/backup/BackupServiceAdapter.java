package com.pesitwizard.server.backup;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pesitwizard.backup.BackupConfig;
import com.pesitwizard.backup.BackupInfo;
import com.pesitwizard.backup.BackupResult;
import com.pesitwizard.backup.RestoreResult;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring adapter for the centralized BackupService.
 * Configures and exposes the backup functionality with Spring properties.
 */
@Slf4j
@Service
public class BackupServiceAdapter {

    @Value("${pesit.backup.directory:./backups}")
    private String backupDirectory;

    @Value("${pesit.backup.retention-days:30}")
    private int retentionDays;

    @Value("${pesit.backup.max-backups:10}")
    private int maxBackups;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String dbUser;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    private com.pesitwizard.backup.BackupService backupService;

    @PostConstruct
    public void init() {
        BackupConfig config = BackupConfig.builder()
                .backupDirectory(backupDirectory)
                .backupPrefix("pesit_backup")
                .retentionDays(retentionDays)
                .maxBackups(maxBackups)
                .datasourceUrl(datasourceUrl)
                .dbUser(dbUser)
                .dbPassword(dbPassword)
                .build();
        this.backupService = new com.pesitwizard.backup.BackupService(config);
        log.info("Backup service initialized with directory: {}", backupDirectory);
    }

    public BackupResult createBackup(String description) {
        return backupService.createBackup(description);
    }

    public List<BackupInfo> listBackups() {
        return backupService.listBackups();
    }

    public RestoreResult restoreBackup(String backupName) {
        return backupService.restoreBackup(backupName);
    }

    public boolean deleteBackup(String backupName) {
        return backupService.deleteBackup(backupName);
    }

    public int cleanupOldBackups() {
        return backupService.cleanupOldBackups();
    }

    @Scheduled(cron = "${pesit.backup.schedule:0 0 1 * * ?}")
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
}
