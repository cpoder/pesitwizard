package com.pesitwizard.server.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.server.entity.Partner;
import com.pesitwizard.server.entity.VirtualFile;
import com.pesitwizard.server.service.AuditService;
import com.pesitwizard.server.service.ConfigService;

@WebMvcTest(ConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ConfigController Tests")
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConfigService configService;

    @MockitoBean
    private AuditService auditService;

    @Nested
    @DisplayName("Partners")
    class PartnersTests {

        private Partner testPartner;

        @BeforeEach
        void setUp() {
            testPartner = new Partner();
            testPartner.setId("PARTNER1");
            testPartner.setDescription("Test Partner");
            testPartner.setEnabled(true);
        }

        @Test
        @DisplayName("should get all partners")
        void shouldGetAllPartners() throws Exception {
            when(configService.getAllPartners()).thenReturn(List.of(testPartner));

            mockMvc.perform(get("/api/v1/config/partners"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("PARTNER1"));
        }

        @Test
        @DisplayName("should get partner by ID")
        void shouldGetPartnerById() throws Exception {
            when(configService.getPartner("PARTNER1")).thenReturn(Optional.of(testPartner));

            mockMvc.perform(get("/api/v1/config/partners/PARTNER1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("PARTNER1"));
        }

        @Test
        @DisplayName("should return 404 for non-existent partner")
        void shouldReturn404ForNonExistent() throws Exception {
            when(configService.getPartner("non-existent")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/config/partners/non-existent"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should create partner")
        void shouldCreatePartner() throws Exception {
            when(configService.partnerExists("PARTNER1")).thenReturn(false);
            when(configService.savePartner(any(Partner.class))).thenReturn(testPartner);

            mockMvc.perform(post("/api/v1/config/partners").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testPartner)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("PARTNER1"));
        }

        @Test
        @DisplayName("should return 409 when creating duplicate partner")
        void shouldReturn409ForDuplicate() throws Exception {
            when(configService.partnerExists("PARTNER1")).thenReturn(true);

            mockMvc.perform(post("/api/v1/config/partners").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testPartner)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should update partner")
        void shouldUpdatePartner() throws Exception {
            when(configService.partnerExists("PARTNER1")).thenReturn(true);
            when(configService.savePartner(any(Partner.class))).thenReturn(testPartner);

            mockMvc.perform(put("/api/v1/config/partners/PARTNER1").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testPartner)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should delete partner")
        void shouldDeletePartner() throws Exception {
            when(configService.partnerExists("PARTNER1")).thenReturn(true);
            doNothing().when(configService).deletePartner("PARTNER1");

            mockMvc.perform(delete("/api/v1/config/partners/PARTNER1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent partner")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            when(configService.partnerExists("non-existent")).thenReturn(false);

            mockMvc.perform(delete("/api/v1/config/partners/non-existent"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Virtual Files")
    class VirtualFilesTests {

        private VirtualFile testFile;

        @BeforeEach
        void setUp() {
            testFile = new VirtualFile();
            testFile.setId("FILE1");
            testFile.setDescription("test.dat");
            testFile.setEnabled(true);
        }

        @Test
        @DisplayName("should get all virtual files")
        void shouldGetAllVirtualFiles() throws Exception {
            when(configService.getAllVirtualFiles()).thenReturn(List.of(testFile));

            mockMvc.perform(get("/api/v1/config/files"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("FILE1"));
        }

        @Test
        @DisplayName("should get virtual file by ID")
        void shouldGetVirtualFileById() throws Exception {
            when(configService.getVirtualFile("FILE1")).thenReturn(Optional.of(testFile));

            mockMvc.perform(get("/api/v1/config/files/FILE1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("FILE1"));
        }

        @Test
        @DisplayName("should create virtual file")
        void shouldCreateVirtualFile() throws Exception {
            when(configService.virtualFileExists("FILE1")).thenReturn(false);
            when(configService.saveVirtualFile(any(VirtualFile.class))).thenReturn(testFile);

            mockMvc.perform(post("/api/v1/config/files").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testFile)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should update virtual file")
        void shouldUpdateVirtualFile() throws Exception {
            when(configService.virtualFileExists("FILE1")).thenReturn(true);
            when(configService.saveVirtualFile(any(VirtualFile.class))).thenReturn(testFile);

            mockMvc.perform(put("/api/v1/config/files/FILE1").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testFile)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should delete virtual file")
        void shouldDeleteVirtualFile() throws Exception {
            when(configService.virtualFileExists("FILE1")).thenReturn(true);
            doNothing().when(configService).deleteVirtualFile("FILE1");

            mockMvc.perform(delete("/api/v1/config/files/FILE1"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("should reject partner with invalid ID - too long")
        void shouldRejectPartnerWithTooLongId() throws Exception {
            Partner invalidPartner = Partner.builder().id("TOOLONGID").description("Test").build();

            mockMvc.perform(post("/api/v1/config/partners").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidPartner)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject partner with invalid characters")
        void shouldRejectPartnerWithInvalidChars() throws Exception {
            Partner invalidPartner = Partner.builder().id("test-id").description("Test").build();

            mockMvc.perform(post("/api/v1/config/partners").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidPartner)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject virtual file with invalid ID")
        void shouldRejectVirtualFileWithInvalidId() throws Exception {
            VirtualFile invalidFile = VirtualFile.builder().id("toolongfileid").sendDirectory("/data").build();

            mockMvc.perform(post("/api/v1/config/files").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidFile)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject virtual file update with invalid ID")
        void shouldRejectVirtualFileUpdateWithInvalidId() throws Exception {
            VirtualFile file = VirtualFile.builder().id("FILE1").sendDirectory("/data").build();

            mockMvc.perform(put("/api/v1/config/files/invalid-id").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(file)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return conflict when creating duplicate virtual file")
        void shouldReturnConflictForDuplicateVirtualFile() throws Exception {
            when(configService.virtualFileExists("FILE1")).thenReturn(true);
            VirtualFile file = VirtualFile.builder().id("FILE1").sendDirectory("/data").build();

            mockMvc.perform(post("/api/v1/config/files").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(file)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Not Found Tests")
    class NotFoundTests {

        @Test
        @DisplayName("should return 404 when deleting non-existent partner")
        void shouldReturn404WhenDeletingNonExistentPartner() throws Exception {
            when(configService.partnerExists("NOTFOUND")).thenReturn(false);

            mockMvc.perform(delete("/api/v1/config/partners/NOTFOUND"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent virtual file")
        void shouldReturn404WhenDeletingNonExistentFile() throws Exception {
            when(configService.virtualFileExists("NOTFOUND")).thenReturn(false);

            mockMvc.perform(delete("/api/v1/config/files/NOTFOUND"))
                    .andExpect(status().isNotFound());
        }
    }
}
