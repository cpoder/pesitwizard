package com.pesitwizard.server.handler;

import com.pesitwizard.channel.PesitChannel;
import com.pesitwizard.channel.TcpPesitChannel;
import com.pesitwizard.channel.TlsPesitChannel;
import com.pesitwizard.fpdu.EbcdicConverter;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduReader;
import com.pesitwizard.fpdu.PesitSessionRecorder;
import com.pesitwizard.fpdu.PesitSessionRecorder.Direction;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.state.ServerState;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.net.ssl.SSLSocket;
import lombok.extern.slf4j.Slf4j;

/** Handles a single TCP connection for PeSIT protocol */
@Slf4j
public class TcpConnectionHandler implements Runnable {

    private final Socket socket;
    private final PesitSessionHandler sessionHandler;
    private final PesitServerProperties properties;
    private final String serverId;
    private SessionContext sessionContext;
    private PesitSessionRecorder recorder;

    public TcpConnectionHandler(
            Socket socket,
            PesitSessionHandler sessionHandler,
            PesitServerProperties properties,
            String serverId) {
        this.socket = socket;
        this.sessionHandler = sessionHandler;
        this.properties = properties;
        this.serverId = serverId;
    }

    @Override
    public void run() {
        String remoteAddress = socket.getRemoteSocketAddress().toString();
        log.info("New connection from {}", remoteAddress);

        try {
            socket.setSoTimeout(properties.getReadTimeout());
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);

            boolean isTls = socket instanceof SSLSocket;

            // Complete TLS handshake before reading data
            if (isTls) {
                SSLSocket sslSocket = (SSLSocket) socket;
                sslSocket.startHandshake();
                log.info(
                        "TLS handshake completed: protocol={}, cipher={}",
                        sslSocket.getSession().getProtocol(),
                        sslSocket.getSession().getCipherSuite());
            }

            sessionContext = sessionHandler.createSession(remoteAddress, serverId);

            // Initialize session recording if enabled
            if (properties.isSessionRecordingEnabled()) {
                recorder = new PesitSessionRecorder("session-" + sessionContext.getSessionId());
                log.info("[{}] Session recording enabled", sessionContext.getSessionId());
            }

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Create the appropriate channel based on transport type
            PesitChannel channel =
                    isTls ? new TlsPesitChannel(in, out) : new TcpPesitChannel(in, out);
            sessionContext.setChannel(channel);

            FpduReader fpduReader = new FpduReader(channel);

            log.info(
                    "[{}] Connection from {} (TLS={}, framing={})",
                    sessionContext.getSessionId(),
                    remoteAddress,
                    isTls,
                    isTls ? "TLS" : "TCP");

            if (isTls) {
                // TLS: probe first bytes to detect pre-connection handshake
                byte[] probe = new byte[1024];
                int probeLen = in.read(probe, 0, probe.length);

                if (probeLen <= 0) {
                    log.warn(
                            "[{}] No data received from TLS client", sessionContext.getSessionId());
                    return;
                }

                byte[] firstData = new byte[probeLen];
                System.arraycopy(probe, 0, firstData, 0, probeLen);
                handleFirstMessage(firstData, channel, fpduReader);
            } else {
                // TCP: read first FPDU (with external length prefix)
                byte[] firstData = channel.readFrame();
                handleFirstMessage(firstData, channel, fpduReader);
            }

            // Main protocol loop
            boolean sessionActive = true;
            while (!socket.isClosed() && sessionActive) {
                try {
                    Fpdu fpdu = fpduReader.read();
                    if (fpdu == null) {
                        log.debug("[{}] No FPDU received", sessionContext.getSessionId());
                        break;
                    }

                    log.debug(
                            "[{}] Received FPDU: type={} (encoding: {})",
                            sessionContext.getSessionId(),
                            fpdu.getFpduType(),
                            sessionContext.isEbcdicEncoding() ? "EBCDIC" : "ASCII");

                    // Record received FPDU if recording is enabled
                    if (recorder != null) {
                        recorder.record(Direction.RECEIVED, fpdu);
                    }

                    // Process the FPDU
                    byte[] response = sessionHandler.processIncomingFpdu(sessionContext, fpdu);

                    // Send response if any (READ streams directly, so response may be null)
                    if (response != null) {
                        channel.writeFrame(response);

                        // Record sent FPDU if recording is enabled
                        if (recorder != null) {
                            recorder.recordRaw(Direction.SENT, response);
                        }
                    }

                    // Check if session ended normally
                    if (sessionContext.getState() == ServerState.CN01_REPOS
                            || sessionContext.isAborted()) {
                        log.info("[{}] Session ended normally", sessionContext.getSessionId());
                        sessionActive = false;
                    }

                } catch (SocketTimeoutException e) {
                    log.warn("[{}] Read timeout", sessionContext.getSessionId());
                    break;
                } catch (EOFException e) {
                    log.info("[{}] Client disconnected", sessionContext.getSessionId());
                    break;
                }
            }

        } catch (SocketException e) {
            log.info(
                    "[{}] Connection reset: {}",
                    sessionContext != null ? sessionContext.getSessionId() : "unknown",
                    e.getMessage());
        } catch (IOException e) {
            log.error(
                    "[{}] IO error: {}",
                    sessionContext != null ? sessionContext.getSessionId() : "unknown",
                    e.getMessage(),
                    e);
        } catch (RuntimeException e) {
            log.error(
                    "[{}] Unexpected error: {}",
                    sessionContext != null ? sessionContext.getSessionId() : "unknown",
                    e.getMessage(),
                    e);
        } finally {
            saveRecordingIfEnabled();
            closeConnection();
        }
    }

    /**
     * Detect EBCDIC pre-connection handshake in first message data. If the data is a 24-byte EBCDIC
     * "PESIT" pre-connection message, handle it; otherwise inject the raw data into the FPDU
     * reader.
     */
    private void handleFirstMessage(byte[] firstData, PesitChannel channel, FpduReader fpduReader)
            throws IOException {
        if (firstData.length >= 24 && EbcdicConverter.isEbcdic(firstData)) {
            byte[] asciiData = EbcdicConverter.toAscii(firstData);
            String preConnMsg =
                    new String(asciiData, 0, Math.min(24, asciiData.length), StandardCharsets.UTF_8)
                            .trim();
            if (preConnMsg.startsWith("PESIT")) {
                sessionContext.setEbcdicEncoding(true);
                log.info("[{}] Client uses EBCDIC encoding", sessionContext.getSessionId());
                handlePreConnection(asciiData, channel);
                sessionContext.setPreConnectionHandled(true);
                return;
            }
        }
        fpduReader.injectRawData(firstData);
    }

    /**
     * Handle standard PeSIT pre-connection handshake.
     *
     * <p>Message format (24 bytes, EBCDIC encoded): - 8 bytes: Protocol identifier ("PESIT ") - 8
     * bytes: Client identifier - 8 bytes: Password
     *
     * <p>Response: "ACK0" (4 bytes in EBCDIC), framed according to channel type.
     */
    private void handlePreConnection(byte[] asciiData, PesitChannel channel) throws IOException {
        String protocol = new String(asciiData, 0, 8, StandardCharsets.UTF_8).trim();
        String identifier = new String(asciiData, 8, 8, StandardCharsets.UTF_8).trim();
        String password = new String(asciiData, 16, 8, StandardCharsets.UTF_8).trim();

        log.info(
                "[{}] Pre-connection handshake: protocol={}, identifier={}, password={}",
                sessionContext.getSessionId(),
                protocol,
                identifier,
                password.replaceAll(".", "*"));

        // Send ACK0 response in EBCDIC, using channel's framing
        byte[] ebcdicAck = EbcdicConverter.asciiToEbcdic("ACK0".getBytes(StandardCharsets.UTF_8));
        channel.writeFrame(ebcdicAck);

        log.info(
                "[{}] Sent pre-connection ACK0 (framing={})",
                sessionContext.getSessionId(),
                channel.isTls() ? "TLS" : "TCP");
    }

    private void saveRecordingIfEnabled() {
        if (recorder != null && recorder.getFrames().size() > 0) {
            try {
                String dir = properties.getSessionRecordingDirectory();
                if (dir == null || dir.isEmpty()) {
                    dir = "recordings";
                }
                Path recordingDir = Paths.get(dir);
                Files.createDirectories(recordingDir);
                String filename =
                        String.format(
                                "session-%s-%d.raw",
                                sessionContext != null ? sessionContext.getSessionId() : "unknown",
                                System.currentTimeMillis());
                Path recordingPath = recordingDir.resolve(filename);
                recorder.saveRawToFile(recordingPath);
                log.info(
                        "[{}] Session recording saved to {}",
                        sessionContext != null ? sessionContext.getSessionId() : "unknown",
                        recordingPath);
            } catch (IOException e) {
                log.error(
                        "[{}] Failed to save session recording: {}",
                        sessionContext != null ? sessionContext.getSessionId() : "unknown",
                        e.getMessage());
            }
        }
    }

    private void closeConnection() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
            log.info(
                    "[{}] Connection closed",
                    sessionContext != null ? sessionContext.getSessionId() : "unknown");
        } catch (IOException e) {
            log.warn("Error closing socket: {}", e.getMessage());
        }
    }

    /** Get the session context for this connection */
    public SessionContext getSessionContext() {
        return sessionContext;
    }
}
