package com.pesitwizard.integration;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduType;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Records PESIT sessions for replay in integration tests.
 * Captures FPDU exchanges to create "golden files" from real server
 * interactions.
 */
@Slf4j
public class PesitSessionRecorder {

    @Getter
    private final List<RecordedFrame> frames = new ArrayList<>();
    private final String sessionName;

    public PesitSessionRecorder(String sessionName) {
        this.sessionName = sessionName;
    }

    public void record(Direction direction, Fpdu fpdu) {
        byte[] data = FpduBuilder.buildFpdu(fpdu);
        frames.add(new RecordedFrame(
                Instant.now(),
                direction,
                fpdu.getFpduType(),
                data));
        log.debug("Recorded {} {} FPDU ({} bytes)",
                direction, fpdu.getFpduType(), data.length);
    }

    public void recordRaw(Direction direction, FpduType type, byte[] data) {
        frames.add(new RecordedFrame(
                Instant.now(),
                direction,
                type,
                data.clone()));
        log.debug("Recorded raw {} {} FPDU ({} bytes)",
                direction, type, data.length);
    }

    public void saveToFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (ObjectOutputStream out = new ObjectOutputStream(
                Files.newOutputStream(path))) {
            out.writeObject(sessionName);
            out.writeObject(frames);
        }
        log.info("Saved {} frames to {}", frames.size(), path);
    }

    @SuppressWarnings("unchecked")
    public static PesitSessionRecorder loadFromFile(Path path) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(
                Files.newInputStream(path))) {
            String name = (String) in.readObject();
            List<RecordedFrame> frames = (List<RecordedFrame>) in.readObject();
            PesitSessionRecorder recorder = new PesitSessionRecorder(name);
            recorder.frames.addAll(frames);
            log.info("Loaded {} frames from {}", frames.size(), path);
            return recorder;
        }
    }

    public void clear() {
        frames.clear();
    }

    public enum Direction {
        SENT, // Client -> Server
        RECEIVED // Server -> Client
    }

    public record RecordedFrame(
            Instant timestamp,
            Direction direction,
            FpduType type,
            byte[] data) implements Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            return String.format("[%s] %s %s (%d bytes)",
                    timestamp, direction, type, data.length);
        }
    }
}
