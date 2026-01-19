package com.pesitwizard.server.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pesitwizard.backup.BackupInfo;
import com.pesitwizard.backup.BackupResult;
import com.pesitwizard.backup.RestoreResult;
import com.pesitwizard.server.backup.BackupServiceAdapter;

@WebMvcTest(BackupController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("BackupController Tests")
class BackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackupServiceAdapter backupService;

    @Test
    @DisplayName("should create backup")
    void shouldCreateBackup() throws Exception {
        BackupResult result = new BackupResult();
        result.setSuccess(true);
        result.setBackupName("backup-2023.zip");
        when(backupService.createBackup(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/backup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.backupName").value("backup-2023.zip"));
    }

    @Test
    @DisplayName("should create backup with description")
    void shouldCreateBackupWithDescription() throws Exception {
        BackupResult result = new BackupResult();
        result.setSuccess(true);
        result.setBackupName("backup-2023.zip");
        when(backupService.createBackup("Manual backup")).thenReturn(result);

        mockMvc.perform(post("/api/v1/backup")
                .param("description", "Manual backup"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should list backups")
    void shouldListBackups() throws Exception {
        BackupInfo backup1 = new BackupInfo();
        backup1.setFilename("backup-1.zip");
        BackupInfo backup2 = new BackupInfo();
        backup2.setFilename("backup-2.zip");
        when(backupService.listBackups()).thenReturn(List.of(backup1, backup2));

        mockMvc.perform(get("/api/v1/backup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].filename").value("backup-1.zip"));
    }

    @Test
    @DisplayName("should restore backup")
    void shouldRestoreBackup() throws Exception {
        RestoreResult result = new RestoreResult();
        result.setSuccess(true);
        when(backupService.restoreBackup("backup-2023.zip")).thenReturn(result);

        mockMvc.perform(post("/api/v1/backup/restore/backup-2023.zip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("should delete backup")
    void shouldDeleteBackup() throws Exception {
        when(backupService.deleteBackup("backup-2023.zip")).thenReturn(true);

        mockMvc.perform(delete("/api/v1/backup/backup-2023.zip"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should return 404 when deleting non-existent backup")
    void shouldReturn404WhenDeletingNonExistent() throws Exception {
        when(backupService.deleteBackup("non-existent.zip")).thenReturn(false);

        mockMvc.perform(delete("/api/v1/backup/non-existent.zip"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should cleanup old backups")
    void shouldCleanupOldBackups() throws Exception {
        when(backupService.cleanupOldBackups()).thenReturn(3);

        mockMvc.perform(post("/api/v1/backup/cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(3));
    }
}
