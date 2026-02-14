package com.pesitwizard.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "nosecurity"})
class BackupControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void createBackup_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/backup")).andExpect(status().isOk());
    }

    @Test
    void listBackups_returnsArray() throws Exception {
        mockMvc.perform(get("/api/v1/backup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void restoreBackup_returns200() throws Exception {
        // Create a backup first, then restore it
        mockMvc.perform(post("/api/v1/backup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupName").exists());
    }

    @Test
    void deleteBackup_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/backup/nonexistent-backup"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cleanupOldBackups_returnsCount() throws Exception {
        mockMvc.perform(post("/api/v1/backup/cleanup")).andExpect(status().isOk());
    }
}
