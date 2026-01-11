package com.pesitwizard.client.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async configuration for background operations.
 *
 * <p>Provides three separate thread pools:
 * <ul>
 *   <li><b>transferExecutor</b> - For PeSIT transfer operations (send/receive)</li>
 *   <li><b>websocketExecutor</b> - For WebSocket message broadcasting (prevents blocking transfers)</li>
 *   <li><b>pluginExecutor</b> - For third-party event listeners (Kafka, monitoring, etc.)</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool for PeSIT transfer operations.
     * Core: 4 threads, Max: 10 threads, Queue: 100 tasks
     */
    @Bean(name = "transferExecutor")
    public Executor transferExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("transfer-");
        executor.initialize();
        return executor;
    }

    /**
     * Thread pool for WebSocket message broadcasting.
     * Separate from transfer threads to prevent WebSocket issues from blocking transfers.
     * Core: 2 threads, Max: 4 threads, Queue: 100 messages
     */
    @Bean(name = "websocketExecutor")
    public Executor websocketExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("websocket-");
        executor.initialize();
        return executor;
    }

    /**
     * Thread pool for third-party event listener plugins.
     * Plugins can use @Async("pluginExecutor") to process events asynchronously.
     * Core: 2 threads, Max: 5 threads, Queue: 50 tasks
     * Rejection policy: CallerRunsPolicy (fallback to caller thread if queue full)
     */
    @Bean(name = "pluginExecutor")
    public Executor pluginExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("plugin-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
