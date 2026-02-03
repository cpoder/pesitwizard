package com.pesitwizard.server.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.config.LogicalFileConfig;
import com.pesitwizard.server.config.PartnerConfig;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.config.SslProperties;
import com.pesitwizard.server.entity.PesitServerConfig;
import com.pesitwizard.server.handler.PesitSessionHandler;
import com.pesitwizard.server.service.PesitServerInstance;
import com.pesitwizard.server.ssl.SslContextFactory;

/**
 * Integration test for complete PeSIT file transfer.
 * Starts an embedded PeSIT server and performs a full transfer cycle.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("PeSIT File Transfer Integration Tests")
class PesitFileTransferIntegrationTest {

    private static final int TEST_PORT = 15000;
    private static final String TEST_SERVER_ID = "TEST_SERVER";
    private static final String TEST_PARTNER_ID = "TEST_PARTNER";

    @Autowired
    private PesitSessionHandler sessionHandler;

    @TempDir
    static Path tempDir;

    @Mock
    private SslProperties sslProperties;

    @Mock
    private SslContextFactory sslContextFactory;

    private PesitServerInstance serverInstance;
    private PesitServerProperties testProperties;

    @BeforeAll
    void startServer() throws IOException {
        // Configure test server properties
        testProperties = new PesitServerProperties();
        testProperties.setPort(TEST_PORT);
        testProperties.setServerId(TEST_SERVER_ID);
        testProperties.setMaxConnections(10);
        testProperties.setReceiveDirectory(tempDir.toString());

        // Add a test partner
        PartnerConfig partner = new PartnerConfig();
        partner.setId(TEST_PARTNER_ID);
        partner.setEnabled(true);
        partner.setAccessType(PartnerConfig.AccessType.BOTH);
        testProperties.getPartners().put(TEST_PARTNER_ID, partner);

        // Add a test logical file
        LogicalFileConfig fileConfig = LogicalFileConfig.builder()
                .id("FILE")
                .enabled(true)
                .direction(LogicalFileConfig.Direction.BOTH)
                .receiveDirectory(tempDir.toString())
                .receiveFilenamePattern("${filename}_${transferId}")
                .build();
        testProperties.getFiles().put("FILE", fileConfig);

        // Create server config
        PesitServerConfig serverConfig = new PesitServerConfig();
        serverConfig.setServerId(TEST_SERVER_ID);
        serverConfig.setPort(TEST_PORT);
        serverConfig.setAutoStart(true);

        // Start the server
        serverInstance = new PesitServerInstance(serverConfig, testProperties, sessionHandler, sslProperties,
                sslContextFactory);
        serverInstance.start();

        // Wait for server to be ready
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterAll
    void stopServer() {
        if (serverInstance != null) {
            serverInstance.stop();
        }
    }

    @Test
    @Disabled("Requires full server initialization - run manually with proper setup")
    @DisplayName("Should complete full file transfer: CONNECT → CREATE → OPEN → WRITE → DTF → close sequence")
    void shouldCompleteFullFileTransfer() throws IOException {
        try (Socket socket = new Socket("localhost", TEST_PORT)) {
            socket.setSoTimeout(5000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            int clientConnectionId = 0x05;

            // Step 1: CONNECT
            ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                    .demandeur(TEST_PARTNER_ID)
                    .serveur(TEST_SERVER_ID)
                    .writeAccess();

            byte[] connectBytes = FpduBuilder.buildFpdu(connectBuilder.build(clientConnectionId));
            out.write(connectBytes);
            out.flush();

            Fpdu aconnect = readFpdu(in);
            assertEquals(FpduType.ACONNECT, aconnect.getFpduType(), "Expected ACONNECT");
            int serverConnectionId = aconnect.getIdSrc();

            // Step 2: CREATE
            CreateMessageBuilder createBuilder = new CreateMessageBuilder()
                    .filename("FILE")
                    .transferId(1)
                    .variableFormat()
                    .recordLength(30)
                    .maxEntitySize(56);

            out.write(FpduBuilder.buildFpdu(createBuilder.build(serverConnectionId)));
            out.flush();

            Fpdu ackCreate = readFpdu(in);
            assertEquals(FpduType.ACK_CREATE, ackCreate.getFpduType(), "Expected ACK(CREATE)");

            // Step 3: OPEN
            out.write(FpduBuilder.buildFpdu(new Fpdu(FpduType.OPEN).withIdDst(serverConnectionId)));
            out.flush();

            Fpdu ackOpen = readFpdu(in);
            assertEquals(FpduType.ACK_OPEN, ackOpen.getFpduType(), "Expected ACK(OPEN)");

            // Step 4: WRITE
            out.write(FpduBuilder.buildFpdu(new Fpdu(FpduType.WRITE).withIdDst(serverConnectionId)));
            out.flush();

            Fpdu ackWrite = readFpdu(in);
            assertEquals(FpduType.ACK_WRITE, ackWrite.getFpduType(), "Expected ACK(WRITE)");

            // Step 5: Send DTF data
            String testData = "Hello from integration test!";
            byte[] dtfBytes = FpduBuilder.buildFpdu(FpduType.DTF, serverConnectionId, 0, testData.getBytes());
            out.write(dtfBytes);
            out.flush();

            // Step 6: DTF.END
            out.write(FpduBuilder.buildFpdu(new Fpdu(FpduType.DTF_END).withIdDst(serverConnectionId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG,
                            new byte[] { 0x00, 0x00, 0x00 }))));
            out.flush();

            // Step 7: TRANS.END
            out.write(FpduBuilder.buildFpdu(new Fpdu(FpduType.TRANS_END).withIdDst(serverConnectionId)));
            out.flush();

            Fpdu ackTransEnd = readFpdu(in);
            assertEquals(FpduType.ACK_TRANS_END, ackTransEnd.getFpduType(), "Expected ACK(TRANS.END)");

            // Step 8: CLOSE
            out.write(FpduBuilder.buildFpdu(new Fpdu(FpduType.CLOSE).withIdDst(serverConnectionId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG,
                            new byte[] { 0x00, 0x00, 0x00 }))));
            out.flush();

            Fpdu ackClose = readFpdu(in);
            assertEquals(FpduType.ACK_CLOSE, ackClose.getFpduType(), "Expected ACK(CLOSE)");

            // Step 9: DESELECT
            out.write(FpduBuilder.buildFpdu(new Fpdu(FpduType.DESELECT).withIdDst(serverConnectionId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG,
                            new byte[] { 0x00, 0x00, 0x00 }))));
            out.flush();

            Fpdu ackDeselect = readFpdu(in);
            assertEquals(FpduType.ACK_DESELECT, ackDeselect.getFpduType(), "Expected ACK(DESELECT)");

            // Step 10: RELEASE
            out.write(FpduBuilder.buildFpdu(new Fpdu(FpduType.RELEASE).withIdDst(serverConnectionId)
                    .withIdSrc(clientConnectionId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG,
                            new byte[] { 0x00, 0x00, 0x00 }))));
            out.flush();

            Fpdu relconf = readFpdu(in);
            assertEquals(FpduType.RELCONF, relconf.getFpduType(), "Expected RELCONF");
        }
    }

    @Test
    @Disabled("Requires full server initialization - run manually with proper setup")
    @DisplayName("Should reject connection with unknown server ID")
    void shouldRejectUnknownServer() throws IOException {
        try (Socket socket = new Socket("localhost", TEST_PORT)) {
            socket.setSoTimeout(5000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                    .demandeur(TEST_PARTNER_ID)
                    .serveur("UNKNOWN_SERVER")
                    .writeAccess();

            out.write(FpduBuilder.buildFpdu(connectBuilder.build(0x05)));
            out.flush();

            Fpdu response = readFpdu(in);
            // Should get RCONNECT (reject) for unknown server
            assertEquals(FpduType.RCONNECT, response.getFpduType(), "Expected RCONNECT for unknown server");
        }
    }

    private Fpdu readFpdu(InputStream in) throws IOException {
        byte[] header = new byte[4];
        int read = in.read(header);
        if (read != 4) {
            throw new IOException("Failed to read FPDU header");
        }

        int length = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        byte[] fpduBytes = new byte[length];
        System.arraycopy(header, 0, fpduBytes, 0, 4);

        int remaining = length - 4;
        int offset = 4;
        while (remaining > 0) {
            read = in.read(fpduBytes, offset, remaining);
            if (read < 0) {
                throw new IOException("Unexpected end of stream");
            }
            offset += read;
            remaining -= read;
        }

        return new FpduParser(fpduBytes).parse();
    }
}
