package com.vectis.server.handler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import com.vectis.fpdu.FpduIO;
import com.vectis.server.config.PesitServerProperties;
import com.vectis.server.model.SessionContext;
import com.vectis.server.state.ServerState;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles a single TCP connection for PeSIT protocol
 */
@Slf4j
public class TcpConnectionHandler implements Runnable {

    private final Socket socket;
    private final PesitSessionHandler sessionHandler;
    private final PesitServerProperties properties;
    private final String serverId;
    private SessionContext sessionContext;

    public TcpConnectionHandler(Socket socket, PesitSessionHandler sessionHandler,
            PesitServerProperties properties, String serverId) {
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

            sessionContext = sessionHandler.createSession(remoteAddress, serverId);

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Main protocol loop - continue until session ends or socket closes
            boolean sessionActive = true;
            while (!socket.isClosed() && sessionActive) {

                try {
                    // Read FPDU using library helper
                    byte[] fpduData = FpduIO.readRawFpdu(in);

                    // Log raw bytes for debugging
                    int[] phaseType = FpduIO.getPhaseAndType(fpduData);
                    if (phaseType != null) {
                        log.debug("[{}] Received {} bytes, phase=0x{}, type=0x{}",
                                sessionContext.getSessionId(), fpduData.length,
                                String.format("%02X", phaseType[0]), String.format("%02X", phaseType[1]));
                    } else {
                        log.debug("[{}] Received {} bytes", sessionContext.getSessionId(), fpduData.length);
                    }

                    // Process the FPDU (may stream data directly to output for READ)
                    byte[] response = null;
                    try {
                        response = sessionHandler.processIncomingFpdu(sessionContext, fpduData, out);
                    } catch (Exception e) {
                        log.error("[{}] Error processing FPDU: {}", sessionContext.getSessionId(), e.getMessage(), e);
                        continue;
                    }

                    // Send response if any (READ streams directly, so response may be null)
                    if (response != null) {
                        FpduIO.writeRawFpdu(out, response);
                        log.debug("[{}] Sent {} bytes", sessionContext.getSessionId(), response.length);
                    }

                    // Check if session ended normally (RELCONF sent or ABORT)
                    if (sessionContext.getState() == ServerState.CN01_REPOS || sessionContext.isAborted()) {
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
            log.info("[{}] Connection reset: {}",
                    sessionContext != null ? sessionContext.getSessionId() : "unknown",
                    e.getMessage());
        } catch (IOException e) {
            log.error("[{}] IO error: {}",
                    sessionContext != null ? sessionContext.getSessionId() : "unknown",
                    e.getMessage(), e);
        } finally {
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
            log.info("[{}] Connection closed",
                    sessionContext != null ? sessionContext.getSessionId() : "unknown");
        } catch (IOException e) {
            log.warn("Error closing socket: {}", e.getMessage());
        }
    }

    /**
     * Get the session context for this connection
     */
    public SessionContext getSessionContext() {
        return sessionContext;
    }
}
