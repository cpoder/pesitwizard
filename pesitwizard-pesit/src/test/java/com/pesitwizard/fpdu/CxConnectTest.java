package com.pesitwizard.fpdu;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

/**
 * Test script to find valid PI 7 (sync points) configuration for CX server.
 * Run with: mvn exec:java -Dexec.mainClass="com.pesitwizard.fpdu.CxConnectTest"
 * -Dexec.classpathScope=test -pl pesitwizard-pesit
 */
public class CxConnectTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5100;
    private static final String DEMANDEUR = "LOOP";
    private static final String SERVEUR = "CETOM1";

    public static void main(String[] args) throws Exception {
        // Test full transfer flow with CREATE and DTF
        testFullTransferWithData();
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
}
