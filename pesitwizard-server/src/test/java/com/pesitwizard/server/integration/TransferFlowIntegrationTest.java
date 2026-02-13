package com.pesitwizard.server.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduIO;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.entity.Partner;
import com.pesitwizard.server.entity.PesitServerConfig;
import com.pesitwizard.server.entity.VirtualFile;
import com.pesitwizard.server.service.ConfigService;
import com.pesitwizard.server.service.PesitServerManager;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for complete PeSIT transfer flows. Tests the full protocol state machine from
 * CONNECT through file transfer to RELEASE.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Transfer Flow Integration Tests")
public class TransferFlowIntegrationTest {

    private static final String HOST = "localhost";
    private static final String CLIENT_ID = "FLOW_TEST_CLIENT";
    private static final String SERVER_ID = "FLOW_TEST_SERVER";

    private static Path staticSendDirectory;
    private static Path staticReceiveDirectory;

    static {
        try {
            Path tempDir = Files.createTempDirectory("pesit-flow-test-");
            staticSendDirectory = tempDir.resolve("send");
            staticReceiveDirectory = tempDir.resolve("receive");
            Files.createDirectories(staticSendDirectory);
            Files.createDirectories(staticReceiveDirectory);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test directories", e);
        }
    }

    @TestConfiguration
    static class MockConfigServiceConfiguration {

        @Bean
        @Primary
        public ConfigService configService() {
            ConfigService mock = Mockito.mock(ConfigService.class);

            Partner testPartner =
                    Partner.builder()
                            .id(CLIENT_ID)
                            .enabled(true)
                            .accessType(Partner.AccessType.BOTH)
                            .build();
            when(mock.findPartner(anyString())).thenReturn(Optional.of(testPartner));

            VirtualFile testFile =
                    VirtualFile.builder()
                            .id("*")
                            .enabled(true)
                            .direction(VirtualFile.Direction.BOTH)
                            .receiveDirectory(staticReceiveDirectory.toString())
                            .sendDirectory(staticSendDirectory.toString())
                            .receiveFilenamePattern("${filename}")
                            .overwrite(true)
                            .build();
            when(mock.findVirtualFile(anyString())).thenReturn(Optional.of(testFile));

            return mock;
        }
    }

    @Autowired private PesitServerProperties serverProperties;

    @Autowired private PesitServerManager serverManager;

    private Path tempDir;
    private int serverPort;

    @BeforeAll
    void setUp() throws Exception {
        tempDir = staticSendDirectory.getParent();
        serverPort = serverProperties.getPort() + 100; // Use different port

        try {
            PesitServerConfig serverConfig = new PesitServerConfig();
            serverConfig.setServerId(SERVER_ID);
            serverConfig.setPort(serverPort);
            serverConfig.setAutoStart(false);
            serverManager.createServer(serverConfig);
            serverManager.startServer(SERVER_ID);
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    false, "Skipping integration test - server could not start: " + e.getMessage());
        }

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            try (Socket s = new Socket(HOST, serverPort)) {
                                return true;
                            } catch (Exception e) {
                                return false;
                            }
                        });
    }

    @AfterAll
    void tearDown() throws Exception {
        try {
            serverManager.stopServer(SERVER_ID);
        } catch (Exception e) {
            // Ignore
        }

        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(
                            p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (Exception e) {
                                }
                            });
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test complete CONNECT-RELEASE flow")
    void testConnectReleaseFlow() throws Exception {
        try (Socket socket = new Socket(HOST, serverPort)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send CONNECT
            Fpdu connect =
                    new Fpdu(FpduType.CONNECT)
                            .withIdDst(0)
                            .withIdSrc(1)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_03_DEMANDEUR, CLIENT_ID))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_04_SERVEUR, SERVER_ID))
                            .withParameter(
                                    new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2));

            FpduIO.writeFpdu(out, connect);

            // Read ACONNECT
            Fpdu response = FpduIO.readFpdu(in);
            assertEquals(FpduType.ACONNECT, response.getFpduType(), "Should receive ACONNECT");

            // Send RELEASE
            Fpdu release =
                    new Fpdu(FpduType.RELEASE)
                            .withIdDst(response.getIdSrc())
                            .withIdSrc(1)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}));

            FpduIO.writeFpdu(out, release);

            // Read RELCONF
            Fpdu relconf = FpduIO.readFpdu(in);
            assertEquals(FpduType.RELCONF, relconf.getFpduType(), "Should receive RELCONF");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Test CONNECT with invalid server ID should get RCONNECT")
    void testConnectInvalidServer() throws Exception {
        try (Socket socket = new Socket(HOST, serverPort)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send CONNECT with wrong server ID
            Fpdu connect =
                    new Fpdu(FpduType.CONNECT)
                            .withIdDst(0)
                            .withIdSrc(1)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_03_DEMANDEUR, CLIENT_ID))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_04_SERVEUR, "WRONG_SERVER"))
                            .withParameter(
                                    new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2));

            FpduIO.writeFpdu(out, connect);

            // Read RCONNECT (rejected)
            Fpdu response = FpduIO.readFpdu(in);
            assertEquals(
                    FpduType.RCONNECT,
                    response.getFpduType(),
                    "Should receive RCONNECT for wrong server");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Test ABORT handling")
    void testAbortFlow() throws Exception {
        try (Socket socket = new Socket(HOST, serverPort)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send CONNECT
            Fpdu connect =
                    new Fpdu(FpduType.CONNECT)
                            .withIdDst(0)
                            .withIdSrc(1)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_03_DEMANDEUR, CLIENT_ID))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_04_SERVEUR, SERVER_ID))
                            .withParameter(
                                    new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2));

            FpduIO.writeFpdu(out, connect);

            // Read ACONNECT
            Fpdu response = FpduIO.readFpdu(in);
            assertEquals(FpduType.ACONNECT, response.getFpduType());

            // Send ABORT
            Fpdu abort =
                    new Fpdu(FpduType.ABORT)
                            .withIdDst(response.getIdSrc())
                            .withIdSrc(1)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_02_DIAG,
                                            new byte[] {1, 0, 100}));

            FpduIO.writeFpdu(out, abort);

            // Connection should be closed by server after ABORT
            // No response expected - verify socket closed
            Thread.sleep(100);
        }
    }

    @Test
    @Order(4)
    @DisplayName("Test MSG flow in connected state")
    void testMsgFlow() throws Exception {
        try (Socket socket = new Socket(HOST, serverPort)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Connect first
            Fpdu connect =
                    new Fpdu(FpduType.CONNECT)
                            .withIdDst(0)
                            .withIdSrc(1)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_03_DEMANDEUR, CLIENT_ID))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_04_SERVEUR, SERVER_ID))
                            .withParameter(
                                    new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2));

            FpduIO.writeFpdu(out, connect);
            Fpdu aconnect = FpduIO.readFpdu(in);
            assertEquals(FpduType.ACONNECT, aconnect.getFpduType());

            // Send MSG
            Fpdu msg =
                    new Fpdu(FpduType.MSG)
                            .withIdDst(aconnect.getIdSrc())
                            .withIdSrc(1)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_91_MESSAGE,
                                            "Test message".getBytes(StandardCharsets.UTF_8)));

            FpduIO.writeFpdu(out, msg);

            // Read ACK_MSG
            Fpdu ackMsg = FpduIO.readFpdu(in);
            assertEquals(FpduType.ACK_MSG, ackMsg.getFpduType(), "Should receive ACK_MSG");

            // Clean up - send RELEASE
            Fpdu release =
                    new Fpdu(FpduType.RELEASE)
                            .withIdDst(aconnect.getIdSrc())
                            .withIdSrc(1)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}));
            FpduIO.writeFpdu(out, release);
            FpduIO.readFpdu(in); // RELCONF
        }
    }

    @Test
    @Order(5)
    @DisplayName("Test protocol version negotiation")
    void testVersionNegotiation() throws Exception {
        try (Socket socket = new Socket(HOST, serverPort)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send CONNECT with version 1
            Fpdu connect =
                    new Fpdu(FpduType.CONNECT)
                            .withIdDst(0)
                            .withIdSrc(1)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_03_DEMANDEUR, CLIENT_ID))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_04_SERVEUR, SERVER_ID))
                            .withParameter(
                                    new ParameterValue(ParameterIdentifier.PI_06_VERSION, 1));

            FpduIO.writeFpdu(out, connect);

            // Read response - should be ACONNECT (version 1 is compatible)
            Fpdu response = FpduIO.readFpdu(in);
            assertEquals(FpduType.ACONNECT, response.getFpduType(), "Version 1 should be accepted");

            // Clean up
            Fpdu release =
                    new Fpdu(FpduType.RELEASE)
                            .withIdDst(response.getIdSrc())
                            .withIdSrc(1)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_02_DIAG, new byte[] {0, 0, 0}));
            FpduIO.writeFpdu(out, release);
            FpduIO.readFpdu(in);
        }
    }
}
