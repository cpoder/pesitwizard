package com.pesitwizard.client.integration;

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

@DisplayName("Client Golden File Tests")
public class GoldenFileClientTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("verify client requests from PUSH session")
    void verifyClientRequests() throws Exception {
        var recorder = loadGoldenFile("golden/cx-push-1mb.raw");
        var frames = recorder.getFrames();

        // Get client frames (RECEIVED = from client perspective, these are what client
        // sent)
        var clientFrames = frames.stream()
                .filter(f -> f.direction() == Direction.RECEIVED)
                .toList();

        assertTrue(clientFrames.size() > 0);

        // Verify key requests exist (for PUSH: CONNECT, CREATE, OPEN, WRITE, DTF*,
        // CLOSE, DESELECT, RELEASE)
        assertTrue(hasType(clientFrames, FpduType.CONNECT));
        assertTrue(hasType(clientFrames, FpduType.CREATE));
        assertTrue(hasType(clientFrames, FpduType.OPEN));
        assertTrue(hasType(clientFrames, FpduType.CLOSE));
        assertTrue(hasType(clientFrames, FpduType.DESELECT));
        assertTrue(hasType(clientFrames, FpduType.RELEASE));
    }

    @Test
    @DisplayName("verify DTF frames contain data")
    void verifyDtfFramesContainData() throws Exception {
        var recorder = loadGoldenFile("golden/cx-push-1mb.raw");

        long dtfCount = recorder.getFrames().stream()
                .filter(f -> f.direction() == Direction.RECEIVED)
                .filter(f -> {
                    FpduType t = new FpduParser(f.data()).parse().getFpduType();
                    return t == FpduType.DTF || t == FpduType.DTFDA || t == FpduType.DTFMA || t == FpduType.DTFFA;
                })
                .count();

        assertTrue(dtfCount > 0, "Should have DTF frames for data transfer");
    }

    private boolean hasType(List<RecordedFrame> frames, FpduType type) {
        return frames.stream().anyMatch(f -> new FpduParser(f.data()).parse().getFpduType() == type);
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
