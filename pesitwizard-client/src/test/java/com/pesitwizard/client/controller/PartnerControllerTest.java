package com.pesitwizard.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.client.entity.Partner;
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
class PartnerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void getAllPartners_returnsArray() throws Exception {
        mockMvc.perform(get("/api/v1/partners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getPartner_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/partners/nonexistent")).andExpect(status().isNotFound());
    }

    @Test
    void getByPartnerId_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/partners/by-partner-id/NOPE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAndDeletePartner() throws Exception {
        String partnerId = "TEST-P-" + System.currentTimeMillis();
        var request = Map.of("partnerId", partnerId, "description", "Test partner");

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/partners")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.partnerId").value(partnerId))
                        .andReturn();

        Partner created =
                objectMapper.readValue(result.getResponse().getContentAsString(), Partner.class);

        // Get by ID
        mockMvc.perform(get("/api/v1/partners/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerId").value(partnerId));

        // Delete
        mockMvc.perform(delete("/api/v1/partners/" + created.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void updatePartner_notFound_returns404() throws Exception {
        var request = Map.of("partnerId", "P1", "description", "test");

        mockMvc.perform(
                        put("/api/v1/partners/nonexistent")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
