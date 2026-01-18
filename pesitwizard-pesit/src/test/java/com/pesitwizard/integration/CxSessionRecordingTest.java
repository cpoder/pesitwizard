package com.pesitwizard.integration;

import static org.junit.jupiter.api.Assumptions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.fpdu.PesitSessionRecorder;
import com.pesitwizard.fpdu.SelectMessageBuilder;
import com.pesitwizard.transport.TcpTransportChannel;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag("integration")
public class CxSessionRecordingTest {
    private static final String HOST = "localhost";
    private static final int PORT = 5100;
    private static final Path GOLDEN_DIR = Paths.get("src/test/resources/golden-sessions");
    private PesitSessionRecorder recorder;

    @BeforeAll
    static void setup() throws Exception {
        Files.createDirectories(GOLDEN_DIR);
    }

    @BeforeEach
    void setUp() {
        recorder = new PesitSessionRecorder("cx");
    }

    private boolean isCxAvailable() {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(HOST, PORT), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void recordConnectRelease() throws Exception {
        assumeTrue(isCxAvailable(), "CX not available");
        TcpTransportChannel ch = new TcpTransportChannel(HOST, PORT);
        try (RecordingPesitSession s = new RecordingPesitSession(ch, recorder)) {
            Fpdu c = new ConnectMessageBuilder().demandeur("LOOP").serveur("CETOM1").writeAccess().build(1);
            Fpdu ac = s.sendFpduWithAck(c);
            s.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(ac.getIdSrc()).withIdSrc(1)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
        }
        recorder.saveToFile(GOLDEN_DIR.resolve("connect-release.dat"));
        log.info("Recorded {} frames", recorder.getFrames().size());
    }

    @Test
    void recordSimplePush() throws Exception {
        assumeTrue(isCxAvailable(), "CX not available");
        byte[] data = "Hello PeSIT!".getBytes();
        TcpTransportChannel ch = new TcpTransportChannel(HOST, PORT);
        try (RecordingPesitSession s = new RecordingPesitSession(ch, recorder)) {
            int id = (int) (System.currentTimeMillis() % 100000);
            Fpdu ac = s.sendFpduWithAck(
                    new ConnectMessageBuilder().demandeur("LOOP").serveur("CETOM1").writeAccess().build(1));
            int srv = ac.getIdSrc();
            s.sendFpduWithAck(new CreateMessageBuilder().filename("FILE").transferId(id).variableFormat()
                    .recordLength(1024).maxEntitySize(65535).fileSizeKB(1).build(srv));
            s.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(srv));
            s.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(srv));
            s.sendFpduWithData(new Fpdu(FpduType.DTF).withIdDst(srv), data);
            s.sendFpdu(new Fpdu(FpduType.DTF_END).withIdDst(srv)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
            s.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(srv));
            s.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(srv)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
            s.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(srv)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
            s.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(srv).withIdSrc(1)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
        }
        recorder.saveToFile(GOLDEN_DIR.resolve("simple-push.dat"));
        log.info("Recorded {} frames for PUSH", recorder.getFrames().size());
    }

    @Test
    void recordSimplePull() throws Exception {
        assumeTrue(isCxAvailable(), "CX not available");
        TcpTransportChannel ch = new TcpTransportChannel(HOST, PORT);
        try (RecordingPesitSession s = new RecordingPesitSession(ch, recorder)) {
            Fpdu ac = s.sendFpduWithAck(
                    new ConnectMessageBuilder().demandeur("LOOP").serveur("CETOM1").readAccess().build(1));
            int srv = ac.getIdSrc();
            s.sendFpduWithAck(new SelectMessageBuilder().filename("SMALL").transferId(0).build(srv));
            s.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(srv));
            s.sendFpduWithAck(new Fpdu(FpduType.READ).withIdDst(srv)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_18_POINT_RELANCE, 0)));
            // Receive data until DTF_END
            boolean done = false;
            int frameCount = 0;
            while (!done && frameCount < 100) {
                Fpdu rx = s.receiveFpdu();
                frameCount++;
                if (rx.getFpduType() == FpduType.DTF_END)
                    done = true;
            }
            // Complete transfer sequence
            s.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(srv));
            s.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(srv)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
            s.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(srv)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
            s.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(srv).withIdSrc(1)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG, new byte[] { 0, 0, 0 })));
        }
        recorder.saveToFile(GOLDEN_DIR.resolve("simple-pull.dat"));
        log.info("Recorded {} frames for PULL", recorder.getFrames().size());
    }
}
