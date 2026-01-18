package com.pesitwizard.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.integration.PesitSessionRecorder.Direction;
import com.pesitwizard.integration.PesitSessionRecorder.RecordedFrame;

/**
 * Tests for session recording and replay functionality.
 */
@DisplayName("Session Replay Tests")
public class SessionReplayTest {

    @TempDir
    Path tempDir;

    private PesitSessionRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new PesitSessionRecorder("test-session");
    }

    @Test
    @DisplayName("should record FPDU frames")
    void shouldRecordFpduFrames() {
        Fpdu fpdu = new Fpdu(FpduType.DTF);
        fpdu.setIdDst(1);
        fpdu.setIdSrc(0);

        recorder.record(Direction.SENT, fpdu);

        assertEquals(1, recorder.getFrames().size());
        RecordedFrame frame = recorder.getFrames().get(0);
        assertEquals(Direction.SENT, frame.direction());
        assertEquals(FpduType.DTF, frame.type());
    }

    @Test
    @DisplayName("should save and load recording")
    void shouldSaveAndLoadRecording() throws Exception {
        Fpdu fpdu1 = new Fpdu(FpduType.DTF).withIdDst(1);
        Fpdu fpdu2 = new Fpdu(FpduType.DTFDA).withIdDst(1);

        recorder.record(Direction.SENT, fpdu1);
        recorder.record(Direction.RECEIVED, fpdu2);

        Path file = tempDir.resolve("golden/test.dat");
        recorder.saveToFile(file);

        PesitSessionRecorder loaded = PesitSessionRecorder.loadFromFile(file);

        assertEquals(2, loaded.getFrames().size());
        assertEquals(Direction.SENT, loaded.getFrames().get(0).direction());
        assertEquals(Direction.RECEIVED, loaded.getFrames().get(1).direction());
    }

    @Test
    @DisplayName("should compare recorded sessions")
    void shouldCompareRecordedSessions() {
        // Record expected session
        recorder.record(Direction.SENT, new Fpdu(FpduType.DTF).withIdDst(1));
        recorder.record(Direction.RECEIVED, new Fpdu(FpduType.DTFDA).withIdDst(1));

        List<RecordedFrame> expected = recorder.getFrames();

        // Simulate actual session
        PesitSessionRecorder actual = new PesitSessionRecorder("actual");
        actual.record(Direction.SENT, new Fpdu(FpduType.DTF).withIdDst(1));
        actual.record(Direction.RECEIVED, new Fpdu(FpduType.DTFDA).withIdDst(1));

        // Compare
        assertEquals(expected.size(), actual.getFrames().size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).direction(), actual.getFrames().get(i).direction());
            assertEquals(expected.get(i).type(), actual.getFrames().get(i).type());
        }
    }

    @Test
    @DisplayName("should record raw byte data")
    void shouldRecordRawByteData() {
        byte[] rawData = new byte[] { 0x00, 0x06, 0x40, 0x21, 0x01, 0x00 };

        recorder.recordRaw(Direction.RECEIVED, FpduType.DTF, rawData);

        assertEquals(1, recorder.getFrames().size());
        assertArrayEquals(rawData, recorder.getFrames().get(0).data());
    }

    @Test
    @DisplayName("should clear recorded frames")
    void shouldClearRecordedFrames() {
        recorder.record(Direction.SENT, new Fpdu(FpduType.DTF));
        recorder.record(Direction.SENT, new Fpdu(FpduType.DTFMA));

        recorder.clear();

        assertTrue(recorder.getFrames().isEmpty());
    }
}
