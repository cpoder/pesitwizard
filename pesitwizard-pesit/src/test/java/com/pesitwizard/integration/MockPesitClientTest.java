package com.pesitwizard.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.fpdu.PesitSessionRecorder;

import lombok.extern.slf4j.Slf4j;

/**
 * Tests for MockPesitClient - client replay functionality.
 */
@Slf4j
@DisplayName("MockPesitClient Tests")
public class MockPesitClientTest {

        @Test
        @DisplayName("should replay client session against mock server")
        void shouldReplayClientSession() throws Exception {
                // Record a client session
                PesitSessionRecorder recorder = new PesitSessionRecorder("client-test");

                Fpdu connect = new ConnectMessageBuilder()
                                .demandeur("TEST")
                                .serveur("MOCK")
                                .writeAccess()
                                .build(1);
                recorder.record(PesitSessionRecorder.Direction.SENT, connect);

                Fpdu aconnect = new Fpdu(FpduType.ACONNECT)
                                .withIdSrc(99)
                                .withIdDst(1)
                                .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, "D"));
                recorder.record(PesitSessionRecorder.Direction.RECEIVED, aconnect);

                Fpdu release = new Fpdu(FpduType.RELEASE)
                                .withIdSrc(1)
                                .withIdDst(99)
                                .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG,
                                                new byte[] { 0, 0, 0 }));
                recorder.record(PesitSessionRecorder.Direction.SENT, release);

                Fpdu relconf = new Fpdu(FpduType.RELCONF)
                                .withIdSrc(99)
                                .withIdDst(1);
                recorder.record(PesitSessionRecorder.Direction.RECEIVED, relconf);

                // Start a mock server that will respond
                try (MockPesitServer server = MockPesitServer.fromRecorder(recorder)) {
                        server.start();

                        // Replay the client session against the mock server
                        try (MockPesitClient client = MockPesitClient.fromRecorder(recorder)) {
                                client.replay("localhost", server.getPort());

                                assertTrue(client.isComplete(), "Client should complete all frames");
                                assertNull(client.getLastError(), "Client should have no errors");
                                assertEquals(2, client.getReceivedFpdus().size(), "Should receive 2 FPDUs");
                                assertEquals(FpduType.ACONNECT, client.getReceivedFpdus().get(0).getFpduType());
                                assertEquals(FpduType.RELCONF, client.getReceivedFpdus().get(1).getFpduType());
                        }

                        assertTrue(server.isComplete(), "Server should complete all frames");
                }
        }

        @Test
        @DisplayName("should track received FPDUs")
        void shouldTrackReceivedFpdus() throws Exception {
                PesitSessionRecorder recorder = new PesitSessionRecorder("tracking-test");

                Fpdu connect = new ConnectMessageBuilder()
                                .demandeur("TEST")
                                .serveur("MOCK")
                                .writeAccess()
                                .build(1);
                recorder.record(PesitSessionRecorder.Direction.SENT, connect);

                Fpdu aconnect = new Fpdu(FpduType.ACONNECT)
                                .withIdSrc(50)
                                .withIdDst(1)
                                .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, "D"));
                recorder.record(PesitSessionRecorder.Direction.RECEIVED, aconnect);

                try (MockPesitServer server = MockPesitServer.fromRecorder(recorder);
                                MockPesitClient client = MockPesitClient.fromRecorder(recorder)) {

                        server.start();
                        client.replay("localhost", server.getPort());

                        assertEquals(1, client.getReceivedFpdus().size());
                        Fpdu received = client.getReceivedFpdus().get(0);
                        assertEquals(FpduType.ACONNECT, received.getFpduType());
                        assertEquals(50, received.getIdSrc());
                }
        }
}
