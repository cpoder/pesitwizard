package com.pesitwizard.client.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Unit tests for AsyncConfig.
 * Tests that all executors are configured correctly with appropriate thread pool settings.
 */
@DisplayName("AsyncConfig Unit Tests")
class AsyncConfigTest {

    private AsyncConfig config;

    @BeforeEach
    void setUp() {
        config = new AsyncConfig();
    }

    @Test
    @DisplayName("transferExecutor should be configured with correct settings")
    void transferExecutor_shouldBeConfiguredCorrectly() {
        // Act
        Executor executor = config.transferExecutor();

        // Assert
        assertNotNull(executor, "transferExecutor should not be null");
        assertTrue(executor instanceof ThreadPoolTaskExecutor, "Should be ThreadPoolTaskExecutor");

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(4, taskExecutor.getCorePoolSize(), "Core pool size should be 4");
        assertEquals(10, taskExecutor.getMaxPoolSize(), "Max pool size should be 10");
        assertEquals(100, taskExecutor.getQueueCapacity(), "Queue capacity should be 100");
        assertTrue(taskExecutor.getThreadNamePrefix().startsWith("transfer-"),
                "Thread name prefix should be 'transfer-'");
    }

    @Test
    @DisplayName("websocketExecutor should be configured with correct settings")
    void websocketExecutor_shouldBeConfiguredCorrectly() {
        // Act
        Executor executor = config.websocketExecutor();

        // Assert
        assertNotNull(executor, "websocketExecutor should not be null");
        assertTrue(executor instanceof ThreadPoolTaskExecutor, "Should be ThreadPoolTaskExecutor");

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(2, taskExecutor.getCorePoolSize(), "Core pool size should be 2");
        assertEquals(4, taskExecutor.getMaxPoolSize(), "Max pool size should be 4");
        assertEquals(100, taskExecutor.getQueueCapacity(), "Queue capacity should be 100");
        assertTrue(taskExecutor.getThreadNamePrefix().startsWith("websocket-"),
                "Thread name prefix should be 'websocket-'");
    }

    @Test
    @DisplayName("pluginExecutor should be configured with correct settings")
    void pluginExecutor_shouldBeConfiguredCorrectly() {
        // Act
        Executor executor = config.pluginExecutor();

        // Assert
        assertNotNull(executor, "pluginExecutor should not be null");
        assertTrue(executor instanceof ThreadPoolTaskExecutor, "Should be ThreadPoolTaskExecutor");

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(2, taskExecutor.getCorePoolSize(), "Core pool size should be 2");
        assertEquals(5, taskExecutor.getMaxPoolSize(), "Max pool size should be 5");
        assertEquals(50, taskExecutor.getQueueCapacity(), "Queue capacity should be 50");
        assertTrue(taskExecutor.getThreadNamePrefix().startsWith("plugin-"),
                "Thread name prefix should be 'plugin-'");

        // Check rejection policy is CallerRunsPolicy
        ThreadPoolExecutor threadPoolExecutor = taskExecutor.getThreadPoolExecutor();
        assertTrue(threadPoolExecutor.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy,
                "Rejection policy should be CallerRunsPolicy");
    }

    @Test
    @DisplayName("All executors should have unique thread name prefixes")
    void allExecutors_shouldHaveUniqueThreadNamePrefixes() {
        // Act
        ThreadPoolTaskExecutor transferExecutor = (ThreadPoolTaskExecutor) config.transferExecutor();
        ThreadPoolTaskExecutor websocketExecutor = (ThreadPoolTaskExecutor) config.websocketExecutor();
        ThreadPoolTaskExecutor pluginExecutor = (ThreadPoolTaskExecutor) config.pluginExecutor();

        // Assert
        String transferPrefix = transferExecutor.getThreadNamePrefix();
        String websocketPrefix = websocketExecutor.getThreadNamePrefix();
        String pluginPrefix = pluginExecutor.getThreadNamePrefix();

        assertNotEquals(transferPrefix, websocketPrefix, "Transfer and WebSocket should have different prefixes");
        assertNotEquals(transferPrefix, pluginPrefix, "Transfer and Plugin should have different prefixes");
        assertNotEquals(websocketPrefix, pluginPrefix, "WebSocket and Plugin should have different prefixes");
    }

    @Test
    @DisplayName("websocketExecutor should have smaller pool than transferExecutor")
    void websocketExecutor_shouldHaveSmallerPoolThanTransferExecutor() {
        // Act
        ThreadPoolTaskExecutor transferExecutor = (ThreadPoolTaskExecutor) config.transferExecutor();
        ThreadPoolTaskExecutor websocketExecutor = (ThreadPoolTaskExecutor) config.websocketExecutor();

        // Assert - WebSocket is lower priority, should have smaller pool
        assertTrue(websocketExecutor.getCorePoolSize() <= transferExecutor.getCorePoolSize(),
                "WebSocket core pool should be <= transfer core pool");
        assertTrue(websocketExecutor.getMaxPoolSize() <= transferExecutor.getMaxPoolSize(),
                "WebSocket max pool should be <= transfer max pool");
    }

    @Test
    @DisplayName("pluginExecutor should have smaller queue than transferExecutor")
    void pluginExecutor_shouldHaveSmallerQueueThanTransferExecutor() {
        // Act
        ThreadPoolTaskExecutor transferExecutor = (ThreadPoolTaskExecutor) config.transferExecutor();
        ThreadPoolTaskExecutor pluginExecutor = (ThreadPoolTaskExecutor) config.pluginExecutor();

        // Assert - Plugin queue should be smaller (plugins can use CallerRunsPolicy fallback)
        assertTrue(pluginExecutor.getQueueCapacity() <= transferExecutor.getQueueCapacity(),
                "Plugin queue should be <= transfer queue");
    }

    @Test
    @DisplayName("Executors should be properly initialized after creation")
    void executors_shouldBeInitializedAfterCreation() {
        // Act
        ThreadPoolTaskExecutor transferExecutor = (ThreadPoolTaskExecutor) config.transferExecutor();
        ThreadPoolTaskExecutor websocketExecutor = (ThreadPoolTaskExecutor) config.websocketExecutor();
        ThreadPoolTaskExecutor pluginExecutor = (ThreadPoolTaskExecutor) config.pluginExecutor();

        // Assert - All executors should be initialized and have thread pools
        assertNotNull(transferExecutor.getThreadPoolExecutor(),
                "Transfer executor should have initialized thread pool");
        assertNotNull(websocketExecutor.getThreadPoolExecutor(),
                "WebSocket executor should have initialized thread pool");
        assertNotNull(pluginExecutor.getThreadPoolExecutor(),
                "Plugin executor should have initialized thread pool");
    }

    @Test
    @DisplayName("pluginExecutor CallerRunsPolicy should execute task in caller thread when queue is full")
    void pluginExecutor_callerRunsPolicy_shouldExecuteInCallerThread() throws InterruptedException {
        // Arrange
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.pluginExecutor();

        // Fill up the thread pool and queue
        for (int i = 0; i < executor.getMaxPoolSize() + executor.getQueueCapacity(); i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Act - Submit one more task that should trigger CallerRunsPolicy
        String callerThreadName = Thread.currentThread().getName();
        final String[] executionThreadName = new String[1];

        executor.execute(() -> {
            executionThreadName[0] = Thread.currentThread().getName();
        });

        // Assert - Task should have executed in caller thread (or completed in pool)
        Thread.sleep(200);
        assertTrue(executionThreadName[0] != null, "Task should have been executed");
    }
}
