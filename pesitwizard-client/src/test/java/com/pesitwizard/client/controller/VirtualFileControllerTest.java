package com.pesitwizard.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.client.entity.VirtualFile;
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
class VirtualFileControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void getAllVirtualFiles_returnsArray() throws Exception {
        mockMvc.perform(get("/api/v1/virtual-files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getAllVirtualFiles_withDirection_returnsArray() throws Exception {
        mockMvc.perform(get("/api/v1/virtual-files?direction=SEND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getVirtualFile_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/virtual-files/nonexistent")).andExpect(status().isNotFound());
    }

    @Test
    void createAndDeleteVirtualFile() throws Exception {
        String name = "VF_TEST_" + System.currentTimeMillis();
        var request = Map.of("name", name, "direction", "SEND");

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/virtual-files")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.name").value(name))
                        .andReturn();

        VirtualFile created =
                objectMapper.readValue(
                        result.getResponse().getContentAsString(), VirtualFile.class);

        // Get by ID
        mockMvc.perform(get("/api/v1/virtual-files/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));

        // Delete
        mockMvc.perform(delete("/api/v1/virtual-files/" + created.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateVirtualFile_notFound_returns404() throws Exception {
        var request = Map.of("name", "test", "direction", "SEND");

        mockMvc.perform(
                        put("/api/v1/virtual-files/nonexistent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
