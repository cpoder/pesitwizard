package com.pesitwizard.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "nosecurity" })
@DisplayName("DashboardController Tests")
class DashboardControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("GET /api/v1/dashboard should return dashboard data structure")
        void getDashboard_shouldReturnDashboardDataStructure() throws Exception {
                mockMvc.perform(get("/api/v1/dashboard"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.transfers").exists())
                                .andExpect(jsonPath("$.transfers.total").isNumber())
                                .andExpect(jsonPath("$.transfers.completed").isNumber())
                                .andExpect(jsonPath("$.transfers.failed").isNumber())
                                .andExpect(jsonPath("$.transfers.inProgress").isNumber())
                                .andExpect(jsonPath("$.transfers.bytesTransferred").isNumber())
                                .andExpect(jsonPath("$.activeTransfers").isArray())
                                .andExpect(jsonPath("$.recentTransfers").isArray())
                                .andExpect(jsonPath("$.servers").exists())
                                .andExpect(jsonPath("$.servers.total").isNumber())
                                .andExpect(jsonPath("$.servers.enabled").isNumber())
                                .andExpect(jsonPath("$.servers.disabled").isNumber())
                                .andExpect(jsonPath("$.servers.list").isArray())
                                .andExpect(jsonPath("$.security").exists())
                                .andExpect(jsonPath("$.security.encryptionEnabled").isBoolean())
                                .andExpect(jsonPath("$.security.encryptionMode").isString())
                                .andExpect(jsonPath("$.scheduledTransfers").isNumber())
                                .andExpect(jsonPath("$.system").exists())
                                .andExpect(jsonPath("$.system.javaVersion").isString())
                                .andExpect(jsonPath("$.system.memoryUsed").isNumber())
                                .andExpect(jsonPath("$.system.memoryMax").isNumber())
                                .andExpect(jsonPath("$.system.processors").isNumber());
        }

        @Test
        @DisplayName("GET /api/v1/dashboard should include system info")
        void getDashboard_shouldIncludeSystemInfo() throws Exception {
                mockMvc.perform(get("/api/v1/dashboard"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.system.javaVersion").value(System.getProperty("java.version")))
                                .andExpect(jsonPath("$.system.processors")
                                                .value(Runtime.getRuntime().availableProcessors()));
        }

        @Test
        @DisplayName("GET /api/v1/dashboard should include security status")
        void getDashboard_shouldIncludeSecurityStatus() throws Exception {
                mockMvc.perform(get("/api/v1/dashboard"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.security.encryptionEnabled").exists())
                                .andExpect(jsonPath("$.security.encryptionMode").exists())
                                .andExpect(jsonPath("$.security.message").exists())
                                .andExpect(jsonPath("$.security.vaultAvailable").isBoolean());
        }
}
