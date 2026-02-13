package com.pesitwizard.integration;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TcpTransportChannel;
import java.io.IOException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Complete file transfer test - sends actual file data Requires a PESIT server running on
 * localhost:5000
 *
 * <p>Run with: mvn test -Dtest=CompleteFileTransferTest -Dpesit.integration.enabled=true
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CompleteFileTransferTest {

    private static final String TEST_HOST = System.getProperty("pesit.test.host", "localhost");
    private static final int TEST_PORT =
            Integer.parseInt(System.getProperty("pesit.test.port", "5000"));
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
            "Complete file transfer: CONNECT → CREATE → OPEN → WRITE → DTF → DTF.END → TRANS.END → CLOSE → DESELECT → RELEASE")
    void testCompleteFileTransfer() throws IOException, InterruptedException {
        System.out.println("\n=== COMPLETE FILE TRANSFER TEST ===\n");

        try (PesitSession session =
                new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT))) {
            // Step 1: CONNECT
            System.out.println("Step 1: CONNECT");
            int clientConnectionId = 0x05; // X = our client ID

            String serverId = System.getProperty("pesit.test.server", "PESIT_SERVER");
            ConnectMessageBuilder connectBuilder =
                    new ConnectMessageBuilder().demandeur("LOOP").serveur(serverId).writeAccess();

            Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(clientConnectionId));

            assertEquals(FpduType.ACONNECT, aconnect.getFpduType(), "Expected ACONNECT FPDU type");
            assertEquals(
                    clientConnectionId,
                    aconnect.getIdDst(),
                    "ACONNECT idDest should match our client ID");

            int serverConnectionId = aconnect.getIdSrc(); // Y = server ID
            System.out.println(
                    "  ✓ Session established, client ID: "
                            + clientConnectionId
                            + ", server ID: "
                            + serverConnectionId);

            // Step 2: CREATE
            System.out.println("\nStep 2: CREATE");
            String filename = "FILE";
            CreateMessageBuilder createBuilder =
                    new CreateMessageBuilder()
                            .filename(filename)
                            .transferId(1)
                            .variableFormat()
                            .recordLength(30)
                            .maxEntitySize(56);

            Fpdu ackCreate = session.sendFpduWithAck(createBuilder.build(serverConnectionId));

            assertEquals(FpduType.ACK_CREATE, ackCreate.getFpduType(), "Expected ACK(CREATE)");
            assertEquals(
                    clientConnectionId,
                    ackCreate.getIdDst(),
                    "ACK(CREATE) idDest should match server ID");
            assertEquals(
                    0, ackCreate.getIdSrc(), "ACK(CREATE) idSrc should be 0 for file-level FPDUs");
            // Parse PI 2 (diagnostic) from ACK(CREATE)
            assertTrue(
                    ackCreate.hasParameter(ParameterIdentifier.PI_02_DIAG),
                    "ACK(CREATE) should contain PI 2 (diagnostic)");
            assertEquals(
                    3,
                    ackCreate.getParameter(ParameterIdentifier.PI_02_DIAG).getValue().length,
                    "PI 2 should have 3 bytes");
            assertEquals(
                    0x00,
                    ackCreate.getParameter(ParameterIdentifier.PI_02_DIAG).getValue()[0] & 0xFF,
                    "PI 2 byte 1 should be 0");
            assertEquals(
                    0x00,
                    ackCreate.getParameter(ParameterIdentifier.PI_02_DIAG).getValue()[1] & 0xFF,
                    "PI 2 byte 2 should be 0");
            assertEquals(
                    0x00,
                    ackCreate.getParameter(ParameterIdentifier.PI_02_DIAG).getValue()[2] & 0xFF,
                    "PI 2 byte 3 should be 0");
            System.out.println("  ✓ CREATE successful for file: " + filename);
            System.out.println("  State: 'fichier sélectionné'");

            // Step 3: OPEN (ORF)
            System.out.println("\nStep 3: OPEN (ORF) - open file for transfer");
            Fpdu ackOpen =
                    session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnectionId));
            assertEquals(FpduType.ACK_OPEN, ackOpen.getFpduType(), "Expected ACK(ORF)");
            assertEquals(
                    clientConnectionId,
                    ackOpen.getIdDst(),
                    "ACK(ORF) idDest should match server ID");
            assertEquals(0, ackOpen.getIdSrc(), "ACK(ORF) idSrc should be 0 for file-level FPDUs");
            System.out.println("  ✓ File opened for transfer");
            System.out.println("  State: 'transfert de données - repos'");

            // Step 4: WRITE
            System.out.println("\nStep 4: WRITE (initiate data transfer)");
            System.out.println(
                    "  WRITE: idDest="
                            + serverConnectionId
                            + " (Y=server), idSrc=0, NO parameters");

            Fpdu ackWrite =
                    session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverConnectionId));
            assertEquals(FpduType.ACK_WRITE, ackWrite.getFpduType(), "Expected ACK(WRITE)");
            assertEquals(
                    clientConnectionId,
                    ackWrite.getIdDst(),
                    "ACK(WRITE) idDest should match server ID");
            assertEquals(
                    0, ackWrite.getIdSrc(), "ACK(WRITE) idSrc should be 0 for file-level FPDUs");
            System.out.println("  ✓ Data transfer phase initiated");

            // Step 5: Send data using DTF (simple mono-article transfer)
            System.out.println("\nStep 5: Send file data via DTF");
            String fileContent1 = "Hello! This is test data";
            String fileContent2 = " from PESIT Java library.\n";
            byte[] dtfData1 = fileContent1.getBytes();
            byte[] dtfData2 = fileContent2.getBytes();

            session.sendFpduWithData(
                    new Fpdu(FpduType.DTF).withIdDst(serverConnectionId), dtfData1);
            System.out.println("  ✓ Sent " + dtfData1.length + " bytes via DTF");

            session.sendFpduWithData(
                    new Fpdu(FpduType.DTF).withIdDst(serverConnectionId), dtfData2);
            System.out.println("  ✓ Sent " + dtfData2.length + " bytes via DTF");
            System.out.println("  State: 'transfert de données - repos'");
            // Step 6: DTF.END
            System.out.println("\nStep 6: DTF.END");

            session.sendFpdu(
                    new Fpdu(FpduType.DTF_END)
                            .withIdDst(serverConnectionId)
                            .withParameter(
                                    new ParameterValue(PI_02_DIAG, new byte[] {0x00, 0x00, 0x00})));
            System.out.println("  ✓ Data transfer end signal sent (no ACK expected)");

            // Step 7: TRANS.END
            System.out.println("\nStep 7: TRANS.END");

            Fpdu ackTransEnd =
                    session.sendFpduWithAck(
                            new Fpdu(FpduType.TRANS_END).withIdDst(serverConnectionId));
            assertEquals(
                    FpduType.ACK_TRANS_END, ackTransEnd.getFpduType(), "Expected ACK(TRANS.END)");
            assertEquals(
                    clientConnectionId,
                    ackTransEnd.getIdDst(),
                    "ACK(TRANS.END) idDest should match server ID");
            assertEquals(
                    0,
                    ackTransEnd.getIdSrc(),
                    "ACK(TRANS.END) idSrc should be 0 for file-level FPDUs");
            System.out.println("  ✓ Transfer end signal sent");

            // Step 8: CLOSE
            System.out.println("\nStep 8: CLOSE");

            Fpdu ackClose =
                    session.sendFpduWithAck(
                            new Fpdu(FpduType.CLOSE)
                                    .withIdDst(serverConnectionId)
                                    .withParameter(
                                            new ParameterValue(
                                                    PI_02_DIAG, new byte[] {0x00, 0x00, 0x00})));

            assertEquals(FpduType.ACK_CLOSE, ackClose.getFpduType(), "Expected ACK(CRF)");
            System.out.println("  ✓ File closed");

            // Step 9: DESELECT (mandatory final step with PI 2)
            System.out.println("\nStep 9: DESELECT");

            Fpdu ackDeselect =
                    session.sendFpduWithAck(
                            new Fpdu(FpduType.DESELECT)
                                    .withIdDst(serverConnectionId)
                                    .withParameter(
                                            new ParameterValue(
                                                    PI_02_DIAG, new byte[] {0x00, 0x00, 0x00})));
            assertEquals(
                    FpduType.ACK_DESELECT, ackDeselect.getFpduType(), "Expected ACK(DESELECT)");
            assertEquals(
                    clientConnectionId,
                    ackDeselect.getIdDst(),
                    "ACK(DESELECT) idDest should match client ID");
            assertEquals(
                    0,
                    ackDeselect.getIdSrc(),
                    "ACK(DESELECT) idSrc should be 0 for file-level FPDUs");
            System.out.println("  ✓ File deselected");

            // Step 10: RELEASE (close the PESIT session)
            System.out.println("\nStep 10: RELEASE");

            Fpdu relconf =
                    session.sendFpduWithAck(
                            new Fpdu(FpduType.RELEASE)
                                    .withIdDst(serverConnectionId)
                                    .withIdSrc(clientConnectionId)
                                    .withParameter(
                                            new ParameterValue(
                                                    PI_02_DIAG, new byte[] {0x00, 0x00, 0x00})));
            System.out.println("  ✓ RELEASE sent");

            // Wait for RELCONF (Release Confirmation)
            assertEquals(FpduType.RELCONF, relconf.getFpduType(), "Expected RELCONF after RELEASE");
            assertEquals(
                    clientConnectionId,
                    relconf.getIdDst(),
                    "RELCONF idDest should match client ID");
            assertEquals(
                    serverConnectionId, relconf.getIdSrc(), "RELCONF idSrc should match server ID");
            System.out.println("  ✓ RELCONF received - session closed cleanly");

            System.out.println("\n✓✓✓ COMPLETE FILE TRANSFER SUCCESSFUL! ✓✓✓");
            System.out.println("Check file in /opt/cexp/in/B<REQNUM> on the server");
        }
    }
}
