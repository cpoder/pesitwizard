package com.pesitwizard.fpdu;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    public String getSessionName() {
        return sessionName;
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

    public void recordRaw(Direction direction, byte[] data) {
        // Parse FPDU type from raw bytes
        FpduType type = null;
        if (data.length >= 3) {
            try {
                Fpdu fpdu = new FpduParser(data).parse();
                type = fpdu.getFpduType();
            } catch (Exception e) {
                log.warn("Could not parse FPDU type from raw data: {}", e.getMessage());
            }
        }
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

    /**
     * Save raw PeSIT frames to a binary file.
     * Format per frame:
     * - 1 byte: direction (0=RECEIVED, 1=SENT)
     * - 2 bytes: frame length (big-endian)
     * - N bytes: raw FPDU data
     * 
     * This format is portable and can be analyzed with standard tools.
     */
    public void saveRawToFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                Files.newOutputStream(path))) {
            for (RecordedFrame frame : frames) {
                out.writeByte(frame.direction() == Direction.RECEIVED ? 0 : 1);
                out.writeShort(frame.data().length);
                out.write(frame.data());
            }
        }
        log.info("Saved {} raw frames to {}", frames.size(), path);
    }

    /**
     * Load raw PeSIT frames from a binary file.
     */
    public static PesitSessionRecorder loadRawFromFile(Path path) throws IOException {
        PesitSessionRecorder recorder = new PesitSessionRecorder(path.getFileName().toString());
        try (java.io.DataInputStream in = new java.io.DataInputStream(
                Files.newInputStream(path))) {
            while (in.available() > 0) {
                int dirByte = in.readUnsignedByte();
                Direction direction = (dirByte == 0) ? Direction.RECEIVED : Direction.SENT;
                int length = in.readUnsignedShort();
                byte[] data = new byte[length];
                in.readFully(data);

                FpduType type = null;
                try {
                    Fpdu fpdu = new FpduParser(data).parse();
                    type = fpdu.getFpduType();
                } catch (Exception e) {
                    log.warn("Could not parse FPDU type: {}", e.getMessage());
                }

                recorder.frames.add(new RecordedFrame(Instant.now(), direction, type, data));
            }
        }
        log.info("Loaded {} raw frames from {}", recorder.frames.size(), path);
        return recorder;
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
        SENT, // Server sent to client
        RECEIVED // Server received from client
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
