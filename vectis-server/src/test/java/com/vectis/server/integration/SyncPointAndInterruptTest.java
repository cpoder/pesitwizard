package com.vectis.server.integration;

import static com.vectis.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.vectis.fpdu.ConnectMessageBuilder;
import com.vectis.fpdu.CreateMessageBuilder;
import com.vectis.fpdu.Fpdu;
import com.vectis.fpdu.FpduType;
import com.vectis.fpdu.ParameterValue;
import com.vectis.session.PesitSession;
import com.vectis.transport.TcpTransportChannel;

/**
 * Integration tests for sync points, transfer resumption, and interruptions.
 * 
 * Requires a PeSIT server running on localhost:5000
 * Run with: mvn test -Dtest=SyncPointAndInterruptTest -Dpesit.integration.enabled=true
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SyncPointAndInterruptTest {

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
    @DisplayName("Test sync points during data transfer")
    void testSyncPointsDuringTransfer() throws Exception {
        System.out.println("\n=== SYNC POINTS TEST ===\n");

        try (PesitSession session = new PesitSession(new TcpTransportChannel(HOST, PORT))) {
            // Step 1: CONNECT with sync points
            System.out.println("Step 1: CONNECT");
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur("LOOP")
                            .serveur("PESIT_SERVER")
                            .build(CLIENT_ID));
            assertEquals(FpduType.ACONNECT, aconnect.getFpduType());
            int serverConnId = aconnect.getIdSrc();
            System.out.println("  ✓ Connected, server ID: " + serverConnId);

            // Step 2: CREATE
            System.out.println("\nStep 2: CREATE");
            Fpdu ackCreate = session.sendFpduWithAck(
                    new CreateMessageBuilder()
                            .filename("SYNCTEST")
                            .transferId(1)
                            .maxEntitySize(56)
                            .build(serverConnId));
            assertEquals(FpduType.ACK_CREATE, ackCreate.getFpduType());
            System.out.println("  ✓ File created");

            // Step 3: OPEN
            System.out.println("\nStep 3: OPEN");
            Fpdu ackOpen = session.sendFpduWithAck(
                    new Fpdu(FpduType.OPEN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            assertEquals(FpduType.ACK_OPEN, ackOpen.getFpduType());
            System.out.println("  ✓ File opened");

            // Step 4: WRITE
            System.out.println("\nStep 4: WRITE");
            Fpdu ackWrite = session.sendFpduWithAck(
                    new Fpdu(FpduType.WRITE).withIdDst(serverConnId));
            assertEquals(FpduType.ACK_WRITE, ackWrite.getFpduType());
            System.out.println("  ✓ Write initiated");

            // Step 5: Send data with sync points
            System.out.println("\nStep 5: Send data with sync points");

            // Send first chunk
            session.sendFpduWithData(
                    new Fpdu(FpduType.DTF).withIdDst(serverConnId),
                    "First chunk of data. ".getBytes());
            System.out.println("  ✓ Sent first chunk");

            // Send SYN 1
            Fpdu ackSyn1 = session.sendFpduWithAck(
                    new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_20_NUM_SYNC, 1)));
            assertEquals(FpduType.ACK_SYN, ackSyn1.getFpduType(), "Expected ACK_SYN");
            System.out.println("  ✓ Sync point 1 acknowledged");

            // Send second chunk
            session.sendFpduWithData(
                    new Fpdu(FpduType.DTF).withIdDst(serverConnId),
                    "Second chunk. ".getBytes());
            System.out.println("  ✓ Sent second chunk");

            // Send SYN 2
            Fpdu ackSyn2 = session.sendFpduWithAck(
                    new Fpdu(FpduType.SYN)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_20_NUM_SYNC, 2)));
            assertEquals(FpduType.ACK_SYN, ackSyn2.getFpduType(), "Expected ACK_SYN");
            System.out.println("  ✓ Sync point 2 acknowledged");

            // Send third chunk
            session.sendFpduWithData(
                    new Fpdu(FpduType.DTF).withIdDst(serverConnId),
                    "Third chunk.".getBytes());
            System.out.println("  ✓ Sent third chunk");

            // Step 6: DTF.END
            System.out.println("\nStep 6: DTF.END");
            session.sendFpdu(new Fpdu(FpduType.DTF_END)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            System.out.println("  ✓ DTF.END sent");

            // Step 7: TRANS.END
            System.out.println("\nStep 7: TRANS.END");
            Fpdu ackTransEnd = session.sendFpduWithAck(
                    new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId));
            assertEquals(FpduType.ACK_TRANS_END, ackTransEnd.getFpduType());
            System.out.println("  ✓ Transfer completed");

            // Cleanup
            System.out.println("\nStep 8: Cleanup");
            session.sendFpduWithAck(new Fpdu(FpduType.CLOSE)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            session.sendFpduWithAck(new Fpdu(FpduType.DESELECT)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            session.sendFpdu(new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnId)
                    .withIdSrc(CLIENT_ID)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            System.out.println("  ✓ Session closed");
        }

        System.out.println("\n✓✓✓ SYNC POINTS TEST PASSED ✓✓✓\n");
    }

    @Test
    @DisplayName("Test transfer interruption with IDT")
    void testTransferInterruption() throws Exception {
        System.out.println("\n=== TRANSFER INTERRUPTION TEST ===\n");

        try (PesitSession session = new PesitSession(new TcpTransportChannel(HOST, PORT))) {
            // Connect
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur("LOOP")
                            .serveur("PESIT_SERVER")
                            .build(CLIENT_ID));
            int serverConnId = aconnect.getIdSrc();
            System.out.println("Step 1: Connected");

            // CREATE
            session.sendFpduWithAck(new CreateMessageBuilder()
                    .filename("IDTTEST")
                    .transferId(1)
                    .maxEntitySize(56)
                    .build(serverConnId));
            System.out.println("Step 2: File created");

            // OPEN
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            System.out.println("Step 3: File opened");

            // WRITE
            session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverConnId));
            System.out.println("Step 4: Write initiated");

            // Send partial data
            session.sendFpduWithData(
                    new Fpdu(FpduType.DTF).withIdDst(serverConnId),
                    "Partial data before interrupt.".getBytes());
            System.out.println("Step 5: Sent partial data");

            // IDT (Interrupt)
            Fpdu ackIdt = session.sendFpduWithAck(
                    new Fpdu(FpduType.IDT)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            assertEquals(FpduType.ACK_IDT, ackIdt.getFpduType(), "Expected ACK_IDT");
            System.out.println("Step 6: Transfer interrupted, ACK_IDT received");

            // CLOSE after interrupt
            session.sendFpduWithAck(new Fpdu(FpduType.CLOSE)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            System.out.println("Step 7: File closed after interrupt");

            // Cleanup
            session.sendFpduWithAck(new Fpdu(FpduType.DESELECT)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            session.sendFpdu(new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnId)
                    .withIdSrc(CLIENT_ID)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
        }

        System.out.println("\n✓✓✓ TRANSFER INTERRUPTION TEST PASSED ✓✓✓\n");
    }

    @Test
    @DisplayName("Test transfer restart from sync point")
    void testTransferRestart() throws Exception {
        System.out.println("\n=== TRANSFER RESTART TEST ===\n");

        try (PesitSession session = new PesitSession(new TcpTransportChannel(HOST, PORT))) {
            // Connect with resync
            Fpdu aconnect = session.sendFpduWithAck(
                    new ConnectMessageBuilder()
                            .demandeur("LOOP")
                            .serveur("PESIT_SERVER")
                            .build(CLIENT_ID));
            int serverConnId = aconnect.getIdSrc();
            System.out.println("Step 1: Connected");

            // CREATE
            session.sendFpduWithAck(new CreateMessageBuilder()
                    .filename("RESTARTTEST")
                    .transferId(1)
                    .maxEntitySize(56)
                    .build(serverConnId));
            System.out.println("Step 2: File created");

            // OPEN
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            System.out.println("Step 3: File opened");

            // WRITE with restart point
            Fpdu ackWrite = session.sendFpduWithAck(
                    new Fpdu(FpduType.WRITE)
                            .withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_18_POINT_RELANCE, 100)));
            assertEquals(FpduType.ACK_WRITE, ackWrite.getFpduType());
            assertTrue(ackWrite.hasParameter(PI_18_POINT_RELANCE), "ACK_WRITE should have restart point");
            System.out.println("Step 4: Write with restart point acknowledged");

            // Send resumed data
            session.sendFpduWithData(
                    new Fpdu(FpduType.DTF).withIdDst(serverConnId),
                    "Resumed data after restart.".getBytes());
            System.out.println("Step 5: Sent resumed data");

            // Complete transfer
            session.sendFpdu(new Fpdu(FpduType.DTF_END)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            session.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId));
            System.out.println("Step 6: Transfer completed");

            // Cleanup
            session.sendFpduWithAck(new Fpdu(FpduType.CLOSE)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            session.sendFpduWithAck(new Fpdu(FpduType.DESELECT)
                    .withIdDst(serverConnId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
            session.sendFpdu(new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnId)
                    .withIdSrc(CLIENT_ID)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[]{0, 0, 0})));
        }

        System.out.println("\n✓✓✓ TRANSFER RESTART TEST PASSED ✓✓✓\n");
    }
}
