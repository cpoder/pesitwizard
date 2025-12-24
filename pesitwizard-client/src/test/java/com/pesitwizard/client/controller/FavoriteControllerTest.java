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
import com.pesitwizard.client.entity.FavoriteTransfer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "nosecurity" })
class FavoriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllFavorites_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getAllFavorites_sortedByLastUsed() throws Exception {
        mockMvc.perform(get("/api/v1/favorites?sortBy=lastUsed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getFavorite_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/favorites/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAndManageFavorite() throws Exception {
        String favoriteName = "test-favorite-" + System.currentTimeMillis();
        var request = Map.of(
                "name", favoriteName,
                "serverId", "server-123",
                "partnerId", "PARTNER1",
                "filename", "test.txt",
                "direction", "SEND");

        // Create favorite
        MvcResult result = mockMvc.perform(post("/api/v1/favorites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(favoriteName))
                .andReturn();

        FavoriteTransfer created = objectMapper.readValue(
                result.getResponse().getContentAsString(), FavoriteTransfer.class);

        // Get by ID
        mockMvc.perform(get("/api/v1/favorites/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(favoriteName));

        // Update favorite
        var updateRequest = Map.of(
                "name", favoriteName + "-updated",
                "serverId", "server-123",
                "partnerId", "PARTNER2",
                "filename", "updated.txt",
                "direction", "SEND");

        mockMvc.perform(put("/api/v1/favorites/" + created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerId").value("PARTNER2"));

        // Delete favorite
        mockMvc.perform(delete("/api/v1/favorites/" + created.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateFavorite_notFound_shouldReturn404() throws Exception {
        var request = Map.of(
                "name", "test",
                "serverId", "server-123",
                "direction", "SEND");

        mockMvc.perform(put("/api/v1/favorites/nonexistent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void executeFavorite_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/favorites/nonexistent/execute"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createFromHistory_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/favorites/from-history/nonexistent?name=test"))
                .andExpect(status().isNotFound());
    }
}
