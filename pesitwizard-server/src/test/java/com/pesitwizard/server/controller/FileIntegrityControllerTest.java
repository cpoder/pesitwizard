package com.pesitwizard.server.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import com.pesitwizard.server.entity.FileChecksum;
import com.pesitwizard.server.entity.FileChecksum.VerificationStatus;
import com.pesitwizard.server.service.FileIntegrityService;
import com.pesitwizard.server.service.FileIntegrityService.IntegrityStatistics;
import com.pesitwizard.server.service.FileIntegrityService.VerificationResult;

@WebMvcTest(FileIntegrityController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("FileIntegrityController Tests")
class FileIntegrityControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private FileIntegrityService integrityService;

        @Test
        @DisplayName("getChecksum should return checksum when found")
        void getChecksumShouldReturnChecksumWhenFound() throws Exception {
                FileChecksum checksum = createTestChecksum(1L, "file.txt");
                when(integrityService.getChecksum(1L)).thenReturn(Optional.of(checksum));

                mockMvc.perform(get("/api/v1/integrity/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.filename").value("file.txt"));
        }

        @Test
        @DisplayName("getChecksum should return 404 when not found")
        void getChecksumShouldReturn404WhenNotFound() throws Exception {
                when(integrityService.getChecksum(999L)).thenReturn(Optional.empty());

                mockMvc.perform(get("/api/v1/integrity/999"))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("getChecksumByTransfer should return checksum")
        void getChecksumByTransferShouldReturnChecksum() throws Exception {
                FileChecksum checksum = createTestChecksum(1L, "transfer.txt");
                when(integrityService.getChecksumByTransferId("TX123")).thenReturn(Optional.of(checksum));

                mockMvc.perform(get("/api/v1/integrity/transfer/TX123"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.filename").value("transfer.txt"));
        }

        @Test
        @DisplayName("getChecksumsByPartner should return paged results")
        void getChecksumsByPartnerShouldReturnPagedResults() throws Exception {
                FileChecksum checksum = createTestChecksum(1L, "partner.txt");
                when(integrityService.getChecksumsByPartner(eq("PARTNER1"), anyInt(), anyInt()))
                                .thenReturn(new PageImpl<>(List.of(checksum)));

                mockMvc.perform(get("/api/v1/integrity/partner/PARTNER1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].filename").value("partner.txt"));
        }

        @Test
        @DisplayName("getChecksumsByStatus should return filtered results")
        void getChecksumsByStatusShouldReturnFilteredResults() throws Exception {
                FileChecksum checksum = createTestChecksum(1L, "verified.txt");
                checksum.setStatus(VerificationStatus.VERIFIED);
                when(integrityService.getChecksumsByStatus(eq(VerificationStatus.VERIFIED), anyInt(), anyInt()))
                                .thenReturn(new PageImpl<>(List.of(checksum)));

                mockMvc.perform(get("/api/v1/integrity/status/VERIFIED"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].filename").value("verified.txt"));
        }

        @Test
        @DisplayName("searchByFilename should return matching results")
        void searchByFilenameShouldReturnMatchingResults() throws Exception {
                FileChecksum checksum = createTestChecksum(1L, "searchable.txt");
                when(integrityService.searchByFilename(eq("search"), anyInt(), anyInt()))
                                .thenReturn(new PageImpl<>(List.of(checksum)));

                mockMvc.perform(get("/api/v1/integrity/search?filename=search"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].filename").value("searchable.txt"));
        }

        @Test
        @DisplayName("verifyFile should return verification result")
        void verifyFileShouldReturnVerificationResult() throws Exception {
                VerificationResult result = new VerificationResult(true, "Verified", VerificationStatus.VERIFIED);
                when(integrityService.verifyFile(1L)).thenReturn(result);

                mockMvc.perform(post("/api/v1/integrity/1/verify"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("verifyPendingFiles should return count")
        void verifyPendingFilesShouldReturnCount() throws Exception {
                when(integrityService.verifyPendingFiles()).thenReturn(5);

                mockMvc.perform(post("/api/v1/integrity/verify-pending"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.verified").value(5));
        }

        @Test
        @DisplayName("checkDuplicate should detect duplicates")
        void checkDuplicateShouldDetectDuplicates() throws Exception {
                FileChecksum dup = createTestChecksum(1L, "dup.txt");
                when(integrityService.isDuplicate("abc123")).thenReturn(true);
                when(integrityService.getDuplicates("abc123")).thenReturn(List.of(dup));

                mockMvc.perform(get("/api/v1/integrity/duplicate-check?hash=abc123"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isDuplicate").value(true))
                                .andExpect(jsonPath("$.count").value(1));
        }

        @Test
        @DisplayName("checkDuplicate should return false for unique")
        void checkDuplicateShouldReturnFalseForUnique() throws Exception {
                when(integrityService.isDuplicate("unique123")).thenReturn(false);

                mockMvc.perform(get("/api/v1/integrity/duplicate-check?hash=unique123"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isDuplicate").value(false))
                                .andExpect(jsonPath("$.count").value(0));
        }

        @Test
        @DisplayName("getAllDuplicates should return duplicates")
        void getAllDuplicatesShouldReturnDuplicates() throws Exception {
                FileChecksum dup = createTestChecksum(1L, "dup.txt");
                when(integrityService.getAllDuplicates()).thenReturn(List.of(dup));

                mockMvc.perform(get("/api/v1/integrity/duplicates"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("getMostDuplicated should return most duplicated files")
        void getMostDuplicatedShouldReturnMostDuplicatedFiles() throws Exception {
                FileChecksum dup = createTestChecksum(1L, "popular.txt");
                when(integrityService.getMostDuplicated(2)).thenReturn(List.of(dup));

                mockMvc.perform(get("/api/v1/integrity/most-duplicated?minCount=2"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("getStatistics should return stats")
        void getStatisticsShouldReturnStats() throws Exception {
                IntegrityStatistics stats = new IntegrityStatistics();
                stats.setTotalFiles(100);
                stats.setVerified(80);
                when(integrityService.getStatistics()).thenReturn(stats);

                mockMvc.perform(get("/api/v1/integrity/stats"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalFiles").value(100));
        }

        private FileChecksum createTestChecksum(Long id, String filename) {
                FileChecksum checksum = new FileChecksum();
                checksum.setId(id);
                checksum.setFilename(filename);
                checksum.setLocalPath("/data/" + filename);
                checksum.setChecksumHash("abc123hash");
                checksum.setAlgorithm(FileChecksum.HashAlgorithm.SHA_256);
                checksum.setFileSize(1024L);
                checksum.setStatus(VerificationStatus.PENDING);
                checksum.setCreatedAt(Instant.now());
                checksum.setUpdatedAt(Instant.now());
                return checksum;
        }
}
