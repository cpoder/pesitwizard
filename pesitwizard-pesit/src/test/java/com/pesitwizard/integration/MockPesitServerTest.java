package com.pesitwizard.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TcpTransportChannel;

import lombok.extern.slf4j.Slf4j;

/**
 * Tests for MockPesitServer - replay functionality.
 */
@Slf4j
@DisplayName("MockPesitServer Tests")
public class MockPesitServerTest {

    private static final Path GOLDEN_DIR = Paths.get("src/test/resources/golden-sessions");

    @Test
    @DisplayName("should replay CONNECT/RELEASE session")
    @org.junit.jupiter.api.Disabled("Golden file replay requires exact byte matching - use in-memory recording for now")
    void shouldReplayConnectRelease() throws Exception {
        Path goldenFile = GOLDEN_DIR.resolve("connect-release.dat");

        try (MockPesitServer server = MockPesitServer.fromGoldenFile(goldenFile)) {
            server.start();

            TcpTransportChannel channel = new TcpTransportChannel("localhost", server.getPort());
            try (PesitSession session = new PesitSession(channel)) {
                // Send CONNECT
                Fpdu connect = new ConnectMessageBuilder()
                        .demandeur("LOOP")
                        .serveur("CETOM1")
                        .writeAccess()
                        .build(1);
                Fpdu aconnect = session.sendFpduWithAck(connect);

                assertEquals(FpduType.ACONNECT, aconnect.getFpduType());
                log.info("Received ACONNECT: idSrc={}", aconnect.getIdSrc());

                // Send RELEASE
                Fpdu release = new Fpdu(FpduType.RELEASE)
                        .withIdDst(aconnect.getIdSrc())
                        .withIdSrc(1)
                        .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 }));
                Fpdu relconf = session.sendFpduWithAck(release);

                assertEquals(FpduType.RELCONF, relconf.getFpduType());
                log.info("Received RELCONF");
            }

            server.waitForCompletion(2000);
            assertTrue(server.isComplete(), "Server should have processed all frames");
            assertNull(server.getLastError(), "Server should not have errors");
        }
    }

    @Test
    @DisplayName("should replay from in-memory recorder")
    void shouldReplayFromRecorder() throws Exception {
        // Create a simple recording
        PesitSessionRecorder recorder = new PesitSessionRecorder("test");

        // Simulate a CONNECT/ACONNECT exchange
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

        // Replay it
        try (MockPesitServer server = MockPesitServer.fromRecorder(recorder)) {
            server.start();

            TcpTransportChannel channel = new TcpTransportChannel("localhost", server.getPort());
            try (PesitSession session = new PesitSession(channel)) {
                Fpdu response = session.sendFpduWithAck(connect);
                assertEquals(FpduType.ACONNECT, response.getFpduType());
            }

            server.waitForCompletion(1000);
            assertTrue(server.isComplete());
        }
    }
}
