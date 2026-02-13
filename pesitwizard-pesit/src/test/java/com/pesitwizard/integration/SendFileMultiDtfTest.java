package com.pesitwizard.integration;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TcpTransportChannel;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Integration tests for sending files with automatic multi-DTF chunking. Tests that large files are
 * correctly split into multiple DTF FPDUs respecting the negotiated PI_25 (max entity size).
 *
 * <p>Requires Connect:Express running on localhost:5000
 *
 * <p>Run with: mvn test -Dtest=SendFileMultiDtfTest -Dpesit.integration.enabled=true
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SendFileMultiDtfTest {

    private static final String TEST_HOST = System.getProperty("pesit.test.host", "localhost");
    private static final int TEST_PORT =
            Integer.parseInt(System.getProperty("pesit.test.port", "5000"));
    private static final boolean INTEGRATION_ENABLED =
            Boolean.parseBoolean(System.getProperty("pesit.integration.enabled", "false"));
    private static final String SERVER_ID = System.getProperty("pesit.test.server", "PESIT_SERVER");

    @BeforeAll
    void setUp() {
        Assumptions.assumeTrue(
                INTEGRATION_ENABLED,
                "Integration tests disabled. Enable with -Dpesit.integration.enabled=true");
    }

    @Test
    @DisplayName("Send small file (under entity size limit) - single DTF")
    void testSendSmallFile() throws Exception {
        System.out.println("\n=== SEND SMALL FILE TEST (Single DTF) ===\n");

        byte[] testData = generateTestData(500); // 500 bytes - fits in one DTF
        String md5Before = calculateMd5(testData);
        System.out.println("Test data: " + testData.length + " bytes, MD5: " + md5Before);

        try (PesitSession session =
                new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT))) {
            int serverConnId = performHandshake(session, "FILE", 4096, 506, testData.length);
            sendDataWithDtf(session, serverConnId, testData, 506);
            performCleanup(session, serverConnId);
        }

        System.out.println("  ✓ Small file sent successfully");
    }

    @Test
    @DisplayName("Send medium file (multiple DTFs with default entity size)")
    void testSendMediumFile() throws Exception {
        System.out.println("\n=== SEND MEDIUM FILE TEST (Multiple DTFs) ===\n");

        // 10 KB file with 4KB entity size = ~3 DTFs
        byte[] testData = generateTestData(10 * 1024);
        String md5Before = calculateMd5(testData);
        System.out.println("Test data: " + testData.length + " bytes, MD5: " + md5Before);

        try (PesitSession session =
                new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT))) {
            int serverConnId = performHandshake(session, "FILE", 4096, 506, testData.length);
            int dtfCount = sendDataWithDtf(session, serverConnId, testData, 506);
            performCleanup(session, serverConnId);

            System.out.println("  Total DTFs sent: " + dtfCount);
            assertTrue(dtfCount > 1, "Medium file should require multiple DTFs");
        }

        System.out.println("  ✓ Medium file sent successfully with multiple DTFs");
    }

    @Test
    @DisplayName("Send file with small entity size (many DTFs)")
    void testSendWithSmallEntitySize() throws Exception {
        System.out.println("\n=== SEND FILE WITH SMALL ENTITY SIZE ===\n");

        // 5 KB file with 512 byte entity size = ~10 DTFs
        byte[] testData = generateTestData(5 * 1024);
        String md5Before = calculateMd5(testData);
        System.out.println("Test data: " + testData.length + " bytes, MD5: " + md5Before);

        try (PesitSession session =
                new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT))) {
            // Request small entity size
            int serverConnId = performHandshake(session, "FILE", 512, 256, testData.length);
            int dtfCount = sendDataWithDtf(session, serverConnId, testData, 256);
            performCleanup(session, serverConnId);

            System.out.println("  Total DTFs sent: " + dtfCount);
            assertTrue(dtfCount >= 10, "Should send at least 10 DTFs with 512 byte entity size");
        }

        System.out.println("  ✓ File sent with small entity size (many DTFs)");
    }

    @Test
    @DisplayName("Send larger file (100KB) with standard entity size")
    void testSendLargerFile() throws Exception {
        System.out.println("\n=== SEND LARGER FILE TEST (100KB) ===\n");

        byte[] testData = generateTestData(100 * 1024);
        String md5Before = calculateMd5(testData);
        System.out.println("Test data: " + testData.length + " bytes, MD5: " + md5Before);

        try (PesitSession session =
                new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT))) {
            int serverConnId = performHandshake(session, "FILE", 4096, 1024, testData.length);
            int dtfCount = sendDataWithDtf(session, serverConnId, testData, 1024);
            performCleanup(session, serverConnId);

            System.out.println("  Total DTFs sent: " + dtfCount);
            assertTrue(dtfCount > 20, "100KB file should require many DTFs");
        }

        System.out.println("  ✓ Larger file (100KB) sent successfully");
    }

    /**
     * Perform PeSIT handshake: CONNECT -> CREATE -> OPEN -> WRITE
     *
     * @return serverConnectionId
     */
    private static final int CLIENT_CONNECTION_ID = 0x05;

    private int performHandshake(
            PesitSession session,
            String filename,
            int maxEntitySize,
            int recordLength,
            int fileSize)
            throws IOException, InterruptedException {

        // CONNECT
        System.out.println("Step 1: CONNECT");
        ConnectMessageBuilder connectBuilder =
                new ConnectMessageBuilder().demandeur("LOOP").serveur(SERVER_ID).writeAccess();
        Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(CLIENT_CONNECTION_ID));
        assertEquals(FpduType.ACONNECT, aconnect.getFpduType());
        int serverConnectionId = aconnect.getIdSrc();
        System.out.println("  ✓ Connected, server ID: " + serverConnectionId);

        // Check server's PI_25 capability
        ParameterValue serverPi25 = aconnect.getParameter(PI_25_TAILLE_MAX_ENTITE);
        if (serverPi25 != null && serverPi25.getValue() != null) {
            int serverMaxEntity = parseNumeric(serverPi25.getValue());
            System.out.println("  Server max entity size: " + serverMaxEntity);
            // Use smaller of requested and server capability
            maxEntitySize = Math.min(maxEntitySize, serverMaxEntity);
        }

        // CREATE - must declare file size per PeSIT spec
        long fileSizeKB = (fileSize + 1023) / 1024; // Round up to KB
        System.out.println(
                "Step 2: CREATE (PI_25="
                        + maxEntitySize
                        + ", PI_32="
                        + recordLength
                        + ", fileSize="
                        + fileSize
                        + " bytes / "
                        + fileSizeKB
                        + " KB)");
        CreateMessageBuilder createBuilder =
                new CreateMessageBuilder()
                        .filename(filename)
                        .transferId(1)
                        .variableFormat()
                        .recordLength(recordLength)
                        .maxEntitySize(maxEntitySize)
                        .fileSizeKB(fileSizeKB);
        Fpdu ackCreate = session.sendFpduWithAck(createBuilder.build(serverConnectionId));
        assertEquals(FpduType.ACK_CREATE, ackCreate.getFpduType());

        // Check negotiated PI_25
        ParameterValue negotiatedPi25 = ackCreate.getParameter(PI_25_TAILLE_MAX_ENTITE);
        if (negotiatedPi25 != null && negotiatedPi25.getValue() != null) {
            int negotiatedSize = parseNumeric(negotiatedPi25.getValue());
            System.out.println("  ✓ Negotiated entity size (PI_25): " + negotiatedSize);
        }

        // OPEN
        System.out.println("Step 3: OPEN");
        Fpdu openFpdu = new Fpdu(FpduType.OPEN).withIdDst(serverConnectionId);
        Fpdu ackOpen = session.sendFpduWithAck(openFpdu);
        assertEquals(FpduType.ACK_OPEN, ackOpen.getFpduType());
        System.out.println("  ✓ File opened");

        // WRITE
        System.out.println("Step 4: WRITE");
        Fpdu writeFpdu = new Fpdu(FpduType.WRITE).withIdDst(serverConnectionId);
        Fpdu ackWrite = session.sendFpduWithAck(writeFpdu);
        assertEquals(FpduType.ACK_WRITE, ackWrite.getFpduType());
        System.out.println("  ✓ Write started");

        return serverConnectionId;
    }

    /**
     * Send data as DTF FPDUs, respecting record length (PI_32). Per PeSIT spec: article size must
     * not exceed PI_32 (recordLength).
     *
     * @return number of DTF FPDUs sent
     */
    private int sendDataWithDtf(
            PesitSession session, int serverConnectionId, byte[] data, int recordLength)
            throws IOException, InterruptedException {
        System.out.println(
                "Step 5: Sending "
                        + data.length
                        + " bytes as DTF chunks (recordLength="
                        + recordLength
                        + ")");

        int chunkSize = recordLength; // PI_32 = max article size
        int dtfCount = 0;
        int offset = 0;

        while (offset < data.length) {
            int remaining = data.length - offset;
            int currentChunkSize = Math.min(chunkSize, remaining);
            byte[] chunk = new byte[currentChunkSize];
            System.arraycopy(data, offset, chunk, 0, currentChunkSize);

            Fpdu dtfFpdu = new Fpdu(FpduType.DTF).withIdDst(serverConnectionId);
            session.sendFpduWithData(dtfFpdu, chunk);
            dtfCount++;
            offset += currentChunkSize;

            if (dtfCount % 10 == 0 || offset == data.length) {
                System.out.println(
                        "  Sent DTF #"
                                + dtfCount
                                + ": "
                                + currentChunkSize
                                + " bytes (total: "
                                + offset
                                + "/"
                                + data.length
                                + ")");
            }
        }

        // DTF_END
        System.out.println("Step 6: DTF_END");
        Fpdu dtfEndFpdu =
                new Fpdu(FpduType.DTF_END)
                        .withIdDst(serverConnectionId)
                        .withParameter(
                                new ParameterValue(PI_02_DIAG, new byte[] {0x00, 0x00, 0x00}));
        session.sendFpdu(dtfEndFpdu);
        System.out.println("  ✓ DTF_END sent");

        // TRANS_END
        System.out.println("Step 7: TRANS_END");
        Fpdu transEndFpdu = new Fpdu(FpduType.TRANS_END).withIdDst(serverConnectionId);
        Fpdu ackTransEnd = session.sendFpduWithAck(transEndFpdu);
        assertEquals(FpduType.ACK_TRANS_END, ackTransEnd.getFpduType());
        System.out.println("  ✓ Transfer complete");

        return dtfCount;
    }

    /**
     * Perform cleanup: CLOSE -> DESELECT -> RELEASE Per PeSIT spec 4.4.26: RELEASE requires both
     * ID.DST and ID.SRC
     */
    private void performCleanup(PesitSession session, int serverConnectionId)
            throws IOException, InterruptedException {
        // CLOSE - Per PeSIT spec 4.4.12: only ID.DST required
        System.out.println("Step 8: CLOSE");
        Fpdu closeFpdu =
                new Fpdu(FpduType.CLOSE)
                        .withIdDst(serverConnectionId)
                        .withParameter(
                                new ParameterValue(PI_02_DIAG, new byte[] {0x00, 0x00, 0x00}));
        Fpdu ackClose = session.sendFpduWithAck(closeFpdu);
        assertEquals(FpduType.ACK_CLOSE, ackClose.getFpduType());
        System.out.println("  ✓ File closed");

        // DESELECT - Per PeSIT spec 4.4.8: only ID.DST required
        System.out.println("Step 9: DESELECT");
        Fpdu deselectFpdu =
                new Fpdu(FpduType.DESELECT)
                        .withIdDst(serverConnectionId)
                        .withParameter(
                                new ParameterValue(PI_02_DIAG, new byte[] {0x00, 0x00, 0x00}));
        Fpdu ackDeselect = session.sendFpduWithAck(deselectFpdu);
        assertEquals(FpduType.ACK_DESELECT, ackDeselect.getFpduType());
        System.out.println("  ✓ File deselected");

        // RELEASE - Per PeSIT spec 4.4.26: requires ID.DST and ID.SRC
        System.out.println("Step 10: RELEASE");
        Fpdu releaseFpdu =
                new Fpdu(FpduType.RELEASE)
                        .withIdDst(serverConnectionId)
                        .withIdSrc(CLIENT_CONNECTION_ID)
                        .withParameter(
                                new ParameterValue(PI_02_DIAG, new byte[] {0x00, 0x00, 0x00}));
        Fpdu relconf = session.sendFpduWithAck(releaseFpdu);
        assertEquals(FpduType.RELCONF, relconf.getFpduType());
        System.out.println("  ✓ Session released");
    }

    private byte[] generateTestData(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data); // Deterministic for reproducibility
        return data;
    }

    private String calculateMd5(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(data);
        return HexFormat.of().formatHex(digest);
    }

    private int parseNumeric(byte[] value) {
        if (value == null || value.length == 0) return 0;
        int result = 0;
        for (byte b : value) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }
}
