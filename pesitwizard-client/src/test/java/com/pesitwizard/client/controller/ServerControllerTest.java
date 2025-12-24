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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.client.dto.PesitServerDto;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "nosecurity" })
class ServerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllServers_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/servers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getEnabledServers_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/servers/enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getServer_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/servers/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getServerByName_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/servers/name/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDefaultServer_noDefault_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/servers/default"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAndManageServer() throws Exception {
        String serverName = "test-server-" + System.currentTimeMillis();
        var request = Map.of(
                "name", serverName,
                "host", "localhost",
                "port", 1761,
                "serverId", "SERVER1",
                "enabled", true);

        // Create server
        MvcResult result = mockMvc.perform(post("/api/v1/servers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(serverName))
                .andReturn();

        PesitServerDto created = objectMapper.readValue(
                result.getResponse().getContentAsString(), PesitServerDto.class);

        // Get by ID
        mockMvc.perform(get("/api/v1/servers/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(serverName));

        // Get by name
        mockMvc.perform(get("/api/v1/servers/name/" + serverName))
                .andExpect(status().isOk());

        // Update server
        var updateRequest = Map.of(
                "name", serverName,
                "host", "127.0.0.1",
                "port", 1762,
                "serverId", "SERVER2",
                "enabled", true);

        mockMvc.perform(put("/api/v1/servers/" + created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value("127.0.0.1"));

        // Test connection
        mockMvc.perform(post("/api/v1/servers/" + created.getId() + "/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Set as default
        mockMvc.perform(post("/api/v1/servers/" + created.getId() + "/default"))
                .andExpect(status().isOk());

        // Verify default
        mockMvc.perform(get("/api/v1/servers/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()));

        // Delete server
        mockMvc.perform(delete("/api/v1/servers/" + created.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void testConnection_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/servers/nonexistent/test"))
                .andExpect(status().isNotFound());
    }
}
