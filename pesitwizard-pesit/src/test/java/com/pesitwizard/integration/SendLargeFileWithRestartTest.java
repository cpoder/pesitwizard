package com.pesitwizard.integration;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TcpTransportChannel;

/**
 * Integration test for sending large files with sync points and restart
 * capability.
 * Tests the 344MB video file with periodic sync points.
 * 
 * File organization: Sequential (0) - appropriate for binary files like .mp4
 * Per PeSIT spec 1.1.1.124.x:
 * - 0 = sequential: data accessed sequentially
 * - 1 = relative: access by record number
 * - 2 = indexed: access by key
 * 
 * Requires Connect:Express running on localhost:5100
 * Run with: mvn test -Dtest=SendLargeFileWithRestartTest
 * -Dpesit.integration.enabled=true
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SendLargeFileWithRestartTest {

    private static final String TEST_HOST = System.getProperty("pesit.test.host", "localhost");
    private static final int TEST_PORT = Integer.parseInt(System.getProperty("pesit.test.port", "5100"));
    private static final String SERVER_ID = System.getProperty("pesit.test.server", "CETOM1");
    private static final boolean INTEGRATION_ENABLED = Boolean.parseBoolean(
            System.getProperty("pesit.integration.enabled", "false"));

    private static final String LARGE_FILE_PATH = "/home/cpo/pesit-data/client/Slow.Horses.S02E01.Last.Stop.1080p.H.265.mp4";
    private static final String SMALL_FILE_PATH = "/home/cpo/pesit-data/client/2022-05-19 22-11-54.mkv";
    private static final int CLIENT_CONNECTION_ID = 0x05;

    // Will be negotiated from server via PI_7 and PI_32
    // FILE virtual file configured for 65529 bytes record length
    private static final int PROPOSED_RECORD_LENGTH = 65529;
    private static final int PROPOSED_MAX_ENTITY_SIZE = 65535;

    @BeforeAll
    void setUp() {
        Assumptions.assumeTrue(INTEGRATION_ENABLED,
                "Integration tests disabled. Enable with -Dpesit.integration.enabled=true");

        File testFile = new File(LARGE_FILE_PATH);
        Assumptions.assumeTrue(testFile.exists() && testFile.canRead(),
                "Test file not found: " + LARGE_FILE_PATH);
    }

    @Test
    @DisplayName("Send 344MB file with sync points (full transfer)")
    void testSendLargeFileWithSyncPoints() throws Exception {
        File testFile = new File(LARGE_FILE_PATH);
        long fileSize = testFile.length();
        System.out.println("\n=== LARGE FILE TRANSFER TEST ===");
        System.out.println("File: " + LARGE_FILE_PATH);
        System.out.println("Size: " + (fileSize / 1024 / 1024) + " MB (" + fileSize + " bytes)");
        System.out.println("Proposed record length (PI_32): " + PROPOSED_RECORD_LENGTH);
        System.out.println();

        try (PesitSession session = new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT))) {
            // CONNECT
            System.out.println("Step 1: CONNECT");
            ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                    .demandeur("LOOP")
                    .serveur(SERVER_ID)
                    .writeAccess()
                    .syncPointsEnabled(true)
                    .resyncEnabled(true);
            Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(CLIENT_CONNECTION_ID));
            assertEquals(FpduType.ACONNECT, aconnect.getFpduType());
            int serverConnectionId = aconnect.getIdSrc();
            System.out.println("  ✓ Connected, server ID: " + serverConnectionId);

            // Read PI_7 sync points negotiation (octets 1-2 = interval in KB)
            long syncIntervalBytes = 0;
            ParameterValue pi7 = aconnect.getParameter(PI_07_SYNC_POINTS);
            if (pi7 != null && pi7.getValue() != null && pi7.getValue().length >= 2) {
                int syncIntervalKb = ((pi7.getValue()[0] & 0xFF) << 8) | (pi7.getValue()[1] & 0xFF);
                if (syncIntervalKb > 0 && syncIntervalKb < 65535) {
                    syncIntervalBytes = syncIntervalKb * 1024L;
                    System.out.println("  Negotiated sync interval (PI_7): " + syncIntervalKb + " KB ("
                            + syncIntervalBytes + " bytes)");
                } else {
                    System.out.println("  Sync interval: " + (syncIntervalKb == 0 ? "disabled" : "undefined"));
                }
            }

            // CREATE - propose record length matching FILE virtual file config (69929)
            long fileSizeKB = (fileSize + 1023) / 1024;
            System.out.println("\nStep 2: CREATE (fileSize=" + fileSizeKB + " KB, proposing PI_32="
                    + PROPOSED_RECORD_LENGTH + ")");
            CreateMessageBuilder createBuilder = new CreateMessageBuilder()
                    .filename("FILE")
                    .transferId(1)
                    .variableFormat()
                    .recordLength(PROPOSED_RECORD_LENGTH)
                    .maxEntitySize(PROPOSED_MAX_ENTITY_SIZE)
                    .fileSizeKB(fileSizeKB);
            Fpdu ackCreate = session.sendFpduWithAck(createBuilder.build(serverConnectionId));
            assertEquals(FpduType.ACK_CREATE, ackCreate.getFpduType());

            // Read negotiated PI_32 (record length) from ACK_CREATE
            int negotiatedRecordLength = PROPOSED_RECORD_LENGTH;
            ParameterValue pi32 = ackCreate.getParameter(PI_32_LONG_ARTICLE);
            if (pi32 != null && pi32.getValue() != null) {
                negotiatedRecordLength = parseNumeric(pi32.getValue());
                System.out.println("  Negotiated record length (PI_32): " + negotiatedRecordLength + " bytes");
            }

            // Read negotiated PI_25 (max entity size) from ACK_CREATE
            int negotiatedEntitySize = PROPOSED_MAX_ENTITY_SIZE;
            ParameterValue pi25 = ackCreate.getParameter(PI_25_TAILLE_MAX_ENTITE);
            if (pi25 != null && pi25.getValue() != null) {
                negotiatedEntitySize = parseNumeric(pi25.getValue());
                System.out.println("  Negotiated max entity size (PI_25): " + negotiatedEntitySize + " bytes");
            }
            System.out.println("  ✓ File created");

            // Chunk size must be <= sync interval to avoid "too much data without sync
            // point" error
            // Also limited by record length and entity size
            int chunkSize = Math.min(negotiatedRecordLength, negotiatedEntitySize - 6);
            if (syncIntervalBytes > 0 && chunkSize > syncIntervalBytes) {
                chunkSize = (int) syncIntervalBytes;
            }
            System.out.println("  Using chunk size: " + chunkSize + " bytes (limited by sync interval)");

            // OPEN
            System.out.println("\nStep 3: OPEN");
            Fpdu openFpdu = new Fpdu(FpduType.OPEN).withIdDst(serverConnectionId);
            Fpdu ackOpen = session.sendFpduWithAck(openFpdu);
            assertEquals(FpduType.ACK_OPEN, ackOpen.getFpduType());
            System.out.println("  ✓ File opened");

            // WRITE
            System.out.println("\nStep 4: WRITE");
            Fpdu writeFpdu = new Fpdu(FpduType.WRITE).withIdDst(serverConnectionId);
            Fpdu ackWrite = session.sendFpduWithAck(writeFpdu);
            assertEquals(FpduType.ACK_WRITE, ackWrite.getFpduType());

            // Check restart point
            ParameterValue pi18 = ackWrite.getParameter(PI_18_POINT_RELANCE);
            if (pi18 != null) {
                System.out.println("  Restart point from server: " + parseNumeric(pi18.getValue()));
            }
            System.out.println("  ✓ Write started");

            // Send data with sync points
            System.out.println("\nStep 5: Sending " + fileSize + " bytes with sync points...");
            if (syncIntervalBytes > 0) {
                System.out.println("  Sync point every " + (syncIntervalBytes / 1024) + " KB");
            } else {
                System.out.println("  No sync points (disabled or undefined)");
            }
            long startTime = System.currentTimeMillis();
            int syncPointNumber = 0;
            long totalSent = 0;
            long bytesSinceLastSync = 0;

            try (FileInputStream fis = new FileInputStream(testFile)) {
                byte[] buffer = new byte[chunkSize];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] chunk = bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);

                    Fpdu dtfFpdu = new Fpdu(FpduType.DTF).withIdDst(serverConnectionId);
                    session.sendFpduWithData(dtfFpdu, chunk);
                    totalSent += bytesRead;
                    bytesSinceLastSync += bytesRead;

                    // Send sync point periodically based on negotiated interval
                    if (syncIntervalBytes > 0 && bytesSinceLastSync >= syncIntervalBytes) {
                        syncPointNumber++;
                        Fpdu synFpdu = new Fpdu(FpduType.SYN)
                                .withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncPointNumber));
                        Fpdu ackSyn = session.sendFpduWithAck(synFpdu);
                        assertEquals(FpduType.ACK_SYN, ackSyn.getFpduType());

                        long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
                        double mbSent = totalSent / 1024.0 / 1024.0;
                        double speed = elapsedSec > 0 ? mbSent / elapsedSec : 0;
                        System.out.printf("  Sync point %d at %.1f MB (%.1f MB/s)%n",
                                syncPointNumber, mbSent, speed);
                        bytesSinceLastSync = 0;
                    }
                }
            }

            long elapsedMs = System.currentTimeMillis() - startTime;
            double mbSent = totalSent / 1024.0 / 1024.0;
            double speed = elapsedMs > 0 ? mbSent / (elapsedMs / 1000.0) : 0;
            System.out.printf("  ✓ Sent %.1f MB in %.1f seconds (%.1f MB/s), %d sync points%n",
                    mbSent, elapsedMs / 1000.0, speed, syncPointNumber);

            // DTF_END
            System.out.println("\nStep 6: DTF_END");
            Fpdu dtfEndFpdu = new Fpdu(FpduType.DTF_END)
                    .withIdDst(serverConnectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
            session.sendFpdu(dtfEndFpdu);
            System.out.println("  ✓ DTF_END sent");

            // TRANS_END
            System.out.println("\nStep 7: TRANS_END");
            Fpdu transEndFpdu = new Fpdu(FpduType.TRANS_END).withIdDst(serverConnectionId);
            Fpdu ackTransEnd = session.sendFpduWithAck(transEndFpdu);
            assertEquals(FpduType.ACK_TRANS_END, ackTransEnd.getFpduType());
            System.out.println("  ✓ Transfer complete");

            // Cleanup
            performCleanup(session, serverConnectionId);
        }

        System.out.println("\n✓✓✓ LARGE FILE TRANSFER COMPLETED ✓✓✓\n");
    }

    @Test
    @DisplayName("Send partial file then interrupt with IDT")
    void testPartialTransferWithInterrupt() throws Exception {
        File testFile = new File(LARGE_FILE_PATH);
        long fileSize = testFile.length();
        long partialSize = 50 * 1024 * 1024; // Send only 50MB then interrupt

        System.out.println("\n=== PARTIAL TRANSFER WITH INTERRUPT TEST ===");
        System.out.println("Will send 50MB then interrupt");
        System.out.println();

        try (PesitSession session = new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT))) {
            // CONNECT
            ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                    .demandeur("LOOP")
                    .serveur(SERVER_ID)
                    .writeAccess()
                    .syncPointsEnabled(true);
            Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(CLIENT_CONNECTION_ID));
            int serverConnectionId = aconnect.getIdSrc();
            System.out.println("Step 1: Connected, server ID: " + serverConnectionId);

            // Read negotiated sync interval from PI_7
            long syncIntervalBytes = 0;
            ParameterValue pi7 = aconnect.getParameter(PI_07_SYNC_POINTS);
            if (pi7 != null && pi7.getValue() != null && pi7.getValue().length >= 2) {
                int syncIntervalKb = ((pi7.getValue()[0] & 0xFF) << 8) | (pi7.getValue()[1] & 0xFF);
                if (syncIntervalKb > 0 && syncIntervalKb < 65535) {
                    syncIntervalBytes = syncIntervalKb * 1024L;
                }
            }

            // CREATE
            long fileSizeKB = (fileSize + 1023) / 1024;
            CreateMessageBuilder createBuilder = new CreateMessageBuilder()
                    .filename("FILE")
                    .transferId(2)
                    .variableFormat()
                    .recordLength(PROPOSED_RECORD_LENGTH)
                    .maxEntitySize(PROPOSED_MAX_ENTITY_SIZE)
                    .fileSizeKB(fileSizeKB);
            Fpdu ackCreate = session.sendFpduWithAck(createBuilder.build(serverConnectionId));
            System.out.println("Step 2: File created");

            // Read negotiated chunk size
            int chunkSize = PROPOSED_RECORD_LENGTH;
            ParameterValue pi32 = ackCreate.getParameter(PI_32_LONG_ARTICLE);
            if (pi32 != null && pi32.getValue() != null) {
                chunkSize = parseNumeric(pi32.getValue());
            }
            ParameterValue pi25 = ackCreate.getParameter(PI_25_TAILLE_MAX_ENTITE);
            if (pi25 != null && pi25.getValue() != null) {
                chunkSize = Math.min(chunkSize, parseNumeric(pi25.getValue()) - 6);
            }
            // Limit to sync interval
            if (syncIntervalBytes > 0 && chunkSize > syncIntervalBytes) {
                chunkSize = (int) syncIntervalBytes;
            }

            // OPEN & WRITE
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnectionId));
            session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverConnectionId));
            System.out.println("Step 3: File opened and write started");

            // Send partial data with sync points
            System.out.println("Step 4: Sending partial data (50MB)...");
            int syncPointNumber = 0;
            long totalSent = 0;
            long bytesSinceLastSync = 0;

            try (FileInputStream fis = new FileInputStream(testFile)) {
                byte[] buffer = new byte[chunkSize];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1 && totalSent < partialSize) {
                    byte[] chunk = bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);

                    session.sendFpduWithData(new Fpdu(FpduType.DTF).withIdDst(serverConnectionId), chunk);
                    totalSent += bytesRead;
                    bytesSinceLastSync += bytesRead;

                    if (syncIntervalBytes > 0 && bytesSinceLastSync >= syncIntervalBytes) {
                        syncPointNumber++;
                        session.sendFpduWithAck(new Fpdu(FpduType.SYN)
                                .withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncPointNumber)));
                        System.out.println(
                                "  Sync point " + syncPointNumber + " at " + (totalSent / 1024 / 1024) + " MB");
                        bytesSinceLastSync = 0;
                    }
                }
            }
            System.out.println("  Sent " + (totalSent / 1024 / 1024) + " MB, last sync point: " + syncPointNumber);

            // IDT (Interrupt)
            System.out.println("Step 5: Sending IDT (interrupt)...");
            Fpdu ackIdt = session.sendFpduWithAck(new Fpdu(FpduType.IDT)
                    .withIdDst(serverConnectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
            assertEquals(FpduType.ACK_IDT, ackIdt.getFpduType());
            System.out.println("  ✓ Transfer interrupted at sync point " + syncPointNumber);

            // Cleanup
            session.sendFpduWithAck(new Fpdu(FpduType.CLOSE)
                    .withIdDst(serverConnectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
            session.sendFpduWithAck(new Fpdu(FpduType.DESELECT)
                    .withIdDst(serverConnectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
            session.sendFpdu(new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(CLIENT_CONNECTION_ID)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
            System.out.println("Step 6: Session closed");
        }

        System.out.println("\n✓✓✓ PARTIAL TRANSFER WITH INTERRUPT COMPLETED ✓✓✓");
        System.out.println("Transfer can be resumed from sync point using PI_18\n");
    }

    private void performCleanup(PesitSession session, int serverConnectionId)
            throws IOException, InterruptedException {
        System.out.println("\nStep 8: Cleanup");
        session.sendFpduWithAck(new Fpdu(FpduType.CLOSE)
                .withIdDst(serverConnectionId)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 })));
        session.sendFpduWithAck(new Fpdu(FpduType.DESELECT)
                .withIdDst(serverConnectionId)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 })));
        session.sendFpdu(new Fpdu(FpduType.RELEASE)
                .withIdDst(serverConnectionId)
                .withIdSrc(CLIENT_CONNECTION_ID)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 })));
        System.out.println("  ✓ Session closed");
    }

    private int parseNumeric(byte[] value) {
        if (value == null || value.length == 0)
            return 0;
        int result = 0;
        for (byte b : value) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    @Test
    @DisplayName("Send 6MB file complete with sync points")
    void testSend6MBFileComplete() throws Exception {
        File testFile = new File(SMALL_FILE_PATH);
        Assumptions.assumeTrue(testFile.exists(), "6MB test file not found: " + SMALL_FILE_PATH);
        long fileSize = testFile.length();

        System.out.println("\n=== 6MB FILE TRANSFER TEST ===");
        System.out.println("File: " + SMALL_FILE_PATH);
        System.out.println("Size: " + (fileSize / 1024) + " KB (" + fileSize + " bytes)");

        int clientId = new java.util.Random().nextInt(200) + 10;
        System.out.println("Client ID: " + clientId);

        try (PesitSession session = new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT))) {
            // CONNECT
            System.out.println("\nStep 1: CONNECT");
            ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                    .demandeur("LOOP")
                    .serveur(SERVER_ID)
                    .writeAccess()
                    .syncPointsEnabled(true);
            Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(clientId));
            int serverConnectionId = aconnect.getIdSrc();
            System.out.println("  ✓ Connected, server ID: " + serverConnectionId);

            // Read sync interval from PI_7
            long syncIntervalBytes = 32768;
            ParameterValue pi7 = aconnect.getParameter(PI_07_SYNC_POINTS);
            if (pi7 != null && pi7.getValue() != null && pi7.getValue().length >= 2) {
                int syncIntervalKb = ((pi7.getValue()[0] & 0xFF) << 8) | (pi7.getValue()[1] & 0xFF);
                if (syncIntervalKb > 0 && syncIntervalKb < 65535) {
                    syncIntervalBytes = syncIntervalKb * 1024L;
                }
            }
            System.out.println("  Sync interval: " + (syncIntervalBytes / 1024) + " KB");

            // CREATE
            long fileSizeKB = (fileSize + 1023) / 1024;
            System.out.println("\nStep 2: CREATE (fileSize=" + fileSizeKB + " KB)");
            CreateMessageBuilder createBuilder = new CreateMessageBuilder()
                    .filename("FILE")
                    .transferId(1)
                    .variableFormat()
                    .recordLength(PROPOSED_RECORD_LENGTH)
                    .maxEntitySize(PROPOSED_MAX_ENTITY_SIZE)
                    .fileSizeKB(fileSizeKB);
            Fpdu ackCreate = session.sendFpduWithAck(createBuilder.build(serverConnectionId));
            assertEquals(FpduType.ACK_CREATE, ackCreate.getFpduType());
            System.out.println("  ✓ File created");

            // Use sync interval as chunk size
            int chunkSize = (int) syncIntervalBytes;
            ParameterValue pi25 = ackCreate.getParameter(PI_25_TAILLE_MAX_ENTITE);
            if (pi25 != null && pi25.getValue() != null) {
                chunkSize = Math.min(chunkSize, parseNumeric(pi25.getValue()) - 6);
            }
            System.out.println("  Chunk size: " + chunkSize + " bytes");

            // OPEN
            System.out.println("\nStep 3: OPEN");
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnectionId));
            System.out.println("  ✓ File opened");

            // WRITE
            System.out.println("\nStep 4: WRITE");
            session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverConnectionId));
            System.out.println("  ✓ Write started");

            // Send data with sync points
            System.out.println("\nStep 5: Sending " + fileSize + " bytes...");
            long startTime = System.currentTimeMillis();
            int syncPointNumber = 0;
            long totalSent = 0;
            long bytesSinceLastSync = 0;

            try (FileInputStream fis = new FileInputStream(testFile)) {
                byte[] buffer = new byte[chunkSize];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] chunk = bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);

                    session.sendFpduWithData(new Fpdu(FpduType.DTF).withIdDst(serverConnectionId), chunk);
                    totalSent += bytesRead;
                    bytesSinceLastSync += bytesRead;

                    if (syncIntervalBytes > 0 && bytesSinceLastSync >= syncIntervalBytes) {
                        syncPointNumber++;
                        session.sendFpduWithAck(new Fpdu(FpduType.SYN)
                                .withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncPointNumber)));
                        bytesSinceLastSync = 0;

                        if (syncPointNumber % 50 == 0) {
                            double mbSent = totalSent / 1024.0 / 1024.0;
                            System.out.printf("  Sync point %d at %.1f MB%n", syncPointNumber, mbSent);
                        }
                    }
                }
            }

            long elapsedMs = System.currentTimeMillis() - startTime;
            double mbSent = totalSent / 1024.0 / 1024.0;
            double speed = elapsedMs > 0 ? mbSent / (elapsedMs / 1000.0) : 0;
            System.out.printf("  ✓ Sent %.1f MB in %.1f seconds (%.1f MB/s), %d sync points%n",
                    mbSent, elapsedMs / 1000.0, speed, syncPointNumber);

            // DTF_END
            System.out.println("\nStep 6: DTF_END");
            session.sendFpdu(new Fpdu(FpduType.DTF_END)
                    .withIdDst(serverConnectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
            System.out.println("  ✓ DTF_END sent");

            // TRANS_END
            System.out.println("\nStep 7: TRANS_END");
            Fpdu ackTransEnd = session.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(serverConnectionId));
            assertEquals(FpduType.ACK_TRANS_END, ackTransEnd.getFpduType());
            System.out.println("  ✓ Transfer complete");

            // Cleanup
            performCleanup(session, serverConnectionId);
        }

        System.out.println("\n✓✓✓ 6MB FILE TRANSFER COMPLETED ✓✓✓\n");
    }
}
