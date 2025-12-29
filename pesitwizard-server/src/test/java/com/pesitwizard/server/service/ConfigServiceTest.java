package com.pesitwizard.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.entity.Partner;
import com.pesitwizard.server.entity.VirtualFile;
import com.pesitwizard.server.repository.PartnerRepository;
import com.pesitwizard.server.repository.VirtualFileRepository;
import com.pesitwizard.security.SecretsService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigService Tests")
class ConfigServiceTest {

    @Mock
    private PartnerRepository partnerRepository;

    @Mock
    private VirtualFileRepository virtualFileRepository;

    @Mock
    private PesitServerProperties serverProperties;

    @Mock
    private SecretsService secretsService;

    @InjectMocks
    private ConfigService configService;

    private Partner testPartner;
    private VirtualFile testVirtualFile;

    @BeforeEach
    void setUp() {
        testPartner = Partner.builder()
                .id("PARTNER1")
                .description("Test Partner")
                .password("secret")
                .enabled(true)
                .accessType(Partner.AccessType.BOTH)
                .maxConnections(5)
                .build();

        testVirtualFile = VirtualFile.builder()
                .id("FILE1")
                .description("Test File")
                .enabled(true)
                .direction(VirtualFile.Direction.BOTH)
                .receiveDirectory("/data/recv")
                .sendDirectory("/data/send")
                .build();
    }

    @Nested
    @DisplayName("Partner Management Tests")
    class PartnerManagementTests {

        @Test
        @DisplayName("Should get all partners")
        void shouldGetAllPartners() {
            when(partnerRepository.findAll()).thenReturn(List.of(testPartner));

            List<Partner> result = configService.getAllPartners();

            assertEquals(1, result.size());
            assertEquals("PARTNER1", result.get(0).getId());
        }

        @Test
        @DisplayName("Should get enabled partners only")
        void shouldGetEnabledPartners() {
            when(partnerRepository.findByEnabled(true)).thenReturn(List.of(testPartner));

            List<Partner> result = configService.getEnabledPartners();

            assertEquals(1, result.size());
            assertTrue(result.get(0).isEnabled());
        }

        @Test
        @DisplayName("Should get partner by ID")
        void shouldGetPartnerById() {
            when(partnerRepository.findById("PARTNER1")).thenReturn(Optional.of(testPartner));

            Optional<Partner> result = configService.getPartner("PARTNER1");

            assertTrue(result.isPresent());
            assertEquals("PARTNER1", result.get().getId());
        }

        @Test
        @DisplayName("Should find partner case-insensitively")
        void shouldFindPartnerCaseInsensitive() {
            when(partnerRepository.findById("partner1")).thenReturn(Optional.empty());
            when(partnerRepository.findAll()).thenReturn(List.of(testPartner));

            Optional<Partner> result = configService.findPartner("partner1");

            assertTrue(result.isPresent());
            assertEquals("PARTNER1", result.get().getId());
        }

        @Test
        @DisplayName("Should return empty for null partner ID")
        void shouldReturnEmptyForNullPartnerId() {
            Optional<Partner> result = configService.findPartner(null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should save partner")
        void shouldSavePartner() {
            when(partnerRepository.save(any(Partner.class))).thenReturn(testPartner);

            Partner result = configService.savePartner(testPartner);

            assertNotNull(result);
            verify(partnerRepository).save(testPartner);
        }

        @Test
        @DisplayName("Should delete partner")
        void shouldDeletePartner() {
            configService.deletePartner("PARTNER1");

            verify(partnerRepository).deleteById("PARTNER1");
        }

        @Test
        @DisplayName("Should check if partner exists")
        void shouldCheckPartnerExists() {
            when(partnerRepository.existsById("PARTNER1")).thenReturn(true);

            assertTrue(configService.partnerExists("PARTNER1"));
        }
    }

    @Nested
    @DisplayName("Virtual File Management Tests")
    class VirtualFileManagementTests {

        @Test
        @DisplayName("Should get all virtual files")
        void shouldGetAllVirtualFiles() {
            when(virtualFileRepository.findAll()).thenReturn(List.of(testVirtualFile));

            List<VirtualFile> result = configService.getAllVirtualFiles();

            assertEquals(1, result.size());
            assertEquals("FILE1", result.get(0).getId());
        }

        @Test
        @DisplayName("Should get enabled virtual files only")
        void shouldGetEnabledVirtualFiles() {
            when(virtualFileRepository.findByEnabled(true)).thenReturn(List.of(testVirtualFile));

            List<VirtualFile> result = configService.getEnabledVirtualFiles();

            assertEquals(1, result.size());
            assertTrue(result.get(0).isEnabled());
        }

        @Test
        @DisplayName("Should get virtual file by ID")
        void shouldGetVirtualFileById() {
            when(virtualFileRepository.findById("FILE1")).thenReturn(Optional.of(testVirtualFile));

            Optional<VirtualFile> result = configService.getVirtualFile("FILE1");

            assertTrue(result.isPresent());
            assertEquals("FILE1", result.get().getId());
        }

        @Test
        @DisplayName("Should return empty for null filename")
        void shouldReturnEmptyForNullFilename() {
            Optional<VirtualFile> result = configService.findVirtualFile(null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should save virtual file")
        void shouldSaveVirtualFile() {
            when(virtualFileRepository.save(any(VirtualFile.class))).thenReturn(testVirtualFile);

            VirtualFile result = configService.saveVirtualFile(testVirtualFile);

            assertNotNull(result);
            verify(virtualFileRepository).save(testVirtualFile);
        }

        @Test
        @DisplayName("Should delete virtual file")
        void shouldDeleteVirtualFile() {
            configService.deleteVirtualFile("FILE1");

            verify(virtualFileRepository).deleteById("FILE1");
        }

        @Test
        @DisplayName("Should check if virtual file exists")
        void shouldCheckVirtualFileExists() {
            when(virtualFileRepository.existsById("FILE1")).thenReturn(true);

            assertTrue(configService.virtualFileExists("FILE1"));
        }
    }
}
