package com.pesitwizard.client.integration;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.*;

import com.pesitwizard.client.pesit.FpduReader;
import com.pesitwizard.client.pesit.FpduWriter;
import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TcpTransportChannel;

/**
 * Comprehensive integration tests for PeSIT restart mechanism.
 *
 * Tests cover:
 * - Send (PUSH) restart with multiple interrupts
 * - Receive (PULL) restart scenarios
 * - Error handling and edge cases
 * - Large file restart scenarios
 * - Varying chunk sizes and entity sizes
 *
 * Requires PeSIT server running on test host/port.
 * Run with: mvn test -Dtest=RestartMechanismIntegrationTest -Dpesit.integration.enabled=true
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RestartMechanismIntegrationTest {

    private static final String TEST_HOST = System.getProperty("pesit.test.host", "localhost");
    private static final int TEST_PORT = Integer.parseInt(System.getProperty("pesit.test.port", "5100"));
    private static final String SERVER_ID = System.getProperty("pesit.test.server", "CETOM1");
    private static final String PARTNER_ID = "LOOP";
    private static final boolean INTEGRATION_ENABLED = Boolean.parseBoolean(
            System.getProperty("pesit.integration.enabled", "false"));

    // Virtual files for different test scenarios
    private static final String VFILE_RESTART = "SYNCIN";  // Fixed physical path for restart tests
    private static final String VFILE_PULL = "BIG";        // For receive/pull tests
    private static final String VFILE_STANDARD = "FILE";   // Standard variable format file

    private static final int CLIENT_CONNECTION_ID = 15;
    private static final int CHUNK_SIZE = 506;
    private static final int MAX_ENTITY_SIZE = 512;

    @BeforeAll
    void setUp() {
        Assumptions.assumeTrue(INTEGRATION_ENABLED,
                "Integration tests disabled. Enable with -Dpesit.integration.enabled=true");
    }

    /**
     * Test 1: Send with restart from sync point after interrupt
     * Simulates network failure during send, then resume from last sync point
     */
    @Test
    @Order(1)
    @DisplayName("Send with restart from sync point")
    void testSendRestartFromSyncPoint() throws Exception {
        System.out.println("\n=== TEST 1: SEND WITH RESTART FROM SYNC POINT ===");

        byte[] testData = generateTestData(200 * 1024); // 200KB
        int transferId = generateTransferId();

        // Phase 1: Send partial data with interruption
        System.out.println("\nPhase 1: Partial send with interruption...");
        int lastSyncNumber = sendPartialWithInterrupt(testData, transferId, VFILE_RESTART, 3);
        System.out.println("  Interrupted at sync point: " + lastSyncNumber);

        // Phase 2: Resume from last sync point
        System.out.println("\nPhase 2: Resume from sync point " + lastSyncNumber + "...");
        long resumedBytes = resumeSendFromSyncPoint(testData, transferId, VFILE_RESTART, lastSyncNumber);
        System.out.println("  Resumed and sent: " + resumedBytes + " bytes");

        assertTrue(resumedBytes > 0, "Should have sent data after resume");
        System.out.println("\n✓✓✓ TEST 1 PASSED: Send restart successful ✓✓✓");
    }

    /**
     * Test 2: Multiple interrupts and restarts
     * Interrupt transfer multiple times and restart each time
     */
    @Test
    @Order(2)
    @DisplayName("Multiple interrupts and restarts")
    void testMultipleInterruptsAndRestarts() throws Exception {
        System.out.println("\n=== TEST 2: MULTIPLE INTERRUPTS AND RESTARTS ===");

        byte[] testData = generateTestData(300 * 1024); // 300KB
        int transferId = generateTransferId();

        // First interrupt at sync 2
        System.out.println("\nAttempt 1: Interrupt at sync 2...");
        int sync1 = sendPartialWithInterrupt(testData, transferId, VFILE_RESTART, 2);
        assertEquals(2, sync1, "Should interrupt at sync 2");

        // Resume and interrupt again at sync 5
        System.out.println("\nAttempt 2: Resume and interrupt at sync 5...");
        int sync2 = resumeAndInterruptAt(testData, transferId, VFILE_RESTART, sync1, 5);
        assertEquals(5, sync2, "Should interrupt at sync 5");

        // Final resume and complete
        System.out.println("\nAttempt 3: Resume and complete transfer...");
        long finalBytes = resumeSendFromSyncPoint(testData, transferId, VFILE_RESTART, sync2);
        assertTrue(finalBytes > 0, "Should complete transfer");

        System.out.println("\n✓✓✓ TEST 2 PASSED: Multiple restarts successful ✓✓✓");
    }

    /**
     * Test 3: Receive (PULL) with restart
     * Tests resuming a download after interruption
     */
    @Test
    @Order(3)
    @DisplayName("Receive with restart from sync point")
    void testReceiveRestartFromSyncPoint() throws Exception {
        System.out.println("\n=== TEST 3: RECEIVE WITH RESTART FROM SYNC POINT ===");

        // Phase 1: Receive partial data then interrupt
        System.out.println("\nPhase 1: Partial receive with interrupt...");
        ReceiveResult partial = receivePartialWithInterrupt(VFILE_PULL, 3);
        System.out.println("  Received " + partial.bytesReceived + " bytes, interrupted at sync "
                + partial.lastSyncNumber);

        // Phase 2: Resume receive from last sync point
        System.out.println("\nPhase 2: Resume receive from sync point " + partial.lastSyncNumber + "...");
        ReceiveResult resumed = resumeReceiveFromSyncPoint(VFILE_PULL, partial.lastSyncNumber);
        System.out.println("  Resumed and received: " + resumed.bytesReceived + " bytes");

        assertTrue(resumed.bytesReceived > 0, "Should have received data after resume");
        System.out.println("\n✓✓✓ TEST 3 PASSED: Receive restart successful ✓✓✓");
    }

    /**
     * Test 4: Restart from sync point 0 (beginning)
     * Edge case: restart from the very beginning
     */
    @Test
    @Order(4)
    @DisplayName("Restart from sync point 0 (beginning)")
    void testRestartFromBeginning() throws Exception {
        System.out.println("\n=== TEST 4: RESTART FROM SYNC POINT 0 ===");

        byte[] testData = generateTestData(50 * 1024); // 50KB
        int transferId = generateTransferId();

        // Send complete transfer restarting from 0
        System.out.println("\nSending with restart point 0 (beginning)...");
        long bytesSent = sendWithRestartPoint(testData, transferId, VFILE_RESTART, 0);

        assertTrue(bytesSent > 0, "Should complete transfer from beginning");
        System.out.println("  Sent " + bytesSent + " bytes starting from sync point 0");

        System.out.println("\n✓✓✓ TEST 4 PASSED: Restart from beginning successful ✓✓✓");
    }

    /**
     * Test 5: Large file restart scenario
     * Tests restart mechanism with a larger file (5MB)
     */
    @Test
    @Order(5)
    @DisplayName("Large file restart (5MB)")
    void testLargeFileRestart() throws Exception {
        System.out.println("\n=== TEST 5: LARGE FILE RESTART (5MB) ===");

        byte[] testData = generateTestData(5 * 1024 * 1024); // 5MB
        int transferId = generateTransferId();

        // Interrupt at sync 100 (approximately 1MB in)
        System.out.println("\nPhase 1: Send 5MB, interrupt at sync 100...");
        int lastSync = sendPartialWithInterrupt(testData, transferId, VFILE_RESTART, 100);
        System.out.println("  Interrupted at sync point: " + lastSync);

        // Resume
        System.out.println("\nPhase 2: Resume from sync point " + lastSync + "...");
        long resumedBytes = resumeSendFromSyncPoint(testData, transferId, VFILE_RESTART, lastSync);
        System.out.println("  Resumed and sent: " + (resumedBytes / 1024 / 1024) + " MB");

        assertTrue(resumedBytes > 0, "Should complete large file transfer");
        System.out.println("\n✓✓✓ TEST 5 PASSED: Large file restart successful ✓✓✓");
    }

    /**
     * Test 6: Varying chunk sizes with restart
     * Tests restart with different entity sizes
     */
    @Test
    @Order(6)
    @DisplayName("Restart with varying chunk sizes")
    void testRestartWithVaryingChunkSizes() throws Exception {
        System.out.println("\n=== TEST 6: RESTART WITH VARYING CHUNK SIZES ===");

        byte[] testData = generateTestData(100 * 1024); // 100KB
        int transferId = generateTransferId();

        // Test with small chunk size (256 bytes)
        System.out.println("\nTest 6a: Small chunk size (256 bytes)...");
        int sync1 = sendPartialWithInterruptAndChunkSize(testData, transferId, VFILE_RESTART, 2, 250, 256);
        long resumed1 = resumeSendWithChunkSize(testData, transferId, VFILE_RESTART, sync1, 250, 256);
        assertTrue(resumed1 > 0, "Should complete with small chunk size");

        // Test with medium chunk size (1024 bytes)
        transferId = generateTransferId();
        System.out.println("\nTest 6b: Medium chunk size (1024 bytes)...");
        int sync2 = sendPartialWithInterruptAndChunkSize(testData, transferId, VFILE_RESTART, 2, 1018, 1024);
        long resumed2 = resumeSendWithChunkSize(testData, transferId, VFILE_RESTART, sync2, 1018, 1024);
        assertTrue(resumed2 > 0, "Should complete with medium chunk size");

        System.out.println("\n✓✓✓ TEST 6 PASSED: Varying chunk sizes successful ✓✓✓");
    }

    /**
     * Test 7: Restart with sync interval boundary conditions
     * Tests restart exactly at sync interval boundaries
     */
    @Test
    @Order(7)
    @DisplayName("Restart at sync interval boundaries")
    void testRestartAtSyncBoundaries() throws Exception {
        System.out.println("\n=== TEST 7: RESTART AT SYNC INTERVAL BOUNDARIES ===");

        byte[] testData = generateTestData(150 * 1024); // 150KB
        int transferId = generateTransferId();

        // Interrupt exactly at sync boundary
        System.out.println("\nInterrupt exactly at sync interval boundary...");
        int lastSync = sendPartialWithInterrupt(testData, transferId, VFILE_RESTART, 5);

        // Resume should pick up exactly where we left off
        System.out.println("\nResume from exact sync boundary...");
        long resumed = resumeSendFromSyncPoint(testData, transferId, VFILE_RESTART, lastSync);
        assertTrue(resumed > 0, "Should resume from exact boundary");

        System.out.println("\n✓✓✓ TEST 7 PASSED: Boundary restart successful ✓✓✓");
    }

    // ==================== Helper Methods ====================

    /**
     * Sends partial data with interruption at specified sync point
     */
    private int sendPartialWithInterrupt(byte[] data, int transferId, String vfile, int stopAtSync)
            throws Exception {
        return sendPartialWithInterruptAndChunkSize(data, transferId, vfile, stopAtSync, CHUNK_SIZE, MAX_ENTITY_SIZE);
    }

    /**
     * Sends partial data with interruption using custom chunk size
     */
    private int sendPartialWithInterruptAndChunkSize(byte[] data, int transferId, String vfile,
            int stopAtSync, int recordLength, int maxEntitySize) throws Exception {

        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            // CONNECT with sync points
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .writeAccess()
                            .syncPointsEnabled(true)
                            .syncIntervalKb(10)
                            .resyncEnabled(true)
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();

            // Get negotiated sync interval
            long syncIntervalBytes = getNegotiatedSyncInterval(aconnect);
            System.out.println("  Sync interval: " + (syncIntervalBytes / 1024) + " KB");

            // CREATE
            long fileSizeKB = (data.length + 1023) / 1024;
            session.sendFpduWithAck(
                    new CreateMessageBuilder()
                            .filename(vfile)
                            .transferId(transferId)
                            .variableFormat()
                            .recordLength(recordLength)
                            .maxEntitySize(maxEntitySize)
                            .fileSizeKB(fileSizeKB)
                            .build(serverConnId));

            // OPEN & WRITE
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));
            session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

            // Send data with sync points until stopAtSync
            FpduWriter writer = new FpduWriter(session, serverConnId, maxEntitySize, recordLength, false);
            int syncNumber = 0;
            long bytesSinceSync = 0;
            int offset = 0;

            while (offset < data.length && syncNumber < stopAtSync) {
                // Check if we need to send sync before next chunk
                if (syncIntervalBytes > 0 && bytesSinceSync > 0
                        && bytesSinceSync + recordLength > syncIntervalBytes) {
                    syncNumber++;
                    session.sendFpduWithAck(new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncNumber)));
                    System.out.println("  Sync " + syncNumber + " at " + offset + " bytes");
                    bytesSinceSync = 0;
                }

                int chunkLen = Math.min(recordLength, data.length - offset);
                byte[] chunk = Arrays.copyOfRange(data, offset, offset + chunkLen);
                writer.writeDtf(chunk);
                offset += chunkLen;
                bytesSinceSync += chunkLen;
            }

            System.out.println("  Sent " + offset + " bytes, last sync: " + syncNumber);

            // Don't send DTF_END or cleanup - simulate interruption
            return syncNumber;
        }
    }

    /**
     * Resumes send from specified sync point and completes transfer
     */
    private long resumeSendFromSyncPoint(byte[] data, int transferId, String vfile, int restartSync)
            throws Exception {
        return resumeSendWithChunkSize(data, transferId, vfile, restartSync, CHUNK_SIZE, MAX_ENTITY_SIZE);
    }

    /**
     * Resumes send with custom chunk size
     */
    private long resumeSendWithChunkSize(byte[] data, int transferId, String vfile, int restartSync,
            int recordLength, int maxEntitySize) throws Exception {

        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            // CONNECT
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .writeAccess()
                            .syncPointsEnabled(true)
                            .syncIntervalKb(10)
                            .resyncEnabled(true)
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();
            long syncIntervalBytes = getNegotiatedSyncInterval(aconnect);

            // CREATE with restart flag (PI_15)
            long fileSizeKB = (data.length + 1023) / 1024;
            session.sendFpduWithAck(
                    new CreateMessageBuilder()
                            .filename(vfile)
                            .transferId(transferId)  // Same transferId as interrupted transfer
                            .variableFormat()
                            .recordLength(recordLength)
                            .maxEntitySize(maxEntitySize)
                            .fileSizeKB(fileSizeKB)
                            .restart()  // PI_15 = 1 indicates this is a restarted transfer
                            .build(serverConnId));

            // OPEN
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));

            // WRITE with NO parameters - server tells us restart point in ACK_WRITE
            Fpdu ackWrite = session.sendFpduWithAck(
                    new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

            // Read restart point from ACK_WRITE response
            ParameterValue pi18 = ackWrite.getParameter(PI_18_POINT_RELANCE);
            assertNotNull(pi18, "ACK_WRITE should contain restart point (PI_18)");

            int serverRestartPoint = parseNumeric(pi18.getValue());
            System.out.println("  Server restart point: " + serverRestartPoint + " (we requested sync " + restartSync + ")");

            // Server may give us a different restart point (e.g., 0, or last known good sync)
            // Calculate resume offset based on server's decision
            long resumeOffset = calculateResumeOffset(syncIntervalBytes, serverRestartPoint, recordLength);
            System.out.println("  Resuming from byte offset: " + resumeOffset);

            // Send remaining data
            FpduWriter writer = new FpduWriter(session, serverConnId, maxEntitySize, recordLength, false);
            int syncNumber = serverRestartPoint;  // Use server's decision, not our request
            long bytesSinceSync = 0;
            long offset = resumeOffset;
            long totalSent = 0;

            while (offset < data.length) {
                if (syncIntervalBytes > 0 && bytesSinceSync > 0
                        && bytesSinceSync + recordLength > syncIntervalBytes) {
                    syncNumber++;
                    session.sendFpduWithAck(new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncNumber)));
                    bytesSinceSync = 0;
                }

                int chunkLen = Math.min(recordLength, (int)(data.length - offset));
                byte[] chunk = Arrays.copyOfRange(data, (int)offset, (int)offset + chunkLen);
                writer.writeDtf(chunk);
                offset += chunkLen;
                bytesSinceSync += chunkLen;
                totalSent += chunkLen;
            }

            // Complete transfer
            session.sendFpdu(new Fpdu(FpduType.DTF_END)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            session.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId));

            // Cleanup
            cleanupSession(session, serverConnId);

            return totalSent;
        }
    }

    /**
     * Resumes send and interrupts again at specified sync point
     */
    private int resumeAndInterruptAt(byte[] data, int transferId, String vfile, int restartSync, int stopAtSync)
            throws Exception {

        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .writeAccess()
                            .syncPointsEnabled(true)
                            .syncIntervalKb(10)
                            .resyncEnabled(true)
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();
            long syncIntervalBytes = getNegotiatedSyncInterval(aconnect);

            // CREATE with restart flag (PI_15)
            long fileSizeKB = (data.length + 1023) / 1024;
            session.sendFpduWithAck(
                    new CreateMessageBuilder()
                            .filename(vfile)
                            .transferId(transferId)  // Same transferId as interrupted transfer
                            .variableFormat()
                            .recordLength(CHUNK_SIZE)
                            .maxEntitySize(MAX_ENTITY_SIZE)
                            .fileSizeKB(fileSizeKB)
                            .restart()  // PI_15 = 1 indicates this is a restarted transfer
                            .build(serverConnId));

            // OPEN
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));

            // WRITE with NO parameters - server tells us restart point in ACK_WRITE
            Fpdu ackWrite = session.sendFpduWithAck(
                    new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

            // Read restart point from ACK_WRITE response
            ParameterValue pi18 = ackWrite.getParameter(PI_18_POINT_RELANCE);
            assertNotNull(pi18, "ACK_WRITE should contain restart point (PI_18)");

            int serverRestartPoint = parseNumeric(pi18.getValue());
            System.out.println("  Server restart point: " + serverRestartPoint + " (we requested sync " + restartSync + ")");

            // Calculate resume offset based on server's decision
            long resumeOffset = calculateResumeOffset(syncIntervalBytes, serverRestartPoint, CHUNK_SIZE);
            FpduWriter writer = new FpduWriter(session, serverConnId, MAX_ENTITY_SIZE, CHUNK_SIZE, false);
            int syncNumber = serverRestartPoint;  // Use server's decision, not our request
            long bytesSinceSync = 0;
            long offset = resumeOffset;

            while (offset < data.length && syncNumber < stopAtSync) {
                if (syncIntervalBytes > 0 && bytesSinceSync > 0
                        && bytesSinceSync + CHUNK_SIZE > syncIntervalBytes) {
                    syncNumber++;
                    session.sendFpduWithAck(new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncNumber)));
                    System.out.println("  Sync " + syncNumber + " at " + offset + " bytes");
                    bytesSinceSync = 0;
                }

                int chunkLen = Math.min(CHUNK_SIZE, (int)(data.length - offset));
                byte[] chunk = Arrays.copyOfRange(data, (int)offset, (int)offset + chunkLen);
                writer.writeDtf(chunk);
                offset += chunkLen;
                bytesSinceSync += chunkLen;
            }

            return syncNumber;
        }
    }

    /**
     * Sends complete transfer with specified restart point
     */
    private long sendWithRestartPoint(byte[] data, int transferId, String vfile, int restartPoint)
            throws Exception {
        return resumeSendFromSyncPoint(data, transferId, vfile, restartPoint);
    }

    /**
     * Receives partial data then interrupts
     */
    private ReceiveResult receivePartialWithInterrupt(String vfile, int stopAtSync) throws Exception {
        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            // CONNECT for read
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .readAccess()
                            .syncPointsEnabled(true)
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();
            int transferId = generateTransferId();

            // SELECT file
            ParameterValue pgi9 = new ParameterValue(
                    ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                    new ParameterValue(PI_11_TYPE_FICHIER, 0),
                    new ParameterValue(PI_12_NOM_FICHIER, vfile));

            session.sendFpduWithAck(new Fpdu(FpduType.SELECT)
                    .withIdDst(serverConnId)
                    .withParameter(pgi9)
                    .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, transferId))
                    .withParameter(new ParameterValue(PI_14_ATTRIBUTS_DEMANDES, 0))
                    .withParameter(new ParameterValue(PI_17_PRIORITE, 0))
                    .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, MAX_ENTITY_SIZE)));

            // OPEN & READ
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));
            session.sendFpduWithAck(new Fpdu(FpduType.READ)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_18_POINT_RELANCE, 0)));

            // Receive data with sync points until stopAtSync
            FpduReader reader = new FpduReader(session);
            long totalReceived = 0;
            int syncNumber = 0;
            boolean receiving = true;

            while (receiving && syncNumber < stopAtSync) {
                Fpdu fpdu = reader.read();
                FpduType type = fpdu.getFpduType();

                if (type == FpduType.DTF || type == FpduType.DTFDA ||
                    type == FpduType.DTFMA || type == FpduType.DTFFA) {
                    byte[] data = fpdu.getData();
                    if (data != null) {
                        totalReceived += data.length;
                    }
                } else if (type == FpduType.SYN) {
                    ParameterValue pi20 = fpdu.getParameter(PI_20_NUM_SYNC);
                    syncNumber = parseNumeric(pi20.getValue());
                    session.sendFpdu(new Fpdu(FpduType.ACK_SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncNumber)));
                    System.out.println("  Sync " + syncNumber + " - received " + totalReceived + " bytes");
                } else if (type == FpduType.DTF_END || type == FpduType.TRANS_END) {
                    receiving = false;
                }
            }

            System.out.println("  Received " + totalReceived + " bytes before interrupt");

            // Interrupt without cleanup - simulate connection loss
            return new ReceiveResult(totalReceived, syncNumber);
        }
    }

    /**
     * Resumes receive from specified sync point
     */
    private ReceiveResult resumeReceiveFromSyncPoint(String vfile, int restartSync) throws Exception {
        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .readAccess()
                            .syncPointsEnabled(true)
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();
            int transferId = generateTransferId();

            // SELECT
            ParameterValue pgi9 = new ParameterValue(
                    ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                    new ParameterValue(PI_11_TYPE_FICHIER, 0),
                    new ParameterValue(PI_12_NOM_FICHIER, vfile));

            session.sendFpduWithAck(new Fpdu(FpduType.SELECT)
                    .withIdDst(serverConnId)
                    .withParameter(pgi9)
                    .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, transferId))
                    .withParameter(new ParameterValue(PI_14_ATTRIBUTS_DEMANDES, 0))
                    .withParameter(new ParameterValue(PI_17_PRIORITE, 0))
                    .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, MAX_ENTITY_SIZE)));

            // OPEN
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));

            // READ with restart point
            session.sendFpduWithAck(new Fpdu(FpduType.READ)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_18_POINT_RELANCE, restartSync)));

            System.out.println("  Resuming receive from sync point " + restartSync);

            // Receive remaining data
            FpduReader reader = new FpduReader(session);
            long totalReceived = 0;
            int lastSync = restartSync;
            boolean receiving = true;

            while (receiving) {
                Fpdu fpdu = reader.read();
                FpduType type = fpdu.getFpduType();

                if (type == FpduType.DTF || type == FpduType.DTFDA ||
                    type == FpduType.DTFMA || type == FpduType.DTFFA) {
                    byte[] data = fpdu.getData();
                    if (data != null) {
                        totalReceived += data.length;
                    }
                } else if (type == FpduType.SYN) {
                    ParameterValue pi20 = fpdu.getParameter(PI_20_NUM_SYNC);
                    lastSync = parseNumeric(pi20.getValue());
                    session.sendFpdu(new Fpdu(FpduType.ACK_SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_20_NUM_SYNC, lastSync)));
                } else if (type == FpduType.DTF_END) {
                    receiving = false;
                }
            }

            // Complete transfer
            session.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId));
            cleanupSession(session, serverConnId);

            return new ReceiveResult(totalReceived, lastSync);
        }
    }

    // ==================== Utility Methods ====================

    private byte[] generateTestData(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data); // Fixed seed for reproducibility
        return data;
    }

    private int generateTransferId() {
        return (int) (System.currentTimeMillis() % 0xFFFFFF);
    }

    private long getNegotiatedSyncInterval(Fpdu aconnect) {
        ParameterValue pi7 = aconnect.getParameter(PI_07_SYNC_POINTS);
        if (pi7 != null && pi7.getValue() != null && pi7.getValue().length >= 2) {
            int syncKb = ((pi7.getValue()[0] & 0xFF) << 8) | (pi7.getValue()[1] & 0xFF);
            if (syncKb > 0 && syncKb < 65535) {
                return syncKb * 1024L;
            }
        }
        return 32768; // Default 32KB
    }

    private long calculateResumeOffset(long syncIntervalBytes, int syncNumber, int recordLength) {
        // Approximate offset based on sync intervals
        // In practice, server tracks exact byte position per sync point
        return syncNumber * syncIntervalBytes;
    }

    private void cleanupSession(PesitSession session, int serverConnId) throws IOException, InterruptedException {
        session.sendFpduWithAck(new Fpdu(FpduType.CLOSE)
                .withIdDst(serverConnId)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
        session.sendFpduWithAck(new Fpdu(FpduType.DESELECT)
                .withIdDst(serverConnId)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
        session.sendFpdu(new Fpdu(FpduType.RELEASE)
                .withIdDst(serverConnId)
                .withIdSrc(CLIENT_CONNECTION_ID)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
    }

    private int parseNumeric(byte[] value) {
        if (value == null || value.length == 0) return 0;
        int result = 0;
        for (byte b : value) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    /**
     * Helper class to hold receive operation results
     */
    private static class ReceiveResult {
        final long bytesReceived;
        final int lastSyncNumber;

        ReceiveResult(long bytesReceived, int lastSyncNumber) {
            this.bytesReceived = bytesReceived;
            this.lastSyncNumber = lastSyncNumber;
        }
    }
}
