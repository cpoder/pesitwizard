package com.pesitwizard.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.client.repository.PesitServerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "nosecurity"})
class TlsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PesitServerRepository serverRepository;

    @Test
    void getTlsStatus_serverNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/servers/nonexistent/tls")).andExpect(status().isNotFound());
    }

    @Test
    void getTlsStatus_serverExists_returnsStatus() throws Exception {
        PesitServer server =
                PesitServer.builder()
                        .name("tls-test-" + System.currentTimeMillis())
                        .host("localhost")
                        .port(6502)
                        .serverId("SID-" + System.currentTimeMillis())
                        .build();
        server = serverRepository.save(server);

        try {
            mockMvc.perform(get("/api/v1/servers/" + server.getId() + "/tls"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tlsEnabled").exists())
                    .andExpect(jsonPath("$.truststoreConfigured").exists())
                    .andExpect(jsonPath("$.keystoreConfigured").exists());
        } finally {
            serverRepository.deleteById(server.getId());
        }
    }

    @Test
    void uploadTruststore_serverNotFound_returns404() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "ca.p12", "application/octet-stream", new byte[] {1});

        mockMvc.perform(
                        multipart("/api/v1/servers/nonexistent/tls/truststore")
                                .file(file)
                                .param("password", "test"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadTruststore_pkcs12WithoutPassword_returns400() throws Exception {
        PesitServer server =
                PesitServer.builder()
                        .name("tls-test2-" + System.currentTimeMillis())
                        .host("localhost")
                        .port(6502)
                        .serverId("SID-" + System.currentTimeMillis())
                        .build();
        server = serverRepository.save(server);

        try {
            MockMultipartFile file =
                    new MockMultipartFile(
                            "file", "ca.p12", "application/octet-stream", new byte[] {1, 2, 3});

            mockMvc.perform(
                            multipart("/api/v1/servers/" + server.getId() + "/tls/truststore")
                                    .file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            jsonPath("$.error").value("Password is required for PKCS12 keystores"));
        } finally {
            serverRepository.deleteById(server.getId());
        }
    }

    @Test
    void uploadTruststore_invalidPkcs12_returns400() throws Exception {
        PesitServer server =
                PesitServer.builder()
                        .name("tls-test3-" + System.currentTimeMillis())
                        .host("localhost")
                        .port(6502)
                        .serverId("SID-" + System.currentTimeMillis())
                        .build();
        server = serverRepository.save(server);

        try {
            MockMultipartFile file =
                    new MockMultipartFile(
                            "file", "ca.p12", "application/octet-stream", new byte[] {1, 2, 3});

            mockMvc.perform(
                            multipart("/api/v1/servers/" + server.getId() + "/tls/truststore")
                                    .file(file)
                                    .param("password", "wrong"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        } finally {
            serverRepository.deleteById(server.getId());
        }
    }

    @Test
    void uploadKeystore_serverNotFound_returns404() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "key.p12", "application/octet-stream", new byte[] {1});

        mockMvc.perform(
                        multipart("/api/v1/servers/nonexistent/tls/keystore")
                                .file(file)
                                .param("password", "test"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTruststore_serverNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/servers/nonexistent/tls/truststore"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTruststore_serverExists_succeeds() throws Exception {
        PesitServer server =
                PesitServer.builder()
                        .name("tls-test4-" + System.currentTimeMillis())
                        .host("localhost")
                        .port(6502)
                        .serverId("SID-" + System.currentTimeMillis())
                        .build();
        server = serverRepository.save(server);

        try {
            mockMvc.perform(delete("/api/v1/servers/" + server.getId() + "/tls/truststore"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        } finally {
            serverRepository.deleteById(server.getId());
        }
    }

    @Test
    void deleteKeystore_serverNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/servers/nonexistent/tls/keystore"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteKeystore_serverExists_succeeds() throws Exception {
        PesitServer server =
                PesitServer.builder()
                        .name("tls-test5-" + System.currentTimeMillis())
                        .host("localhost")
                        .port(6502)
                        .serverId("SID-" + System.currentTimeMillis())
                        .build();
        server = serverRepository.save(server);

        try {
            mockMvc.perform(delete("/api/v1/servers/" + server.getId() + "/tls/keystore"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        } finally {
            serverRepository.deleteById(server.getId());
        }
    }
}
