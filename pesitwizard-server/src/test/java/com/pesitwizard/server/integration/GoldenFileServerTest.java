package com.pesitwizard.server.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.PesitSessionRecorder;
import com.pesitwizard.fpdu.PesitSessionRecorder.Direction;
import com.pesitwizard.fpdu.PesitSessionRecorder.RecordedFrame;

@DisplayName("Server Golden File Tests")
public class GoldenFileServerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("verify request-response pairs from PUSH session")
    void verifyRequestResponsePairs() throws Exception {
        var recorder = loadGoldenFile("golden/cx-push-1mb.raw");
        var frames = recorder.getFrames();

        // Count by direction
        long clientCount = frames.stream().filter(f -> f.direction() == Direction.RECEIVED).count();
        long serverCount = frames.stream().filter(f -> f.direction() == Direction.SENT).count();

        assertTrue(clientCount > 0);
        assertTrue(serverCount > 0);

        // Verify key responses exist
        assertTrue(hasType(frames, Direction.SENT, FpduType.ACONNECT));
        assertTrue(hasType(frames, Direction.SENT, FpduType.ACK_CREATE));
        assertTrue(hasType(frames, Direction.SENT, FpduType.ACK_OPEN));
        assertTrue(hasType(frames, Direction.SENT, FpduType.ACK_CLOSE));
    }

    private boolean hasType(List<RecordedFrame> frames, Direction dir, FpduType type) {
        return frames.stream()
                .anyMatch(f -> f.direction() == dir && new FpduParser(f.data()).parse().getFpduType() == type);
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
