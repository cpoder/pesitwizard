package com.pesitwizard.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.client.entity.StorageConnection;
import com.pesitwizard.client.repository.StorageConnectionRepository;
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
class ConnectorControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private StorageConnectionRepository connectionRepository;

    @Test
    void listConnectorTypes_shouldReturnAvailableTypes() throws Exception {
        mockMvc.perform(get("/api/v1/connectors/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createAndTestLocalConnection() throws Exception {
        // Create connection
        var request =
                Map.of(
                        "name",
                        "test-local-" + System.currentTimeMillis(),
                        "description",
                        "Test local connection",
                        "connectorType",
                        "local",
                        "config",
                        Map.of("basePath", "/tmp"));

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/connectors/connections")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.name").value(request.get("name")))
                        .andReturn();

        StorageConnection created =
                objectMapper.readValue(
                        result.getResponse().getContentAsString(), StorageConnection.class);

        // Test connection
        mockMvc.perform(post("/api/v1/connectors/connections/" + created.getId() + "/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Browse connection
        mockMvc.perform(get("/api/v1/connectors/connections/" + created.getId() + "/browse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Delete connection
        mockMvc.perform(delete("/api/v1/connectors/connections/" + created.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void createConnection_duplicateName_shouldFail() throws Exception {
        String name = "duplicate-test-" + System.currentTimeMillis();
        var request =
                Map.of(
                        "name",
                        name,
                        "connectorType",
                        "local",
                        "config",
                        Map.of("basePath", "/tmp"));

        // First creation should succeed
        mockMvc.perform(
                        post("/api/v1/connectors/connections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Second creation with same name should fail
        mockMvc.perform(
                        post("/api/v1/connectors/connections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Connection name already exists"));
    }

    @Test
    void createConnection_unknownType_shouldFail() throws Exception {
        var request =
                Map.of(
                        "name", "unknown-type-test",
                        "connectorType", "unknown",
                        "config", Map.of());

        mockMvc.perform(
                        post("/api/v1/connectors/connections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown connector type: unknown"));
    }

    @Test
    void getConnection_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/connectors/connections/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
