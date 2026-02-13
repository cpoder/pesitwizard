package com.pesitwizard.integration;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.fpdu.SelectMessageBuilder;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TcpTransportChannel;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Receive file test - receives file data from Connect:Express Requires Connect:Express running on
 * localhost:5100
 *
 * <p>Run with: mvn test -Dtest=ReceiveFileTest -Dpesit.test.port=5100
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReceiveFileTest {

    private static final String TEST_HOST = System.getProperty("pesit.test.host", "localhost");
    private static final int TEST_PORT =
            Integer.parseInt(System.getProperty("pesit.test.port", "5100"));
    private static final boolean INTEGRATION_ENABLED =
            Boolean.parseBoolean(System.getProperty("pesit.integration.enabled", "false"));

    @BeforeAll
    void setUp() {
        Assumptions.assumeTrue(
                INTEGRATION_ENABLED,
                "Integration tests disabled. Enable with -Dpesit.integration.enabled=true");
    }

    @Test
    @DisplayName(
            "Receive file: CONNECT → SELECT → OPEN → READ → DTF... → DTF.END → TRANS.END → CLOSE → DESELECT → RELEASE")
    void testReceiveFile() throws IOException, InterruptedException {
        System.out.println("\n=== RECEIVE FILE TEST ===\n");

        try (PesitSession session =
                new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT))) {
            // Step 1: CONNECT with read access
            System.out.println("Step 1: CONNECT (read access)");
            int clientConnectionId = 0x05;

            String serverId = System.getProperty("pesit.test.server", "CETOM1");
            ConnectMessageBuilder connectBuilder =
                    new ConnectMessageBuilder().demandeur("LOOP").serveur(serverId).readAccess();

            Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(clientConnectionId));
            assertEquals(FpduType.ACONNECT, aconnect.getFpduType(), "Expected ACONNECT");

            int serverConnectionId = aconnect.getIdSrc();
            System.out.println("  ✓ Session established, server ID: " + serverConnectionId);

            // Step 2: SELECT - request existing file
            System.out.println("\nStep 2: SELECT");
            String filename = System.getProperty("pesit.test.file", "FOUT");

            SelectMessageBuilder selectBuilder =
                    new SelectMessageBuilder().filename(filename).transferId(1);

            Fpdu ackSelect = session.sendFpduWithAck(selectBuilder.build(serverConnectionId));
            assertEquals(FpduType.ACK_SELECT, ackSelect.getFpduType(), "Expected ACK_SELECT");
            System.out.println("  ✓ SELECT successful for file: " + filename);

            // Step 3: OPEN
            System.out.println("\nStep 3: OPEN");
            Fpdu ackOpen =
                    session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnectionId));
            assertEquals(FpduType.ACK_OPEN, ackOpen.getFpduType(), "Expected ACK_OPEN");
            System.out.println("  ✓ File opened");

            // Step 4: READ - request data (PI_18 restart point = 0 for start)
            System.out.println("\nStep 4: READ");
            Fpdu ackRead =
                    session.sendFpduWithAck(
                            new Fpdu(FpduType.READ)
                                    .withIdDst(serverConnectionId)
                                    .withParameter(new ParameterValue(PI_18_POINT_RELANCE, 0)));
            assertEquals(FpduType.ACK_READ, ackRead.getFpduType(), "Expected ACK_READ");
            System.out.println("  ✓ Read initiated");

            // Step 5: Receive DTF data until DTF.END
            System.out.println("\nStep 5: Receiving data...");
            ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
            int dtfCount = 0;

            while (true) {
                Fpdu fpdu = session.receiveFpdu();
                FpduType type = fpdu.getFpduType();
                // Handle all DTF variants: DTF, DTFDA, DTFMA, DTFFA
                if (type == FpduType.DTF
                        || type == FpduType.DTFDA
                        || type == FpduType.DTFMA
                        || type == FpduType.DTFFA) {
                    byte[] data = fpdu.getData();
                    if (data != null && data.length > 0) {
                        dataBuffer.write(data);
                        dtfCount++;
                        if (dtfCount % 1000 == 0) {
                            System.out.println(
                                    "  ... received "
                                            + dtfCount
                                            + " chunks, "
                                            + dataBuffer.size()
                                            + " bytes");
                        }
                    }
                } else if (type == FpduType.DTF_END) {
                    System.out.println("  ✓ Received DTF.END after " + dtfCount + " DTF packets");
                    break;
                } else {
                    System.out.println("  Unexpected FPDU: " + type);
                    break;
                }
            }

            byte[] receivedData = dataBuffer.toByteArray();
            System.out.println("  ✓ Received " + receivedData.length + " bytes total");

            // Step 6: TRANS.END
            System.out.println("\nStep 6: TRANS.END");
            Fpdu ackTransEnd =
                    session.sendFpduWithAck(
                            new Fpdu(FpduType.TRANS_END).withIdDst(serverConnectionId));
            assertEquals(
                    FpduType.ACK_TRANS_END, ackTransEnd.getFpduType(), "Expected ACK_TRANS_END");
            System.out.println("  ✓ Transfer completed");

            // Step 7: CLOSE
            System.out.println("\nStep 7: CLOSE");
            Fpdu ackClose =
                    session.sendFpduWithAck(
                            new Fpdu(FpduType.CLOSE)
                                    .withIdDst(serverConnectionId)
                                    .withParameter(
                                            new ParameterValue(
                                                    PI_02_DIAG, new byte[] {0x00, 0x00, 0x00})));
            assertEquals(FpduType.ACK_CLOSE, ackClose.getFpduType(), "Expected ACK_CLOSE");
            System.out.println("  ✓ File closed");

            // Step 8: DESELECT
            System.out.println("\nStep 8: DESELECT");
            Fpdu ackDeselect =
                    session.sendFpduWithAck(
                            new Fpdu(FpduType.DESELECT)
                                    .withIdDst(serverConnectionId)
                                    .withParameter(
                                            new ParameterValue(
                                                    PI_02_DIAG, new byte[] {0x00, 0x00, 0x00})));
            assertEquals(FpduType.ACK_DESELECT, ackDeselect.getFpduType(), "Expected ACK_DESELECT");
            System.out.println("  ✓ File deselected");

            // Step 9: RELEASE
            System.out.println("\nStep 9: RELEASE");
            Fpdu relconf =
                    session.sendFpduWithAck(
                            new Fpdu(FpduType.RELEASE)
                                    .withIdDst(serverConnectionId)
                                    .withIdSrc(clientConnectionId)
                                    .withParameter(
                                            new ParameterValue(
                                                    PI_02_DIAG, new byte[] {0x00, 0x00, 0x00})));
            assertEquals(FpduType.RELCONF, relconf.getFpduType(), "Expected RELCONF");
            System.out.println("  ✓ Session closed");

            System.out.println("\n✓✓✓ RECEIVE FILE TEST SUCCESSFUL! ✓✓✓");
            System.out.println("Received " + receivedData.length + " bytes");
            if (receivedData.length > 0 && receivedData.length < 200) {
                System.out.println("Content: " + new String(receivedData));
            }
        }
    }

    @Test
    @DisplayName("Receive file with SYNC, interrupt, and restart")
    void testReceiveFileWithSyncAndRestart()
            throws IOException, InterruptedException, NoSuchAlgorithmException {
        System.out.println("\n=== RECEIVE FILE WITH SYNC & RESTART TEST ===\n");

        String filename = System.getProperty("pesit.test.file", "BIG");
        String originalFilePath =
                System.getProperty(
                        "pesit.test.original",
                        "/home/cpo/pesit-data/client/Slow.Horses.S02E01.Last.Stop.1080p.H.265.mp4");
        File outputFile = new File("/tmp/pesit_test_" + filename + ".dat");

        // Delete any previous test file
        if (outputFile.exists()) {
            outputFile.delete();
        }

        int lastSyncPoint = 0;
        long lastSyncBytePosition = 0;
        int serverConnectionId = 0;
        int clientConnectionId = 0x05;
        String serverId = System.getProperty("pesit.test.server", "CETOM1");

        // PHASE 1: Start transfer, receive some data with sync points, then interrupt
        System.out.println(
                "=== PHASE 1: Start transfer, receive until sync point 3, then interrupt ===\n");

        try (PesitSession session =
                        new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT));
                RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {

            // CONNECT with sync points enabled
            System.out.println("Step 1: CONNECT (read access, sync points enabled)");
            ConnectMessageBuilder connectBuilder =
                    new ConnectMessageBuilder()
                            .demandeur("LOOP")
                            .serveur(serverId)
                            .readAccess()
                            .syncIntervalKb(4096) // Sync every 4 MB (larger = less overhead)
                            .syncAckWindow(1); // Ack window of 1
            Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(clientConnectionId));
            assertEquals(FpduType.ACONNECT, aconnect.getFpduType());
            serverConnectionId = aconnect.getIdSrc();
            System.out.println("  ✓ Connected, server ID: " + serverConnectionId);

            // SELECT
            System.out.println("\nStep 2: SELECT");
            SelectMessageBuilder selectBuilder =
                    new SelectMessageBuilder().filename(filename).transferId(1);
            Fpdu ackSelect = session.sendFpduWithAck(selectBuilder.build(serverConnectionId));
            assertEquals(FpduType.ACK_SELECT, ackSelect.getFpduType());
            System.out.println("  ✓ Selected file: " + filename);

            // OPEN
            System.out.println("\nStep 3: OPEN");
            Fpdu ackOpen =
                    session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnectionId));
            assertEquals(FpduType.ACK_OPEN, ackOpen.getFpduType());
            System.out.println("  ✓ File opened");

            // READ from start
            System.out.println("\nStep 4: READ (from start)");
            Fpdu ackRead =
                    session.sendFpduWithAck(
                            new Fpdu(FpduType.READ)
                                    .withIdDst(serverConnectionId)
                                    .withParameter(new ParameterValue(PI_18_POINT_RELANCE, 0)));
            assertEquals(FpduType.ACK_READ, ackRead.getFpduType());
            System.out.println("  ✓ Read initiated");

            // Receive data until we hit sync point 3, then interrupt
            System.out.println("\nStep 5: Receiving data until sync point 3...");
            long totalBytes = 0;
            int targetSyncPoint = 3;
            boolean interrupted = false;

            while (!interrupted) {
                Fpdu fpdu = session.receiveFpdu();
                FpduType type = fpdu.getFpduType();

                if (type == FpduType.DTF
                        || type == FpduType.DTFDA
                        || type == FpduType.DTFMA
                        || type == FpduType.DTFFA) {
                    byte[] data = fpdu.getData();
                    if (data != null && data.length > 0) {
                        raf.write(data);
                        totalBytes += data.length;
                    }
                } else if (type == FpduType.SYN) {
                    // Parse sync point number
                    ParameterValue syncNum = fpdu.getParameter(PI_20_NUM_SYNC);
                    if (syncNum != null
                            && syncNum.getValue() != null
                            && syncNum.getValue().length > 0) {
                        lastSyncPoint = syncNum.getValue()[0] & 0xFF;
                    } else {
                        lastSyncPoint++;
                    }
                    lastSyncBytePosition = totalBytes;
                    System.out.println(
                            "  SYN #" + lastSyncPoint + " at byte " + lastSyncBytePosition);

                    // Send ACK_SYN
                    session.sendFpdu(
                            new Fpdu(FpduType.ACK_SYN)
                                    .withParameter(
                                            new ParameterValue(PI_20_NUM_SYNC, lastSyncPoint))
                                    .withIdDst(serverConnectionId));

                    // Interrupt after reaching target sync point by closing connection abruptly
                    if (lastSyncPoint >= targetSyncPoint) {
                        System.out.println(
                                "\n  >>> Interrupting after sync point " + lastSyncPoint + " <<<");
                        System.out.println(
                                "  (Closing connection abruptly to simulate network failure)");
                        interrupted = true;
                        // Just break out - the try-with-resources will close the connection
                    }
                } else if (type == FpduType.DTF_END) {
                    System.out.println("  DTF_END received (file completed before target sync)");
                    break;
                } else {
                    System.out.println("  Received: " + type);
                }
            }

            System.out.println(
                    "\n  Phase 1 complete: " + totalBytes + " bytes, last sync: " + lastSyncPoint);
        }

        System.out.println("\n=== PHASE 2: Resume from sync point " + lastSyncPoint + " ===\n");

        // PHASE 2: Resume transfer from last sync point
        try (PesitSession session =
                        new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT));
                RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {

            // Seek to last sync position
            raf.seek(lastSyncBytePosition);
            System.out.println("  Seeking to byte position: " + lastSyncBytePosition);

            // CONNECT with sync points enabled
            System.out.println("\nStep 1: CONNECT (read access, sync points enabled)");
            ConnectMessageBuilder connectBuilder =
                    new ConnectMessageBuilder()
                            .demandeur("LOOP")
                            .serveur(serverId)
                            .readAccess()
                            .syncIntervalKb(4096)
                            .syncAckWindow(1);
            Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(clientConnectionId));
            assertEquals(FpduType.ACONNECT, aconnect.getFpduType());
            serverConnectionId = aconnect.getIdSrc();
            System.out.println("  ✓ Connected, server ID: " + serverConnectionId);

            // SELECT with restart flag
            System.out.println("\nStep 2: SELECT (restart)");
            SelectMessageBuilder selectBuilder =
                    new SelectMessageBuilder().filename(filename).transferId(1).restart();
            Fpdu ackSelect = session.sendFpduWithAck(selectBuilder.build(serverConnectionId));
            assertEquals(FpduType.ACK_SELECT, ackSelect.getFpduType());
            System.out.println("  ✓ Selected file: " + filename);

            // OPEN
            System.out.println("\nStep 3: OPEN");
            Fpdu ackOpen =
                    session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnectionId));
            assertEquals(FpduType.ACK_OPEN, ackOpen.getFpduType());
            System.out.println("  ✓ File opened");

            // READ with restart point
            System.out.println("\nStep 4: READ (restart from sync point " + lastSyncPoint + ")");
            Fpdu ackRead =
                    session.sendFpduWithAck(
                            new Fpdu(FpduType.READ)
                                    .withIdDst(serverConnectionId)
                                    .withParameter(
                                            new ParameterValue(
                                                    PI_18_POINT_RELANCE, lastSyncPoint)));
            assertEquals(FpduType.ACK_READ, ackRead.getFpduType());
            System.out.println("  ✓ Read resumed from sync point " + lastSyncPoint);

            // Receive remaining data
            System.out.println("\nStep 5: Receiving remaining data...");
            long totalBytes = lastSyncBytePosition;
            int dtfCount = 0;

            while (true) {
                Fpdu fpdu = session.receiveFpdu();
                FpduType type = fpdu.getFpduType();

                if (type == FpduType.DTF
                        || type == FpduType.DTFDA
                        || type == FpduType.DTFMA
                        || type == FpduType.DTFFA) {
                    byte[] data = fpdu.getData();
                    if (data != null && data.length > 0) {
                        raf.write(data);
                        totalBytes += data.length;
                        dtfCount++;
                        if (dtfCount % 1000 == 0) {
                            System.out.println(
                                    "  ... " + dtfCount + " chunks, " + totalBytes + " bytes");
                        }
                    }
                } else if (type == FpduType.SYN) {
                    ParameterValue syncNum = fpdu.getParameter(PI_20_NUM_SYNC);
                    int syncPoint =
                            (syncNum != null
                                            && syncNum.getValue() != null
                                            && syncNum.getValue().length > 0)
                                    ? syncNum.getValue()[0] & 0xFF
                                    : lastSyncPoint + 1;
                    lastSyncPoint = syncPoint;
                    System.out.println("  SYN #" + syncPoint + " at byte " + totalBytes);
                    session.sendFpdu(
                            new Fpdu(FpduType.ACK_SYN)
                                    .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncPoint))
                                    .withIdDst(serverConnectionId));
                } else if (type == FpduType.DTF_END) {
                    System.out.println("  ✓ DTF_END received after " + dtfCount + " DTF packets");
                    break;
                } else {
                    System.out.println("  Received: " + type);
                }
            }

            // Cleanup: TRANS_END, CLOSE, DESELECT, RELEASE
            System.out.println("\nStep 6: Cleanup");
            session.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(serverConnectionId));
            session.sendFpduWithAck(
                    new Fpdu(FpduType.CLOSE)
                            .withIdDst(serverConnectionId)
                            .withParameter(
                                    new ParameterValue(PI_02_DIAG, new byte[] {0x00, 0x00, 0x00})));
            session.sendFpduWithAck(
                    new Fpdu(FpduType.DESELECT)
                            .withIdDst(serverConnectionId)
                            .withParameter(
                                    new ParameterValue(PI_02_DIAG, new byte[] {0x00, 0x00, 0x00})));
            session.sendFpduWithAck(
                    new Fpdu(FpduType.RELEASE)
                            .withIdDst(serverConnectionId)
                            .withIdSrc(clientConnectionId)
                            .withParameter(
                                    new ParameterValue(PI_02_DIAG, new byte[] {0x00, 0x00, 0x00})));
            System.out.println("  ✓ Session closed properly");

            System.out.println("\n  Total bytes received: " + totalBytes);
        }

        // PHASE 3: Verify file integrity
        System.out.println("\n=== PHASE 3: Verify file integrity ===\n");

        File originalFile = new File(originalFilePath);
        if (originalFile.exists()) {
            byte[] originalHash = computeMD5(originalFile);
            byte[] receivedHash = computeMD5(outputFile);

            System.out.println("Original file: " + originalFile.length() + " bytes");
            System.out.println("Received file: " + outputFile.length() + " bytes");
            System.out.println("Original MD5:  " + bytesToHex(originalHash));
            System.out.println("Received MD5:  " + bytesToHex(receivedHash));

            assertArrayEquals(originalHash, receivedHash, "File checksums should match!");
            assertEquals(originalFile.length(), outputFile.length(), "File sizes should match!");
            System.out.println("\n✓✓✓ FILE INTEGRITY VERIFIED! ✓✓✓");
        } else {
            System.out.println("Original file not found at: " + originalFilePath);
            System.out.println(
                    "Received file: "
                            + outputFile.length()
                            + " bytes at "
                            + outputFile.getAbsolutePath());
        }
    }

    private byte[] computeMD5(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (var fis = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        return md.digest();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
