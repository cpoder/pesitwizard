package com.pesitwizard.client.service;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesitwizard.client.connector.ConnectorRegistry;
import com.pesitwizard.client.dto.MessageRequest;
import com.pesitwizard.client.dto.TransferRequest;
import com.pesitwizard.client.dto.TransferResponse;
import com.pesitwizard.client.dto.TransferStats;
import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.client.entity.StorageConnection;
import com.pesitwizard.client.entity.TransferConfig;
import com.pesitwizard.client.entity.TransferHistory;
import com.pesitwizard.client.entity.TransferHistory.TransferDirection;
import com.pesitwizard.client.entity.TransferHistory.TransferStatus;
import com.pesitwizard.client.repository.StorageConnectionRepository;
import com.pesitwizard.client.repository.TransferConfigRepository;
import com.pesitwizard.client.repository.TransferHistoryRepository;
import com.pesitwizard.client.security.SecretsService;
import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.StorageConnector;
import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TcpTransportChannel;
import com.pesitwizard.transport.TlsTransportChannel;
import com.pesitwizard.transport.TransportChannel;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for executing PeSIT transfers with telemetry
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TransferService {

        private static final AtomicInteger TRANSFER_ID_COUNTER = new AtomicInteger(1);

        private final PesitServerService serverService;
        private final TransferConfigRepository configRepository;
        private final TransferHistoryRepository historyRepository;
        private final ObservationRegistry observationRegistry;
        private final PathPlaceholderService placeholderService;
        private final ConnectorRegistry connectorRegistry;
        private final StorageConnectionRepository connectionRepository;
        private final ObjectMapper objectMapper;
        private final SecretsService secretsService;

        @Transactional
        public TransferResponse sendFile(TransferRequest request) {
                String correlationId = request.getCorrelationId() != null ? request.getCorrelationId()
                                : UUID.randomUUID().toString();

                return Observation.createNotStarted("pesit.send", observationRegistry)
                                .lowCardinalityKeyValue("pesit.direction", "SEND")
                                .highCardinalityKeyValue("pesit.server", request.getServer())
                                .highCardinalityKeyValue("correlation.id", correlationId)
                                .observe(() -> doSendFile(request, correlationId));
        }

        private TransferResponse doSendFile(TransferRequest request, String correlationId) {
                PesitServer server = resolveServer(request.getServer());
                TransferConfig config = resolveConfig(request.getTransferConfig());

                // Use filename if provided, fallback to deprecated localPath for compatibility
                String filename = request.getFilename() != null ? request.getFilename() : request.getFilename();
                String sourceConnId = request.getSourceConnectionId();

                TransferHistory history = createHistory(server, config, TransferDirection.SEND,
                                filename, request.getRemoteFilename(), request.getPartnerId(),
                                correlationId);

                try {
                        byte[] fileData;
                        long fileSize;

                        if (sourceConnId != null) {
                                // Read from storage connector
                                StorageConnector connector = createConnectorFromConnectionId(sourceConnId);
                                try (var inputStream = connector.read(filename, 0)) {
                                        fileData = inputStream.readAllBytes();
                                        fileSize = fileData.length;
                                } finally {
                                        connector.close();
                                }
                                log.info("Read {} bytes from connector {} path {}", fileSize, sourceConnId, filename);
                        } else {
                                // Read from local filesystem
                                Path localFile = Path.of(filename);
                                if (!Files.exists(localFile)) {
                                        throw new IllegalArgumentException("Local file not found: " + filename);
                                }
                                fileData = Files.readAllBytes(localFile);
                                fileSize = fileData.length;
                        }

                        history.setFileSize(fileSize);
                        history.setStatus(TransferStatus.IN_PROGRESS);
                        history = historyRepository.save(history);

                        TransportChannel channel = createChannel(server);

                        try (PesitSession session = new PesitSession(channel, false)) {
                                executeSendTransfer(session, server, request, fileData, config);
                        }

                        history.setStatus(TransferStatus.COMPLETED);
                        history.setBytesTransferred((long) fileData.length);
                        history.setChecksum(computeChecksum(fileData));
                        history.setCompletedAt(Instant.now());

                } catch (Exception e) {
                        history.setStatus(TransferStatus.FAILED);
                        history.setErrorMessage(e.getMessage());
                        history.setCompletedAt(Instant.now());
                        log.error("Send transfer failed: {}", e.getMessage(), e);
                }

                history = historyRepository.save(history);
                return mapToResponse(history);
        }

        @Transactional
        public TransferResponse receiveFile(TransferRequest request) {
                String correlationId = request.getCorrelationId() != null ? request.getCorrelationId()
                                : UUID.randomUUID().toString();

                return Observation.createNotStarted("pesit.receive", observationRegistry)
                                .lowCardinalityKeyValue("pesit.direction", "RECEIVE")
                                .highCardinalityKeyValue("pesit.server", request.getServer())
                                .highCardinalityKeyValue("correlation.id", correlationId)
                                .observe(() -> doReceiveFile(request, correlationId));
        }

        private TransferResponse doReceiveFile(TransferRequest request, String correlationId) {
                PesitServer server = resolveServer(request.getServer());
                TransferConfig config = resolveConfig(request.getTransferConfig());

                // Use filename if provided, fallback to deprecated localPath for compatibility
                String filename = request.getFilename() != null ? request.getFilename() : request.getFilename();
                String destConnId = request.getDestinationConnectionId();

                // Resolve placeholders in filename
                String resolvedFilename = placeholderService.resolvePath(
                                filename,
                                PathPlaceholderService.PlaceholderContext.builder()
                                                .partnerId(request.getPartnerId())
                                                .virtualFile(request.getRemoteFilename())
                                                .serverId(server.getId())
                                                .serverName(server.getName())
                                                .direction("RECEIVE")
                                                .build());

                TransferHistory history = createHistory(server, config, TransferDirection.RECEIVE,
                                resolvedFilename, request.getRemoteFilename(), request.getPartnerId(),
                                correlationId);

                try {
                        history.setStatus(TransferStatus.IN_PROGRESS);
                        history = historyRepository.save(history);

                        TransportChannel channel = createChannel(server);

                        // Always use a connector - local connector if none specified
                        StorageConnector connector = destConnId != null
                                        ? createConnectorFromConnectionId(destConnId)
                                        : connectorRegistry.createConnector("local", java.util.Map.of());

                        long bytesReceived;
                        try (PesitSession session = new PesitSession(channel, false)) {
                                bytesReceived = executeReceiveTransfer(session, server, request,
                                                connector, resolvedFilename, config);
                        } finally {
                                connector.close();
                        }
                        log.info("Wrote {} bytes to path {}", bytesReceived, resolvedFilename);

                        history.setStatus(TransferStatus.COMPLETED);
                        history.setFileSize(bytesReceived);
                        history.setBytesTransferred(bytesReceived);
                        history.setCompletedAt(Instant.now());

                } catch (Exception e) {
                        history.setStatus(TransferStatus.FAILED);
                        history.setErrorMessage(e.getMessage());
                        history.setCompletedAt(Instant.now());
                        log.error("Receive transfer failed: {}", e.getMessage(), e);
                }

                history = historyRepository.save(history);
                return mapToResponse(history);
        }

        @Transactional
        public TransferResponse sendMessage(MessageRequest request) {
                String correlationId = request.getCorrelationId() != null ? request.getCorrelationId()
                                : UUID.randomUUID().toString();

                return Observation.createNotStarted("pesit.message", observationRegistry)
                                .lowCardinalityKeyValue("pesit.direction", "MESSAGE")
                                .lowCardinalityKeyValue("pesit.mode", request.getMode().name())
                                .highCardinalityKeyValue("pesit.server", request.getServer())
                                .highCardinalityKeyValue("correlation.id", correlationId)
                                .observe(() -> doSendMessage(request, correlationId));
        }

        private TransferResponse doSendMessage(MessageRequest request, String correlationId) {
                PesitServer server = resolveServer(request.getServer());

                TransferHistory history = TransferHistory.builder()
                                .serverId(server.getId())
                                .serverName(server.getName())
                                .direction(TransferDirection.MESSAGE)
                                .remoteFilename(request.getMessageName())
                                .fileSize((long) request.getMessage().length())
                                .correlationId(correlationId)
                                .status(TransferStatus.IN_PROGRESS)
                                .build();
                history = historyRepository.save(history);

                try {
                        TransportChannel channel = createChannel(server);

                        try (PesitSession session = new PesitSession(channel, false)) {
                                switch (request.getMode()) {
                                        case FPDU -> executeMessageFpdu(session, server, request.getPartnerId(),
                                                        request.getMessage());
                                        case PI99 -> executeMessagePi99(session, server, request.getPartnerId(),
                                                        request.getMessage(),
                                                        request.isUsePi91());
                                        case FILE ->
                                                executeMessageAsFile(session, server, request.getPartnerId(),
                                                                request.getMessage(),
                                                                request.getMessageName());
                                }
                        }

                        history.setStatus(TransferStatus.COMPLETED);
                        history.setBytesTransferred((long) request.getMessage().length());
                        history.setCompletedAt(Instant.now());

                } catch (Exception e) {
                        history.setStatus(TransferStatus.FAILED);
                        history.setErrorMessage(e.getMessage());
                        history.setCompletedAt(Instant.now());
                        log.error("Message send failed: {}", e.getMessage(), e);
                }

                history = historyRepository.save(history);
                return mapToResponse(history);
        }

        @Transactional(readOnly = true)
        public Page<TransferHistory> getHistory(Pageable pageable) {
                return historyRepository.findAll(pageable);
        }

        @Transactional(readOnly = true)
        public Optional<TransferHistory> getTransferById(String id) {
                return historyRepository.findById(id);
        }

        @Transactional(readOnly = true)
        public List<TransferHistory> getByCorrelationId(String correlationId) {
                return historyRepository.findByCorrelationId(correlationId);
        }

        @Transactional(readOnly = true)
        public TransferStats getStats() {
                Long bytesLast24h = historyRepository.sumBytesTransferredSince(
                                TransferStatus.COMPLETED, Instant.now().minusSeconds(86400));
                return new TransferStats(
                                historyRepository.count(),
                                historyRepository.countByStatus(TransferStatus.COMPLETED),
                                historyRepository.countByStatus(TransferStatus.FAILED),
                                historyRepository.countByStatus(TransferStatus.IN_PROGRESS),
                                bytesLast24h != null ? bytesLast24h : 0L);
        }

        /**
         * Replay a previous transfer by creating a new transfer with the same
         * parameters.
         * Only SEND and RECEIVE transfers can be replayed (not MESSAGE).
         */
        @Transactional
        public Optional<TransferResponse> replayTransfer(String transferId) {
                return historyRepository.findById(transferId)
                                .map(original -> {
                                        log.info("Replaying transfer {} ({})", transferId, original.getDirection());

                                        switch (original.getDirection()) {
                                                case SEND -> {
                                                        TransferRequest request = TransferRequest.builder()
                                                                        .server(original.getServerId())
                                                                        .partnerId(original.getPartnerId())
                                                                        .filename(original.getLocalFilename())
                                                                        .remoteFilename(original.getRemoteFilename())
                                                                        .transferConfig(original.getTransferConfigId())
                                                                        .correlationId(UUID.randomUUID().toString())
                                                                        .build();
                                                        return sendFile(request);
                                                }
                                                case RECEIVE -> {
                                                        TransferRequest request = TransferRequest.builder()
                                                                        .server(original.getServerId())
                                                                        .partnerId(original.getPartnerId())
                                                                        .filename(original.getLocalFilename())
                                                                        .remoteFilename(original.getRemoteFilename())
                                                                        .transferConfig(original.getTransferConfigId())
                                                                        .correlationId(UUID.randomUUID().toString())
                                                                        .build();
                                                        return receiveFile(request);
                                                }
                                                case MESSAGE -> {
                                                        log.warn("Cannot replay MESSAGE transfers - original message content not stored");
                                                        throw new IllegalArgumentException(
                                                                        "MESSAGE transfers cannot be replayed");
                                                }
                                                default -> throw new IllegalArgumentException(
                                                                "Unknown transfer direction: "
                                                                                + original.getDirection());
                                        }
                                });
        }

        /**
         * Cancel an in-progress transfer.
         * Marks the transfer as CANCELLED and stores current progress for potential
         * resume.
         */
        @Transactional
        public Optional<TransferResponse> cancelTransfer(String transferId) {
                return historyRepository.findById(transferId)
                                .map(history -> {
                                        if (history.getStatus() != TransferStatus.IN_PROGRESS) {
                                                log.warn("Cannot cancel transfer {} - status is {}",
                                                                transferId, history.getStatus());
                                                return history;
                                        }

                                        log.info("Cancelling transfer {} at {} bytes",
                                                        transferId, history.getBytesTransferred());

                                        history.setStatus(TransferStatus.CANCELLED);
                                        history.setErrorMessage("Transfer cancelled by user");
                                        history.setCompletedAt(Instant.now());
                                        return historyRepository.save(history);
                                })
                                .map(this::mapToResponse);
        }

        /**
         * Resume a failed/cancelled transfer from the last sync point.
         * Creates a new transfer that continues from where the original left off.
         */
        @Transactional
        public Optional<TransferResponse> resumeTransfer(String transferId) {
                return historyRepository.findById(transferId)
                                .filter(h -> h.getStatus() == TransferStatus.FAILED
                                                || h.getStatus() == TransferStatus.CANCELLED)
                                .filter(h -> Boolean.TRUE.equals(h.getSyncPointsEnabled())
                                                && h.getLastSyncPoint() != null
                                                && h.getLastSyncPoint() > 0)
                                .map(original -> {
                                        log.info("Resuming transfer {} from sync point {} ({} bytes)",
                                                        transferId, original.getLastSyncPoint(),
                                                        original.getBytesAtLastSyncPoint());

                                        // Create a new transfer request with resume info
                                        TransferRequest request = TransferRequest.builder()
                                                        .server(original.getServerId())
                                                        .partnerId(original.getPartnerId())
                                                        .filename(original.getLocalFilename())
                                                        .remoteFilename(original.getRemoteFilename())
                                                        .transferConfig(original.getTransferConfigId())
                                                        .correlationId(original.getCorrelationId())
                                                        .syncPointsEnabled(true)
                                                        .resyncEnabled(true)
                                                        .resumeFromTransferId(transferId)
                                                        .build();

                                        return switch (original.getDirection()) {
                                                case SEND -> sendFile(request);
                                                case RECEIVE -> receiveFile(request);
                                                default -> throw new IllegalArgumentException(
                                                                "Cannot resume MESSAGE transfers");
                                        };
                                });
        }

        /**
         * Get transfers that can be resumed (failed/cancelled with sync points).
         */
        @Transactional(readOnly = true)
        public Page<TransferHistory> getResumableTransfers(Pageable pageable) {
                return historyRepository.findResumableTransfers(pageable);
        }

        // ========== Private helper methods ==========

        private PesitServer resolveServer(String serverNameOrId) {
                return serverService.findServer(serverNameOrId)
                                .orElseGet(() -> serverService.getDefaultServer()
                                                .orElseThrow(() -> new IllegalArgumentException(
                                                                "Server not found and no default configured: "
                                                                                + serverNameOrId)));
        }

        private TransferConfig resolveConfig(String configNameOrId) {
                if (configNameOrId == null) {
                        return configRepository.findByDefaultConfigTrue()
                                        .orElse(createDefaultConfig());
                }
                return configRepository.findByName(configNameOrId)
                                .or(() -> configRepository.findById(configNameOrId))
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Transfer config not found: " + configNameOrId));
        }

        private TransferConfig createDefaultConfig() {
                return TransferConfig.builder()
                                .name("default")
                                .chunkSize(32768)
                                .compressionEnabled(false)
                                .crcEnabled(true)
                                .build();
        }

        private TransportChannel createChannel(PesitServer server) {
                if (server.isTlsEnabled()) {
                        TlsTransportChannel tlsChannel;
                        if (server.getTruststoreData() != null && server.getTruststoreData().length > 0) {
                                tlsChannel = new TlsTransportChannel(
                                                server.getHost(),
                                                server.getPort(),
                                                server.getTruststoreData(),
                                                secretsService.decrypt(server.getTruststorePassword()),
                                                server.getKeystoreData(),
                                                secretsService.decrypt(server.getKeystorePassword()));
                        } else {
                                tlsChannel = new TlsTransportChannel(server.getHost(), server.getPort());
                        }
                        tlsChannel.setReceiveTimeout(server.getReadTimeout());
                        return tlsChannel;
                }
                TcpTransportChannel channel = new TcpTransportChannel(server.getHost(), server.getPort());
                channel.setReceiveTimeout(server.getReadTimeout());
                return channel;
        }

        private StorageConnector createConnectorFromConnectionId(String connectionId) {
                StorageConnection connection = connectionRepository.findById(connectionId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Storage connection not found: " + connectionId));

                if (!connection.isEnabled()) {
                        throw new IllegalArgumentException("Storage connection is disabled: " + connection.getName());
                }

                try {
                        java.util.Map<String, String> config = objectMapper.readValue(
                                        connection.getConfigJson(),
                                        new TypeReference<java.util.Map<String, String>>() {
                                        });
                        // Decrypt sensitive fields before using
                        config = decryptSensitiveFields(config);
                        return connectorRegistry.createConnector(connection.getConnectorType(), config);
                } catch (Exception e) {
                        throw new IllegalArgumentException(
                                        "Failed to create connector for connection " + connection.getName() + ": "
                                                        + e.getMessage(),
                                        e);
                }
        }

        // Sensitive fields that need decryption
        private static final java.util.List<String> SENSITIVE_FIELDS = java.util.List.of(
                        "password", "secret", "secretKey", "accessKeySecret",
                        "privateKey", "passphrase", "apiKey", "token");

        private java.util.Map<String, String> decryptSensitiveFields(java.util.Map<String, String> config) {
                if (config == null)
                        return null;
                java.util.Map<String, String> result = new java.util.HashMap<>(config);
                for (String field : SENSITIVE_FIELDS) {
                        if (result.containsKey(field) && result.get(field) != null) {
                                result.put(field, secretsService.decrypt(result.get(field)));
                        }
                }
                return result;
        }

        private TransferHistory createHistory(PesitServer server, TransferConfig config,
                        TransferDirection direction, String localPath,
                        String remotePath, String partnerId, String correlationId) {
                return TransferHistory.builder()
                                .serverId(server.getId())
                                .serverName(server.getName())
                                .partnerId(partnerId)
                                .direction(direction)
                                .localFilename(localPath)
                                .remoteFilename(remotePath)
                                .transferConfigId(config.getId())
                                .transferConfigName(config.getName())
                                .correlationId(correlationId)
                                .status(TransferStatus.PENDING)
                                .build();
        }

        private void executeSendTransfer(PesitSession session, PesitServer server,
                        TransferRequest request, byte[] data, TransferConfig config)
                        throws IOException, InterruptedException {
                int connectionId = 1;

                // Resolve transfer parameters from request or config
                String virtualFile = request.getVirtualFile() != null ? request.getVirtualFile()
                                : request.getRemoteFilename();
                int fileType = request.getFileType() != null ? request.getFileType() : 0; // default binary
                int chunkSize = request.getChunkSize() != null ? request.getChunkSize() : config.getChunkSize();
                int priority = request.getPriority() != null ? request.getPriority() : config.getPriority();
                int recordLength = config.getRecordLength() != null ? config.getRecordLength() : 0;

                log.info("Transfer config: virtualFile={}, fileType={}, chunkSize={}, priority={}",
                                virtualFile, fileType, chunkSize, priority);

                // Determine sync point settings (request overrides config)
                boolean syncPointsEnabled = request.getSyncPointsEnabled() != null
                                ? request.getSyncPointsEnabled()
                                : config.isSyncPointsEnabled();
                boolean resyncEnabled = request.getResyncEnabled() != null
                                ? request.getResyncEnabled()
                                : config.isResyncEnabled();

                // CONNECT - use ConnectMessageBuilder for correct structure
                ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                                .demandeur(request.getPartnerId())
                                .serveur(server.getServerId())
                                .writeAccess()
                                .syncPointsEnabled(syncPointsEnabled)
                                .resyncEnabled(resyncEnabled);
                if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                        // Decrypt password if it's encrypted (vault: or ENC: prefix)
                        String password = secretsService.decrypt(request.getPassword());
                        connectBuilder.password(password);
                        log.debug("Password provided for CONNECT (length: {})", password.length());
                }
                Fpdu connectFpdu = connectBuilder.build(connectionId);

                Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
                int serverConnectionId = aconnect.getIdSrc();

                // CREATE - use CreateMessageBuilder for correct structure
                int transferId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF; // PI_13 is 3 bytes max
                // Use small values known to work with Connect:Express
                int effectiveRecordLength = recordLength > 0 ? Math.min(recordLength, 1024) : 30;
                int effectiveMaxEntity = Math.min(chunkSize, 4096);
                Fpdu createFpdu = new CreateMessageBuilder()
                                .filename(virtualFile)
                                .transferId(transferId)
                                .variableFormat()
                                .recordLength(effectiveRecordLength)
                                .maxEntitySize(effectiveMaxEntity)
                                .build(serverConnectionId);

                session.sendFpduWithAck(createFpdu);

                // OPEN (ORF) - open file for writing
                Fpdu openFpdu = new Fpdu(FpduType.OPEN)
                                .withIdDst(serverConnectionId);
                session.sendFpduWithAck(openFpdu);

                // WRITE (signals start of data transfer, no data payload)
                Fpdu writeFpdu = new Fpdu(FpduType.WRITE)
                                .withIdDst(serverConnectionId);
                session.sendFpduWithAck(writeFpdu);

                // DTF - send data in chunks using configured chunk size
                // Send sync points periodically for restart capability
                int offset = 0;
                long bytesSinceLastSync = 0;
                int syncPointNumber = 0;
                // Calculate sync point interval in bytes (auto or from request/config)
                long syncIntervalBytes = calculateSyncPointInterval(request, config, data.length);

                while (offset < data.length) {
                        int currentChunkSize = Math.min(chunkSize, data.length - offset);
                        byte[] chunk = new byte[currentChunkSize];
                        System.arraycopy(data, offset, chunk, 0, currentChunkSize);

                        Fpdu dtfFpdu = new Fpdu(FpduType.DTF)
                                        .withIdDst(serverConnectionId);
                        session.sendFpduWithData(dtfFpdu, chunk);
                        offset += currentChunkSize;
                        bytesSinceLastSync += currentChunkSize;
                        log.debug("Sent DTF chunk: {} bytes, total sent: {}/{}", currentChunkSize, offset, data.length);

                        // Send sync point periodically for restart capability
                        if (syncPointsEnabled && syncIntervalBytes > 0 && bytesSinceLastSync >= syncIntervalBytes) {
                                syncPointNumber++;
                                Fpdu synFpdu = new Fpdu(FpduType.SYN)
                                                .withIdDst(serverConnectionId)
                                                .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncPointNumber));
                                session.sendFpduWithAck(synFpdu);
                                log.info("Sync point {} acknowledged at {} bytes", syncPointNumber, offset);
                                bytesSinceLastSync = 0;
                        }
                }

                // DTF_END - signal end of data transfer (no ACK expected)
                Fpdu dtfEndFpdu = new Fpdu(FpduType.DTF_END)
                                .withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
                session.sendFpdu(dtfEndFpdu);

                // TRANS_END
                Fpdu transendFpdu = new Fpdu(FpduType.TRANS_END)
                                .withIdDst(serverConnectionId);
                session.sendFpduWithAck(transendFpdu);

                // CLOSE (CRF) - close file
                Fpdu closeFpdu = new Fpdu(FpduType.CLOSE)
                                .withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
                session.sendFpduWithAck(closeFpdu);

                // DESELECT
                Fpdu deselectFpdu = new Fpdu(FpduType.DESELECT)
                                .withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
                session.sendFpduWithAck(deselectFpdu);

                // RELEASE (session-level FPDU - keeps idSrc)
                Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                                .withIdDst(serverConnectionId)
                                .withIdSrc(connectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
                session.sendFpduWithAck(releaseFpdu);
        }

        private long executeReceiveTransfer(PesitSession session, PesitServer server,
                        TransferRequest request, StorageConnector connector, String destPath, TransferConfig config)
                        throws IOException, InterruptedException, ConnectorException {
                int connectionId = 1;
                int chunkSize = config.getChunkSize();
                String remoteFilename = request.getRemoteFilename();

                // Determine sync point settings (request overrides config)
                boolean syncPointsEnabled = request.getSyncPointsEnabled() != null
                                ? request.getSyncPointsEnabled()
                                : config.isSyncPointsEnabled();
                boolean resyncEnabled = request.getResyncEnabled() != null
                                ? request.getResyncEnabled()
                                : config.isResyncEnabled();

                // CONNECT with read access - use ConnectMessageBuilder
                ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                                .demandeur(request.getPartnerId())
                                .serveur(server.getServerId())
                                .readAccess()
                                .syncPointsEnabled(syncPointsEnabled)
                                .resyncEnabled(resyncEnabled);
                if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                        // Decrypt password if it's encrypted (vault: or ENC: prefix)
                        String password = secretsService.decrypt(request.getPassword());
                        connectBuilder.password(password);
                        log.debug("Password provided for CONNECT (length: {})", password.length());
                }
                Fpdu connectFpdu = connectBuilder.build(connectionId);

                Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
                int serverConnectionId = aconnect.getIdSrc();

                // SELECT
                String virtualFile = request.getVirtualFile() != null ? request.getVirtualFile() : remoteFilename;
                ParameterValue pgi9 = new ParameterValue(
                                ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                                new ParameterValue(PI_11_TYPE_FICHIER, 0),
                                new ParameterValue(PI_12_NOM_FICHIER, virtualFile));

                int transferId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF;
                Fpdu selectFpdu = new Fpdu(FpduType.SELECT)
                                .withIdDst(serverConnectionId)
                                .withParameter(pgi9)
                                .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, transferId))
                                .withParameter(new ParameterValue(PI_14_ATTRIBUTS_DEMANDES, 0)) // Required by C:X
                                .withParameter(new ParameterValue(PI_17_PRIORITE, 0))
                                .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, chunkSize));
                session.sendFpduWithAck(selectFpdu);

                // OPEN (file-level - no idSrc)
                session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnectionId));

                // READ (file-level - no idSrc)
                session.sendFpduWithAck(new Fpdu(FpduType.READ)
                                .withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_18_POINT_RELANCE, 0)));

                // Receive DTF chunks and write to connector
                long totalBytes = 0;
                int chunkCount = 0;
                try (OutputStream connectorOut = connector.write(destPath, false)) {
                        boolean receiving = true;
                        while (receiving) {
                                byte[] rawFpdu = session.receiveRawFpdu();
                                if (rawFpdu.length >= 4) {
                                        int phase = rawFpdu[2] & 0xFF;
                                        int type = rawFpdu[3] & 0xFF;

                                        if (phase == 0x00 && (type == 0x00 || type == 0x40 || type == 0x41
                                                        || type == 0x42)) {
                                                if (rawFpdu.length > 6) {
                                                        int dataLen = rawFpdu.length - 6;
                                                        connectorOut.write(rawFpdu, 6, dataLen);
                                                        totalBytes += dataLen;
                                                        chunkCount++;
                                                }
                                        } else if (phase == 0xC0 && type == 0x22) {
                                                receiving = false;
                                        } else {
                                                receiving = false;
                                        }
                                }
                        }
                }
                log.info("Wrote {} bytes to connector in {} chunks", totalBytes, chunkCount);

                // Cleanup: TRANS_END, CLOSE, DESELECT (file-level - no idSrc), RELEASE
                // (session-level - with idSrc)
                session.sendFpduWithAck(
                                new Fpdu(FpduType.TRANS_END).withIdDst(serverConnectionId));
                session.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 })));
                session.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 })));
                session.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(serverConnectionId).withIdSrc(connectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 })));

                return totalBytes;
        }

        private void executeMessageFpdu(PesitSession session, PesitServer server, String partnerId, String message)
                        throws IOException, InterruptedException {
                int connectionId = 1;

                // CONNECT - use ConnectMessageBuilder
                Fpdu connectFpdu = new ConnectMessageBuilder()
                                .demandeur(partnerId)
                                .serveur(server.getServerId())
                                .writeAccess()
                                .build(connectionId);

                Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
                int serverConnectionId = aconnect.getIdSrc();

                // MSG (file-level - no idSrc)
                ParameterValue pgi9 = new ParameterValue(
                                ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                                new ParameterValue(PI_12_NOM_FICHIER, "MESSAGE"));

                int transferId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF;
                Fpdu msgFpdu = new Fpdu(FpduType.MSG)
                                .withIdDst(serverConnectionId)
                                .withParameter(pgi9)
                                .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, transferId))
                                .withParameter(new ParameterValue(PI_91_MESSAGE, message));

                session.sendFpduWithAck(msgFpdu);

                // RELEASE (session-level - keeps idSrc)
                Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                                .withIdDst(serverConnectionId)
                                .withIdSrc(connectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
                session.sendFpduWithAck(releaseFpdu);
        }

        private void executeMessagePi99(PesitSession session, PesitServer server, String partnerId,
                        String message, boolean usePi91) throws IOException, InterruptedException {
                int connectionId = 1;

                // CONNECT with message in PI_91 or PI_99 (special case - can't use
                // ConnectMessageBuilder)
                Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                                .withIdSrc(connectionId)
                                .withIdDst(0) // Must be 0 for CONNECT
                                .withParameter(new ParameterValue(PI_03_DEMANDEUR, partnerId))
                                .withParameter(new ParameterValue(PI_04_SERVEUR, server.getServerId()))
                                .withParameter(new ParameterValue(PI_06_VERSION, 2))
                                .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

                if (usePi91) {
                        connectFpdu.withParameter(new ParameterValue(PI_91_MESSAGE, message));
                } else {
                        connectFpdu.withParameter(new ParameterValue(PI_99_MESSAGE_LIBRE, message));
                }

                Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
                int serverConnectionId = aconnect.getIdSrc();

                Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                                .withIdDst(serverConnectionId)
                                .withIdSrc(connectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
                session.sendFpduWithAck(releaseFpdu);
        }

        private void executeMessageAsFile(PesitSession session, PesitServer server, String partnerId,
                        String message, String messageName) throws IOException, InterruptedException {
                byte[] data = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                String filename = messageName != null ? messageName : "message_" + System.currentTimeMillis() + ".txt";

                TransferConfig config = createDefaultConfig();
                TransferRequest request = TransferRequest.builder()
                                .partnerId(partnerId)
                                .remoteFilename(filename)
                                .build();
                executeSendTransfer(session, server, request, data, config);
        }

        private TransferResponse mapToResponse(TransferHistory history) {
                return TransferResponse.builder()
                                .transferId(history.getId())
                                .correlationId(history.getCorrelationId())
                                .direction(history.getDirection())
                                .status(history.getStatus())
                                .serverName(history.getServerName())
                                .localFilename(history.getLocalFilename())
                                .remoteFilename(history.getRemoteFilename())
                                .fileSize(history.getFileSize())
                                .bytesTransferred(history.getBytesTransferred())
                                .checksum(history.getChecksum())
                                .errorMessage(history.getErrorMessage())
                                .diagnosticCode(history.getDiagnosticCode())
                                .startedAt(history.getStartedAt())
                                .completedAt(history.getCompletedAt())
                                .durationMs(history.getDurationMs())
                                .traceId(history.getTraceId())
                                .spanId(history.getSpanId())
                                .build();
        }

        private String computeChecksum(byte[] data) {
                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        return HexFormat.of().formatHex(digest.digest(data));
                } catch (Exception e) {
                        return null;
                }
        }

        /**
         * Calculate sync point interval in bytes.
         * If request specifies an interval, use it.
         * Otherwise, auto-calculate based on file size:
         * - Files < 1MB: no sync points (return 0)
         * - Files 1-10MB: every 256KB
         * - Files 10-100MB: every 1MB
         * - Files > 100MB: every 5MB
         */
        private long calculateSyncPointInterval(TransferRequest request, TransferConfig config, int fileSize) {
                // Request takes priority
                if (request.getSyncPointIntervalBytes() != null && request.getSyncPointIntervalBytes() > 0) {
                        return request.getSyncPointIntervalBytes();
                }

                // Check if sync points are enabled
                boolean enabled = request.getSyncPointsEnabled() != null
                                ? request.getSyncPointsEnabled()
                                : config.isSyncPointsEnabled();
                if (!enabled) {
                        return 0;
                }

                // Auto-calculate based on file size
                long KB = 1024;
                long MB = 1024 * KB;

                if (fileSize < MB) {
                        return 0; // No sync points for small files
                } else if (fileSize < 10 * MB) {
                        return 256 * KB; // 256KB intervals
                } else if (fileSize < 100 * MB) {
                        return MB; // 1MB intervals
                } else {
                        return 5 * MB; // 5MB intervals
                }
        }
}
