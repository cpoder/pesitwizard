package com.pesitwizard.integration;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.fpdu.SelectMessageBuilder;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TcpTransportChannel;

/**
 * Receive file test - receives file data from Connect:Express
 * Requires Connect:Express running on localhost:5100
 * 
 * Run with: mvn test -Dtest=ReceiveFileTest -Dpesit.test.port=5100
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReceiveFileTest {

    private static final String TEST_HOST = System.getProperty("pesit.test.host", "localhost");
    private static final int TEST_PORT = Integer.parseInt(System.getProperty("pesit.test.port", "5100"));
    private static final boolean INTEGRATION_ENABLED = Boolean.parseBoolean(
            System.getProperty("pesit.integration.enabled", "false"));

    @BeforeAll
    void setUp() {
        Assumptions.assumeTrue(INTEGRATION_ENABLED,
                "Integration tests disabled. Enable with -Dpesit.integration.enabled=true");
    }

    @Test
    @DisplayName("Receive file: CONNECT → SELECT → OPEN → READ → DTF... → DTF.END → TRANS.END → CLOSE → DESELECT → RELEASE")
    void testReceiveFile() throws IOException, InterruptedException {
        System.out.println("\n=== RECEIVE FILE TEST ===\n");

        try (PesitSession session = new PesitSession(new TcpTransportChannel(TEST_HOST, TEST_PORT))) {
            // Step 1: CONNECT with read access
            System.out.println("Step 1: CONNECT (read access)");
            int clientConnectionId = 0x05;

            String serverId = System.getProperty("pesit.test.server", "CETOM1");
            ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                    .demandeur("LOOP")
                    .serveur(serverId)
                    .readAccess();

            Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(clientConnectionId));
            assertEquals(FpduType.ACONNECT, aconnect.getFpduType(), "Expected ACONNECT");

            int serverConnectionId = aconnect.getIdSrc();
            System.out.println("  ✓ Session established, server ID: " + serverConnectionId);

            // Step 2: SELECT - request existing file
            System.out.println("\nStep 2: SELECT");
            String filename = System.getProperty("pesit.test.file", "FOUT");

            SelectMessageBuilder selectBuilder = new SelectMessageBuilder()
                    .filename(filename)
                    .transferId(1);

            Fpdu ackSelect = session.sendFpduWithAck(selectBuilder.build(serverConnectionId));
            assertEquals(FpduType.ACK_SELECT, ackSelect.getFpduType(), "Expected ACK_SELECT");
            System.out.println("  ✓ SELECT successful for file: " + filename);

            // Step 3: OPEN
            System.out.println("\nStep 3: OPEN");
            Fpdu ackOpen = session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnectionId));
            assertEquals(FpduType.ACK_OPEN, ackOpen.getFpduType(), "Expected ACK_OPEN");
            System.out.println("  ✓ File opened");

            // Step 4: READ - request data (PI_18 restart point = 0 for start)
            System.out.println("\nStep 4: READ");
            Fpdu ackRead = session.sendFpduWithAck(new Fpdu(FpduType.READ)
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
                if (fpdu.getFpduType() == FpduType.DTF) {
                    byte[] data = fpdu.getData();
                    if (data != null && data.length > 0) {
                        dataBuffer.write(data);
                        dtfCount++;
                    }
                } else if (fpdu.getFpduType() == FpduType.DTF_END) {
                    System.out.println("  ✓ Received DTF.END after " + dtfCount + " DTF packets");
                    break;
                } else {
                    System.out.println("  Unexpected FPDU: " + fpdu.getFpduType());
                    break;
                }
            }

            byte[] receivedData = dataBuffer.toByteArray();
            System.out.println("  ✓ Received " + receivedData.length + " bytes total");

            // Step 6: TRANS.END
            System.out.println("\nStep 6: TRANS.END");
            Fpdu ackTransEnd = session.sendFpduWithAck(
                    new Fpdu(FpduType.TRANS_END).withIdDst(serverConnectionId));
            assertEquals(FpduType.ACK_TRANS_END, ackTransEnd.getFpduType(), "Expected ACK_TRANS_END");
            System.out.println("  ✓ Transfer completed");

            // Step 7: CLOSE
            System.out.println("\nStep 7: CLOSE");
            Fpdu ackClose = session.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(serverConnectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 })));
            assertEquals(FpduType.ACK_CLOSE, ackClose.getFpduType(), "Expected ACK_CLOSE");
            System.out.println("  ✓ File closed");

            // Step 8: DESELECT
            System.out.println("\nStep 8: DESELECT");
            Fpdu ackDeselect = session.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(serverConnectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 })));
            assertEquals(FpduType.ACK_DESELECT, ackDeselect.getFpduType(), "Expected ACK_DESELECT");
            System.out.println("  ✓ File deselected");

            // Step 9: RELEASE
            System.out.println("\nStep 9: RELEASE");
            Fpdu relconf = session.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(serverConnectionId)
                    .withIdSrc(clientConnectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 })));
            assertEquals(FpduType.RELCONF, relconf.getFpduType(), "Expected RELCONF");
            System.out.println("  ✓ Session closed");

            System.out.println("\n✓✓✓ RECEIVE FILE TEST SUCCESSFUL! ✓✓✓");
            System.out.println("Received " + receivedData.length + " bytes");
            if (receivedData.length > 0 && receivedData.length < 200) {
                System.out.println("Content: " + new String(receivedData));
            }
        }
    }
}
