package com.pesitwizard.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "nosecurity"})
class ConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void getOtlpConfig_returnsMap() throws Exception {
        mockMvc.perform(get("/api/v1/config/otlp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricsEnabled").exists())
                .andExpect(jsonPath("$.tracingEnabled").exists());
    }

    @Test
    void updateOtlpConfig_returnsUpdated() throws Exception {
        var request =
                Map.of(
                        "endpoint", "http://localhost:4318",
                        "metricsEnabled", true,
                        "tracingEnabled", false);

        mockMvc.perform(
                        put("/api/v1/config/otlp")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricsEnabled").value(true))
                .andExpect(jsonPath("$.tracingEnabled").value(false))
                .andExpect(jsonPath("$.message").exists());
    }
}
