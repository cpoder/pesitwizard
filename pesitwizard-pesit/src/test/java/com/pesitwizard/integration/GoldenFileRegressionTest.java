package com.pesitwizard.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
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
