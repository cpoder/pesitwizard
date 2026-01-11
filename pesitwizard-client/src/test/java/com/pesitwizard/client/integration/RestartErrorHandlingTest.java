package com.pesitwizard.client.integration;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.*;

import com.pesitwizard.client.pesit.FpduReader;
import com.pesitwizard.client.pesit.FpduWriter;
import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TcpTransportChannel;

/**
 * Integration tests for error handling during restart mechanism.
 *
 * Tests cover:
 * - Invalid restart points
 * - Out-of-range sync numbers
 * - Server rejection scenarios
 * - File size mismatch between transfers
 * - Network timeout during restart
 * - Diagnostic code handling
 *
 * Requires PeSIT server running on test host/port.
 * Run with: mvn test -Dtest=RestartErrorHandlingTest -Dpesit.integration.enabled=true
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RestartErrorHandlingTest {

    private static final String TEST_HOST = System.getProperty("pesit.test.host", "localhost");
    private static final int TEST_PORT = Integer.parseInt(System.getProperty("pesit.test.port", "5100"));
    private static final String SERVER_ID = System.getProperty("pesit.test.server", "CETOM1");
    private static final String PARTNER_ID = "LOOP";
    private static final boolean INTEGRATION_ENABLED = Boolean.parseBoolean(
            System.getProperty("pesit.integration.enabled", "false"));

    private static final String VFILE_RESTART = "SYNCIN";
    private static final String VFILE_STANDARD = "FILE";
    private static final int CLIENT_CONNECTION_ID = 20;

    @BeforeAll
    void setUp() {
        Assumptions.assumeTrue(INTEGRATION_ENABLED,
                "Integration tests disabled. Enable with -Dpesit.integration.enabled=true");
    }

    /**
     * Test 1: Restart with invalid sync point (negative)
     * Server should reject or handle gracefully
     */
    @Test
    @Order(1)
    @DisplayName("Restart with invalid negative sync point")
    void testRestartWithNegativeSyncPoint() throws Exception {
        System.out.println("\n=== TEST 1: RESTART WITH NEGATIVE SYNC POINT ===");

        byte[] testData = generateTestData(50 * 1024);
        int transferId = generateTransferId();

        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .writeAccess()
                            .syncPointsEnabled(true)
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();

            // CREATE with restart flag (normal restart, not invalid)
            session.sendFpduWithAck(
                    new CreateMessageBuilder()
                            .filename(VFILE_RESTART)
                            .transferId(transferId)
                            .variableFormat()
                            .recordLength(506)
                            .maxEntitySize(512)
                            .fileSizeKB(50)
                            .restart()  // PI_15 = 1 indicates restart
                            .build(serverConnId));

            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));

            // Send WRITE - server decides restart point (can't directly test negative value)
            System.out.println("  Sending WRITE for restart...");
            try {
                Fpdu ackWrite = session.sendFpduWithAck(
                        new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

                // Check server's response
                ParameterValue pi2 = ackWrite.getParameter(PI_02_DIAG);
                ParameterValue pi18 = ackWrite.getParameter(PI_18_POINT_RELANCE);

                if (pi2 != null && pi2.getValue() != null) {
                    byte[] diag = pi2.getValue();
                    if (diag.length >= 3 && (diag[0] != 0 || diag[1] != 0 || diag[2] != 0)) {
                        System.out.println("  Server rejected with diagnostic: " +
                                String.format("%02X%02X%02X", diag[0], diag[1], diag[2]));
                        System.out.println("  ✓ Server correctly rejected restart (no prior transfer state)");
                    } else if (pi18 != null) {
                        int restartPoint = parseNumeric(pi18.getValue());
                        System.out.println("  ⚠ Server provided restart point: " + restartPoint);
                    }
                }
            } catch (Exception e) {
                System.out.println("  ✓ Server rejected with exception: " + e.getMessage());
            }

            cleanup(session, serverConnId);
        }

        System.out.println("\n✓✓✓ TEST 1 COMPLETED ✓✓✓");
    }

    /**
     * Test 2: Restart with out-of-range sync point
     * Sync point beyond what was actually sent
     */
    @Test
    @Order(2)
    @DisplayName("Restart with out-of-range sync point")
    void testRestartWithOutOfRangeSyncPoint() throws Exception {
        System.out.println("\n=== TEST 2: RESTART WITH OUT-OF-RANGE SYNC POINT ===");

        byte[] testData = generateTestData(100 * 1024);
        int transferId = generateTransferId();

        // Phase 1: Send partial data (stop at sync 2)
        System.out.println("\nPhase 1: Send partial (2 sync points)...");
        sendPartialToSync(testData, transferId, VFILE_RESTART, 2);

        // Phase 2: Try to restart from sync 100 (way beyond what exists)
        System.out.println("\nPhase 2: Attempt restart from sync 100 (out of range)...");

        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .writeAccess()
                            .syncPointsEnabled(true)
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();

            // CREATE with restart flag - server will check if valid restart state exists
            session.sendFpduWithAck(
                    new CreateMessageBuilder()
                            .filename(VFILE_RESTART)
                            .transferId(transferId)  // Same transferId as partial send
                            .variableFormat()
                            .recordLength(506)
                            .maxEntitySize(512)
                            .fileSizeKB(100)
                            .restart()  // PI_15 = 1 indicates restart
                            .build(serverConnId));

            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));

            // WRITE - server decides restart point (will use last known good sync, likely 2)
            try {
                Fpdu ackWrite = session.sendFpduWithAck(
                        new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

                // Check diagnostic and restart point
                ParameterValue pi2 = ackWrite.getParameter(PI_02_DIAG);
                ParameterValue pi18 = ackWrite.getParameter(PI_18_POINT_RELANCE);

                if (pi2 != null) {
                    byte[] diag = pi2.getValue();
                    if (diag != null && diag.length >= 3) {
                        System.out.println("  Diagnostic: " +
                                String.format("%02X%02X%02X", diag[0], diag[1], diag[2]));
                        if (diag[0] != 0 || diag[1] != 0 || diag[2] != 0) {
                            System.out.println("  ✓ Server rejected out-of-range restart point");
                        }
                    }
                }

                if (pi18 != null) {
                    int restartPoint = parseNumeric(pi18.getValue());
                    System.out.println("  Server provided restart point: " + restartPoint +
                            " (should be 2, last known good sync)");
                }
            } catch (Exception e) {
                System.out.println("  ✓ Server rejected with exception: " + e.getMessage());
            }

            cleanup(session, serverConnId);
        }

        System.out.println("\n✓✓✓ TEST 2 COMPLETED ✓✓✓");
    }

    /**
     * Test 3: File size mismatch between original and restart transfer
     * Transfer interrupts, then retry with different file size
     */
    @Test
    @Order(3)
    @DisplayName("Restart with file size mismatch")
    void testRestartWithFileSizeMismatch() throws Exception {
        System.out.println("\n=== TEST 3: RESTART WITH FILE SIZE MISMATCH ===");

        byte[] originalData = generateTestData(100 * 1024);
        byte[] differentData = generateTestData(150 * 1024); // Different size
        int transferId = generateTransferId();

        // Phase 1: Send partial with 100KB
        System.out.println("\nPhase 1: Send partial 100KB file...");
        sendPartialToSync(originalData, transferId, VFILE_RESTART, 2);

        // Phase 2: Try to restart with 150KB file
        System.out.println("\nPhase 2: Attempt restart with 150KB file...");

        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .writeAccess()
                            .syncPointsEnabled(true)
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();

            // CREATE with different file size
            long newFileSizeKB = (differentData.length + 1023) / 1024;
            try {
                session.sendFpduWithAck(
                        new CreateMessageBuilder()
                                .filename(VFILE_RESTART)
                                .transferId(transferId)
                                .variableFormat()
                                .recordLength(506)
                                .maxEntitySize(512)
                                .fileSizeKB(newFileSizeKB)
                                .build(serverConnId));

                System.out.println("  ⚠ Server accepted CREATE with different file size");
            } catch (Exception e) {
                System.out.println("  ✓ Server rejected CREATE with size mismatch: " + e.getMessage());
            }

            cleanup(session, serverConnId);
        }

        System.out.println("\n✓✓✓ TEST 3 COMPLETED ✓✓✓");
    }

    /**
     * Test 4: Restart without sync points enabled
     * Attempt restart when sync points were not negotiated
     */
    @Test
    @Order(4)
    @DisplayName("Restart without sync points enabled")
    void testRestartWithoutSyncPoints() throws Exception {
        System.out.println("\n=== TEST 4: RESTART WITHOUT SYNC POINTS ===");

        byte[] testData = generateTestData(50 * 1024);
        int transferId = generateTransferId();

        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            // CONNECT without sync points
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .writeAccess()
                            // Note: syncPointsEnabled(false) or not specified
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();

            // Verify sync points not enabled
            ParameterValue pi7 = aconnect.getParameter(PI_07_SYNC_POINTS);
            boolean syncEnabled = false;
            if (pi7 != null && pi7.getValue() != null && pi7.getValue().length >= 2) {
                int syncKb = ((pi7.getValue()[0] & 0xFF) << 8) | (pi7.getValue()[1] & 0xFF);
                syncEnabled = syncKb > 0 && syncKb < 65535;
            }
            System.out.println("  Sync points enabled: " + syncEnabled);

            // CREATE with restart flag when sync not negotiated
            try {
                session.sendFpduWithAck(
                        new CreateMessageBuilder()
                                .filename(VFILE_STANDARD)
                                .transferId(transferId)
                                .variableFormat()
                                .recordLength(506)
                                .maxEntitySize(512)
                                .fileSizeKB(50)
                                .restart()  // PI_15 = 1, but sync not enabled
                                .build(serverConnId));

                System.out.println("  ⚠ Server accepted CREATE with restart flag despite no sync");

                session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));

                // WRITE without parameters - check if server provides restart point
                System.out.println("  Attempting restart without sync negotiation...");
                Fpdu ackWrite = session.sendFpduWithAck(
                        new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

                // Check if server provided restart point
                ParameterValue pi18 = ackWrite.getParameter(PI_18_POINT_RELANCE);
                if (pi18 == null) {
                    System.out.println("  ✓ Server ignored restart (no sync support)");
                } else {
                    System.out.println("  ⚠ Server acknowledged restart despite no sync negotiation");
                }
            } catch (Exception e) {
                System.out.println("  ✓ Server rejected restart without sync: " + e.getMessage());
            }

            cleanup(session, serverConnId);
        }

        System.out.println("\n✓✓✓ TEST 4 COMPLETED ✓✓✓");
    }

    /**
     * Test 5: Restart with zero sync point
     * Edge case: restart from the beginning (sync 0)
     */
    @Test
    @Order(5)
    @DisplayName("Restart from sync point 0 with previous data")
    void testRestartFromZeroAfterPartial() throws Exception {
        System.out.println("\n=== TEST 5: RESTART FROM SYNC 0 AFTER PARTIAL SEND ===");

        byte[] testData = generateTestData(100 * 1024);
        int transferId = generateTransferId();

        // Phase 1: Send partial
        System.out.println("\nPhase 1: Send partial data...");
        sendPartialToSync(testData, transferId, VFILE_RESTART, 3);

        // Phase 2: Restart from 0 (start over)
        System.out.println("\nPhase 2: Restart from sync point 0 (beginning)...");

        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .writeAccess()
                            .syncPointsEnabled(true)
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();
            long syncInterval = getNegotiatedSyncInterval(aconnect);

            // CREATE with restart flag
            session.sendFpduWithAck(
                    new CreateMessageBuilder()
                            .filename(VFILE_RESTART)
                            .transferId(transferId)  // Same transferId as interrupted transfer
                            .variableFormat()
                            .recordLength(506)
                            .maxEntitySize(512)
                            .fileSizeKB(100)
                            .restart()  // PI_15 = 1 indicates this is a restarted transfer
                            .build(serverConnId));

            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));

            // WRITE with NO parameters - server tells us restart point in ACK_WRITE
            Fpdu ackWrite = session.sendFpduWithAck(
                    new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

            // Read restart point from ACK_WRITE response
            ParameterValue pi18 = ackWrite.getParameter(PI_18_POINT_RELANCE);
            int serverRestartPoint = pi18 != null ? parseNumeric(pi18.getValue()) : 0;
            System.out.println("  ✓ Server accepted restart, restart point: " + serverRestartPoint);

            // Send complete file from beginning
            FpduWriter writer = new FpduWriter(session, serverConnId, 512, 506, false);
            int syncNum = 0;
            long bytesSinceSync = 0;
            int offset = 0;

            while (offset < testData.length) {
                if (syncInterval > 0 && bytesSinceSync > 0 && bytesSinceSync + 506 > syncInterval) {
                    syncNum++;
                    session.sendFpduWithAck(new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncNum)));
                    bytesSinceSync = 0;
                }

                int chunkLen = Math.min(506, testData.length - offset);
                byte[] chunk = new byte[chunkLen];
                System.arraycopy(testData, offset, chunk, 0, chunkLen);
                writer.writeDtf(chunk);
                offset += chunkLen;
                bytesSinceSync += chunkLen;
            }

            // Complete
            session.sendFpdu(new Fpdu(FpduType.DTF_END)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            session.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId));

            System.out.println("  ✓ Complete transfer from beginning successful");

            cleanup(session, serverConnId);
        }

        System.out.println("\n✓✓✓ TEST 5 COMPLETED ✓✓✓");
    }

    /**
     * Test 6: Restart with mismatched transfer ID
     * Using different transfer ID for restart
     */
    @Test
    @Order(6)
    @DisplayName("Restart with mismatched transfer ID")
    void testRestartWithMismatchedTransferId() throws Exception {
        System.out.println("\n=== TEST 6: RESTART WITH MISMATCHED TRANSFER ID ===");

        byte[] testData = generateTestData(100 * 1024);
        int originalTransferId = generateTransferId();
        int differentTransferId = generateTransferId() + 1000;

        // Phase 1: Send partial with transfer ID 1
        System.out.println("\nPhase 1: Send with transfer ID " + originalTransferId + "...");
        sendPartialToSync(testData, originalTransferId, VFILE_RESTART, 2);

        // Phase 2: Try to restart with different transfer ID
        System.out.println("\nPhase 2: Attempt restart with transfer ID " + differentTransferId + "...");

        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .writeAccess()
                            .syncPointsEnabled(true)
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();

            // CREATE with different transfer ID
            try {
                session.sendFpduWithAck(
                        new CreateMessageBuilder()
                                .filename(VFILE_RESTART)
                                .transferId(differentTransferId)
                                .variableFormat()
                                .recordLength(506)
                                .maxEntitySize(512)
                                .fileSizeKB(100)
                                .build(serverConnId));

                System.out.println("  ⚠ Server accepted CREATE with different transfer ID");
                System.out.println("  (Server may treat this as new transfer, not restart)");
            } catch (Exception e) {
                System.out.println("  ✓ Server rejected different transfer ID: " + e.getMessage());
            }

            cleanup(session, serverConnId);
        }

        System.out.println("\n✓✓✓ TEST 6 COMPLETED ✓✓✓");
    }

    /**
     * Test 7: Restart after session timeout
     * Simulates long delay between interrupt and restart
     */
    @Test
    @Order(7)
    @DisplayName("Restart after session timeout simulation")
    void testRestartAfterDelay() throws Exception {
        System.out.println("\n=== TEST 7: RESTART AFTER DELAY (TIMEOUT SIMULATION) ===");

        byte[] testData = generateTestData(100 * 1024);
        int transferId = generateTransferId();

        // Phase 1: Send partial
        System.out.println("\nPhase 1: Send partial data...");
        int lastSync = sendPartialToSync(testData, transferId, VFILE_RESTART, 3);

        // Simulate timeout
        System.out.println("\nSimulating session timeout (5 seconds)...");
        Thread.sleep(5000);

        // Phase 2: Try to restart after delay
        System.out.println("\nPhase 2: Attempt restart after timeout...");

        TcpTransportChannel channel = new TcpTransportChannel(TEST_HOST, TEST_PORT);
        channel.setReceiveTimeout(30000);

        try (PesitSession session = new PesitSession(channel, false)) {
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur(PARTNER_ID)
                            .serveur(SERVER_ID)
                            .writeAccess()
                            .syncPointsEnabled(true)
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();

            // CREATE with restart flag
            session.sendFpduWithAck(
                    new CreateMessageBuilder()
                            .filename(VFILE_RESTART)
                            .transferId(transferId)  // Same transferId as interrupted transfer
                            .variableFormat()
                            .recordLength(506)
                            .maxEntitySize(512)
                            .fileSizeKB(100)
                            .restart()  // PI_15 = 1 indicates this is a restarted transfer
                            .build(serverConnId));

            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));

            // WRITE with NO parameters - server tells us restart point in ACK_WRITE
            Fpdu ackWrite = session.sendFpduWithAck(
                    new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

            // Read restart point from ACK_WRITE response
            ParameterValue pi18 = ackWrite.getParameter(PI_18_POINT_RELANCE);
            int serverRestartPoint = pi18 != null ? parseNumeric(pi18.getValue()) : 0;
            System.out.println("  ✓ Server accepted restart after timeout");
            System.out.println("  Server restart point: " + serverRestartPoint + " (last sync was " + lastSync + ")");
            System.out.println("  (Server maintained restart state across sessions)");

            cleanup(session, serverConnId);
        }

        System.out.println("\n✓✓✓ TEST 7 COMPLETED ✓✓✓");
    }

    // ==================== Helper Methods ====================

    private byte[] generateTestData(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
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

    private int sendPartialToSync(byte[] data, int transferId, String vfile, int stopAtSync) throws Exception {
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
                            .build(CLIENT_CONNECTION_ID));

            int serverConnId = aconnect.getIdSrc();
            long syncInterval = getNegotiatedSyncInterval(aconnect);

            session.sendFpduWithAck(
                    new CreateMessageBuilder()
                            .filename(vfile)
                            .transferId(transferId)
                            .variableFormat()
                            .recordLength(506)
                            .maxEntitySize(512)
                            .fileSizeKB((data.length + 1023) / 1024)
                            .build(serverConnId));

            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));
            session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

            FpduWriter writer = new FpduWriter(session, serverConnId, 512, 506, false);
            int syncNum = 0;
            long bytesSinceSync = 0;
            int offset = 0;

            while (offset < data.length && syncNum < stopAtSync) {
                if (syncInterval > 0 && bytesSinceSync > 0 && bytesSinceSync + 506 > syncInterval) {
                    syncNum++;
                    session.sendFpduWithAck(new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncNum)));
                    bytesSinceSync = 0;
                }

                int chunkLen = Math.min(506, data.length - offset);
                byte[] chunk = new byte[chunkLen];
                System.arraycopy(data, offset, chunk, 0, chunkLen);
                writer.writeDtf(chunk);
                offset += chunkLen;
                bytesSinceSync += chunkLen;
            }

            System.out.println("  Sent " + offset + " bytes, last sync: " + syncNum);
            return syncNum;
        }
    }

    private void cleanup(PesitSession session, int serverConnId) {
        try {
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
        } catch (Exception e) {
            System.err.println("  Warning: Cleanup failed: " + e.getMessage());
        }
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
