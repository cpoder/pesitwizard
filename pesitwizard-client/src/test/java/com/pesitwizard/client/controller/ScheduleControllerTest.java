package com.pesitwizard.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.client.entity.ScheduledTransfer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "nosecurity"})
class ScheduleControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Test
    void getAllSchedules_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getSchedule_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/nonexistent-id")).andExpect(status().isNotFound());
    }

    @Test
    void createAndManageSchedule() throws Exception {
        String scheduleName = "test-schedule-" + System.currentTimeMillis();
        var request =
                Map.of(
                        "name", scheduleName,
                        "serverId", "server-123",
                        "partnerId", "PARTNER1",
                        "filename", "test.txt",
                        "direction", "SEND",
                        "scheduleType", "INTERVAL",
                        "intervalMinutes", 60,
                        "enabled", true);

        // Create schedule
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/schedules")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.name").value(scheduleName))
                        .andReturn();

        ScheduledTransfer created =
                objectMapper.readValue(
                        result.getResponse().getContentAsString(), ScheduledTransfer.class);

        // Get by ID
        mockMvc.perform(get("/api/v1/schedules/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(scheduleName));

        // Update schedule
        var updateRequest =
                Map.of(
                        "name", scheduleName + "-updated",
                        "serverId", "server-123",
                        "partnerId", "PARTNER1",
                        "filename", "test.txt",
                        "direction", "SEND",
                        "scheduleType", "INTERVAL",
                        "intervalMinutes", 120,
                        "enabled", true);

        mockMvc.perform(
                        put("/api/v1/schedules/" + created.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalMinutes").value(120));

        // Toggle enabled
        mockMvc.perform(post("/api/v1/schedules/" + created.getId() + "/toggle"))
                .andExpect(status().isOk());

        // Delete schedule
        mockMvc.perform(delete("/api/v1/schedules/" + created.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void toggleEnabled_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/schedules/nonexistent/toggle"))
                .andExpect(status().isNotFound());
    }

    @Test
    void runNow_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/schedules/nonexistent/run")).andExpect(status().isNotFound());
    }

    @Test
    void updateSchedule_notFound_shouldReturn404() throws Exception {
        var request =
                Map.of(
                        "name", "test",
                        "serverId", "server-123",
                        "partnerId", "PARTNER1",
                        "filename", "test.txt",
                        "direction", "SEND",
                        "scheduleType", "INTERVAL",
                        "intervalMinutes", 60);

        mockMvc.perform(
                        put("/api/v1/schedules/nonexistent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
