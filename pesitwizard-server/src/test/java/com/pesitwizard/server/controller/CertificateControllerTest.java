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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pesitwizard.server.entity.CertificateStore;
import com.pesitwizard.server.entity.CertificateStore.CertificatePurpose;
import com.pesitwizard.server.entity.CertificateStore.StoreFormat;
import com.pesitwizard.server.entity.CertificateStore.StoreType;
import com.pesitwizard.server.service.CertificateService;
import com.pesitwizard.server.service.CertificateService.CertificateStatistics;
import com.pesitwizard.server.service.CertificateService.StoreEntry;
import com.pesitwizard.server.ssl.SslConfigurationException;

@WebMvcTest(CertificateController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CertificateController Tests")
class CertificateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CertificateService certificateService;

    private CertificateStore testKeystore;
    private CertificateStore testTruststore;

    @BeforeEach
    void setUp() {
        testKeystore = new CertificateStore();
        testKeystore.setId(1L);
        testKeystore.setName("server-keystore");
        testKeystore.setStoreType(StoreType.KEYSTORE);
        testKeystore.setFormat(StoreFormat.PKCS12);
        testKeystore.setPurpose(CertificatePurpose.SERVER);
        testKeystore.setActive(true);

        testTruststore = new CertificateStore();
        testTruststore.setId(2L);
        testTruststore.setName("ca-truststore");
        testTruststore.setStoreType(StoreType.TRUSTSTORE);
        testTruststore.setFormat(StoreFormat.JKS);
        testTruststore.setPurpose(CertificatePurpose.CA);
        testTruststore.setActive(true);
    }

    @Nested
    @DisplayName("List & Get")
    class ListAndGetTests {

        @Test
        @DisplayName("should list all certificates")
        void shouldListAllCertificates() throws Exception {
            when(certificateService.getAllCertificateStores())
                    .thenReturn(List.of(testKeystore, testTruststore));

            mockMvc.perform(get("/api/v1/certificates"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("should list keystores")
        void shouldListKeystores() throws Exception {
            when(certificateService.getActiveCertificateStoresByType(StoreType.KEYSTORE))
                    .thenReturn(List.of(testKeystore));

            mockMvc.perform(get("/api/v1/certificates/keystores"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].storeType").value("KEYSTORE"));
        }

        @Test
        @DisplayName("should list truststores")
        void shouldListTruststores() throws Exception {
            when(certificateService.getActiveCertificateStoresByType(StoreType.TRUSTSTORE))
                    .thenReturn(List.of(testTruststore));

            mockMvc.perform(get("/api/v1/certificates/truststores"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].storeType").value("TRUSTSTORE"));
        }

        @Test
        @DisplayName("should get certificate by ID")
        void shouldGetCertificateById() throws Exception {
            when(certificateService.getCertificateStore(1L)).thenReturn(Optional.of(testKeystore));

            mockMvc.perform(get("/api/v1/certificates/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("server-keystore"));
        }

        @Test
        @DisplayName("should return 404 for non-existent certificate")
        void shouldReturn404ForNonExistent() throws Exception {
            when(certificateService.getCertificateStore(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/certificates/999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should get certificate by name")
        void shouldGetCertificateByName() throws Exception {
            when(certificateService.getCertificateStoreByName("server-keystore"))
                    .thenReturn(Optional.of(testKeystore));

            mockMvc.perform(get("/api/v1/certificates/name/server-keystore"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("should get default keystore")
        void shouldGetDefaultKeystore() throws Exception {
            testKeystore.setIsDefault(true);
            when(certificateService.getDefaultCertificateStore(StoreType.KEYSTORE))
                    .thenReturn(Optional.of(testKeystore));

            mockMvc.perform(get("/api/v1/certificates/keystores/default"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("server-keystore"));
        }

        @Test
        @DisplayName("should get default truststore")
        void shouldGetDefaultTruststore() throws Exception {
            testTruststore.setIsDefault(true);
            when(certificateService.getDefaultCertificateStore(StoreType.TRUSTSTORE))
                    .thenReturn(Optional.of(testTruststore));

            mockMvc.perform(get("/api/v1/certificates/truststores/default"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("ca-truststore"));
        }
    }

    @Nested
    @DisplayName("Actions")
    class ActionsTests {

        @Test
        @DisplayName("should set certificate as default")
        void shouldSetAsDefault() throws Exception {
            testKeystore.setIsDefault(true);
            when(certificateService.setAsDefault(1L)).thenReturn(testKeystore);

            mockMvc.perform(post("/api/v1/certificates/1/set-default"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should activate certificate")
        void shouldActivateCertificate() throws Exception {
            when(certificateService.activate(1L)).thenReturn(testKeystore);

            mockMvc.perform(post("/api/v1/certificates/1/activate"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should deactivate certificate")
        void shouldDeactivateCertificate() throws Exception {
            testKeystore.setActive(false);
            when(certificateService.deactivate(1L)).thenReturn(testKeystore);

            mockMvc.perform(post("/api/v1/certificates/1/deactivate"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should validate certificate")
        void shouldValidateCertificate() throws Exception {
            doNothing().when(certificateService).validateCertificateStore(1L);

            mockMvc.perform(post("/api/v1/certificates/1/validate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Certificate is valid"));
        }

        @Test
        @DisplayName("should return 400 for invalid certificate validation")
        void shouldReturn400ForInvalidCertificate() throws Exception {
            doThrow(new SslConfigurationException("Certificate expired"))
                    .when(certificateService).validateCertificateStore(1L);

            mockMvc.perform(post("/api/v1/certificates/1/validate"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("should delete certificate")
        void shouldDeleteCertificate() throws Exception {
            doNothing().when(certificateService).deleteCertificateStore(1L);

            mockMvc.perform(delete("/api/v1/certificates/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent certificate")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            doThrow(new IllegalArgumentException("Not found"))
                    .when(certificateService).deleteCertificateStore(999L);

            mockMvc.perform(delete("/api/v1/certificates/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Expiration")
    class ExpirationTests {

        @Test
        @DisplayName("should get expiring certificates")
        void shouldGetExpiringCertificates() throws Exception {
            when(certificateService.getExpiringCertificates(30))
                    .thenReturn(List.of(testKeystore));

            mockMvc.perform(get("/api/v1/certificates/expiring"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("should get expired certificates")
        void shouldGetExpiredCertificates() throws Exception {
            when(certificateService.getExpiredCertificates()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/certificates/expired"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("Partner Certificates")
    class PartnerCertificatesTests {

        @Test
        @DisplayName("should get partner certificates")
        void shouldGetPartnerCertificates() throws Exception {
            testKeystore.setPartnerId("partner-1");
            when(certificateService.getPartnerCertificates("partner-1"))
                    .thenReturn(List.of(testKeystore));

            mockMvc.perform(get("/api/v1/certificates/partner/partner-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].partnerId").value("partner-1"));
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("should get certificate statistics")
        void shouldGetStatistics() throws Exception {
            CertificateStatistics stats = new CertificateStatistics();
            stats.setTotalKeystores(5);
            stats.setTotalTruststores(3);
            when(certificateService.getStatistics()).thenReturn(stats);

            mockMvc.perform(get("/api/v1/certificates/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalKeystores").value(5));
        }
    }

    @Nested
    @DisplayName("Create Empty Stores")
    class CreateEmptyStoresTests {

        @Test
        @DisplayName("should create empty keystore")
        void shouldCreateEmptyKeystore() throws Exception {
            when(certificateService.createEmptyKeystore(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                    .thenReturn(testKeystore);

            mockMvc.perform(post("/api/v1/certificates/keystores/create")
                    .param("name", "new-keystore")
                    .param("storePassword", "password123"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("should create empty truststore")
        void shouldCreateEmptyTruststore() throws Exception {
            when(certificateService.createEmptyTruststore(any(), any(), any(), any(), any(), anyBoolean(), any()))
                    .thenReturn(testTruststore);

            mockMvc.perform(post("/api/v1/certificates/truststores/create")
                    .param("name", "new-truststore")
                    .param("storePassword", "password123"))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Entries Management")
    class EntriesManagementTests {

        @Test
        @DisplayName("should list entries")
        void shouldListEntries() throws Exception {
            StoreEntry entry1 = new StoreEntry("alias1", "certificate", "CN=Test1", null);
            StoreEntry entry2 = new StoreEntry("alias2", "key", "CN=Test2", null);
            when(certificateService.listStoreEntries(1L)).thenReturn(List.of(entry1, entry2));

            mockMvc.perform(get("/api/v1/certificates/1/entries"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("should remove entry")
        void shouldRemoveEntry() throws Exception {
            when(certificateService.removeStoreEntry(1L, "old-alias")).thenReturn(testKeystore);

            mockMvc.perform(delete("/api/v1/certificates/1/entries/old-alias"))
                    .andExpect(status().isOk());
        }
    }
}
