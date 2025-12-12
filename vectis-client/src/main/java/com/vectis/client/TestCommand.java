package com.vectis.client;

import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;

import com.vectis.client.config.ClientConfig;
import com.vectis.client.service.PesitClientService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Test connection command
 */
@Component
@Command(name = "test", aliases = { "ping", "check" }, description = "Test connection to a PeSIT server")
public class TestCommand implements Callable<Integer> {

    private final PesitClientService clientService;
    private final ClientConfig config;

    @Option(names = { "-h", "--host" }, description = "Server host")
    private String host;

    @Option(names = { "-p", "--port" }, description = "Server port")
    private Integer port;

    @Option(names = { "-s", "--server-id" }, description = "Server ID (default: ${DEFAULT-VALUE})")
    private String serverId = "PESIT_SERVER";

    @Option(names = { "-n", "--count" }, description = "Number of connection tests (default: ${DEFAULT-VALUE})")
    private int count = 1;

    public TestCommand(PesitClientService clientService, ClientConfig config) {
        this.clientService = clientService;
        this.config = config;
    }

    @Override
    public Integer call() {
        String targetHost = host != null ? host : config.getHost();
        int targetPort = port != null ? port : config.getPort();

        System.out.println("Testing connection to " + targetHost + ":" + targetPort);
        System.out.println("Server ID: " + serverId);
        System.out.println();

        int successful = 0;
        int failed = 0;
        long totalTime = 0;

        for (int i = 0; i < count; i++) {
            if (count > 1) {
                System.out.print("Test " + (i + 1) + "/" + count + ": ");
            }

            long startTime = System.currentTimeMillis();
            boolean success = clientService.testConnection(targetHost, targetPort, serverId);
            long duration = System.currentTimeMillis() - startTime;
            totalTime += duration;

            if (success) {
                successful++;
                System.out.println("✓ Connected (" + duration + " ms)");
            } else {
                failed++;
                System.out.println("✗ Failed");
            }

            if (count > 1 && i < count - 1) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        System.out.println();
        if (count > 1) {
            System.out.println("Results:");
            System.out.println("  Successful: " + successful + "/" + count);
            System.out.println("  Failed:     " + failed + "/" + count);
            System.out.println("  Avg time:   " + (totalTime / count) + " ms");
        }

        return failed == 0 ? 0 : 1;
    }
}
