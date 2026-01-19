package com.pesitwizard.client.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pesitwizard.backup.BackupInfo;
import com.pesitwizard.backup.BackupResult;
import com.pesitwizard.backup.RestoreResult;
import com.pesitwizard.client.backup.BackupServiceAdapter;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupServiceAdapter backupService;

    @PostMapping
    public ResponseEntity<BackupResult> createBackup(@RequestParam(required = false) String description) {
        return ResponseEntity.ok(backupService.createBackup(description));
    }

    @GetMapping
    public ResponseEntity<List<BackupInfo>> listBackups() {
        return ResponseEntity.ok(backupService.listBackups());
    }

    @PostMapping("/restore/{backupName}")
    public ResponseEntity<RestoreResult> restoreBackup(@PathVariable String backupName) {
        return ResponseEntity.ok(backupService.restoreBackup(backupName));
    }

    @DeleteMapping("/{backupName}")
    public ResponseEntity<Void> deleteBackup(@PathVariable String backupName) {
        return backupService.deleteBackup(backupName)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/cleanup")
    public ResponseEntity<Integer> cleanupOldBackups() {
        return ResponseEntity.ok(backupService.cleanupOldBackups());
    }
}
