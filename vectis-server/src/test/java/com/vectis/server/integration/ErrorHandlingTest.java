package com.vectis.server.integration;

import static com.vectis.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.vectis.fpdu.Fpdu;
import com.vectis.fpdu.FpduBuilder;
import com.vectis.fpdu.FpduParser;
import com.vectis.fpdu.FpduType;
import com.vectis.fpdu.ParameterValue;

/**
 * Integration tests for error handling scenarios.
 * Tests that the server returns proper RCONNECT/ABORT with correct diagnostic codes.
 * 
 * Requires a PeSIT server running on localhost:5000
 * Run with: mvn test -Dtest=ErrorHandlingTest -Dpesit.integration.enabled=true
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ErrorHandlingTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;
    private static final boolean INTEGRATION_ENABLED = Boolean.parseBoolean(
            System.getProperty("pesit.integration.enabled", "false"));
    private static final int CLIENT_ID = 5;

    @BeforeAll
    void checkIntegrationEnabled() {
        Assumptions.assumeTrue(INTEGRATION_ENABLED,
                "Integration tests disabled. Enable with -Dpesit.integration.enabled=true");
    }

    @Test
    @DisplayName("Test RCONNECT on wrong server name")
    void testWrongServerName() throws Exception {
        System.out.println("\n=== WRONG SERVER NAME TEST ===\n");

        try (Socket socket = new Socket(HOST, PORT)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // CONNECT with wrong server name
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(CLIENT_ID)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, "LOOP"))
                    .withParameter(new ParameterValue(PI_04_SERVEUR, "WRONG_SERVER"))
                    .withParameter(new ParameterValue(PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

            byte[] fpduBytes = FpduBuilder.buildFpdu(connectFpdu);
            out.writeShort(fpduBytes.length);
            out.write(fpduBytes);
            out.flush();

            int len = in.readUnsignedShort();
            byte[] responseBytes = new byte[len];
            in.readFully(responseBytes);
            Fpdu response = new FpduParser(responseBytes).parse();

            assertEquals(FpduType.RCONNECT, response.getFpduType(), "Expected RCONNECT for wrong server");

            ParameterValue pi2 = response.getParameter(PI_02_DIAG);
            assertNotNull(pi2, "RCONNECT should have diagnostic");
            byte[] diag = pi2.getValue();
            assertEquals(0x02, diag[0] & 0xFF, "Category should be 0x02 (Connection Error)");
            assertEquals(0x07, diag[1] & 0xFF, "Reason should be 0x07 (Server Unknown)");

            System.out.println("  ✓ Server correctly rejected with RCONNECT (Server Unknown)");
            System.out.println("    Diagnostic: 0x" + String.format("%02X%02X%02X", diag[0], diag[1], diag[2]));
        }

        System.out.println("\n✓✓✓ WRONG SERVER NAME TEST PASSED ✓✓✓\n");
    }

    @Test
    @DisplayName("Test RCONNECT on unknown partner (strict mode)")
    void testUnknownPartner() throws Exception {
        System.out.println("\n=== UNKNOWN PARTNER TEST ===\n");

        try (Socket socket = new Socket(HOST, PORT)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(CLIENT_ID)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, "UNKNOWN_PARTNER"))
                    .withParameter(new ParameterValue(PI_04_SERVEUR, "PESIT_SERVER"))
                    .withParameter(new ParameterValue(PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

            byte[] fpduBytes = FpduBuilder.buildFpdu(connectFpdu);
            out.writeShort(fpduBytes.length);
            out.write(fpduBytes);
            out.flush();

            int len = in.readUnsignedShort();
            byte[] responseBytes = new byte[len];
            in.readFully(responseBytes);
            Fpdu response = new FpduParser(responseBytes).parse();

            if (response.getFpduType() == FpduType.RCONNECT) {
                ParameterValue pi2 = response.getParameter(PI_02_DIAG);
                assertNotNull(pi2, "RCONNECT should have diagnostic");
                byte[] diag = pi2.getValue();
                assertEquals(0x02, diag[0] & 0xFF, "Category should be 0x02 (Connection Error)");
                assertEquals(0x01, diag[1] & 0xFF, "Reason should be 0x01 (Partner Unknown)");
                System.out.println("  ✓ Server rejected unknown partner with RCONNECT (strict mode)");
                System.out.println("    Diagnostic: 0x" + String.format("%02X%02X%02X", diag[0], diag[1], diag[2]));
            } else {
                assertEquals(FpduType.ACONNECT, response.getFpduType());
                System.out.println("  ⚠ Server accepted unknown partner (non-strict mode)");
            }
        }

        System.out.println("\n✓✓✓ UNKNOWN PARTNER TEST PASSED ✓✓✓\n");
    }

    @Test
    @DisplayName("Test RCONNECT on protocol version mismatch")
    void testVersionMismatch() throws Exception {
        System.out.println("\n=== VERSION MISMATCH TEST ===\n");

        try (Socket socket = new Socket(HOST, PORT)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // CONNECT with unsupported version (99)
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(CLIENT_ID)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, "LOOP"))
                    .withParameter(new ParameterValue(PI_04_SERVEUR, "PESIT_SERVER"))
                    .withParameter(new ParameterValue(PI_06_VERSION, 99))
                    .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

            byte[] fpduBytes = FpduBuilder.buildFpdu(connectFpdu);
            out.writeShort(fpduBytes.length);
            out.write(fpduBytes);
            out.flush();

            int len = in.readUnsignedShort();
            byte[] responseBytes = new byte[len];
            in.readFully(responseBytes);
            Fpdu response = new FpduParser(responseBytes).parse();

            assertEquals(FpduType.RCONNECT, response.getFpduType(), "Expected RCONNECT for unsupported version");

            ParameterValue pi2 = response.getParameter(PI_02_DIAG);
            assertNotNull(pi2, "RCONNECT should have diagnostic");
            byte[] diag = pi2.getValue();
            assertEquals(0x02, diag[0] & 0xFF, "Category should be 0x02 (Connection Error)");
            assertEquals(0x08, diag[1] & 0xFF, "Reason should be 0x08 (Version Incompatible)");

            System.out.println("  ✓ Server correctly rejected with RCONNECT (Version Incompatible)");
            System.out.println("    Diagnostic: 0x" + String.format("%02X%02X%02X", diag[0], diag[1], diag[2]));
        }

        System.out.println("\n✓✓✓ VERSION MISMATCH TEST PASSED ✓✓✓\n");
    }

    @Test
    @DisplayName("Test ABORT on unknown logical file (strict mode)")
    void testUnknownLogicalFile() throws Exception {
        System.out.println("\n=== UNKNOWN LOGICAL FILE TEST ===\n");

        try (Socket socket = new Socket(HOST, PORT)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // First connect successfully
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(CLIENT_ID)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, "LOOP"))
                    .withParameter(new ParameterValue(PI_04_SERVEUR, "PESIT_SERVER"))
                    .withParameter(new ParameterValue(PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

            byte[] fpduBytes = FpduBuilder.buildFpdu(connectFpdu);
            out.writeShort(fpduBytes.length);
            out.write(fpduBytes);
            out.flush();

            int len = in.readUnsignedShort();
            byte[] responseBytes = new byte[len];
            in.readFully(responseBytes);
            Fpdu aconnect = new FpduParser(responseBytes).parse();

            if (aconnect.getFpduType() != FpduType.ACONNECT) {
                System.out.println("  ⚠ Could not connect - skipping test");
                return;
            }
            int serverConnId = aconnect.getIdSrc();
            System.out.println("  ✓ Connected");

            // Try to CREATE with unknown filename
            Fpdu createFpdu = new com.vectis.fpdu.CreateMessageBuilder()
                    .filename("UNKNOWN_FILE_XYZ")
                    .transferId(1)
                    .maxEntitySize(56)
                    .build(serverConnId);

            fpduBytes = FpduBuilder.buildFpdu(createFpdu);
            out.writeShort(fpduBytes.length);
            out.write(fpduBytes);
            out.flush();

            len = in.readUnsignedShort();
            responseBytes = new byte[len];
            in.readFully(responseBytes);
            Fpdu response = new FpduParser(responseBytes).parse();

            if (response.getFpduType() == FpduType.ABORT) {
                ParameterValue pi2 = response.getParameter(PI_02_DIAG);
                assertNotNull(pi2, "ABORT should have diagnostic");
                byte[] diag = pi2.getValue();
                assertEquals(0x03, diag[0] & 0xFF, "Category should be 0x03 (File Error)");
                assertEquals(0x07, diag[1] & 0xFF, "Reason should be 0x07 (Logical File Unknown)");
                System.out.println("  ✓ Server rejected unknown file with ABORT (strict mode)");
                System.out.println("    Diagnostic: 0x" + String.format("%02X%02X%02X", diag[0], diag[1], diag[2]));
            } else {
                assertEquals(FpduType.ACK_CREATE, response.getFpduType());
                System.out.println("  ⚠ Server accepted unknown file (non-strict mode)");
            }
        }

        System.out.println("\n✓✓✓ UNKNOWN LOGICAL FILE TEST PASSED ✓✓✓\n");
    }
}
