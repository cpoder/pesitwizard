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
        // Test with CX config: 32 KB interval, window 2
        testConnect("PI 7 = 32KB, w=2 (CX config)", new byte[] { 0x00, 0x20, 0x02 });

        // Test 1: No sync points (PI 7 absent)
        testConnect("No PI 7", null);

        // Test 2: Sync disabled (interval=0, window=0)
        testConnect("PI 7 = 0,0,0", new byte[] { 0x00, 0x00, 0x00 });

        // Test 3: Sync with interval=64KB, window=1
        testConnect("PI 7 = 64KB, w=1", new byte[] { 0x00, 0x40, 0x01 });

        // Test 4: Sync with interval=4KB (SIT min), window=1
        testConnect("PI 7 = 4KB, w=1", new byte[] { 0x00, 0x04, 0x01 });
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
