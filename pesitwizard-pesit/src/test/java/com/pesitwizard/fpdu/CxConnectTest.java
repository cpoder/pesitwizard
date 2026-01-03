package com.pesitwizard.fpdu;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TcpTransportChannel;

/**
 * Test script for CX server using PesitSession and TcpTransportChannel.
 * Run with: mvn exec:java -Dexec.mainClass="com.pesitwizard.fpdu.CxConnectTest"
 * -Dexec.classpathScope=test -pl pesitwizard-pesit
 */
public class CxConnectTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5100;
    private static final String DEMANDEUR = "LOOP";
    private static final String SERVEUR = "CETOM1";

    public static void main(String[] args) throws Exception {
        // Test PULL (read) transfer with interruption and resume
        testPullWithResume();
    }

    /**
     * Test 1MB transfer using PesitSession and TcpTransportChannel abstractions.
     * Much cleaner than raw socket manipulation.
     */
    private static void testWithPesitSession() {
        System.out.println("\n=== Test: 1MB transfer using PesitSession ===");

        // Generate 1MB of test data
        int totalDataSize = 1024 * 1024; // 1MB
        byte[] fullData = new byte[totalDataSize];
        for (int i = 0; i < totalDataSize; i++) {
            fullData[i] = (byte) ('A' + (i % 26));
        }

        int articleSize = 30 * 1024; // 30KB articles
        int syncIntervalKB = 256; // Proposed - server will negotiate

        System.out.println("Total data: " + (totalDataSize / 1024) + " KB");
        System.out.println("Article size: " + articleSize + " bytes");

        TcpTransportChannel channel = new TcpTransportChannel(HOST, PORT);
        try (PesitSession session = new PesitSession(channel)) {

            // 1. CONNECT with sync points
            byte[] pi7Value = new byte[] {
                    (byte) ((syncIntervalKB >> 8) & 0xFF),
                    (byte) (syncIntervalKB & 0xFF),
                    1 // window = 1
            };
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_03_DEMANDEUR, DEMANDEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_04_SERVEUR, SERVEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_07_SYNC_POINTS, pi7Value))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 0))
                    .withIdSrc(1).withIdDst(0);

            Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
            int serverConnId = aconnect.getIdSrc();
            System.out.println("Connected, server ID: " + serverConnId);

            // Get negotiated sync interval
            ParameterValue pi7Response = aconnect.getParameter(ParameterIdentifier.PI_07_SYNC_POINTS);
            int syncIntervalBytes = syncIntervalKB * 1024;
            if (pi7Response != null) {
                byte[] pi7Bytes = pi7Response.getValue();
                syncIntervalKB = ((pi7Bytes[0] & 0xFF) << 8) | (pi7Bytes[1] & 0xFF);
                syncIntervalBytes = syncIntervalKB * 1024;
                System.out.println("Negotiated sync interval: " + syncIntervalKB + " KB");
            }

            // 2. CREATE
            Fpdu createFpdu = new CreateMessageBuilder()
                    .filename("FILE")
                    .transferId(1)
                    .variableFormat()
                    .recordLength(articleSize)
                    .maxEntitySize(65535)
                    .fileSizeKB(totalDataSize / 1024)
                    .build(serverConnId);
            session.sendFpduWithAck(createFpdu);
            System.out.println("CREATE accepted");

            // 3. OPEN
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));

            // 4. WRITE
            session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

            // 5. Send data with sync points
            int offset = 0;
            int syncCount = 0;
            int bytesSinceSync = 0;

            while (offset < totalDataSize) {
                int chunkSize = Math.min(articleSize, totalDataSize - offset);

                // Send SYN before exceeding interval
                if (bytesSinceSync + chunkSize > syncIntervalBytes) {
                    syncCount++;
                    Fpdu synFpdu = new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(ParameterIdentifier.PI_20_NUM_SYNC,
                                    new byte[] { (byte) syncCount }));
                    session.sendFpduWithAck(synFpdu);
                    bytesSinceSync = 0;
                }

                // Send DTF
                byte[] article = new byte[chunkSize];
                System.arraycopy(fullData, offset, article, 0, chunkSize);
                session.sendFpduWithData(new Fpdu(FpduType.DTF).withIdDst(serverConnId), article);

                offset += chunkSize;
                bytesSinceSync += chunkSize;
            }
            System.out.println("Sent " + syncCount + " sync points");

            // 6. Complete transfer
            session.sendFpdu(new Fpdu(FpduType.DTF_END).withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
            session.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId));
            session.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
            session.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
            session.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(serverConnId).withIdSrc(1)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));

            System.out.println("\n✓ SUCCESS - 1MB transfer with " + syncCount + " sync points completed!");

        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test PULL (read) transfer with interruption and resume.
     * Phase 1: SELECT file BIG, READ, receive data, interrupt after some sync
     * points
     * Phase 2: Reconnect with SELECT(PI 15=1), get restart point from ACK_READ(PI
     * 18)
     */
    private static void testPullWithResume() {
        System.out.println("\n=== Test: PULL transfer with interruption and resume ===");

        int interruptAfterSyncPoints = 5;
        int transferId = (int) (System.currentTimeMillis() % 100000);
        System.out.println("Using transfer ID: " + transferId);

        int lastAckedSyncPoint = 0;
        long bytesReceived = 0;
        int serverTransferId = 0; // Server assigns transfer ID for READ

        // ========== PHASE 1: Start PULL and interrupt ==========
        System.out
                .println("\n--- PHASE 1: Start PULL, interrupt after " + interruptAfterSyncPoints + " sync points ---");

        TcpTransportChannel channel1 = new TcpTransportChannel(HOST, PORT);
        try (PesitSession session = new PesitSession(channel1)) {

            // 1. CONNECT in READ mode with sync points (like ReceiveFileTest but with sync)
            int clientConnectionId = 0x05;
            Fpdu connectFpdu = new ConnectMessageBuilder()
                    .demandeur(DEMANDEUR)
                    .serveur(SERVEUR)
                    .readAccess()
                    .syncIntervalKb(256)
                    .syncAckWindow(1)
                    .build(clientConnectionId);
            Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
            int serverConnId = aconnect.getIdSrc();
            System.out.println("Connected in READ mode, server ID: " + serverConnId);

            // 2. SELECT file BIG
            Fpdu selectFpdu = new SelectMessageBuilder()
                    .filename("BIG")
                    .transferId(0) // 0 for new transfer, server assigns ID
                    .build(serverConnId);
            Fpdu ackSelect = session.sendFpduWithAck(selectFpdu);
            System.out.println("SELECT accepted: " + ackSelect);

            // Get server-assigned transfer ID from ACK_SELECT
            ParameterValue pi13 = ackSelect.getParameter(ParameterIdentifier.PI_13_ID_TRANSFERT);
            if (pi13 != null) {
                serverTransferId = bytesToInt(pi13.getValue());
                System.out.println("Server assigned transfer ID: " + serverTransferId);
            }

            // 3. OPEN
            Fpdu openFpdu = new Fpdu(FpduType.OPEN).withIdDst(serverConnId);
            session.sendFpduWithAck(openFpdu);
            System.out.println("OPEN accepted");

            // 4. READ - request data from restart point 0
            Fpdu readFpdu = new Fpdu(FpduType.READ)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_18_POINT_RELANCE, 0))
                    .withIdDst(serverConnId);
            session.sendFpduWithAck(readFpdu);
            System.out.println("READ accepted, receiving data...");

            // 5. Receive DTF packets and SYN until interrupt
            int syncPointCount = 0;
            while (syncPointCount < interruptAfterSyncPoints) {
                Fpdu received = session.receiveFpdu();
                FpduType type = received.getFpduType();

                // DTF types: DTF, DTFDA, DTFMA, DTFFA
                if (type == FpduType.DTF || type == FpduType.DTFDA
                        || type == FpduType.DTFMA || type == FpduType.DTFFA) {
                    byte[] data = received.getData();
                    if (data != null) {
                        bytesReceived += data.length;
                    }
                } else if (type == FpduType.SYN) {
                    syncPointCount++;
                    ParameterValue syncNum = received.getParameter(ParameterIdentifier.PI_20_NUM_SYNC);
                    int syncPoint = syncNum != null ? bytesToInt(syncNum.getValue()) : syncPointCount;
                    lastAckedSyncPoint = syncPoint;
                    System.out.println("SYN #" + syncPoint + " received, bytes so far: " + bytesReceived);

                    // Send ACK_SYN
                    Fpdu ackSyn = new Fpdu(FpduType.ACK_SYN)
                            .withParameter(new ParameterValue(ParameterIdentifier.PI_20_NUM_SYNC, syncPoint))
                            .withIdDst(serverConnId);
                    session.sendFpdu(ackSyn);
                } else if (received.getFpduType() == FpduType.CLOSE) {
                    System.out.println("Server sent CLOSE - file transfer complete");
                    break;
                }
            }

            System.out.println("\n*** INTERRUPTING after " + syncPointCount + " sync points ***");
            System.out.println("Bytes received: " + bytesReceived);

        } catch (Exception e) {
            System.out.println("Phase 1 error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Last acked sync point: " + lastAckedSyncPoint);
        System.out.println("Server transfer ID: " + serverTransferId);

        // Wait for server to process
        System.out.println("Waiting 5 seconds for CX to release resources...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }

        // ========== PHASE 2: Resume PULL ==========
        System.out.println("\n--- PHASE 2: Resume PULL from sync point " + lastAckedSyncPoint + " ---");

        TcpTransportChannel channel2 = new TcpTransportChannel(HOST, PORT);
        try (PesitSession session = new PesitSession(channel2)) {

            // 1. CONNECT in READ mode with sync points
            int clientConnectionId = 0x05;
            Fpdu connectFpdu = new ConnectMessageBuilder()
                    .demandeur(DEMANDEUR)
                    .serveur(SERVEUR)
                    .readAccess()
                    .syncIntervalKb(256)
                    .syncAckWindow(1)
                    .build(clientConnectionId);
            Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
            int serverConnId = aconnect.getIdSrc();
            System.out.println("Reconnected, server ID: " + serverConnId);

            // 2. SELECT with restart flag and same transfer ID
            Fpdu selectFpdu = new SelectMessageBuilder()
                    .filename("BIG")
                    .transferId(serverTransferId) // Use server's transfer ID
                    .restart() // PI 15 = 1
                    .build(serverConnId);
            System.out.println("Sending SELECT with restart, transferId=" + serverTransferId);
            Fpdu ackSelect = session.sendFpduWithAck(selectFpdu);
            System.out.println("SELECT restart accepted: " + ackSelect);

            // 3. OPEN
            Fpdu openFpdu = new Fpdu(FpduType.OPEN).withIdDst(serverConnId);
            session.sendFpduWithAck(openFpdu);
            System.out.println("OPEN accepted");

            // 4. READ - server will tell us restart point via ACK_READ PI 18
            Fpdu readFpdu = new Fpdu(FpduType.READ)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_18_POINT_RELANCE, lastAckedSyncPoint))
                    .withIdDst(serverConnId);
            Fpdu ackRead = session.sendFpduWithAck(readFpdu);

            ParameterValue pi18 = ackRead.getParameter(ParameterIdentifier.PI_18_POINT_RELANCE);
            int restartPoint = pi18 != null ? bytesToInt(pi18.getValue()) : 0;
            System.out.println("ACK_READ restart point (PI 18): " + restartPoint);

            if (restartPoint > 0) {
                System.out.println("*** SUCCESS! Server accepted restart from sync point " + restartPoint + " ***");
            }

            // 5. Receive remaining data
            long bytesInPhase2 = 0;
            int syncPointCount = 0;
            int maxSyncPoints = 10; // Limit for testing

            while (syncPointCount < maxSyncPoints) {
                Fpdu received = session.receiveFpdu();
                FpduType type = received.getFpduType();

                // DTF types: DTF, DTFDA, DTFMA, DTFFA
                if (type == FpduType.DTF || type == FpduType.DTFDA
                        || type == FpduType.DTFMA || type == FpduType.DTFFA) {
                    byte[] data = received.getData();
                    if (data != null) {
                        bytesInPhase2 += data.length;
                    }
                } else if (type == FpduType.SYN) {
                    syncPointCount++;
                    ParameterValue syncNum = received.getParameter(ParameterIdentifier.PI_20_NUM_SYNC);
                    int syncPoint = syncNum != null ? bytesToInt(syncNum.getValue()) : syncPointCount;
                    System.out.println("SYN #" + syncPoint + " received");

                    // Send ACK_SYN
                    Fpdu ackSyn = new Fpdu(FpduType.ACK_SYN)
                            .withParameter(new ParameterValue(ParameterIdentifier.PI_20_NUM_SYNC, syncPoint))
                            .withIdDst(serverConnId);
                    session.sendFpdu(ackSyn);
                } else if (received.getFpduType() == FpduType.CLOSE) {
                    System.out.println("Transfer complete!");
                    break;
                }
            }

            System.out.println("Phase 2 bytes received: " + bytesInPhase2);
            System.out.println("Total bytes: " + (bytesReceived + bytesInPhase2));

        } catch (Exception e) {
            System.out.println("Phase 2 ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test transfer with interruption (socket close) and resume using PI 15.
     * Phase 1: Start transfer, send some data with sync points, then close socket
     * Phase 2: Reconnect with CREATE(PI 15=1), get restart point from ACK_WRITE(PI
     * 18)
     */
    private static void testTransferWithResume() {
        System.out.println("\n=== Test: Transfer with interruption and resume ===");

        // Generate 512KB of test data (smaller for faster testing)
        int totalDataSize = 512 * 1024;
        byte[] fullData = new byte[totalDataSize];
        for (int i = 0; i < totalDataSize; i++) {
            fullData[i] = (byte) ('A' + (i % 26));
        }

        int articleSize = 30 * 1024; // 30KB articles
        int syncIntervalKB = 256;
        int interruptAfterSyncPoints = 5; // Interrupt after 5 sync points
        int transferId = (int) (System.currentTimeMillis() % 100000); // Unique ID for each test run
        System.out.println("Using transfer ID: " + transferId);
        String creationDate = "250102120000"; // Fixed date - MUST be same in Phase 1 and Phase 2!

        System.out.println("Total data: " + (totalDataSize / 1024) + " KB");
        System.out.println("Article size: " + articleSize + " bytes");
        System.out.println("Will interrupt after " + interruptAfterSyncPoints + " sync points");

        int lastAckedSyncPoint = 0;
        int bytesAtLastSync = 0;

        // ========== PHASE 1: Start transfer and interrupt ==========
        System.out.println(
                "\n--- PHASE 1: Start transfer, interrupt after " + interruptAfterSyncPoints + " sync points ---");

        TcpTransportChannel channel1 = new TcpTransportChannel(HOST, PORT);
        try (PesitSession session = new PesitSession(channel1)) {

            // 1. CONNECT with sync points
            byte[] pi7Value = new byte[] {
                    (byte) ((syncIntervalKB >> 8) & 0xFF),
                    (byte) (syncIntervalKB & 0xFF),
                    1 // window = 1
            };
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_03_DEMANDEUR, DEMANDEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_04_SERVEUR, SERVEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_07_SYNC_POINTS, pi7Value))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 0))
                    .withIdSrc(1).withIdDst(0);

            Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
            int serverConnId = aconnect.getIdSrc();
            System.out.println("Connected, server ID: " + serverConnId);

            // Get negotiated sync interval
            ParameterValue pi7Response = aconnect.getParameter(ParameterIdentifier.PI_07_SYNC_POINTS);
            int syncIntervalBytes = syncIntervalKB * 1024;
            if (pi7Response != null) {
                byte[] pi7Bytes = pi7Response.getValue();
                syncIntervalKB = ((pi7Bytes[0] & 0xFF) << 8) | (pi7Bytes[1] & 0xFF);
                syncIntervalBytes = syncIntervalKB * 1024;
                System.out.println("Negotiated sync interval: " + syncIntervalKB + " KB");
            }

            // 2. CREATE (new transfer)
            Fpdu createFpdu = new CreateMessageBuilder()
                    .filename("FILE")
                    .transferId(transferId)
                    .creationDate(creationDate) // Fixed date for resume matching
                    .variableFormat()
                    .recordLength(articleSize)
                    .maxEntitySize(65535)
                    .fileSizeKB(totalDataSize / 1024)
                    .build(serverConnId);
            // Debug: print PI 13 value
            ParameterValue pi13 = createFpdu.getParameter(ParameterIdentifier.PI_13_ID_TRANSFERT);
            System.out.println("Phase 1 - PI 13 (transferId): " + bytesToHex(pi13.getValue()));
            session.sendFpduWithAck(createFpdu);
            System.out.println("CREATE accepted");

            // 3. OPEN
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));

            // 4. WRITE
            session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

            // 5. Send data with sync points until interrupt
            int offset = 0;
            int syncCount = 0;
            int bytesSinceSync = 0;

            while (offset < totalDataSize) {
                int chunkSize = Math.min(articleSize, totalDataSize - offset);

                // Send SYN before exceeding interval
                if (bytesSinceSync + chunkSize > syncIntervalBytes) {
                    syncCount++;
                    Fpdu synFpdu = new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(ParameterIdentifier.PI_20_NUM_SYNC,
                                    new byte[] { (byte) syncCount }));
                    session.sendFpduWithAck(synFpdu);
                    System.out.println("SYN #" + syncCount + " acked at offset " + offset);

                    lastAckedSyncPoint = syncCount;
                    bytesAtLastSync = offset;
                    bytesSinceSync = 0;

                    // Check if we should interrupt
                    if (syncCount >= interruptAfterSyncPoints) {
                        System.out.println("\n*** INTERRUPTING after " + syncCount + " sync points ***");
                        System.out.println("Bytes sent: " + offset + " / " + totalDataSize);
                        // Just break and let socket close - simulating network failure
                        break;
                    }
                }

                // Send DTF
                byte[] article = new byte[chunkSize];
                System.arraycopy(fullData, offset, article, 0, chunkSize);
                session.sendFpduWithData(new Fpdu(FpduType.DTF).withIdDst(serverConnId), article);

                offset += chunkSize;
                bytesSinceSync += chunkSize;
            }

        } catch (Exception e) {
            System.out.println("Phase 1 error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Last acked sync point: " + lastAckedSyncPoint);
        System.out.println("Bytes at last sync: " + bytesAtLastSync);

        // Wait for server to process and release resources
        System.out.println("Waiting 5 seconds for CX to release resources...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }

        // ========== PHASE 2: Resume transfer ==========
        System.out.println("\n--- PHASE 2: Resume from sync point " + lastAckedSyncPoint + " ---");

        TcpTransportChannel channel2 = new TcpTransportChannel(HOST, PORT);
        try (PesitSession session = new PesitSession(channel2)) {

            // 1. CONNECT (normal, without PI 23)
            byte[] pi7Value = new byte[] {
                    (byte) ((syncIntervalKB >> 8) & 0xFF),
                    (byte) (syncIntervalKB & 0xFF),
                    1
            };
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_03_DEMANDEUR, DEMANDEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_04_SERVEUR, SERVEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_07_SYNC_POINTS, pi7Value))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 0))
                    .withIdSrc(1).withIdDst(0);

            Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
            int serverConnId = aconnect.getIdSrc();
            System.out.println("Reconnected, server ID: " + serverConnId);

            // Get negotiated sync interval
            ParameterValue pi7Response = aconnect.getParameter(ParameterIdentifier.PI_07_SYNC_POINTS);
            int syncIntervalBytes = syncIntervalKB * 1024;
            if (pi7Response != null) {
                byte[] pi7Bytes = pi7Response.getValue();
                syncIntervalKB = ((pi7Bytes[0] & 0xFF) << 8) | (pi7Bytes[1] & 0xFF);
                syncIntervalBytes = syncIntervalKB * 1024;
            }

            // 2. CREATE with PI 15 = 1 (restart) and SAME parameters as Phase 1
            Fpdu createFpdu = new CreateMessageBuilder()
                    .filename("FILE")
                    .transferId(transferId)
                    .creationDate(creationDate) // MUST match Phase 1!
                    .variableFormat()
                    .recordLength(articleSize)
                    .maxEntitySize(65535)
                    .fileSizeKB(totalDataSize / 1024)
                    .restart() // PI 15 = 1
                    .build(serverConnId);
            // Debug: print PI 13 value
            ParameterValue pi13 = createFpdu.getParameter(ParameterIdentifier.PI_13_ID_TRANSFERT);
            System.out.println("Phase 2 - PI 13 (transferId): " + bytesToHex(pi13.getValue()));
            session.sendFpduWithAck(createFpdu);
            System.out.println("CREATE (restart with PI 15) accepted");

            // 3. OPEN - try skipping for resume?
            try {
                session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));
                System.out.println("OPEN accepted");
            } catch (Exception e) {
                System.out.println("OPEN failed: " + e.getMessage() + " - trying direct WRITE...");
            }

            // 4. WRITE - server should return PI 18 with restart point
            Fpdu ackWrite = session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

            // Parse PI 18 from ackWrite
            int restartPoint = 0;
            ParameterValue pi18 = ackWrite.getParameter(ParameterIdentifier.PI_18_POINT_RELANCE);
            if (pi18 != null) {
                byte[] pi18Bytes = pi18.getValue();
                for (byte b : pi18Bytes) {
                    restartPoint = (restartPoint << 8) | (b & 0xFF);
                }
                System.out.println("Server restart point (PI 18): " + restartPoint);
            } else {
                System.out.println("Server did not provide restart point, starting from beginning");
            }

            // Calculate offset based on restart point
            // Each sync point represents ~syncIntervalBytes of data
            int resumeOffset = restartPoint * syncIntervalBytes;
            if (resumeOffset > totalDataSize)
                resumeOffset = 0;
            System.out.println("Resuming from offset: " + resumeOffset);

            // 5. Continue sending data from resume point
            int offset = resumeOffset;
            int syncCount = restartPoint;
            int bytesSinceSync = 0;

            while (offset < totalDataSize) {
                int chunkSize = Math.min(articleSize, totalDataSize - offset);

                // Send SYN before exceeding interval
                if (bytesSinceSync + chunkSize > syncIntervalBytes) {
                    syncCount++;
                    Fpdu synFpdu = new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(ParameterIdentifier.PI_20_NUM_SYNC,
                                    new byte[] { (byte) syncCount }));
                    session.sendFpduWithAck(synFpdu);
                    bytesSinceSync = 0;
                }

                // Send DTF
                byte[] article = new byte[chunkSize];
                System.arraycopy(fullData, offset, article, 0, chunkSize);
                session.sendFpduWithData(new Fpdu(FpduType.DTF).withIdDst(serverConnId), article);

                offset += chunkSize;
                bytesSinceSync += chunkSize;
            }
            System.out.println("Data transfer complete, " + syncCount + " total sync points");

            // 6. Complete transfer
            session.sendFpdu(new Fpdu(FpduType.DTF_END).withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
            session.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId));
            session.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
            session.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
            session.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(serverConnId).withIdSrc(1)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));

            System.out.println("\n✓ SUCCESS - Transfer with interruption and resume completed!");

        } catch (Exception e) {
            System.out.println("Phase 2 ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test 1MB transfer with sync points, simulated interruption, and resume.
     * Phase 1: Start transfer, send some data with sync points, then ABORT
     * Phase 2: Resume from last sync point and complete
     */
    @SuppressWarnings("unused")
    private static void testSyncPointsWithResume() {
        System.out.println("\n=== Test: 1MB transfer with sync points and resume ===");

        // Generate 1MB of test data
        int totalDataSize = 1024 * 1024; // 1MB
        byte[] fullData = new byte[totalDataSize];
        for (int i = 0; i < totalDataSize; i++) {
            fullData[i] = (byte) ('A' + (i % 26));
        }

        // Article size must be <= sync interval for sync points to work
        // Server negotiates sync interval = 32KB, so use 30KB articles
        int articleSize = 30 * 1024; // 30KB articles (< 32KB sync interval)
        int syncIntervalKB = 256; // Proposed - server may negotiate lower (typically 32KB)
        int syncIntervalBytes = syncIntervalKB * 1024;
        int interruptAfterSyncPoint = -1; // -1 = no interruption, complete transfer

        System.out.println("Total data: " + (totalDataSize / 1024) + " KB");
        System.out.println("Article size (PI32): " + articleSize + " bytes");
        System.out.println("Sync interval: " + syncIntervalKB + " KB");
        System.out.println("Will interrupt after sync point: " + interruptAfterSyncPoint);

        int lastSyncPoint = 0;
        long bytesAtLastSync = 0;

        // ========== PHASE 1: Start transfer and interrupt ==========
        System.out.println(
                "\n--- PHASE 1: Start transfer, interrupt after " + interruptAfterSyncPoint + " sync points ---");

        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(30000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // 1. CONNECT with sync points enabled (PI 7)
            byte[] pi7Value = new byte[3];
            pi7Value[0] = (byte) ((syncIntervalKB >> 8) & 0xFF); // interval high byte
            pi7Value[1] = (byte) (syncIntervalKB & 0xFF); // interval low byte
            pi7Value[2] = 1; // window = 1

            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_03_DEMANDEUR, DEMANDEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_04_SERVEUR, SERVEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_07_SYNC_POINTS, pi7Value))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 0))
                    .withIdSrc(1)
                    .withIdDst(0);
            sendFpdu(out, connectFpdu, "CONNECT");
            Fpdu aconnect = readFpdu(in, "ACONNECT");
            if (aconnect.getFpduType() != FpduType.ACONNECT) {
                System.out.println("ERROR: Expected ACONNECT, got " + aconnect.getFpduType());
                return;
            }
            int serverConnId = aconnect.getIdSrc();
            System.out.println("Server connection ID: " + serverConnId);

            // Check negotiated PI 7 - USE SERVER'S VALUE!
            ParameterValue pi7Response = aconnect.getParameter(ParameterIdentifier.PI_07_SYNC_POINTS);
            if (pi7Response != null) {
                byte[] pi7Bytes = pi7Response.getValue();
                syncIntervalKB = ((pi7Bytes[0] & 0xFF) << 8) | (pi7Bytes[1] & 0xFF);
                syncIntervalBytes = syncIntervalKB * 1024;
                int window = pi7Bytes[2] & 0xFF;
                System.out.println("Server PI 7: interval=" + syncIntervalKB + "KB, window=" + window);
            }

            // 2. CREATE
            int proposedPi32 = articleSize;
            int proposedPi25 = 65535;
            Fpdu createFpdu = new CreateMessageBuilder()
                    .filename("FILE")
                    .transferId(1)
                    .variableFormat()
                    .recordLength(proposedPi32)
                    .maxEntitySize(proposedPi25)
                    .fileSizeKB(totalDataSize / 1024)
                    .build(serverConnId);
            sendFpdu(out, createFpdu, "CREATE");
            Fpdu ackCreate = readFpdu(in, "ACK_CREATE");
            if (!checkDiagnostic(ackCreate, "ACK_CREATE"))
                return;

            // 3. OPEN
            Fpdu openFpdu = new Fpdu(FpduType.OPEN).withIdDst(serverConnId);
            sendFpdu(out, openFpdu, "OPEN");
            if (!checkDiagnostic(readFpdu(in, "ACK_OPEN"), "ACK_OPEN"))
                return;

            // 4. WRITE
            Fpdu writeFpdu = new Fpdu(FpduType.WRITE).withIdDst(serverConnId);
            sendFpdu(out, writeFpdu, "WRITE");
            if (!checkDiagnostic(readFpdu(in, "ACK_WRITE"), "ACK_WRITE"))
                return;

            // 5. Send data with sync points
            // IMPORTANT: Sync point must be sent BEFORE exceeding the interval
            int offset = 0;
            int currentSyncPoint = 0;
            int bytesSinceLastSync = 0;

            while (offset < totalDataSize) {
                int chunkSize = Math.min(articleSize, totalDataSize - offset);

                // Check if sending this chunk would exceed sync interval - send SYN first!
                if (bytesSinceLastSync + chunkSize > syncIntervalBytes) {
                    currentSyncPoint++;
                    System.out.println("Sending SYN #" + currentSyncPoint + " at offset " + offset +
                            " (bytesSinceLastSync=" + bytesSinceLastSync + ")");

                    Fpdu synFpdu = new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(ParameterIdentifier.PI_20_NUM_SYNC,
                                    new byte[] { (byte) currentSyncPoint }));
                    sendFpdu(out, synFpdu, "SYN");

                    Fpdu ackSyn = readFpdu(in, "ACK_SYN");
                    if (!checkDiagnostic(ackSyn, "ACK_SYN"))
                        return;

                    lastSyncPoint = currentSyncPoint;
                    bytesAtLastSync = offset;
                    bytesSinceLastSync = 0;

                    // Check if we should interrupt (only if interruptAfterSyncPoint > 0)
                    if (interruptAfterSyncPoint > 0 && currentSyncPoint >= interruptAfterSyncPoint) {
                        System.out
                                .println("\n*** SIMULATING INTERRUPTION after sync point " + currentSyncPoint + " ***");
                        System.out.println("Bytes sent: " + offset + " / " + totalDataSize);

                        Fpdu abortFpdu = new Fpdu(FpduType.ABORT)
                                .withIdDst(serverConnId)
                                .withIdSrc(1)
                                .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG,
                                        new byte[] { 0, 0, 0 }));
                        sendFpdu(out, abortFpdu, "ABORT (interruption)");
                        break;
                    }
                }

                // Send DTF
                byte[] article = new byte[chunkSize];
                System.arraycopy(fullData, offset, article, 0, chunkSize);

                byte[] dtfBytes = FpduBuilder.buildFpdu(FpduType.DTF, serverConnId, 0, article);
                out.writeShort(dtfBytes.length);
                out.write(dtfBytes);
                out.flush();

                offset += chunkSize;
                bytesSinceLastSync += chunkSize;
            }

            // If no interruption, complete the transfer
            if (interruptAfterSyncPoint < 0) {
                System.out.println("Transfer complete - sent " + currentSyncPoint + " sync points");

                // Complete transfer
                Fpdu dtfEndFpdu = new Fpdu(FpduType.DTF_END)
                        .withIdDst(serverConnId)
                        .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
                sendFpdu(out, dtfEndFpdu, "DTF_END");

                Fpdu transEndFpdu = new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId);
                sendFpdu(out, transEndFpdu, "TRANS_END");
                if (!checkDiagnostic(readFpdu(in, "ACK_TRANS_END"), "ACK_TRANS_END"))
                    return;

                Fpdu closeFpdu = new Fpdu(FpduType.CLOSE)
                        .withIdDst(serverConnId)
                        .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
                sendFpdu(out, closeFpdu, "CLOSE");
                if (!checkDiagnostic(readFpdu(in, "ACK_CLOSE"), "ACK_CLOSE"))
                    return;

                Fpdu deselectFpdu = new Fpdu(FpduType.DESELECT)
                        .withIdDst(serverConnId)
                        .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
                sendFpdu(out, deselectFpdu, "DESELECT");
                if (!checkDiagnostic(readFpdu(in, "ACK_DESELECT"), "ACK_DESELECT"))
                    return;

                Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                        .withIdDst(serverConnId)
                        .withIdSrc(1)
                        .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
                sendFpdu(out, releaseFpdu, "RELEASE");
                readFpdu(in, "RELCONF");

                System.out.println("\n✓ SUCCESS - 1MB transfer with " + currentSyncPoint + " sync points completed!");
                return; // Done, no Phase 2 needed
            }

            System.out.println(
                    "Phase 1 complete. Last sync point: " + lastSyncPoint + ", bytes at sync: " + bytesAtLastSync);

        } catch (Exception e) {
            System.out.println("Phase 1 ERROR: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // ========== PHASE 2: Resume from last sync point ==========
        System.out.println("\n--- PHASE 2: Resume from sync point " + lastSyncPoint + " ---");

        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(30000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // 1. CONNECT with sync points and resync enabled (PI 23)
            byte[] pi7Value = new byte[3];
            pi7Value[0] = (byte) ((syncIntervalKB >> 8) & 0xFF);
            pi7Value[1] = (byte) (syncIntervalKB & 0xFF);
            pi7Value[2] = 1;

            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_03_DEMANDEUR, DEMANDEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_04_SERVEUR, SERVEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_07_SYNC_POINTS, pi7Value))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 0))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_23_RESYNC, (byte) 1)) // Enable resync
                    .withIdSrc(1)
                    .withIdDst(0);
            sendFpdu(out, connectFpdu, "CONNECT (resume)");
            Fpdu aconnect = readFpdu(in, "ACONNECT");
            if (aconnect.getFpduType() != FpduType.ACONNECT) {
                System.out.println("ERROR: Expected ACONNECT, got " + aconnect.getFpduType());
                return;
            }
            int serverConnId = aconnect.getIdSrc();

            // 2. CREATE with restart point (PI 18)
            Fpdu createFpdu = new CreateMessageBuilder()
                    .filename("FILE")
                    .transferId(1)
                    .variableFormat()
                    .recordLength(articleSize)
                    .maxEntitySize(65535)
                    .fileSizeKB(totalDataSize / 1024)
                    .restart() // PI 15 = 1 indicates resume
                    .build(serverConnId);
            sendFpdu(out, createFpdu, "CREATE (resume from sync " + lastSyncPoint + ")");
            Fpdu ackCreate = readFpdu(in, "ACK_CREATE");
            if (!checkDiagnostic(ackCreate, "ACK_CREATE"))
                return;

            // Check server's restart point in ACK_CREATE
            ParameterValue pi18 = ackCreate.getParameter(ParameterIdentifier.PI_18_POINT_RELANCE);
            if (pi18 != null) {
                int serverRestartPoint = parseNumeric(pi18.getValue());
                System.out.println("Server confirmed restart from sync point: " + serverRestartPoint);
            }

            // 3. OPEN
            Fpdu openFpdu = new Fpdu(FpduType.OPEN).withIdDst(serverConnId);
            sendFpdu(out, openFpdu, "OPEN");
            if (!checkDiagnostic(readFpdu(in, "ACK_OPEN"), "ACK_OPEN"))
                return;

            // 4. WRITE
            Fpdu writeFpdu = new Fpdu(FpduType.WRITE).withIdDst(serverConnId);
            sendFpdu(out, writeFpdu, "WRITE");
            if (!checkDiagnostic(readFpdu(in, "ACK_WRITE"), "ACK_WRITE"))
                return;

            // 5. Resume sending data from bytesAtLastSync
            int offset = (int) bytesAtLastSync;
            int currentSyncPoint = lastSyncPoint;
            int nextSyncAt = (int) bytesAtLastSync + syncIntervalBytes;
            int articlesSent = 0;

            System.out.println("Resuming from offset " + offset + " (sync point " + lastSyncPoint + ")");

            while (offset < totalDataSize) {
                // Check if we should send a sync point
                if (offset >= nextSyncAt) {
                    currentSyncPoint++;
                    System.out.println("Sending SYN #" + currentSyncPoint + " at offset " + offset);

                    Fpdu synFpdu = new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(ParameterIdentifier.PI_20_NUM_SYNC,
                                    new byte[] { (byte) currentSyncPoint }));
                    sendFpdu(out, synFpdu, "SYN");

                    Fpdu ackSyn = readFpdu(in, "ACK_SYN");
                    if (!checkDiagnostic(ackSyn, "ACK_SYN"))
                        return;

                    nextSyncAt += syncIntervalBytes;
                }

                // Send DTF
                int chunkSize = Math.min(articleSize, totalDataSize - offset);
                byte[] article = new byte[chunkSize];
                System.arraycopy(fullData, offset, article, 0, chunkSize);

                byte[] dtfBytes = FpduBuilder.buildFpdu(FpduType.DTF, serverConnId, 0, article);
                out.writeShort(dtfBytes.length);
                out.write(dtfBytes);
                out.flush();

                offset += chunkSize;
                articlesSent++;
            }
            System.out.println("Sent " + articlesSent + " articles after resume");

            // 6. Complete transfer
            Fpdu dtfEndFpdu = new Fpdu(FpduType.DTF_END)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
            sendFpdu(out, dtfEndFpdu, "DTF_END");

            Fpdu transEndFpdu = new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId);
            sendFpdu(out, transEndFpdu, "TRANS_END");
            if (!checkDiagnostic(readFpdu(in, "ACK_TRANS_END"), "ACK_TRANS_END"))
                return;

            Fpdu closeFpdu = new Fpdu(FpduType.CLOSE)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
            sendFpdu(out, closeFpdu, "CLOSE");
            if (!checkDiagnostic(readFpdu(in, "ACK_CLOSE"), "ACK_CLOSE"))
                return;

            Fpdu deselectFpdu = new Fpdu(FpduType.DESELECT)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
            sendFpdu(out, deselectFpdu, "DESELECT");
            if (!checkDiagnostic(readFpdu(in, "ACK_DESELECT"), "ACK_DESELECT"))
                return;

            Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnId)
                    .withIdSrc(1)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
            sendFpdu(out, releaseFpdu, "RELEASE");
            readFpdu(in, "RELCONF");

            System.out.println("\n✓ SUCCESS - 1MB transfer with sync points and resume completed!");
            System.out.println("  Total sync points: " + currentSyncPoint);
            System.out.println("  Interrupted at sync point: " + lastSyncPoint);
            System.out.println("  Resumed and completed successfully");

        } catch (Exception e) {
            System.out.println("Phase 2 ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test complete transfer: CONNECT -> CREATE -> OPEN -> WRITE -> DTF -> DTF_END
     * -> TRANS_END -> CLOSE -> DESELECT -> RELEASE
     */
    private static void testFullTransferWithData() {
        System.out.println("\n=== Test: Full transfer with DTF data ===");

        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(10000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // 1. CONNECT
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_03_DEMANDEUR, DEMANDEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_04_SERVEUR, SERVEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 0))
                    .withIdSrc(1)
                    .withIdDst(0);
            sendFpdu(out, connectFpdu, "CONNECT");
            Fpdu aconnect = readFpdu(in, "ACONNECT");
            if (aconnect.getFpduType() != FpduType.ACONNECT) {
                System.out.println("ERROR: Expected ACONNECT, got " + aconnect.getFpduType());
                return;
            }
            int serverConnId = aconnect.getIdSrc();
            System.out.println("Server connection ID: " + serverConnId);

            // 2. CREATE - TRUE NEGOTIATION: PI 25 and PI 32 are INDEPENDENT
            // PI 25 = max entity size (container), PI 32 = max article size (record)
            // CX server: PI 25 = 65535, PI 32 = 1024 (configured on FILE virtual file)
            int proposedPi32 = 1024; // Article size configured on server for FILE
            int proposedPi25 = 65535; // Max entity size
            int negotiatedPi25 = proposedPi25;
            Fpdu ackCreate = null;
            int minPi25 = proposedPi32 + 6; // Entity must fit at least one article

            while (proposedPi25 >= minPi25) {
                System.out.println("CREATE: proposing PI25=" + proposedPi25 + ", PI32=" + proposedPi32);
                Fpdu createFpdu = new CreateMessageBuilder()
                        .filename("FILE")
                        .transferId(1)
                        .variableFormat()
                        .recordLength(proposedPi32)
                        .maxEntitySize(proposedPi25)
                        .fileSizeKB(100) // 100KB test file
                        .build(serverConnId);
                sendFpdu(out, createFpdu, "CREATE");
                ackCreate = readFpdu(in, "ACK_CREATE");

                // Check if CREATE was rejected
                ParameterValue diagPv = ackCreate.getParameter(ParameterIdentifier.PI_02_DIAG);
                boolean rejected = false;
                if (diagPv != null && diagPv.getValue() != null) {
                    byte[] diagBytes = (byte[]) diagPv.getValue();
                    if (diagBytes.length >= 2 && (diagBytes[0] != 0 || diagBytes[1] != 0)) {
                        rejected = true;
                        DiagnosticCode dc = DiagnosticCode.fromParameterValue(diagPv);
                        System.out.println("CREATE rejected: " + dc);

                        // Read server's suggested PI 25 AND PI 32
                        ParameterValue serverPi25 = ackCreate.getParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE);
                        ParameterValue serverPi32 = ackCreate
                                .getParameter(ParameterIdentifier.PI_32_LONG_ARTICLE);
                        int serverValue = 0;
                        if (serverPi25 != null && serverPi25.getValue() != null) {
                            serverValue = parseNumeric(serverPi25.getValue());
                            System.out.println("Server PI25 in response=" + serverValue);
                        }
                        if (serverPi32 != null && serverPi32.getValue() != null) {
                            int serverPi32Value = parseNumeric(serverPi32.getValue());
                            System.out.println("Server PI32 in response=" + serverPi32Value);
                        }

                        // Try server's value if smaller, otherwise halve our proposal
                        if (serverValue > 0 && serverValue < proposedPi25) {
                            proposedPi25 = serverValue;
                        } else {
                            proposedPi25 = proposedPi25 / 2;
                        }
                        System.out.println("Retrying with PI25=" + proposedPi25);
                        continue;
                    }
                }

                if (!rejected) {
                    // Success! Read negotiated PI 25
                    ParameterValue ackPi25 = ackCreate.getParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE);
                    if (ackPi25 != null && ackPi25.getValue() != null) {
                        negotiatedPi25 = parseNumeric(ackPi25.getValue());
                        System.out.println("ACK_CREATE: server negotiated PI25=" + negotiatedPi25);
                    } else {
                        negotiatedPi25 = proposedPi25;
                        System.out.println("ACK_CREATE: no PI25 in response, using proposed=" + proposedPi25);
                    }
                    break;
                }
            }

            if (proposedPi25 < minPi25) {
                System.out.println("ERROR: Could not negotiate PI25, gave up at " + proposedPi25);
                return;
            }

            int actualChunkSize = negotiatedPi25 - 6;
            System.out.println("Using actual chunk size (PI32) = " + actualChunkSize);

            // 3. OPEN
            Fpdu openFpdu = new Fpdu(FpduType.OPEN).withIdDst(serverConnId);
            sendFpdu(out, openFpdu, "OPEN");
            Fpdu ackOpen = readFpdu(in, "ACK_OPEN");
            if (!checkDiagnostic(ackOpen, "ACK_OPEN"))
                return;

            // 4. WRITE
            Fpdu writeFpdu = new Fpdu(FpduType.WRITE).withIdDst(serverConnId);
            sendFpdu(out, writeFpdu, "WRITE");
            Fpdu ackWrite = readFpdu(in, "ACK_WRITE");
            if (!checkDiagnostic(ackWrite, "ACK_WRITE"))
                return;

            // 5. DTF - test transfer with 100KB file using MONO-ARTICLE DTFs
            // Multi-article requires prior agreement with server - use mono-article for now
            // PI 25 = negotiated entity size, PI 32 = 1024 (article size)
            int totalDataSize = 100 * 1024; // 100KB
            byte[] fullData = new byte[totalDataSize];
            for (int i = 0; i < totalDataSize; i++) {
                fullData[i] = (byte) ('A' + (i % 26));
            }

            int articleSize = proposedPi32; // 1024 bytes per article
            System.out.println("=== Mono-article transfer test ===");
            System.out.println("Total data: " + totalDataSize + " bytes (" + (totalDataSize / 1024) + " KB)");
            System.out.println("Article size (PI32): " + articleSize + " bytes");
            System.out.println("Entity size (PI25): " + negotiatedPi25 + " bytes");

            int offset = 0;
            int totalArticles = 0;

            while (offset < totalDataSize) {
                int chunkSize = Math.min(articleSize, totalDataSize - offset);
                byte[] article = new byte[chunkSize];
                System.arraycopy(fullData, offset, article, 0, chunkSize);

                // Send mono-article DTF (one article per entity)
                byte[] dtfBytes = FpduBuilder.buildFpdu(FpduType.DTF, serverConnId, 0, article);
                out.writeShort(dtfBytes.length); // transport framing
                out.write(dtfBytes);
                out.flush();

                offset += chunkSize;
                totalArticles++;
            }
            System.out.println("Sent " + totalArticles + " mono-article DTFs");

            // 6. DTF_END
            Fpdu dtfEndFpdu = new Fpdu(FpduType.DTF_END)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
            sendFpdu(out, dtfEndFpdu, "DTF_END");
            // No ACK for DTF_END

            // 7. TRANS_END
            Fpdu transEndFpdu = new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId);
            sendFpdu(out, transEndFpdu, "TRANS_END");
            Fpdu ackTransEnd = readFpdu(in, "ACK_TRANS_END");
            if (!checkDiagnostic(ackTransEnd, "ACK_TRANS_END"))
                return;

            // 8. CLOSE
            Fpdu closeFpdu = new Fpdu(FpduType.CLOSE)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
            sendFpdu(out, closeFpdu, "CLOSE");
            Fpdu ackClose = readFpdu(in, "ACK_CLOSE");
            if (!checkDiagnostic(ackClose, "ACK_CLOSE"))
                return;

            // 9. DESELECT
            Fpdu deselectFpdu = new Fpdu(FpduType.DESELECT)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
            sendFpdu(out, deselectFpdu, "DESELECT");
            Fpdu ackDeselect = readFpdu(in, "ACK_DESELECT");
            if (!checkDiagnostic(ackDeselect, "ACK_DESELECT"))
                return;

            // 10. RELEASE
            Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnId)
                    .withIdSrc(1)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
            sendFpdu(out, releaseFpdu, "RELEASE");
            Fpdu ackRelease = readFpdu(in, "ACK_RELEASE");

            System.out.println("\n✓ SUCCESS - Full transfer completed!");

        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void sendFpdu(DataOutputStream out, Fpdu fpdu, String name) throws Exception {
        byte[] data = FpduBuilder.buildFpdu(fpdu);
        System.out.println("Sending " + name + " (" + data.length + " bytes)");
        // Transport framing: write length prefix, then FPDU bytes (which also contain
        // internal length)
        out.writeShort(data.length);
        out.write(data);
        out.flush();
    }

    private static Fpdu readFpdu(DataInputStream in, String expectedName) throws Exception {
        Fpdu fpdu = FpduIO.readFpdu(in);
        System.out.println("Got: " + fpdu.getFpduType() + " (expected " + expectedName + ")");
        return fpdu;
    }

    private static boolean checkDiagnostic(Fpdu fpdu, String name) {
        // Check if we got ABORT instead of expected ACK
        if (fpdu.getFpduType() == FpduType.ABORT) {
            System.out.println("ERROR: Got ABORT instead of " + name);
            ParameterValue diag = fpdu.getParameter(ParameterIdentifier.PI_02_DIAG);
            if (diag != null && diag.getValue() != null) {
                byte[] diagBytes = diag.getValue();
                int code = diagBytes[0] & 0xFF;
                int reason = diagBytes.length >= 3 ? ((diagBytes[1] & 0xFF) << 8) | (diagBytes[2] & 0xFF) : 0;
                System.out.println("  ABORT diagnostic: D" + code + "_" + reason);
                // Lookup in DiagnosticCode enum
                for (DiagnosticCode dc : DiagnosticCode.values()) {
                    if (dc.getCode() == code && dc.getReason() == reason) {
                        System.out.println("  Message: " + dc.getMessage());
                        break;
                    }
                }
            }
            return false;
        }
        ParameterValue diag = fpdu.getParameter(ParameterIdentifier.PI_02_DIAG);
        if (diag != null && diag.getValue() != null && diag.getValue().length >= 1) {
            byte[] diagBytes = diag.getValue();
            int code = diagBytes[0] & 0xFF;
            if (code != 0) {
                int reason = diagBytes.length >= 3 ? ((diagBytes[1] & 0xFF) << 8) | (diagBytes[2] & 0xFF) : 0;
                System.out.println("ERROR: " + name + " diagnostic D" + code + "_" + reason);
                // Lookup in DiagnosticCode enum
                for (DiagnosticCode dc : DiagnosticCode.values()) {
                    if (dc.getCode() == code && dc.getReason() == reason) {
                        System.out.println("  Message: " + dc.getMessage());
                        break;
                    }
                }
                return false;
            }
        }
        return true;
    }

    private static void testFullTransfer() {
        System.out.println("\n=== Test: Full CONNECT + CREATE flow ===");

        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(10000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // 1. CONNECT
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_03_DEMANDEUR, DEMANDEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_04_SERVEUR, SERVEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 0))
                    .withIdSrc(1)
                    .withIdDst(0);

            byte[] fpduData = FpduBuilder.buildFpdu(connectFpdu);
            System.out.println("Sending CONNECT (" + fpduData.length + " bytes)");
            out.writeShort(fpduData.length);
            out.write(fpduData);
            out.flush();

            // Read ACONNECT
            Fpdu aconnect = FpduIO.readFpdu(in);
            System.out.println("Got: " + aconnect.getFpduType());

            if (aconnect.getFpduType() != FpduType.ACONNECT) {
                System.out.println("Expected ACONNECT, aborting");
                return;
            }

            int serverConnId = aconnect.getIdSrc();
            System.out.println("Server connection ID: " + serverConnId);

            // Check PI 25 from ACONNECT
            ParameterValue aconnectPi25 = aconnect.getParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE);
            if (aconnectPi25 != null) {
                int serverMaxEntity = parseNumeric(aconnectPi25.getValue());
                System.out.println("ACONNECT PI 25 (max entity): " + serverMaxEntity);
            } else {
                System.out.println("ACONNECT: No PI 25");
            }

            // 2. CREATE with small values PI 32 = 506, PI 25 = 512
            System.out.println("\n--- Sending CREATE with PI 32 = 506, PI 25 = 512 ---");
            Fpdu createFpdu = new CreateMessageBuilder()
                    .filename("FILE")
                    .transferId(1)
                    .variableFormat()
                    .recordLength(506) // PI 32 = 512 - 6
                    .maxEntitySize(512) // PI 25
                    .fileSizeKB(1)
                    .build(serverConnId);

            byte[] createData = FpduBuilder.buildFpdu(createFpdu);
            System.out.println("Sending CREATE (" + createData.length + " bytes)");
            printHex("CREATE", createData);
            out.writeShort(createData.length);
            out.write(createData);
            out.flush();

            // Read ACK_CREATE or error
            Fpdu ackCreate = FpduIO.readFpdu(in);
            System.out.println("Got: " + ackCreate.getFpduType());

            // Check for diagnostic
            ParameterValue diag = ackCreate.getParameter(ParameterIdentifier.PI_02_DIAG);
            if (diag != null) {
                byte[] diagBytes = diag.getValue();
                System.out.println("Diagnostic: " + bytesToHex(diagBytes));
                if (diagBytes.length >= 3) {
                    int code = diagBytes[0] & 0xFF;
                    int reason = ((diagBytes[1] & 0xFF) << 8) | (diagBytes[2] & 0xFF);
                    System.out.println("  Code: " + code + ", Reason: " + reason + " (D" + code + "_" + reason + ")");
                }
            }

            // Check PI 25 from ACK_CREATE
            ParameterValue createPi25 = ackCreate.getParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE);
            if (createPi25 != null) {
                int serverMaxEntity = parseNumeric(createPi25.getValue());
                System.out.println("ACK_CREATE PI 25 (max entity): " + serverMaxEntity);
            }

            // Check PI 32 from ACK_CREATE
            ParameterValue serverPi32 = ackCreate.getParameter(ParameterIdentifier.PI_32_LONG_ARTICLE);
            if (serverPi32 != null) {
                int serverRecordLen = parseNumeric(serverPi32.getValue());
                System.out.println("ACK_CREATE PI 32 (record length): " + serverRecordLen);
            }

            // Send RELEASE to close session cleanly
            System.out.println("\n--- Sending RELEASE ---");
            Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnId)
                    .withIdSrc(1);
            byte[] releaseData = FpduBuilder.buildFpdu(releaseFpdu);
            out.writeShort(releaseData.length);
            out.write(releaseData);
            out.flush();
            System.out.println("Session closed cleanly");

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int parseNumeric(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return 0;
        int value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }

    private static void testConnect(String description, byte[] pi7Value) {
        System.out.println("\n=== Test: " + description + " ===");

        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Build CONNECT FPDU - PI order is critical: PI_03, PI_04, PI_06, PI_07, PI_22
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_03_DEMANDEUR, DEMANDEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_04_SERVEUR, SERVEUR))
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2))
                    .withIdSrc(1)
                    .withIdDst(0);

            // Add PI 7 BEFORE PI 22 (order is essential in PeSIT!)
            if (pi7Value != null) {
                connectFpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_07_SYNC_POINTS, pi7Value));
            }

            // PI 22 must come AFTER PI 7
            connectFpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 0)); // write

            // Serialize using FpduBuilder
            byte[] fpduData = FpduBuilder.buildFpdu(connectFpdu);

            // Send with transport framing: 2-byte length prefix + FPDU data
            System.out.println("Sending CONNECT (" + fpduData.length + " bytes)");
            printHex("FPDU", fpduData);
            out.writeShort(fpduData.length); // Transport length prefix
            out.write(fpduData);
            out.flush();

            // Read response with transport framing
            int responseLen = in.readUnsignedShort();
            byte[] responseData = new byte[responseLen];
            in.readFully(responseData);

            printHex("Response", responseData);

            // Parse response using FpduParser
            try {
                // FpduParser expects length at start, so prepend it
                byte[] fullResponse = new byte[responseLen + 2];
                fullResponse[0] = (byte) ((responseLen >> 8) & 0xFF);
                fullResponse[1] = (byte) (responseLen & 0xFF);
                System.arraycopy(responseData, 0, fullResponse, 2, responseLen);

                FpduParser parser = new FpduParser(fullResponse);
                Fpdu responseFpdu = parser.parse();
                System.out.println("Response type: " + responseFpdu.getFpduType());

                if (responseFpdu.getFpduType() == FpduType.ACONNECT) {
                    System.out.println("✓ SUCCESS - Got ACONNECT!");
                    // Check PI 7 in response
                    ParameterValue pi7Response = responseFpdu.getParameter(ParameterIdentifier.PI_07_SYNC_POINTS);
                    if (pi7Response != null) {
                        byte[] pi7Bytes = pi7Response.getValue();
                        System.out.println("Server PI 7: " + bytesToHex(pi7Bytes));
                        if (pi7Bytes.length >= 3) {
                            int interval = ((pi7Bytes[0] & 0xFF) << 8) | (pi7Bytes[1] & 0xFF);
                            int window = pi7Bytes[2] & 0xFF;
                            System.out.println("  Interval: " + interval + " KB, Window: " + window);
                        }
                    } else {
                        System.out.println("  No PI 7 in response (sync disabled by server)");
                    }
                } else if (responseFpdu.getFpduType() == FpduType.ABORT) {
                    System.out.println("✗ ABORT received");
                    ParameterValue diag = responseFpdu.getParameter(ParameterIdentifier.PI_02_DIAG);
                    if (diag != null) {
                        System.out.println("  Diagnostic: " + bytesToHex(diag.getValue()));
                    }
                }
            } catch (Exception e) {
                System.out.println("Failed to parse response: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void printHex(String label, byte[] data) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" (").append(data.length).append(" bytes): ");
        for (int i = 0; i < Math.min(data.length, 64); i++) {
            sb.append(String.format("%02X ", data[i]));
        }
        if (data.length > 64) {
            sb.append("...");
        }
        System.out.println(sb);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static int bytesToInt(byte[] bytes) {
        int result = 0;
        for (byte b : bytes) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }
}
