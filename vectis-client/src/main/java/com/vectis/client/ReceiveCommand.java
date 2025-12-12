package com.vectis.client;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;

import com.vectis.client.config.ClientConfig;
import com.vectis.client.service.PesitClientService;
import com.vectis.client.service.PesitClientService.TransferResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Receive file command
 */
@Component
@Command(name = "receive", description = "Receive a file from a PeSIT server")
public class ReceiveCommand implements Callable<Integer> {

    private final PesitClientService clientService;
    private final ClientConfig config;

    @Parameters(index = "0", description = "Remote filename to receive")
    private String remoteFilename;

    @Parameters(index = "1", arity = "0..1", description = "Local file path (default: current directory)")
    private Path localFile;

    @Option(names = { "-h", "--host" }, description = "Server host (default: ${DEFAULT-VALUE})")
    private String host;

    @Option(names = { "-p", "--port" }, description = "Server port (default: ${DEFAULT-VALUE})")
    private Integer port;

    @Option(names = { "-s", "--server-id" }, description = "Server ID (default: ${DEFAULT-VALUE})")
    private String serverId = "PESIT_SERVER";

    @Option(names = { "-o", "--output-dir" }, description = "Output directory")
    private Path outputDir;

    @Option(names = { "-r", "--retries" }, description = "Number of retries (default: ${DEFAULT-VALUE})")
    private int retries = 3;

    public ReceiveCommand(PesitClientService clientService, ClientConfig config) {
        this.clientService = clientService;
        this.config = config;
    }

    @Override
    public Integer call() throws Exception {
        String targetHost = host != null ? host : config.getHost();
        int targetPort = port != null ? port : config.getPort();

        // Determine local file path
        Path targetFile = localFile;
        if (targetFile == null) {
            Path dir = outputDir != null ? outputDir : Path.of(config.getReceiveDirectory());
            targetFile = dir.resolve(remoteFilename);
        }

        System.out.println("Receiving file: " + remoteFilename);
        System.out.println("From: " + targetHost + ":" + targetPort);
        System.out.println("To: " + targetFile);
        System.out.println();

        TransferResult result = null;
        int attempt = 0;

        while (attempt < retries) {
            attempt++;
            if (attempt > 1) {
                System.out.println("Retry attempt " + attempt + "/" + retries);
                Thread.sleep(config.getRetryDelay());
            }

            try {
                result = clientService.receiveFile(targetHost, targetPort, serverId, remoteFilename, targetFile);
                if (result.isSuccess()) {
                    break;
                }
            } catch (Exception e) {
                System.err.println("Attempt " + attempt + " failed: " + e.getMessage());
                if (attempt >= retries) {
                    throw e;
                }
            }
        }

        if (result != null && result.isSuccess()) {
            System.out.println("✓ Transfer completed successfully");
            System.out.println("  Bytes received: " + result.getBytesTransferred());
            System.out.println("  Duration: " + result.getDurationMs() + " ms");
            System.out.println("  Saved to: " + targetFile);
            if (result.getChecksum() != null) {
                System.out.println("  Checksum (SHA-256): " + result.getChecksum().substring(0, 16) + "...");
            }
            return 0;
        } else {
            System.err.println("✗ Transfer failed");
            if (result != null && result.getErrorMessage() != null) {
                System.err.println("  Error: " + result.getErrorMessage());
            }
            return 1;
        }
    }
}
