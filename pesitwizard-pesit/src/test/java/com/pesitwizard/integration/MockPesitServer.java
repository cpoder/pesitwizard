package com.pesitwizard.integration;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.PesitSessionRecorder;
import com.pesitwizard.fpdu.PesitSessionRecorder.Direction;
import com.pesitwizard.fpdu.PesitSessionRecorder.RecordedFrame;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Mock PeSIT server that replays recorded sessions from golden files.
 * 
 * Usage:
 * 
 * <pre>
 * try (MockPesitServer server = MockPesitServer.fromGoldenFile(path)) {
 *     server.start();
 *     // Connect to server.getPort() and run your test
 * }
 * </pre>
 */
@Slf4j
public class MockPesitServer implements Closeable {

    private final List<RecordedFrame> frames;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread serverThread;

    @Getter
    private int port;

    @Getter
    private int frameIndex = 0;

    @Getter
    private Throwable lastError;

    public MockPesitServer(List<RecordedFrame> frames) {
        this.frames = frames;
    }

    public static MockPesitServer fromGoldenFile(Path path) throws IOException, ClassNotFoundException {
        PesitSessionRecorder recorder = PesitSessionRecorder.loadFromFile(path);
        return new MockPesitServer(recorder.getFrames());
    }

    public static MockPesitServer fromRecorder(PesitSessionRecorder recorder) {
        return new MockPesitServer(recorder.getFrames());
    }

    public void start() throws IOException {
        start(0); // Random available port
    }

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(5000);
        this.port = serverSocket.getLocalPort();
        running.set(true);

        serverThread = new Thread(this::runServer, "MockPesitServer");
        serverThread.setDaemon(true);
        serverThread.start();

        // Give the server thread time to start accepting connections
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("MockPesitServer started on port {} with {} frames", this.port, frames.size());
    }

    private void runServer() {
        try {
            while (running.get()) {
                try {
                    Socket client = serverSocket.accept();
                    log.debug("Client connected from {}", client.getRemoteSocketAddress());
                    handleClient(client);
                } catch (SocketTimeoutException e) {
                    // Continue loop to check running flag
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                log.error("Server error", e);
                lastError = e;
            }
        }
    }

    private void handleClient(Socket client) {
        try (client) {
            DataInputStream in = new DataInputStream(client.getInputStream());
            DataOutputStream out = new DataOutputStream(client.getOutputStream());

            while (running.get() && frameIndex < frames.size()) {
                RecordedFrame expected = frames.get(frameIndex);

                if (expected.direction() == Direction.SENT) {
                    // Read with transport framing: [length:2][data]
                    int len = in.readUnsignedShort();
                    byte[] received = new byte[len];
                    in.readFully(received);

                    // Parse and validate
                    Fpdu receivedFpdu = new FpduParser(received).parse();
                    log.debug("Received {} (expected {})", receivedFpdu.getFpduType(), expected.type());

                    if (receivedFpdu.getFpduType() != expected.type()) {
                        log.warn("Frame mismatch at index {}: expected {} but got {}",
                                frameIndex, expected.type(), receivedFpdu.getFpduType());
                    }

                    frameIndex++;

                    // Send all consecutive RECEIVED frames with transport framing
                    while (frameIndex < frames.size() &&
                            frames.get(frameIndex).direction() == Direction.RECEIVED) {
                        RecordedFrame response = frames.get(frameIndex);
                        out.writeShort(response.data().length);
                        out.write(response.data());
                        out.flush();
                        log.debug("Sent {} ({} bytes)", response.type(), response.data().length);
                        frameIndex++;
                    }

                } else {
                    // RECEIVED without prior SENT - server initiates
                    out.writeShort(expected.data().length);
                    out.write(expected.data());
                    out.flush();
                    log.debug("Sent {} ({} bytes)", expected.type(), expected.data().length);
                    frameIndex++;
                }
            }

            log.info("Session complete, processed {}/{} frames", frameIndex, frames.size());

        } catch (Exception e) {
            log.error("Error handling client", e);
            lastError = e;
        }
    }

    public boolean isComplete() {
        return frameIndex >= frames.size();
    }

    public void waitForCompletion(long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!isComplete() && System.currentTimeMillis() - start < timeoutMs) {
            Thread.sleep(50);
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.warn("Error closing server socket", e);
            }
        }
        if (serverThread != null) {
            try {
                serverThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("MockPesitServer stopped");
    }
}
