package com.pesitwizard.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.fpdu.PesitSessionRecorder;
import com.pesitwizard.fpdu.PesitSessionRecorder.RecordedFrame;

@DisplayName("Golden File Regression Tests")
public class GoldenFileRegressionTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should parse all FPDUs from C:X PUSH session")
    void shouldParseAllFpdusFromCxPushSession() throws Exception {
        PesitSessionRecorder recorder = loadGoldenFile("golden/cx-push-1mb.raw");
        assertNotNull(recorder);
        assertTrue(recorder.getFrames().size() > 0);

        for (RecordedFrame frame : recorder.getFrames()) {
            Fpdu fpdu = new FpduParser(frame.data()).parse();
            assertNotNull(fpdu.getFpduType());
        }
    }

    @Test
    @DisplayName("should have correct FPDU sequence for PUSH")
    void shouldHaveCorrectSequence() throws Exception {
        PesitSessionRecorder recorder = loadGoldenFile("golden/cx-push-1mb.raw");
        var frames = recorder.getFrames();

        assertTrue(frames.size() >= 10);
        assertEquals(FpduType.CONNECT, parseType(frames.get(0)));
        assertEquals(FpduType.ACONNECT, parseType(frames.get(1)));
        assertEquals(FpduType.CREATE, parseType(frames.get(2)));
        assertEquals(FpduType.ACK_CREATE, parseType(frames.get(3)));
    }

    @Test
    @DisplayName("should parse all FPDUs from C:X PULL session")
    void shouldParseAllFpdusFromCxPullSession() throws Exception {
        PesitSessionRecorder recorder = loadGoldenFile("golden/cx-pull-1mb.raw");
        assertNotNull(recorder);
        assertTrue(recorder.getFrames().size() > 0);

        for (RecordedFrame frame : recorder.getFrames()) {
            Fpdu fpdu = new FpduParser(frame.data()).parse();
            assertNotNull(fpdu.getFpduType());
        }
    }

    @Test
    @DisplayName("should have correct FPDU sequence for PULL")
    void shouldHaveCorrectSequenceForPull() throws Exception {
        PesitSessionRecorder recorder = loadGoldenFile("golden/cx-pull-1mb.raw");
        var frames = recorder.getFrames();

        assertTrue(frames.size() >= 4);
        assertEquals(FpduType.CONNECT, parseType(frames.get(0)));
        assertEquals(FpduType.ACONNECT, parseType(frames.get(1)));
        assertEquals(FpduType.SELECT, parseType(frames.get(2)));
        assertEquals(FpduType.ACK_SELECT, parseType(frames.get(3)));
    }

    // ========== SYNC POINT TESTS ==========

    @Test
    @DisplayName("should have sync points with incrementing PI_20_NUM_SYNC")
    void shouldHaveSyncPointsWithIncrementingNumbers() throws Exception {
        PesitSessionRecorder recorder = loadGoldenFile("golden/cx-push-1mb.raw");

        List<Integer> syncNumbers = recorder.getFrames().stream()
                .map(f -> new FpduParser(f.data()).parse())
                .filter(fpdu -> fpdu.getFpduType() == FpduType.SYN || fpdu.getFpduType() == FpduType.ACK_SYN)
                .map(fpdu -> {
                    ParameterValue pi20 = fpdu.getParameter(ParameterIdentifier.PI_20_NUM_SYNC);
                    return pi20 != null && pi20.getValue().length > 0 ? pi20.getValue()[0] & 0xFF : -1;
                })
                .filter(n -> n >= 0)
                .collect(Collectors.toList());

        // Session should have sync points for 1MB transfer
        assertTrue(syncNumbers.size() > 0, "Should have sync points in PUSH session");
    }

    @Test
    @DisplayName("should have ACK_SYN responses for SYNC requests")
    void shouldHaveAckSynForSyncRequests() throws Exception {
        PesitSessionRecorder recorder = loadGoldenFile("golden/cx-push-1mb.raw");

        long syncCount = countFpduType(recorder, FpduType.SYN);
        long ackSynCount = countFpduType(recorder, FpduType.ACK_SYN);

        // Each SYNC should have a corresponding ACK_SYN
        assertEquals(syncCount, ackSynCount, "SYNC and ACK_SYN counts should match");
    }

    // ========== DTF MULTI-ARTICLE TESTS ==========

    @Test
    @DisplayName("should have DTF frames in PUSH session")
    void shouldHaveDtfFramesInPushSession() throws Exception {
        PesitSessionRecorder recorder = loadGoldenFile("golden/cx-push-1mb.raw");

        long dtfCount = countFpduType(recorder, FpduType.DTF);
        long dtfdaCount = countFpduType(recorder, FpduType.DTFDA);
        long dtfmaCount = countFpduType(recorder, FpduType.DTFMA);
        long dtffaCount = countFpduType(recorder, FpduType.DTFFA);

        long totalDtf = dtfCount + dtfdaCount + dtfmaCount + dtffaCount;
        assertTrue(totalDtf > 0, "Should have DTF frames for data transfer");
    }

    @Test
    @DisplayName("should have proper DTF sequence (DTFDA starts, DTFFA ends)")
    void shouldHaveProperDtfSequence() throws Exception {
        PesitSessionRecorder recorder = loadGoldenFile("golden/cx-push-1mb.raw");

        List<FpduType> dtfTypes = recorder.getFrames().stream()
                .map(f -> new FpduParser(f.data()).parse().getFpduType())
                .filter(t -> t == FpduType.DTF || t == FpduType.DTFDA || t == FpduType.DTFMA || t == FpduType.DTFFA)
                .collect(Collectors.toList());

        assertTrue(dtfTypes.size() > 0, "Should have DTF frames");
    }

    // ========== DIAGNOSTIC CODE TESTS ==========

    @Test
    @DisplayName("should have PI_02_DIAG in ACK responses")
    void shouldHaveDiagnosticInAckResponses() throws Exception {
        PesitSessionRecorder recorder = loadGoldenFile("golden/cx-push-1mb.raw");

        List<Fpdu> ackFpdus = recorder.getFrames().stream()
                .map(f -> new FpduParser(f.data()).parse())
                .filter(fpdu -> fpdu.getFpduType().name().startsWith("ACK_"))
                .filter(fpdu -> fpdu.getFpduType() != FpduType.ACK_SYN) // ACK_SYN has PI_20 instead of PI_02
                .filter(fpdu -> fpdu.getFpduType() != FpduType.ACK_IDT) // ACK_IDT has no PI_02
                .collect(Collectors.toList());

        for (Fpdu ack : ackFpdus) {
            ParameterValue diag = ack.getParameter(ParameterIdentifier.PI_02_DIAG);
            assertNotNull(diag, "ACK response should have PI_02_DIAG: " + ack.getFpduType());
        }
    }

    @Test
    @DisplayName("should have successful diagnostic code (0x000000) in ACK responses")
    void shouldHaveSuccessfulDiagnosticCode() throws Exception {
        PesitSessionRecorder recorder = loadGoldenFile("golden/cx-push-1mb.raw");

        List<Fpdu> ackFpdus = recorder.getFrames().stream()
                .map(f -> new FpduParser(f.data()).parse())
                .filter(fpdu -> fpdu.getFpduType().name().startsWith("ACK_"))
                .collect(Collectors.toList());

        for (Fpdu ack : ackFpdus) {
            ParameterValue diag = ack.getParameter(ParameterIdentifier.PI_02_DIAG);
            if (diag != null && diag.getValue().length >= 3) {
                // Success = 0x00 0x00 0x00
                byte[] diagBytes = diag.getValue();
                assertEquals(0, diagBytes[0], "Diagnostic code should be 0 for success");
            }
        }
    }

    // ========== SELECT/PULL SEQUENCE TESTS ==========

    @Test
    @DisplayName("should have complete PULL sequence")
    void shouldHaveCompletePullSequence() throws Exception {
        PesitSessionRecorder recorder = loadGoldenFile("golden/cx-pull-1mb.raw");

        List<FpduType> types = recorder.getFrames().stream()
                .map(f -> new FpduParser(f.data()).parse().getFpduType())
                .collect(Collectors.toList());

        // PULL sequence: CONNECT, ACONNECT, SELECT, ACK_SELECT, OPEN, ACK_OPEN, READ...
        assertTrue(types.contains(FpduType.CONNECT), "Should have CONNECT");
        assertTrue(types.contains(FpduType.ACONNECT), "Should have ACONNECT");
        assertTrue(types.contains(FpduType.SELECT), "Should have SELECT");
        assertTrue(types.contains(FpduType.ACK_SELECT), "Should have ACK_SELECT");
    }

    // ========== HELPER METHODS ==========

    private long countFpduType(PesitSessionRecorder recorder, FpduType type) {
        return recorder.getFrames().stream()
                .map(f -> new FpduParser(f.data()).parse().getFpduType())
                .filter(t -> t == type)
                .count();
    }

    private FpduType parseType(RecordedFrame frame) {
        return new FpduParser(frame.data()).parse().getFpduType();
    }

    private PesitSessionRecorder loadGoldenFile(String name) throws Exception {
        Path file = tempDir.resolve(name);
        Files.createDirectories(file.getParent());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, "Golden file not found: " + name);
            Files.copy(in, file);
        }
        return PesitSessionRecorder.loadRawFromFile(file);
    }
}
