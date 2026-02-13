package com.pesitwizard.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.client.entity.BusinessCalendar;
import java.util.List;
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
class CalendarControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Test
    void getAllCalendars_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/calendars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getCalendar_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/calendars/nonexistent-id")).andExpect(status().isNotFound());
    }

    @Test
    void getDefaultCalendar_noDefault_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/calendars/default")).andExpect(status().isNotFound());
    }

    @Test
    void createAndManageCalendar() throws Exception {
        String calendarName = "test-calendar-" + System.currentTimeMillis();
        var request =
                Map.of(
                        "name",
                        calendarName,
                        "description",
                        "Test business calendar",
                        "timezone",
                        "Europe/Paris",
                        "workingDays",
                        List.of(1, 2, 3, 4, 5),
                        "businessHoursStart",
                        "08:00",
                        "businessHoursEnd",
                        "18:00",
                        "restrictToBusinessHours",
                        false,
                        "defaultCalendar",
                        false);

        // Create calendar
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/calendars")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.name").value(calendarName))
                        .andReturn();

        BusinessCalendar created =
                objectMapper.readValue(
                        result.getResponse().getContentAsString(), BusinessCalendar.class);

        // Get by ID
        mockMvc.perform(get("/api/v1/calendars/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(calendarName));

        // Update calendar
        var updateRequest =
                Map.of(
                        "name",
                        calendarName,
                        "description",
                        "Updated description",
                        "timezone",
                        "Europe/London",
                        "workingDays",
                        List.of(1, 2, 3, 4, 5),
                        "businessHoursStart",
                        "09:00",
                        "businessHoursEnd",
                        "17:00",
                        "restrictToBusinessHours",
                        true,
                        "defaultCalendar",
                        false);

        mockMvc.perform(
                        put("/api/v1/calendars/" + created.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/London"));

        // Add holidays
        var holidays = List.of("2025-12-25", "2025-01-01");
        mockMvc.perform(
                        post("/api/v1/calendars/" + created.getId() + "/holidays")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(holidays)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holidays").isArray());

        // Remove holidays
        mockMvc.perform(
                        delete("/api/v1/calendars/" + created.getId() + "/holidays")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(List.of("2025-12-25"))))
                .andExpect(status().isOk());

        // Delete calendar
        mockMvc.perform(delete("/api/v1/calendars/" + created.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateCalendar_notFound_shouldReturn404() throws Exception {
        var request =
                Map.of(
                        "name", "test",
                        "timezone", "Europe/Paris",
                        "workingDays", List.of(1, 2, 3, 4, 5));

        mockMvc.perform(
                        put("/api/v1/calendars/nonexistent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addHolidays_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(
                        post("/api/v1/calendars/nonexistent/holidays")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(List.of("2025-12-25"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeHolidays_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/calendars/nonexistent/holidays")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(List.of("2025-12-25"))))
                .andExpect(status().isNotFound());
    }
}
