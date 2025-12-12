package com.vectis.client.service;

import static com.vectis.fpdu.ParameterIdentifier.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.vectis.client.config.ClientConfig;
import com.vectis.exception.PesitException;
import com.vectis.fpdu.Fpdu;
import com.vectis.fpdu.FpduType;
import com.vectis.fpdu.ParameterGroupIdentifier;
import com.vectis.fpdu.ParameterValue;
import com.vectis.session.PesitSession;
import com.vectis.transport.TcpTransportChannel;
import com.vectis.transport.TransportChannel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core PeSIT client service for file transfers
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PesitClientService {

    private final ClientConfig config;

    private int connectionId = 0;
    private int serverConnectionId = 0;

    /**
     * Send a file to a PeSIT server
     */
    public TransferResult sendFile(String host, int port, String serverId,
            Path localFile, String remoteFilename) throws IOException, InterruptedException {

        log.info("Sending file {} to {}:{} as {}", localFile, host, port, remoteFilename);

        TransferResult result = new TransferResult();
        result.setFilename(remoteFilename);
        result.setDirection("SEND");
        result.setStartTime(Instant.now());
        result.setHost(host);
        result.setPort(port);
        result.setServerId(serverId);

        if (!Files.exists(localFile)) {
            result.setSuccess(false);
            result.setErrorMessage("Local file not found: " + localFile);
            return result;
        }

        long fileSize = Files.size(localFile);
        result.setFileSize(fileSize);

        TransportChannel channel = createChannel(host, port);

        try (PesitSession session = new PesitSession(channel, config.isStrictMode())) {
            // 1. CONNECT
            connectionId = 1;
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, config.getClientId()))
                    .withParameter(new ParameterValue(PI_04_SERVEUR, serverId))
                    .withParameter(new ParameterValue(PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                connectFpdu.withParameter(new ParameterValue(PI_05_CONTROLE_ACCES, config.getPassword()));
            }

            Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
            serverConnectionId = aconnect.getIdSrc();
            log.info("Connected to server, connection ID: {}", serverConnectionId);

            // 2. CREATE (file creation request)
            // Build PGI 9 (File Identification) - required: PI_11_TYPE_FICHIER,
            // PI_12_NOM_FICHIER
            ParameterValue pgi9 = new ParameterValue(
                    ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                    new ParameterValue(PI_11_TYPE_FICHIER, 0), // 0 = binary
                    new ParameterValue(PI_12_NOM_FICHIER, remoteFilename));

            // Build PGI 30 (Logical Attributes) - required: PI_32_LONG_ARTICLE
            ParameterValue pgi30 = new ParameterValue(
                    ParameterGroupIdentifier.PGI_30_ATTR_LOGIQUES,
                    new ParameterValue(PI_32_LONG_ARTICLE, 0)); // 0 = variable length

            // Build PGI 40 (Physical Attributes) - required: PI_42_MAX_RESERVATION
            ParameterValue pgi40 = new ParameterValue(
                    ParameterGroupIdentifier.PGI_40_ATTR_PHYSIQUES,
                    new ParameterValue(PI_42_MAX_RESERVATION, fileSize));

            // Build PGI 50 (Historical Attributes) - required: PI_51_DATE_CREATION
            ParameterValue pgi50 = new ParameterValue(
                    ParameterGroupIdentifier.PGI_50_ATTR_HISTORIQUES,
                    new ParameterValue(PI_51_DATE_CREATION, Instant.now().toString()));

            Fpdu createFpdu = new Fpdu(FpduType.CREATE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(pgi9)
                    .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, 1))
                    .withParameter(new ParameterValue(PI_17_PRIORITE, 0))
                    .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, 4096))
                    .withParameter(pgi30)
                    .withParameter(pgi40)
                    .withParameter(pgi50);

            session.sendFpduWithAck(createFpdu);
            log.info("File creation acknowledged");

            // 3. OPEN (ORF) - open file for writing
            Fpdu openFpdu = new Fpdu(FpduType.OPEN)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId);
            session.sendFpduWithAck(openFpdu);
            log.info("File opened");

            // 4. WRITE (signals start of data transfer, no data payload)
            byte[] fileData = Files.readAllBytes(localFile);

            // Calculate checksum
            String checksum = computeChecksum(fileData);
            result.setChecksum(checksum);

            Fpdu writeFpdu = new Fpdu(FpduType.WRITE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId);
            session.sendFpduWithAck(writeFpdu);
            log.info("WRITE acknowledged, starting data transfer");

            // 4. DTF - send data in chunks (max entity size, default 4096)
            int maxChunkSize = 4096;
            int offset = 0;
            while (offset < fileData.length) {
                int chunkSize = Math.min(maxChunkSize, fileData.length - offset);
                byte[] chunk = new byte[chunkSize];
                System.arraycopy(fileData, offset, chunk, 0, chunkSize);

                Fpdu dtfFpdu = new Fpdu(FpduType.DTF)
                        .withIdDst(serverConnectionId)
                        .withIdSrc(connectionId);
                session.sendFpduWithData(dtfFpdu, chunk);
                offset += chunkSize;
                log.debug("Sent DTF chunk: {} bytes, total: {}/{}", chunkSize, offset, fileData.length);
            }
            log.info("File data sent: {} bytes in {} chunk(s)", fileData.length,
                    (fileData.length + maxChunkSize - 1) / maxChunkSize);
            result.setBytesTransferred((long) fileData.length);

            // 5. DTF_END - signal end of data transfer (no ACK expected)
            Fpdu dtfEndFpdu = new Fpdu(FpduType.DTF_END)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
            session.sendFpdu(dtfEndFpdu);
            log.info("DTF_END sent");

            // 6. TRANS_END (end of transfer)
            Fpdu transendFpdu = new Fpdu(FpduType.TRANS_END)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId);
            session.sendFpduWithAck(transendFpdu);
            log.info("Transfer end acknowledged");

            // 6. CLOSE (CRF) - close file
            Fpdu closeFpdu = new Fpdu(FpduType.CLOSE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
            session.sendFpduWithAck(closeFpdu);
            log.info("File closed");

            // 7. DESELECT
            Fpdu deselectFpdu = new Fpdu(FpduType.DESELECT)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));

            session.sendFpduWithAck(deselectFpdu);
            log.info("File deselected");

            // 6. RELEASE
            Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));

            session.sendFpduWithAck(releaseFpdu);
            log.info("Connection released");

            result.setSuccess(true);
            result.setEndTime(Instant.now());
            log.info("File transfer completed successfully");

        } catch (PesitException e) {
            result.setSuccess(false);
            result.setErrorMessage("PeSIT error: " + e.getMessage());
            log.error("Transfer failed: {}", e.getMessage());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("Transfer failed: {}", e.getMessage(), e);
        }

        result.setEndTime(Instant.now());
        return result;
    }

    /**
     * Receive a file from a PeSIT server
     */
    public TransferResult receiveFile(String host, int port, String serverId,
            String remoteFilename, Path localFile) throws IOException, InterruptedException {

        log.info("Receiving file {} from {}:{} to {}", remoteFilename, host, port, localFile);

        TransferResult result = new TransferResult();
        result.setFilename(remoteFilename);
        result.setDirection("RECEIVE");
        result.setStartTime(Instant.now());
        result.setHost(host);
        result.setPort(port);
        result.setServerId(serverId);

        TransportChannel channel = createChannel(host, port);

        try (PesitSession session = new PesitSession(channel, config.isStrictMode())) {
            // 1. CONNECT
            connectionId = 1;
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, config.getClientId()))
                    .withParameter(new ParameterValue(PI_04_SERVEUR, serverId))
                    .withParameter(new ParameterValue(PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 1)); // Read access

            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                connectFpdu.withParameter(new ParameterValue(PI_05_CONTROLE_ACCES, config.getPassword()));
            }

            Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
            serverConnectionId = aconnect.getIdSrc();
            log.info("Connected to server, connection ID: {}", serverConnectionId);

            // 2. SELECT (file selection for reading)
            // Build PGI 9 (File Identification) - required: PI_11_TYPE_FICHIER,
            // PI_12_NOM_FICHIER
            ParameterValue pgi9 = new ParameterValue(
                    ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                    new ParameterValue(PI_11_TYPE_FICHIER, 0), // 0 = binary
                    new ParameterValue(PI_12_NOM_FICHIER, remoteFilename));

            Fpdu selectFpdu = new Fpdu(FpduType.SELECT)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(pgi9)
                    .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, 1))
                    .withParameter(new ParameterValue(PI_17_PRIORITE, 0))
                    .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, 4096));

            session.sendFpduWithAck(selectFpdu);
            log.info("File selected");

            // 3. OPEN (open file for reading)
            Fpdu openFpdu = new Fpdu(FpduType.OPEN)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId);

            session.sendFpduWithAck(openFpdu);
            log.info("File opened for reading");

            // 4. READ (request file data)
            // PI_18_POINT_RELANCE: restart point (0 = start from beginning)
            Fpdu readFpdu = new Fpdu(FpduType.READ)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_18_POINT_RELANCE, 0));

            session.sendFpduWithAck(readFpdu);
            log.info("Read request sent, receiving data...");

            // 5. Receive DTF chunks until DTF.END
            long totalBytes = 0;
            int chunkCount = 0;
            try (OutputStream fileOut = Files.newOutputStream(localFile)) {
                boolean receiving = true;
                while (receiving) {
                    byte[] rawFpdu = session.receiveRawFpdu();

                    // Check FPDU type from raw bytes (phase at offset 2, type at offset 3)
                    if (rawFpdu.length >= 4) {
                        int phase = rawFpdu[2] & 0xFF;
                        int type = rawFpdu[3] & 0xFF;

                        if (phase == 0x00 && (type == 0x00 || type == 0x40 || type == 0x41 || type == 0x42)) {
                            // DTF - extract data (skip 6-byte header: 2 length + 2 ids + 2 phase/type)
                            if (rawFpdu.length > 6) {
                                int dataLen = rawFpdu.length - 6;
                                fileOut.write(rawFpdu, 6, dataLen);
                                totalBytes += dataLen;
                                chunkCount++;
                                log.debug("Received DTF chunk {}: {} bytes", chunkCount, dataLen);
                            }
                        } else if (phase == 0xC0 && type == 0x22) {
                            // DTF.END - end of data transfer
                            log.info("Received DTF.END after {} chunks, {} bytes total", chunkCount, totalBytes);
                            receiving = false;
                        } else {
                            log.warn("Unexpected FPDU during data reception: phase=0x{}, type=0x{}",
                                    String.format("%02X", phase), String.format("%02X", type));
                            receiving = false;
                        }
                    }
                }
            }

            result.setBytesTransferred(totalBytes);
            log.info("File data received: {} bytes in {} chunks", totalBytes, chunkCount);

            // 6. TRANS_END (client sends after receiving DTF.END)
            Fpdu transendFpdu = new Fpdu(FpduType.TRANS_END)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId);

            session.sendFpduWithAck(transendFpdu);
            log.info("Transfer end acknowledged");

            // 7. CLOSE (requires PI_02_DIAG)
            Fpdu closeFpdu = new Fpdu(FpduType.CLOSE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));

            session.sendFpduWithAck(closeFpdu);
            log.info("File closed");

            // 8. DESELECT (requires PI_02_DIAG)
            Fpdu deselectFpdu = new Fpdu(FpduType.DESELECT)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));

            session.sendFpduWithAck(deselectFpdu);
            log.info("File deselected");

            // 9. RELEASE
            Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));

            session.sendFpduWithAck(releaseFpdu);

            result.setSuccess(true);
            log.info("File receive completed successfully");

        } catch (PesitException e) {
            result.setSuccess(false);
            result.setErrorMessage("PeSIT error: " + e.getMessage());
            log.error("Receive failed: {}", e.getMessage());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("Receive failed: {}", e.getMessage(), e);
        }

        result.setEndTime(Instant.now());
        return result;
    }

    /**
     * Test connection to a PeSIT server
     */
    public boolean testConnection(String host, int port, String serverId) {
        log.info("Testing connection to {}:{}", host, port);

        try {
            TransportChannel channel = createChannel(host, port);

            try (PesitSession session = new PesitSession(channel, config.isStrictMode())) {
                connectionId = 1;
                Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                        .withIdSrc(connectionId)
                        .withParameter(new ParameterValue(PI_03_DEMANDEUR, config.getClientId()))
                        .withParameter(new ParameterValue(PI_04_SERVEUR, serverId))
                        .withParameter(new ParameterValue(PI_06_VERSION, 2))
                        .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

                Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
                serverConnectionId = aconnect.getIdSrc();
                log.info("Connection successful, server ID: {}", serverConnectionId);

                // Release connection
                Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                        .withIdDst(serverConnectionId)
                        .withIdSrc(connectionId)
                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));

                session.sendFpduWithAck(releaseFpdu);
                return true;
            }
        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Send a message using the dedicated MSG FPDU (phase 0xC0, type 0x16)
     * This is the proper PeSIT way to send messages
     */
    @SuppressWarnings("unused") // usePi91 reserved for future use with PI_99 alternative
    public TransferResult sendMessageFpdu(String host, int port, String serverId,
            String message, boolean usePi91) {

        log.info("Sending message via MSG FPDU to {}:{}", host, port);

        TransferResult result = new TransferResult();
        result.setDirection("MSG_FPDU");
        result.setStartTime(Instant.now());
        result.setHost(host);
        result.setPort(port);
        result.setServerId(serverId);

        TransportChannel channel = createChannel(host, port);

        try (PesitSession session = new PesitSession(channel, config.isStrictMode())) {
            // 1. CONNECT
            connectionId = 1;
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, config.getClientId()))
                    .withParameter(new ParameterValue(PI_04_SERVEUR, serverId))
                    .withParameter(new ParameterValue(PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                connectFpdu.withParameter(new ParameterValue(PI_05_CONTROLE_ACCES, config.getPassword()));
            }

            Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
            serverConnectionId = aconnect.getIdSrc();
            log.info("Connected to server, connection ID: {}", serverConnectionId);

            // 2. MSG FPDU with message content
            // Build PGI 9 (File Identification) - required for MSG FPDU
            ParameterValue pgi9 = new ParameterValue(
                    ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                    new ParameterValue(PI_12_NOM_FICHIER, "MESSAGE"));

            Fpdu msgFpdu = new Fpdu(FpduType.MSG)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(pgi9)
                    .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, 1))
                    .withParameter(new ParameterValue(PI_91_MESSAGE, message));

            session.sendFpduWithAck(msgFpdu);
            log.info("Message sent and acknowledged");

            // 3. RELEASE
            Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));

            session.sendFpduWithAck(releaseFpdu);
            log.info("Connection released");

            result.setSuccess(true);
            result.setBytesTransferred((long) message.length());
            log.info("Message sent successfully via MSG FPDU ({} chars)", message.length());

        } catch (PesitException e) {
            result.setSuccess(false);
            result.setErrorMessage("PeSIT error: " + e.getMessage());
            log.error("MSG FPDU send failed: {}", e.getMessage());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("MSG FPDU send failed: {}", e.getMessage(), e);
        }

        result.setEndTime(Instant.now());
        return result;
    }

    /**
     * Send a message using PI_99 (free message) or PI_91 (message) parameter
     * The message is sent within the CONNECT/RELEASE exchange
     */
    public TransferResult sendMessage(String host, int port, String serverId,
            String message, boolean usePi91) {

        log.info("Sending message to {}:{} using {}", host, port, usePi91 ? "PI_91" : "PI_99");

        TransferResult result = new TransferResult();
        result.setDirection("MESSAGE");
        result.setStartTime(Instant.now());
        result.setHost(host);
        result.setPort(port);
        result.setServerId(serverId);

        TransportChannel channel = createChannel(host, port);

        try (PesitSession session = new PesitSession(channel, config.isStrictMode())) {
            // CONNECT with message
            connectionId = 1;
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, config.getClientId()))
                    .withParameter(new ParameterValue(PI_04_SERVEUR, serverId))
                    .withParameter(new ParameterValue(PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

            // Add message parameter
            if (usePi91) {
                connectFpdu.withParameter(new ParameterValue(PI_91_MESSAGE, message));
            } else {
                connectFpdu.withParameter(new ParameterValue(PI_99_MESSAGE_LIBRE, message));
            }

            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                connectFpdu.withParameter(new ParameterValue(PI_05_CONTROLE_ACCES, config.getPassword()));
            }

            Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
            serverConnectionId = aconnect.getIdSrc();
            log.info("Connected with message, server ID: {}", serverConnectionId);

            // RELEASE
            Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));

            session.sendFpduWithAck(releaseFpdu);
            log.info("Connection released");

            result.setSuccess(true);
            result.setBytesTransferred((long) message.length());
            log.info("Message sent successfully ({} chars)", message.length());

        } catch (PesitException e) {
            result.setSuccess(false);
            result.setErrorMessage("PeSIT error: " + e.getMessage());
            log.error("Message send failed: {}", e.getMessage());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("Message send failed: {}", e.getMessage(), e);
        }

        result.setEndTime(Instant.now());
        return result;
    }

    /**
     * Send a message as a file transfer
     * This allows sending larger messages (no size limit)
     */
    public TransferResult sendMessageAsFile(String host, int port, String serverId,
            String message, String messageName) {

        log.info("Sending message as file to {}:{}", host, port);

        TransferResult result = new TransferResult();
        result.setFilename(messageName);
        result.setDirection("MESSAGE_FILE");
        result.setStartTime(Instant.now());
        result.setHost(host);
        result.setPort(port);
        result.setServerId(serverId);

        byte[] messageData = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        result.setFileSize((long) messageData.length);

        TransportChannel channel = createChannel(host, port);

        try (PesitSession session = new PesitSession(channel, config.isStrictMode())) {
            // 1. CONNECT
            connectionId = 1;
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, config.getClientId()))
                    .withParameter(new ParameterValue(PI_04_SERVEUR, serverId))
                    .withParameter(new ParameterValue(PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                connectFpdu.withParameter(new ParameterValue(PI_05_CONTROLE_ACCES, config.getPassword()));
            }

            Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
            serverConnectionId = aconnect.getIdSrc();
            log.info("Connected to server, connection ID: {}", serverConnectionId);

            // 2. CREATE (message as file)
            Fpdu createFpdu = new Fpdu(FpduType.CREATE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_12_NOM_FICHIER, messageName))
                    .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, 1))
                    .withParameter(new ParameterValue(PI_17_PRIORITE, 0))
                    // Add the message in PI_99 as well for metadata
                    .withParameter(new ParameterValue(PI_99_MESSAGE_LIBRE,
                            message.length() > 254 ? message.substring(0, 254) : message));

            session.sendFpduWithAck(createFpdu);
            log.info("File creation acknowledged");

            // 3. WRITE (send message data)
            String checksum = computeChecksum(messageData);
            result.setChecksum(checksum);

            Fpdu writeFpdu = new Fpdu(FpduType.WRITE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId);

            session.sendFpduWithDataAndAck(writeFpdu, messageData);
            log.info("Message data sent: {} bytes", messageData.length);
            result.setBytesTransferred((long) messageData.length);

            // 4. TRANS_END
            Fpdu transendFpdu = new Fpdu(FpduType.TRANS_END)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId);

            session.sendFpduWithAck(transendFpdu);
            log.info("Transfer end acknowledged");

            // 5. DESELECT
            Fpdu deselectFpdu = new Fpdu(FpduType.DESELECT)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));

            session.sendFpduWithAck(deselectFpdu);
            log.info("File deselected");

            // 6. RELEASE
            Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnectionId)
                    .withIdSrc(connectionId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));

            session.sendFpduWithAck(releaseFpdu);
            log.info("Connection released");

            result.setSuccess(true);
            log.info("Message file transfer completed successfully");

        } catch (PesitException e) {
            result.setSuccess(false);
            result.setErrorMessage("PeSIT error: " + e.getMessage());
            log.error("Message file transfer failed: {}", e.getMessage());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("Message file transfer failed: {}", e.getMessage(), e);
        }

        result.setEndTime(Instant.now());
        return result;
    }

    /**
     * Create transport channel (plain or TLS)
     */
    private TransportChannel createChannel(String host, int port) {
        TcpTransportChannel channel = new TcpTransportChannel(host, port);
        channel.setReceiveTimeout(config.getReadTimeout());
        return channel;
    }

    /**
     * Compute SHA-256 checksum
     */
    private String computeChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Transfer result DTO
     */
    @lombok.Data
    public static class TransferResult {
        private boolean success;
        private String filename;
        private String direction;
        private String host;
        private int port;
        private String serverId;
        private Long fileSize;
        private Long bytesTransferred;
        private String checksum;
        private Instant startTime;
        private Instant endTime;
        private String errorMessage;
        private String errorCode;

        public long getDurationMs() {
            if (startTime != null && endTime != null) {
                return java.time.Duration.between(startTime, endTime).toMillis();
            }
            return 0;
        }
    }
}
