package com.vectis.client;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command to run the PeSIT client as a REST API server
 */
@Slf4j
@Component
@Command(name = "serve", description = "Run as REST API server", mixinStandardHelpOptions = true)
public class ServeCommand implements Callable<Integer> {

    @Option(names = { "-p", "--port" }, description = "Server port (default: 8081)")
    private Integer port = 8081;

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    @Override
    public Integer call() {
        log.info("PeSIT Client REST API server started on port {}", port);
        log.info("API endpoints available at http://localhost:{}/api/v1/", port);
        log.info("Health check: http://localhost:{}/actuator/health", port);
        log.info("H2 Console: http://localhost:{}/h2-console", port);
        log.info("Press Ctrl+C to stop...");

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            shutdownLatch.countDown();
        }));

        try {
            // Keep the application running
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return 0;
    }
}
