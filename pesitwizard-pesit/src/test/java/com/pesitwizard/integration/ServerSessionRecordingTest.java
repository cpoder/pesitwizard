package com.pesitwizard.integration;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.integration.PesitSessionRecorder.Direction;

import lombok.extern.slf4j.Slf4j;

/**
 * Records PeSIT sessions from the SERVER's perspective.
 * 
 * These tests start a simple PeSIT server and wait for Connect:Express
 * to connect as a client (using cx-test-push.sh or cx-test-pull.sh).
 * The recorded sessions can then be replayed with MockPesitClient.
 * 
 * Run these tests manually with C:X available:
 * 1. Start this test (it will wait for connection)
 * 2. In another terminal, run: ./scripts/cx-test-push.sh
 * 3. The session will be recorded to golden-sessions/
 */
@Slf4j
@Tag("integration")
@Tag("manual")
@DisplayName("Server Session Recording (requires C:X)")
public class ServerSessionRecordingTest {

    private static final int SERVER_PORT = 6502; // Port configuré dans C:X pour PesitWizard
    private static final Path GOLDEN_DIR = Paths.get("src/test/resources/golden-sessions");

    @BeforeAll
    static void setup() throws Exception {
        Files.createDirectories(GOLDEN_DIR);
    }

    @Test
    @DisplayName("Record incoming PUSH from C:X")
    @Disabled("Manual test - run with cx-test-push.sh")
    void recordIncomingPush() throws Exception {
        PesitSessionRecorder recorder = new PesitSessionRecorder("cx-push-to-server");

        log.info("Starting server on port {}, waiting for C:X connection...", SERVER_PORT);
        log.info("Run: ./scripts/cx-test-push.sh in another terminal");

        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            serverSocket.setSoTimeout(120000); // 2 minutes timeout

            try (Socket client = serverSocket.accept()) {
                log.info("Client connected from {}", client.getRemoteSocketAddress());
                recordSession(client, recorder, true); // true = we respond as server
            }
        }

        recorder.saveToFile(GOLDEN_DIR.resolve("cx-push-incoming.dat"));
        log.info("Recorded {} frames for incoming PUSH", recorder.getFrames().size());
    }

    @Test
    @DisplayName("Record incoming PULL from C:X")
    @Disabled("Manual test - run with cx-test-pull.sh")
    void recordIncomingPull() throws Exception {
        PesitSessionRecorder recorder = new PesitSessionRecorder("cx-pull-from-server");

        log.info("Starting server on port {}, waiting for C:X connection...", SERVER_PORT);
        log.info("Run: ./scripts/cx-test-pull.sh in another terminal");

        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            serverSocket.setSoTimeout(120000);

            try (Socket client = serverSocket.accept()) {
                log.info("Client connected from {}", client.getRemoteSocketAddress());
                recordSession(client, recorder, true);
            }
        }

        recorder.saveToFile(GOLDEN_DIR.resolve("cx-pull-incoming.dat"));
        log.info("Recorded {} frames for incoming PULL", recorder.getFrames().size());
    }

    /**
     * Record a PeSIT session, responding as a minimal server.
     */
    private void recordSession(Socket client, PesitSessionRecorder recorder, boolean respond)
            throws IOException {
        DataInputStream in = new DataInputStream(client.getInputStream());
        DataOutputStream out = new DataOutputStream(client.getOutputStream());
        client.setSoTimeout(30000);

        int serverConnId = 1;
        boolean sessionActive = true;

        while (sessionActive) {
            try {
                // Read incoming FPDU
                int len = in.readUnsignedShort();
                byte[] data = new byte[len];
                in.readFully(data);

                Fpdu received = new FpduParser(data).parse();
                recorder.recordRaw(Direction.RECEIVED, received.getFpduType(), data);
                log.info("Received: {} (id_src={}, id_dst={})",
                        received.getFpduType(), received.getIdSrc(), received.getIdDst());

                if (respond) {
                    Fpdu response = createResponse(received, serverConnId);
                    if (response != null) {
                        byte[] respData = FpduBuilder.buildFpdu(response);
                        out.writeShort(respData.length);
                        out.write(respData);
                        out.flush();
                        recorder.recordRaw(Direction.SENT, response.getFpduType(), respData);
                        log.info("Sent: {}", response.getFpduType());
                    }
                }

                // End session on RELCONF or ABORT
                if (received.getFpduType() == FpduType.RELEASE) {
                    sessionActive = false;
                }

            } catch (IOException e) {
                log.info("Session ended: {}", e.getMessage());
                sessionActive = false;
            }
        }
    }

    /**
     * Create minimal server responses for common FPDUs.
     */
    private Fpdu createResponse(Fpdu received, int serverConnId) {
        return switch (received.getFpduType()) {
            case CONNECT -> new Fpdu(FpduType.ACONNECT)
                    .withIdSrc(serverConnId)
                    .withIdDst(received.getIdSrc())
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, "D"));

            case CREATE -> new Fpdu(FpduType.ACK_CREATE)
                    .withIdSrc(serverConnId)
                    .withIdDst(received.getIdSrc())
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));

            case SELECT -> new Fpdu(FpduType.ACK_SELECT)
                    .withIdSrc(serverConnId)
                    .withIdDst(received.getIdSrc())
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));

            case OPEN -> new Fpdu(FpduType.ACK_OPEN)
                    .withIdSrc(serverConnId)
                    .withIdDst(received.getIdSrc())
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));

            case WRITE -> new Fpdu(FpduType.ACK_WRITE)
                    .withIdSrc(serverConnId)
                    .withIdDst(received.getIdSrc())
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));

            case READ -> new Fpdu(FpduType.ACK_READ)
                    .withIdSrc(serverConnId)
                    .withIdDst(received.getIdSrc())
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));

            case TRANS_END -> new Fpdu(FpduType.ACK_TRANS_END)
                    .withIdSrc(serverConnId)
                    .withIdDst(received.getIdSrc())
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));

            case CLOSE -> new Fpdu(FpduType.ACK_CLOSE)
                    .withIdSrc(serverConnId)
                    .withIdDst(received.getIdSrc())
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));

            case DESELECT -> new Fpdu(FpduType.ACK_DESELECT)
                    .withIdSrc(serverConnId)
                    .withIdDst(received.getIdSrc())
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));

            case RELEASE -> new Fpdu(FpduType.RELCONF)
                    .withIdSrc(serverConnId)
                    .withIdDst(received.getIdSrc());

            case DTF, DTF_END, SYN, ACK_SYN -> null; // No response needed

            default -> {
                log.warn("No response defined for {}", received.getFpduType());
                yield null;
            }
        };
    }
}
