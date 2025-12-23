package com.pesitwizard.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "nosecurity" })
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getHistory_shouldReturnPagedResults() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageable").exists());
    }

    @Test
    void getTransfer_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByCorrelationId_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/correlation/nonexistent-corr"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStats_shouldReturnStatistics() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransfers").exists())
                .andExpect(jsonPath("$.completedTransfers").exists())
                .andExpect(jsonPath("$.failedTransfers").exists());
    }

    @Test
    void replayTransfer_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/nonexistent-id/replay"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendFile_missingServer_shouldFail() throws Exception {
        var request = Map.of(
                "localPath", "/tmp/test.txt",
                "remoteFilename", "test.txt");

        mockMvc.perform(post("/api/v1/transfers/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receiveFile_missingServer_shouldFail() throws Exception {
        var request = Map.of(
                "remoteFilename", "test.txt");

        mockMvc.perform(post("/api/v1/transfers/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessage_missingServer_shouldFail() throws Exception {
        var request = Map.of(
                "message", "Hello PeSIT");

        mockMvc.perform(post("/api/v1/transfers/message")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
