package com.vectis.client;

import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;

import com.vectis.client.config.ClientConfig;
import com.vectis.client.service.PesitClientService;
import com.vectis.client.service.PesitClientService.TransferResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Send message command - supports both PI_99 (free message) and file-based
 * messages
 */
@Component
@Command(name = "message", aliases = { "msg" }, description = "Send a message to a PeSIT server")
public class MessageCommand implements Callable<Integer> {

    private final PesitClientService clientService;
    private final ClientConfig config;

    @Parameters(index = "0", description = "Message text to send")
    private String message;

    @Option(names = { "-h", "--host" }, description = "Server host")
    private String host;

    @Option(names = { "-p", "--port" }, description = "Server port")
    private Integer port;

    @Option(names = { "-s", "--server-id" }, description = "Server ID (default: ${DEFAULT-VALUE})")
    private String serverId = "PESIT_SERVER";

    @Option(names = { "-m",
            "--mode" }, description = "Message mode: fpdu (MSG FPDU), pi99 (parameter in CONNECT), or file (send as file). Default: ${DEFAULT-VALUE}")
    private MessageMode mode = MessageMode.FPDU;

    @Option(names = { "-n", "--name" }, description = "Message name/identifier (for file mode, used as filename)")
    private String messageName = "MESSAGE";

    @Option(names = { "--pi91" }, description = "Use PI_91 (4096 chars) instead of PI_99 (254 chars) for pi99 mode")
    private boolean usePi91 = false;

    public MessageCommand(PesitClientService clientService, ClientConfig config) {
        this.clientService = clientService;
        this.config = config;
    }

    @Override
    public Integer call() throws Exception {
        String targetHost = host != null ? host : config.getHost();
        int targetPort = port != null ? port : config.getPort();

        System.out.println("Sending message to " + targetHost + ":" + targetPort);
        System.out.println("Mode: " + mode);
        System.out.println("Message length: " + message.length() + " chars");
        System.out.println();

        // Validate message length for parameter-based modes
        if (mode == MessageMode.PI99 || mode == MessageMode.FPDU) {
            int maxLength = usePi91 ? 4096 : 254;
            if (message.length() > maxLength) {
                System.err.println("✗ Message too long for " + (usePi91 ? "PI_91" : "PI_99") +
                        " (max " + maxLength + " chars, got " + message.length() + ")");
                System.err.println("  Use --mode=file to send as a file, or --pi91 for longer messages");
                return 1;
            }
        }

        TransferResult result;

        switch (mode) {
            case FPDU:
                // Send message using MSG FPDU (dedicated message FPDU)
                result = clientService.sendMessageFpdu(targetHost, targetPort, serverId,
                        message, usePi91);
                break;
            case FILE:
                // Send message as a file
                result = clientService.sendMessageAsFile(targetHost, targetPort, serverId,
                        message, messageName);
                break;
            case PI99:
            default:
                // Send message using PI_99 or PI_91 parameter in CONNECT
                result = clientService.sendMessage(targetHost, targetPort, serverId,
                        message, usePi91);
                break;
        }

        if (result.isSuccess()) {
            System.out.println("✓ Message sent successfully");
            System.out.println("  Duration: " + result.getDurationMs() + " ms");
            return 0;
        } else {
            System.err.println("✗ Message send failed");
            if (result.getErrorMessage() != null) {
                System.err.println("  Error: " + result.getErrorMessage());
            }
            return 1;
        }
    }

    public enum MessageMode {
        FPDU, // Send using dedicated MSG FPDU (phase 0xC0, type 0x16)
        PI99, // Send as PI_99 or PI_91 parameter in CONNECT/RELEASE
        FILE // Send as a file transfer
    }
}
