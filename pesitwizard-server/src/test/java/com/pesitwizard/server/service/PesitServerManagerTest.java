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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.server.cluster.ClusterEvent;
import com.pesitwizard.server.cluster.ClusterProvider;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.config.SslProperties;
import com.pesitwizard.server.entity.PesitServerConfig;
import com.pesitwizard.server.entity.PesitServerConfig.ServerStatus;
import com.pesitwizard.server.handler.PesitSessionHandler;
import com.pesitwizard.server.repository.PesitServerConfigRepository;
import com.pesitwizard.server.ssl.SslContextFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("PesitServerManager Tests")
class PesitServerManagerTest {

    @Mock
    private PesitServerConfigRepository configRepository;

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private PesitSessionHandler sessionHandler;

    @Mock
    private FileSystemService fileSystemService;

    @Mock
    private SslProperties sslProperties;

    @Mock
    private SslContextFactory sslContextFactory;

    @Mock
    private PesitServerProperties globalProperties;

    private PesitServerManager serverManager;

    private PesitServerConfig testConfig;

    @BeforeEach
    void setUp() {
        serverManager = new PesitServerManager(configRepository, clusterProvider,
                sessionHandler, fileSystemService, sslProperties, sslContextFactory, globalProperties);

        testConfig = new PesitServerConfig();
        testConfig.setId(1L);
        testConfig.setServerId("SERVER1");
        testConfig.setPort(5100);
        testConfig.setStatus(ServerStatus.STOPPED);
    }

    @Nested
    @DisplayName("CRUD Operations")
    class CrudTests {

        @Test
        @DisplayName("should create server config")
        void shouldCreateServer() {
            when(configRepository.existsByServerId("SERVER1")).thenReturn(false);
            when(configRepository.existsByPort(5100)).thenReturn(false);
            when(configRepository.save(any(PesitServerConfig.class))).thenReturn(testConfig);

            PesitServerConfig result = serverManager.createServer(testConfig);

            assertNotNull(result);
            assertEquals("SERVER1", result.getServerId());
            verify(configRepository).save(testConfig);
        }

        @Test
        @DisplayName("should throw when server ID already exists")
        void shouldThrowWhenServerIdExists() {
            when(configRepository.existsByServerId("SERVER1")).thenReturn(true);

            assertThrows(IllegalArgumentException.class,
                    () -> serverManager.createServer(testConfig));
        }

        @Test
        @DisplayName("should throw when port already in use")
        void shouldThrowWhenPortInUse() {
            when(configRepository.existsByServerId("SERVER1")).thenReturn(false);
            when(configRepository.existsByPort(5100)).thenReturn(true);

            assertThrows(IllegalArgumentException.class,
                    () -> serverManager.createServer(testConfig));
        }

        @Test
        @DisplayName("should get all servers")
        void shouldGetAllServers() {
            when(configRepository.findAll()).thenReturn(List.of(testConfig));

            List<PesitServerConfig> servers = serverManager.getAllServers();

            assertEquals(1, servers.size());
            assertEquals("SERVER1", servers.get(0).getServerId());
        }

        @Test
        @DisplayName("should get server by ID")
        void shouldGetServerById() {
            when(configRepository.findByServerId("SERVER1")).thenReturn(Optional.of(testConfig));

            Optional<PesitServerConfig> result = serverManager.getServer("SERVER1");

            assertTrue(result.isPresent());
            assertEquals("SERVER1", result.get().getServerId());
        }

        @Test
        @DisplayName("should return empty for non-existent server")
        void shouldReturnEmptyForNonExistent() {
            when(configRepository.findByServerId("non-existent")).thenReturn(Optional.empty());

            Optional<PesitServerConfig> result = serverManager.getServer("non-existent");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should delete server")
        void shouldDeleteServer() {
            when(configRepository.findByServerId("SERVER1")).thenReturn(Optional.of(testConfig));

            serverManager.deleteServer("SERVER1");

            verify(configRepository).delete(testConfig);
        }

        @Test
        @DisplayName("should throw when deleting non-existent server")
        void shouldThrowWhenDeletingNonExistent() {
            when(configRepository.findByServerId("non-existent")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> serverManager.deleteServer("non-existent"));
        }
    }

    @Nested
    @DisplayName("Server Status")
    class ServerStatusTests {

        @Test
        @DisplayName("should get server status")
        void shouldGetServerStatus() {
            testConfig.setStatus(ServerStatus.RUNNING);
            when(configRepository.findByServerId("SERVER1")).thenReturn(Optional.of(testConfig));

            ServerStatus status = serverManager.getServerStatus("SERVER1");

            assertEquals(ServerStatus.RUNNING, status);
        }

        @Test
        @DisplayName("should return null for non-existent server")
        void shouldReturnNullForNonExistent() {
            when(configRepository.findByServerId("non-existent")).thenReturn(Optional.empty());

            ServerStatus status = serverManager.getServerStatus("non-existent");

            assertNull(status);
        }

        @Test
        @DisplayName("should check if server is running")
        void shouldCheckIfServerRunning() {
            // Server not in runningServers map
            boolean running = serverManager.isServerRunning("SERVER1");

            assertFalse(running);
        }

        @Test
        @DisplayName("should get active connections")
        void shouldGetActiveConnections() {
            // Server not running
            int connections = serverManager.getActiveConnections("SERVER1");

            assertEquals(0, connections);
        }
    }

    @Nested
    @DisplayName("Cluster Events")
    class ClusterEventTests {

        @Test
        @DisplayName("should handle BECAME_LEADER event")
        void shouldHandleBecameLeaderEvent() {
            when(configRepository.findByAutoStartTrue()).thenReturn(List.of());

            ClusterEvent event = ClusterEvent.becameLeader("node-1");
            serverManager.onClusterEvent(event);

            verify(configRepository).findByAutoStartTrue();
        }

        @Test
        @DisplayName("should handle LOST_LEADERSHIP event")
        void shouldHandleLostLeadershipEvent() {
            ClusterEvent event = ClusterEvent.lostLeadership("node-1");
            serverManager.onClusterEvent(event);

            // Should attempt to stop all servers (none running in this test)
        }

        @Test
        @DisplayName("should ignore other cluster events")
        void shouldIgnoreOtherEvents() {
            ClusterEvent event = ClusterEvent.viewChanged("node-2", 2, false);
            serverManager.onClusterEvent(event);

            // No server operations should be triggered
            verifyNoInteractions(configRepository);
        }
    }

    @Nested
    @DisplayName("Update Server")
    class UpdateServerTests {

        @Test
        @DisplayName("should update server config")
        void shouldUpdateServer() {
            PesitServerConfig updates = new PesitServerConfig();
            updates.setPort(5100);
            updates.setBindAddress("127.0.0.1");
            updates.setMaxConnections(50);

            when(configRepository.findByServerId("SERVER1")).thenReturn(Optional.of(testConfig));
            when(configRepository.save(any(PesitServerConfig.class))).thenReturn(testConfig);

            PesitServerConfig result = serverManager.updateServer("SERVER1", updates);

            assertNotNull(result);
            verify(configRepository).save(any(PesitServerConfig.class));
        }

        @Test
        @DisplayName("should throw when updating non-existent server")
        void shouldThrowWhenUpdatingNonExistent() {
            when(configRepository.findByServerId("non-existent")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> serverManager.updateServer("non-existent", testConfig));
        }

        @Test
        @DisplayName("should throw when port change conflicts")
        void shouldThrowWhenPortChangeConflicts() {
            PesitServerConfig updates = new PesitServerConfig();
            updates.setPort(5200); // Different port

            when(configRepository.findByServerId("SERVER1")).thenReturn(Optional.of(testConfig));
            when(configRepository.existsByPort(5200)).thenReturn(true);

            assertThrows(IllegalArgumentException.class,
                    () -> serverManager.updateServer("SERVER1", updates));
        }
    }
}
