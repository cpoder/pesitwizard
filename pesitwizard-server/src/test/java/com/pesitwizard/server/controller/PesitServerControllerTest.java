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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.server.entity.PesitServerConfig;
import com.pesitwizard.server.entity.PesitServerConfig.ServerStatus;
import com.pesitwizard.server.service.PesitServerManager;

@WebMvcTest(PesitServerController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PesitServerController Tests")
class PesitServerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PesitServerManager serverManager;

    private PesitServerConfig testServer;

    @BeforeEach
    void setUp() {
        testServer = new PesitServerConfig();
        testServer.setServerId("server-1");
        testServer.setPort(5100);
    }

    @Nested
    @DisplayName("List & Get")
    class ListAndGetTests {

        @Test
        @DisplayName("should get all servers")
        void shouldGetAllServers() throws Exception {
            when(serverManager.getAllServers()).thenReturn(List.of(testServer));

            mockMvc.perform(get("/api/servers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].serverId").value("server-1"));
        }

        @Test
        @DisplayName("should get server by ID")
        void shouldGetServerById() throws Exception {
            when(serverManager.getServer("server-1")).thenReturn(Optional.of(testServer));

            mockMvc.perform(get("/api/servers/server-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.serverId").value("server-1"));
        }

        @Test
        @DisplayName("should return 404 for non-existent server")
        void shouldReturn404ForNonExistent() throws Exception {
            when(serverManager.getServer("non-existent")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/servers/non-existent"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("CRUD Operations")
    class CrudTests {

        @Test
        @DisplayName("should create server")
        void shouldCreateServer() throws Exception {
            when(serverManager.createServer(any(PesitServerConfig.class))).thenReturn(testServer);

            mockMvc.perform(post("/api/servers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testServer)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.serverId").value("server-1"));
        }

        @Test
        @DisplayName("should return 400 for invalid server config")
        void shouldReturn400ForInvalidConfig() throws Exception {
            when(serverManager.createServer(any())).thenThrow(new IllegalArgumentException("Invalid config"));

            mockMvc.perform(post("/api/servers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testServer)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("should update server")
        void shouldUpdateServer() throws Exception {
            when(serverManager.updateServer(eq("server-1"), any(PesitServerConfig.class))).thenReturn(testServer);

            mockMvc.perform(put("/api/servers/server-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testServer)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 409 when updating running server")
        void shouldReturn409WhenUpdatingRunningServer() throws Exception {
            when(serverManager.updateServer(eq("server-1"), any()))
                    .thenThrow(new IllegalStateException("Server is running"));

            mockMvc.perform(put("/api/servers/server-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testServer)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should delete server")
        void shouldDeleteServer() throws Exception {
            doNothing().when(serverManager).deleteServer("server-1");

            mockMvc.perform(delete("/api/servers/server-1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent server")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            doThrow(new IllegalArgumentException("Not found")).when(serverManager).deleteServer("non-existent");

            mockMvc.perform(delete("/api/servers/non-existent"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Server Actions")
    class ServerActionsTests {

        @Test
        @DisplayName("should start server")
        void shouldStartServer() throws Exception {
            doNothing().when(serverManager).startServer("server-1");

            mockMvc.perform(post("/api/servers/server-1/start"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RUNNING"));
        }

        @Test
        @DisplayName("should return 404 when starting non-existent server")
        void shouldReturn404WhenStartingNonExistent() throws Exception {
            doThrow(new IllegalArgumentException("Not found")).when(serverManager).startServer("non-existent");

            mockMvc.perform(post("/api/servers/non-existent/start"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 409 when server already running")
        void shouldReturn409WhenAlreadyRunning() throws Exception {
            doThrow(new IllegalStateException("Already running")).when(serverManager).startServer("server-1");

            mockMvc.perform(post("/api/servers/server-1/start"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should stop server")
        void shouldStopServer() throws Exception {
            doNothing().when(serverManager).stopServer("server-1");

            mockMvc.perform(post("/api/servers/server-1/stop"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("STOPPED"));
        }

        @Test
        @DisplayName("should return 409 when stopping already stopped server")
        void shouldReturn409WhenAlreadyStopped() throws Exception {
            doThrow(new IllegalStateException("Not running")).when(serverManager).stopServer("server-1");

            mockMvc.perform(post("/api/servers/server-1/stop"))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Server Status")
    class ServerStatusTests {

        @Test
        @DisplayName("should get server status")
        void shouldGetServerStatus() throws Exception {
            when(serverManager.getServer("server-1")).thenReturn(Optional.of(testServer));
            when(serverManager.getServerStatus("server-1")).thenReturn(ServerStatus.RUNNING);
            when(serverManager.isServerRunning("server-1")).thenReturn(true);
            when(serverManager.getActiveConnections("server-1")).thenReturn(5);

            mockMvc.perform(get("/api/servers/server-1/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RUNNING"))
                    .andExpect(jsonPath("$.running").value(true))
                    .andExpect(jsonPath("$.activeConnections").value(5));
        }

        @Test
        @DisplayName("should return 404 for status of non-existent server")
        void shouldReturn404ForStatusOfNonExistent() throws Exception {
            when(serverManager.getServer("non-existent")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/servers/non-existent/status"))
                    .andExpect(status().isNotFound());
        }
    }
}
