package com.pesitwizard.server.handler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLSocket;

import com.pesitwizard.fpdu.EbcdicConverter;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduIO;
import com.pesitwizard.fpdu.FpduReader;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.state.ServerState;

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

            // For SSL sockets, explicitly complete the handshake before reading data
            if (socket instanceof SSLSocket sslSocket) {
                try {
                    sslSocket.startHandshake();
                    log.info("TLS handshake completed: protocol={}, cipher={}",
                            sslSocket.getSession().getProtocol(),
                            sslSocket.getSession().getCipherSuite());
                } catch (IOException e) {
                    log.error("TLS handshake failed: {}", e.getMessage());
                    throw e;
                }
            }

            sessionContext = sessionHandler.createSession(remoteAddress, serverId);

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Use FpduReader to handle concatenated FPDUs (PeSIT section 4.5)
            FpduReader fpduReader = new FpduReader(in);

            // Handle pre-connection handshake first (IBM CX compatibility)
            // This is a 24-byte PURE EBCDIC message that comes BEFORE the CONNECT FPDU
            byte[] firstData = FpduIO.readRawFpdu(in);
            boolean isPreConnection = false;
            if (firstData.length == 24) {
                boolean isEbcdic = EbcdicConverter.isEbcdic(firstData);
                if (isEbcdic) {
                    byte[] asciiData = EbcdicConverter.toAscii(firstData);
                    String preConnMsg = new String(asciiData).trim();
                    if (preConnMsg.startsWith("PESIT")) {
                        sessionContext.setEbcdicEncoding(true);
                        log.info("[{}] Client uses EBCDIC encoding (IBM mainframe)",
                                sessionContext.getSessionId());
                        handlePreConnection(asciiData, out);
                        sessionContext.setPreConnectionHandled(true);
                        isPreConnection = true;
                    }
                }
            }
            // If not pre-connection, inject data into FpduReader for normal processing
            if (!isPreConnection) {
                fpduReader.injectRawData(firstData);
            }

            // Main protocol loop - continue until session ends or socket closes
            boolean sessionActive = true;
            while (!socket.isClosed() && sessionActive) {

                try {
                    // Read next FPDU using FpduReader (handles concatenated FPDUs)
                    Fpdu fpdu = fpduReader.read();
                    if (fpdu == null) {
                        log.debug("[{}] No FPDU received", sessionContext.getSessionId());
                        break;
                    }

                    log.debug("[{}] Received FPDU: type={} (encoding: {})",
                            sessionContext.getSessionId(), fpdu.getFpduType(),
                            sessionContext.isEbcdicEncoding() ? "EBCDIC" : "ASCII");

                    // Process the FPDU directly (pass parsed Fpdu object to avoid data loss)
                    byte[] response = sessionHandler.processIncomingFpdu(sessionContext, fpdu, in, out);

                    // Send response if any (READ streams directly, so response may be null)
                    if (response != null) {
                        // Protocol FPDUs are always binary - do NOT convert to EBCDIC
                        // Only the pre-connection ACK0 was in pure EBCDIC

                        // Debug: log first 16 bytes of response in hex
                        if (response.length >= 8) {
                            StringBuilder hex = new StringBuilder();
                            for (int i = 0; i < Math.min(16, response.length); i++) {
                                hex.append(String.format("%02X ", response[i] & 0xFF));
                            }
                            log.debug("[{}] Sending response bytes: {}", sessionContext.getSessionId(),
                                    hex.toString().trim());
                        }

                        FpduIO.writeRawFpdu(out, response);
                        log.debug("[{}] Sent {} bytes (client encoding: {})",
                                sessionContext.getSessionId(), response.length,
                                sessionContext.isEbcdicEncoding() ? "EBCDIC" : "ASCII");
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
        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}",
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
     * Handle pre-connection handshake (IBM CX compatibility)
     *
     * Message format (24 bytes, EBCDIC encoded):
     * - 8 bytes: Protocol identifier ("PESIT ")
     * - 8 bytes: Client identifier (e.g., "CXCLIENT")
     * - 8 bytes: Password (e.g., "TEST123 ")
     *
     * Response: "ACK0" (4 bytes in EBCDIC)
     */
    private void handlePreConnection(byte[] asciiData, DataOutputStream out) throws IOException {
        // Parse the 24-byte message
        String protocol = new String(asciiData, 0, 8).trim();
        String identifier = new String(asciiData, 8, 8).trim();
        String password = new String(asciiData, 16, 8).trim();

        log.info("[{}] Pre-connection handshake: protocol={}, identifier={}, password={}",
                sessionContext.getSessionId(), protocol, identifier, password.replaceAll(".", "*"));

        // Send ACK0 response in EBCDIC (without frame length prefix)
        byte[] ack = "ACK0".getBytes();
        byte[] ebcdicAck = EbcdicConverter.asciiToEbcdic(ack); // Direct EBCDIC conversion for 4-byte ACK0

        // Write ACK0 with frame length prefix
        out.writeShort(4); // Frame length
        out.write(ebcdicAck);
        out.flush();

        log.info("[{}] Sent pre-connection ACK0", sessionContext.getSessionId());
    }

    /**
     * Get the session context for this connection
     */
    public SessionContext getSessionContext() {
        return sessionContext;
    }
}
