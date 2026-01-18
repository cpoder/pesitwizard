package com.pesitwizard.integration;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.PesitSessionRecorder;
import com.pesitwizard.fpdu.PesitSessionRecorder.Direction;
import com.pesitwizard.fpdu.PesitSessionRecorder.RecordedFrame;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Mock PeSIT client that replays recorded sessions against a server.
 * 
 * This is useful for testing PeSIT server implementations by replaying
 * what a real client (like Connect:Express) would send.
 * 
 * Usage:
 * 
 * <pre>
 * // Start your PeSIT server on some port
 * MockPesitClient client = MockPesitClient.fromGoldenFile(path);
 * client.replay("localhost", serverPort);
 * assertTrue(client.isComplete());
 * </pre>
 */
@Slf4j
public class MockPesitClient implements Closeable {

    private final List<RecordedFrame> frames;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    @Getter
    private int frameIndex = 0;

    @Getter
    private Throwable lastError;

    @Getter
    private final List<Fpdu> receivedFpdus = new ArrayList<>();

    public MockPesitClient(List<RecordedFrame> frames) {
        this.frames = frames;
    }

    public static MockPesitClient fromGoldenFile(Path path) throws IOException, ClassNotFoundException {
        PesitSessionRecorder recorder = PesitSessionRecorder.loadFromFile(path);
        return new MockPesitClient(recorder.getFrames());
    }

    public static MockPesitClient fromRecorder(PesitSessionRecorder recorder) {
        return new MockPesitClient(recorder.getFrames());
    }

    /**
     * Replay the recorded session against a server.
     * Sends SENT frames and validates RECEIVED frames.
     */
    public void replay(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(30000);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        log.info("MockPesitClient connected to {}:{}, replaying {} frames", host, port, frames.size());

        try {
            while (frameIndex < frames.size()) {
                RecordedFrame frame = frames.get(frameIndex);

                if (frame.direction() == Direction.SENT) {
                    // Send this frame to the server
                    out.writeShort(frame.data().length);
                    out.write(frame.data());
                    out.flush();
                    log.debug("Sent {} ({} bytes)", frame.type(), frame.data().length);
                    frameIndex++;

                } else {
                    // Expect to receive this frame from the server
                    int len = in.readUnsignedShort();
                    byte[] data = new byte[len];
                    in.readFully(data);

                    Fpdu received = new FpduParser(data).parse();
                    receivedFpdus.add(received);
                    log.debug("Received {} (expected {})", received.getFpduType(), frame.type());

                    if (received.getFpduType() != frame.type()) {
                        log.warn("Frame mismatch at index {}: expected {} but got {}",
                                frameIndex, frame.type(), received.getFpduType());
                    }
                    frameIndex++;
                }
            }
            log.info("Replay complete, processed {}/{} frames", frameIndex, frames.size());

        } catch (Exception e) {
            log.error("Error during replay at frame {}", frameIndex, e);
            lastError = e;
            throw e;
        }
    }

    /**
     * Replay with inverted directions - useful when the recording was made
     * from the server's perspective but you want to act as a client.
     */
    public void replayInverted(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(30000);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        log.info("MockPesitClient (inverted) connected to {}:{}, replaying {} frames", host, port, frames.size());

        try {
            while (frameIndex < frames.size()) {
                RecordedFrame frame = frames.get(frameIndex);

                // Inverted: RECEIVED becomes what we send, SENT becomes what we expect
                if (frame.direction() == Direction.RECEIVED) {
                    // Send this frame to the server
                    out.writeShort(frame.data().length);
                    out.write(frame.data());
                    out.flush();
                    log.debug("Sent (inverted) {} ({} bytes)", frame.type(), frame.data().length);
                    frameIndex++;

                } else {
                    // Expect to receive this frame from the server
                    int len = in.readUnsignedShort();
                    byte[] data = new byte[len];
                    in.readFully(data);

                    Fpdu received = new FpduParser(data).parse();
                    receivedFpdus.add(received);
                    log.debug("Received (inverted) {} (expected {})", received.getFpduType(), frame.type());

                    if (received.getFpduType() != frame.type()) {
                        log.warn("Frame mismatch at index {}: expected {} but got {}",
                                frameIndex, frame.type(), received.getFpduType());
                    }
                    frameIndex++;
                }
            }
            log.info("Inverted replay complete, processed {}/{} frames", frameIndex, frames.size());

        } catch (Exception e) {
            log.error("Error during inverted replay at frame {}", frameIndex, e);
            lastError = e;
            throw e;
        }
    }

    public boolean isComplete() {
        return frameIndex >= frames.size();
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        log.debug("MockPesitClient closed");
    }
}
