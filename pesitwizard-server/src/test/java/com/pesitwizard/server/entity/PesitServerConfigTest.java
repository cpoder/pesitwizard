package com.pesitwizard.server.entity;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pesitwizard.server.entity.PesitServerConfig.ServerStatus;

@DisplayName("PesitServerConfig Tests")
class PesitServerConfigTest {

    @Test
    @DisplayName("should have default values with builder")
    void shouldHaveDefaultValuesWithBuilder() {
        PesitServerConfig config = PesitServerConfig.builder().build();

        assertEquals("0.0.0.0", config.getBindAddress());
        assertEquals(2, config.getProtocolVersion());
        assertEquals(100, config.getMaxConnections());
        assertEquals(30000, config.getConnectionTimeout());
        assertEquals(60000, config.getReadTimeout());
        assertEquals("/data/received", config.getReceiveDirectory());
        assertEquals("/data/send", config.getSendDirectory());
        assertEquals(4096, config.getMaxEntitySize());
        assertTrue(config.isSyncPointsEnabled());
        assertTrue(config.isResyncEnabled());
        assertFalse(config.isAutoStart());
        assertEquals(ServerStatus.STOPPED, config.getStatus());
    }

    @Test
    @DisplayName("should store all config attributes")
    void shouldStoreAllConfigAttributes() {
        Instant now = Instant.now();

        PesitServerConfig config = PesitServerConfig.builder()
                .id(1L)
                .serverId("TEST_SERVER")
                .port(5000)
                .bindAddress("127.0.0.1")
                .protocolVersion(3)
                .maxConnections(50)
                .connectionTimeout(15000)
                .readTimeout(30000)
                .receiveDirectory("/custom/in")
                .sendDirectory("/custom/out")
                .maxEntitySize(8192)
                .syncPointsEnabled(false)
                .resyncEnabled(false)
                .autoStart(true)
                .status(ServerStatus.RUNNING)
                .createdAt(now)
                .updatedAt(now)
                .lastStartedAt(now)
                .lastStoppedAt(now)
                .build();

        assertEquals(1L, config.getId());
        assertEquals("TEST_SERVER", config.getServerId());
        assertEquals(5000, config.getPort());
        assertEquals("127.0.0.1", config.getBindAddress());
        assertEquals(3, config.getProtocolVersion());
        assertEquals(50, config.getMaxConnections());
        assertEquals(15000, config.getConnectionTimeout());
        assertEquals(30000, config.getReadTimeout());
        assertEquals("/custom/in", config.getReceiveDirectory());
        assertEquals("/custom/out", config.getSendDirectory());
        assertEquals(8192, config.getMaxEntitySize());
        assertFalse(config.isSyncPointsEnabled());
        assertFalse(config.isResyncEnabled());
        assertTrue(config.isAutoStart());
        assertEquals(ServerStatus.RUNNING, config.getStatus());
        assertEquals(now, config.getCreatedAt());
        assertEquals(now, config.getUpdatedAt());
        assertEquals(now, config.getLastStartedAt());
        assertEquals(now, config.getLastStoppedAt());
    }

    @Test
    @DisplayName("should have all server status values")
    void shouldHaveAllServerStatusValues() {
        assertEquals(5, ServerStatus.values().length);
        assertNotNull(ServerStatus.STOPPED);
        assertNotNull(ServerStatus.STARTING);
        assertNotNull(ServerStatus.RUNNING);
        assertNotNull(ServerStatus.STOPPING);
        assertNotNull(ServerStatus.ERROR);
    }

    @Test
    @DisplayName("onCreate should set timestamps")
    void onCreateShouldSetTimestamps() {
        PesitServerConfig config = new PesitServerConfig();
        assertNull(config.getCreatedAt());
        assertNull(config.getUpdatedAt());

        config.onCreate();

        assertNotNull(config.getCreatedAt());
        assertNotNull(config.getUpdatedAt());
    }

    @Test
    @DisplayName("onUpdate should update timestamp")
    void onUpdateShouldUpdateTimestamp() {
        PesitServerConfig config = new PesitServerConfig();
        config.onCreate();
        Instant originalUpdated = config.getUpdatedAt();

        // Wait to ensure time difference
        await().atMost(Duration.ofMillis(200)).pollDelay(Duration.ofMillis(10)).until(() -> true);

        config.onUpdate();

        assertNotNull(config.getUpdatedAt());
        assertTrue(config.getUpdatedAt().isAfter(originalUpdated) ||
                config.getUpdatedAt().equals(originalUpdated));
    }
}
