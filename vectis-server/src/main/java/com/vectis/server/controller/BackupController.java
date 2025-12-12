package com.vectis.server.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vectis.server.backup.BackupService;
import com.vectis.server.backup.BackupService.BackupInfo;
import com.vectis.server.backup.BackupService.BackupResult;
import com.vectis.server.backup.BackupService.RestoreResult;

import lombok.RequiredArgsConstructor;

/**
 * REST API for backup and recovery operations.
 * All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/backup")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    private final BackupService backupService;

    /**
     * Create a new backup
     */
    @PostMapping
    public ResponseEntity<BackupResult> createBackup(
            @RequestParam(required = false) String description) throws IOException {
        return ResponseEntity.ok(backupService.createBackup(description));
    }

    /**
     * List all available backups
     */
    @GetMapping
    public ResponseEntity<List<BackupInfo>> listBackups() throws IOException {
        return ResponseEntity.ok(backupService.listBackups());
    }

    /**
     * Restore from a backup
     */
    @PostMapping("/restore/{backupName}")
    public ResponseEntity<RestoreResult> restoreBackup(@PathVariable String backupName) throws IOException {
        return ResponseEntity.ok(backupService.restoreBackup(backupName));
    }

    /**
     * Delete a backup
     */
    @DeleteMapping("/{backupName}")
    public ResponseEntity<Void> deleteBackup(@PathVariable String backupName) throws IOException {
        if (backupService.deleteBackup(backupName)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Cleanup old backups
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Integer> cleanupOldBackups() throws IOException {
        return ResponseEntity.ok(backupService.cleanupOldBackups());
    }
}
