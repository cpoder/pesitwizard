package com.pesitwizard.client.service;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
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
import com.pesitwizard.fpdu.ParameterIdentifier;
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

        /** Set of transfer IDs that have been requested to cancel */
        private final Set<String> cancelledTransfers = ConcurrentHashMap.newKeySet();

        private final PesitServerService serverService;
        private final TransferConfigRepository configRepository;
        private final TransferHistoryRepository historyRepository;
        private final ObservationRegistry observationRegistry;
        private final PathPlaceholderService placeholderService;
        private final ConnectorRegistry connectorRegistry;
        private final StorageConnectionRepository connectionRepository;
        private final ObjectMapper objectMapper;
        private final SecretsService secretsService;
        private final TransferProgressService progressService;

        public TransferResponse sendFile(TransferRequest request) {
                String correlationId = request.getCorrelationId() != null ? request.getCorrelationId()
                                : UUID.randomUUID().toString();

                PesitServer server = resolveServer(request.getServer());
                TransferConfig config = resolveConfig(request.getTransferConfig());
                String filename = request.getFilename();
                String sourceConnId = request.getSourceConnectionId();

                // Get file size for history record
                long fileSize;
                try {
                        if (sourceConnId != null) {
                                StorageConnector connector = createConnectorFromConnectionId(sourceConnId);
                                fileSize = connector.getMetadata(filename).getSize();
                                connector.close();
                        } else {
                                Path localFile = Path.of(filename);
                                if (!Files.exists(localFile)) {
                                        throw new IllegalArgumentException("Local file not found: " + filename);
                                }
                                fileSize = Files.size(localFile);
                        }
                } catch (Exception e) {
                        throw new RuntimeException("Failed to get file size: " + e.getMessage(), e);
                }

                // Create history record and return immediately
                TransferHistory history = createHistory(server, config, TransferDirection.SEND,
                                filename, request.getRemoteFilename(), request.getPartnerId(),
                                correlationId);
                history.setFileSize(fileSize);
                history.setStatus(TransferStatus.IN_PROGRESS);
                history.setBytesTransferred(0L);
                history = historyRepository.save(history);

                // Execute transfer asynchronously
                doSendFileAsync(request, history.getId(), server, config, fileSize, correlationId);

                return mapToResponse(history);
        }

        @Async("transferExecutor")
        public void doSendFileAsync(TransferRequest request, String historyId, PesitServer server,
                        TransferConfig config, long fileSize, String correlationId) {
                Observation.createNotStarted("pesit.send", observationRegistry)
                                .lowCardinalityKeyValue("pesit.direction", "SEND")
                                .highCardinalityKeyValue("pesit.server", request.getServer())
                                .highCardinalityKeyValue("correlation.id", correlationId)
                                .observe(() -> doSendFile(request, historyId, server, config, fileSize));
        }

        private void doSendFile(TransferRequest request, String historyId, PesitServer server,
                        TransferConfig config, long fileSize) {
                String filename = request.getFilename();
                String sourceConnId = request.getSourceConnectionId();

                StorageConnector connector = null;
                InputStream inputStream = null;

                try {
                        if (sourceConnId != null) {
                                connector = createConnectorFromConnectionId(sourceConnId);
                                inputStream = connector.read(filename, 0);
                                log.info("Streaming {} bytes from connector {} path {}", fileSize, sourceConnId,
                                                filename);
                        } else {
                                Path localFile = Path.of(filename);
                                inputStream = new BufferedInputStream(Files.newInputStream(localFile), 64 * 1024);
                                log.info("Streaming {} bytes from local file {}", fileSize, filename);
                        }

                        TransportChannel channel = createChannel(server, fileSize);

                        try (PesitSession session = new PesitSession(channel, false)) {
                                executeSendTransferStreaming(session, server, request, inputStream, fileSize, config,
                                                historyId);
                        }

                        // Update history on success
                        log.info("Transfer {} completed successfully, updating DB and sending WebSocket", historyId);
                        historyRepository.findById(historyId).ifPresent(history -> {
                                history.setStatus(TransferStatus.COMPLETED);
                                history.setBytesTransferred(fileSize);
                                history.setCompletedAt(Instant.now());
                                historyRepository.save(history);
                        });

                        // Send completion via WebSocket
                        log.info("Sending WebSocket COMPLETED for transfer {}", historyId);
                        progressService.sendComplete(historyId, fileSize, fileSize);

                } catch (Exception e) {
                        // Update history on failure
                        log.error("Transfer {} FAILED: {}", historyId, e.getMessage());
                        historyRepository.findById(historyId).ifPresent(history -> {
                                history.setStatus(TransferStatus.FAILED);
                                history.setErrorMessage(e.getMessage());
                                history.setCompletedAt(Instant.now());
                                historyRepository.save(history);
                        });

                        // Send failure via WebSocket
                        log.info("Sending WebSocket FAILED for transfer {}", historyId);
                        progressService.sendFailed(historyId, e.getMessage());
                        log.error("Send transfer failed: {}", e.getMessage(), e);
                } finally {
                        // Clear cancellation flag
                        clearCancellation(historyId);

                        if (inputStream != null) {
                                try {
                                        inputStream.close();
                                } catch (Exception ignored) {
                                }
                        }
                        if (connector != null) {
                                try {
                                        connector.close();
                                } catch (Exception ignored) {
                                }
                        }
                }
        }

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
         * Signals the running transfer to stop and marks it as CANCELLED.
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

                                        // Signal the running async transfer to stop
                                        cancelledTransfers.add(transferId);
                                        log.info("Signalling cancellation for transfer {} at {} bytes",
                                                        transferId, history.getBytesTransferred());

                                        history.setStatus(TransferStatus.CANCELLED);
                                        history.setErrorMessage("Transfer cancelled by user");
                                        history.setCompletedAt(Instant.now());

                                        // Send WebSocket notification
                                        progressService.sendFailed(transferId, "Transfer cancelled by user");

                                        return historyRepository.save(history);
                                })
                                .map(this::mapToResponse);
        }

        /**
         * Check if a transfer has been cancelled.
         */
        private boolean isCancelled(String transferId) {
                return cancelledTransfers.contains(transferId);
        }

        /**
         * Clear cancellation flag after transfer completes.
         */
        private void clearCancellation(String transferId) {
                cancelledTransfers.remove(transferId);
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

        /**
         * Calculate optimal chunk size based on file size.
         * PeSIT SIT standard: max entity size = 4050 bytes (article) + 6 (header) =
         * 4056
         * CX and most servers support max 4096 bytes.
         * We use 4096 as the standard max to ensure compatibility.
         */
        private int calculateOptimalChunkSize(long fileSize) {
                // Use 4096 as the standard PeSIT chunk size (compatible with SIT/CX)
                return 4096;
        }

        /**
         * Parse a numeric value from a byte array (big-endian).
         */
        private int parseNumericValue(byte[] bytes) {
                int value = 0;
                for (byte b : bytes) {
                        value = (value << 8) | (b & 0xFF);
                }
                return value;
        }

        private TransportChannel createChannel(PesitServer server) {
                return createChannel(server, 0);
        }

        private TransportChannel createChannel(PesitServer server, long fileSize) {
                // Calculate timeout based on file size: min 60s, add 1 minute per 50MB
                int baseTimeout = server.getReadTimeout() != null ? server.getReadTimeout() : 60000;
                int fileSizeTimeout = (int) ((fileSize / (50 * 1024 * 1024)) * 60000);
                int timeout = Math.max(baseTimeout, baseTimeout + fileSizeTimeout);
                // Cap at 30 minutes max
                timeout = Math.min(timeout, 30 * 60 * 1000);

                if (fileSize > 0) {
                        log.info("Using timeout of {}ms for file size {} bytes", timeout, fileSize);
                }

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
                        tlsChannel.setReceiveTimeout(timeout);
                        return tlsChannel;
                }
                TcpTransportChannel channel = new TcpTransportChannel(server.getHost(), server.getPort());
                channel.setReceiveTimeout(timeout);
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
                        TransferRequest request, byte[] data, TransferConfig config, String historyId)
                        throws IOException, InterruptedException {
                int connectionId = 1;

                // Resolve transfer parameters from request or config
                String virtualFile = request.getVirtualFile() != null ? request.getVirtualFile()
                                : request.getRemoteFilename();
                int fileType = request.getFileType() != null ? request.getFileType() : 0; // default binary
                // Auto-calculate chunkSize based on file size, or use explicit override
                int chunkSize = request.getChunkSize() != null ? request.getChunkSize()
                                : calculateOptimalChunkSize(data.length);
                int priority = request.getPriority() != null ? request.getPriority() : config.getPriority();
                int recordLength = config.getRecordLength() != null ? config.getRecordLength() : 0;

                log.info("Transfer config: virtualFile={}, fileType={}, chunkSize={} (fileSize={}), priority={}",
                                virtualFile, fileType, chunkSize, data.length, priority);

                // Determine sync point settings (request overrides config)
                boolean syncPointsEnabled = request.getSyncPointsEnabled() != null
                                ? request.getSyncPointsEnabled()
                                : config.isSyncPointsEnabled();
                boolean resyncEnabled = request.getResyncEnabled() != null
                                ? request.getResyncEnabled()
                                : config.isResyncEnabled();

                // Progress tracking: update every 5% of file or minimum every 32KB
                final long progressUpdateInterval = Math.max(32 * 1024, data.length / 20);
                long bytesSinceLastProgressUpdate = 0;
                log.info("Progress update interval: {} bytes (fileSize={})", progressUpdateInterval, data.length);

                // Calculate sync interval BEFORE CONNECT so we can declare it in PI 7
                long plannedSyncIntervalBytes = calculateSyncPointInterval(request, config, data.length);
                int plannedSyncIntervalKb = plannedSyncIntervalBytes > 0
                                ? (int) (plannedSyncIntervalBytes / 1024)
                                : 0; // 0 = no sync points

                // CONNECT - use ConnectMessageBuilder for correct structure
                // IMPORTANT: declare the SAME interval we will actually use for sending SYN
                ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                                .demandeur(request.getPartnerId())
                                .serveur(server.getServerId())
                                .writeAccess()
                                .syncPointsEnabled(syncPointsEnabled && plannedSyncIntervalKb > 0)
                                .syncIntervalKb(plannedSyncIntervalKb)
                                .resyncEnabled(resyncEnabled);
                if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                        // Decrypt password if it's encrypted (vault: or ENC: prefix)
                        String password = secretsService.decrypt(request.getPassword());
                        connectBuilder.password(password);
                        log.debug("Password provided for CONNECT (length: {})", password.length());
                }
                log.info("CONNECT: declaring sync interval = {} KB (syncEnabled={})",
                                plannedSyncIntervalKb, syncPointsEnabled && plannedSyncIntervalKb > 0);
                Fpdu connectFpdu = connectBuilder.build(connectionId);

                Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
                int serverConnectionId = aconnect.getIdSrc();

                // Parse negotiated sync points from ACONNECT (PI 7)
                // IMPORTANT: Use server's negotiated interval, not client's auto-calculation
                long negotiatedSyncIntervalBytes = 0;
                ParameterValue pi7NonStreaming = aconnect.getParameter(ParameterIdentifier.PI_07_SYNC_POINTS);
                if (pi7NonStreaming != null && pi7NonStreaming.getValue() != null
                                && pi7NonStreaming.getValue().length >= 3) {
                        byte[] syncBytes = pi7NonStreaming.getValue();
                        int negotiatedSyncIntervalKb = ((syncBytes[0] & 0xFF) << 8) | (syncBytes[1] & 0xFF);
                        int negotiatedSyncWindow = syncBytes[2] & 0xFF;
                        log.info("ACONNECT: Negotiated sync points - interval={}KB, window={}",
                                        negotiatedSyncIntervalKb, negotiatedSyncWindow);
                        if (negotiatedSyncIntervalKb == 0) {
                                syncPointsEnabled = false;
                                log.info("Server disabled sync points (interval=0)");
                        } else {
                                negotiatedSyncIntervalBytes = negotiatedSyncIntervalKb * 1024L;
                                log.info("Using server-negotiated sync interval: {} bytes",
                                                negotiatedSyncIntervalBytes);
                        }
                }

                // Parse PI 25 from ACONNECT - server's max entity size capability
                int serverMaxEntitySize = 0;
                ParameterValue aconnectPi25 = aconnect.getParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE);
                if (aconnectPi25 != null && aconnectPi25.getValue() != null) {
                        serverMaxEntitySize = parseNumericValue(aconnectPi25.getValue());
                        log.info("ACONNECT: Server max entity size (PI 25) = {}", serverMaxEntitySize);
                }

                // CREATE - use CreateMessageBuilder for correct structure
                // Strategy: propose MAX values, server will respond with its limits in
                // ACK_CREATE
                int transferId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF; // PI_13 is 3 bytes max
                // PI 25 (maxEntitySize) = propose max (65535) or server limit from ACONNECT
                // PI 32 (recordLength) = PI 25 - 6 (FPDU header)
                int proposedMaxEntity = serverMaxEntitySize > 0 ? serverMaxEntitySize : 65535;
                int proposedRecordLength = proposedMaxEntity - 6;
                // PI 42 (maxReservation) = file size in KB (with PI 41 = 0 for KB unit)
                long fileSizeKB = (data.length + 1023) / 1024; // Round up to KB
                log.info("CREATE: proposing PI25(entity)={}, PI32(article)={}, syncPointsEnabled={}",
                                proposedMaxEntity, proposedRecordLength, syncPointsEnabled);
                Fpdu createFpdu = new CreateMessageBuilder()
                                .filename(virtualFile)
                                .transferId(transferId)
                                .variableFormat()
                                .recordLength(proposedRecordLength)
                                .maxEntitySize(proposedMaxEntity)
                                .fileSizeKB(fileSizeKB)
                                .build(serverConnectionId);

                Fpdu ackCreate = session.sendFpduWithAck(createFpdu);

                // Check diagnostic - according to spec, non-zero = failure, return to CONNECTED
                // state
                checkAckDiagnostic(ackCreate, "ACK_CREATE");

                // Use server's negotiated PI 25 from ACK_CREATE to determine actual chunk size
                int actualChunkSize;
                ParameterValue pi25 = ackCreate.getParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE);
                if (pi25 != null && pi25.getValue() != null) {
                        int negotiatedMaxEntity = parseNumericValue(pi25.getValue());
                        actualChunkSize = negotiatedMaxEntity - 6; // Entity includes 6-byte header
                        log.info("ACK_CREATE: PI25 negotiated={}, using chunk size (article)={}",
                                        negotiatedMaxEntity, actualChunkSize);
                } else {
                        // Server didn't specify, use our proposed value
                        actualChunkSize = proposedRecordLength;
                        log.info("ACK_CREATE: no PI25 in response, using proposed chunk size={}", actualChunkSize);
                }

                // OPEN (ORF) - open file for writing
                Fpdu openFpdu = new Fpdu(FpduType.OPEN)
                                .withIdDst(serverConnectionId);
                Fpdu ackOpen = session.sendFpduWithAck(openFpdu);
                checkAckDiagnostic(ackOpen, "ACK_OPEN");

                // Check negotiated compression from ACK_OPEN (PI 21)
                ParameterValue pi21 = ackOpen.getParameter(ParameterIdentifier.PI_21_COMPRESSION);
                if (pi21 != null && pi21.getValue() != null && pi21.getValue().length >= 1) {
                        int compressionAccepted = pi21.getValue()[0] & 0xFF;
                        log.info("ACK_OPEN: Compression {} (0=refused, 1=accepted)",
                                        compressionAccepted == 0 ? "refused" : "accepted");
                }

                // WRITE (signals start of data transfer, no data payload)
                Fpdu writeFpdu = new Fpdu(FpduType.WRITE)
                                .withIdDst(serverConnectionId);
                Fpdu ackWrite = session.sendFpduWithAck(writeFpdu);
                checkAckDiagnostic(ackWrite, "ACK_WRITE");

                // Check restart point from ACK_WRITE (PI 18)
                ParameterValue pi18 = ackWrite.getParameter(ParameterIdentifier.PI_18_POINT_RELANCE);
                if (pi18 != null && pi18.getValue() != null) {
                        int restartPoint = parseNumericValue(pi18.getValue());
                        log.info("ACK_WRITE: Restart point = {}", restartPoint);
                }

                // DTF - send data in chunks using configured chunk size
                // Send sync points periodically for restart capability
                int offset = 0;
                long bytesSinceLastSync = 0;
                int syncPointNumber = 0;
                // Use server-negotiated sync interval (from ACONNECT PI 7)
                // Fall back to auto-calculation only if server didn't negotiate
                long syncIntervalBytes = negotiatedSyncIntervalBytes > 0
                                ? negotiatedSyncIntervalBytes
                                : calculateSyncPointInterval(request, config, data.length);
                log.info("Sync interval: {} bytes (negotiated={}, syncEnabled={})",
                                syncIntervalBytes, negotiatedSyncIntervalBytes, syncPointsEnabled);

                while (offset < data.length) {
                        // Check for cancellation
                        if (isCancelled(historyId)) {
                                log.info("Transfer {} cancelled at {} bytes", historyId, offset);
                                throw new RuntimeException("Transfer cancelled by user");
                        }

                        int currentChunkSize = Math.min(actualChunkSize, data.length - offset);
                        byte[] chunk = new byte[currentChunkSize];
                        System.arraycopy(data, offset, chunk, 0, currentChunkSize);

                        Fpdu dtfFpdu = new Fpdu(FpduType.DTF)
                                        .withIdDst(serverConnectionId);
                        session.sendFpduWithData(dtfFpdu, chunk);
                        offset += currentChunkSize;
                        bytesSinceLastSync += currentChunkSize;
                        bytesSinceLastProgressUpdate += currentChunkSize;
                        log.debug("Sent DTF chunk: {} bytes, total sent: {}/{}", currentChunkSize, offset, data.length);

                        // Update progress in database periodically
                        if (bytesSinceLastProgressUpdate >= progressUpdateInterval) {
                                updateTransferProgress(historyId, offset, data.length, syncPointNumber);
                                bytesSinceLastProgressUpdate = 0;
                        }

                        // Send sync point periodically for restart capability
                        if (syncPointsEnabled && syncIntervalBytes > 0 && bytesSinceLastSync >= syncIntervalBytes) {
                                syncPointNumber++;
                                Fpdu synFpdu = new Fpdu(FpduType.SYN)
                                                .withIdDst(serverConnectionId)
                                                .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncPointNumber));
                                session.sendFpduWithAck(synFpdu);
                                log.info("Sync point {} acknowledged at {} bytes", syncPointNumber, offset);
                                bytesSinceLastSync = 0;
                                // Also update progress after sync point
                                updateTransferProgress(historyId, offset, data.length, syncPointNumber);
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

        /**
         * Execute send transfer with streaming - reads from InputStream in chunks
         * without loading entire file into memory.
         */
        private void executeSendTransferStreaming(PesitSession session, PesitServer server,
                        TransferRequest request, InputStream inputStream, long fileSize, TransferConfig config,
                        String historyId)
                        throws IOException, InterruptedException {
                int connectionId = 1;

                // Resolve transfer parameters from request or config
                String virtualFile = request.getVirtualFile() != null ? request.getVirtualFile()
                                : request.getRemoteFilename();
                // Auto-calculate chunkSize based on file size, or use explicit override
                int chunkSize = request.getChunkSize() != null ? request.getChunkSize()
                                : calculateOptimalChunkSize(fileSize);
                int recordLength = config.getRecordLength() != null ? config.getRecordLength() : 0;

                log.info("Streaming transfer: virtualFile={}, fileSize={}, chunkSize={} (auto-calculated)",
                                virtualFile, fileSize, chunkSize);

                // Determine sync point settings (request overrides config)
                boolean syncPointsEnabled = request.getSyncPointsEnabled() != null
                                ? request.getSyncPointsEnabled()
                                : config.isSyncPointsEnabled();
                boolean resyncEnabled = request.getResyncEnabled() != null
                                ? request.getResyncEnabled()
                                : config.isResyncEnabled();

                // Progress tracking: update every 5% of file or minimum every 32KB
                final long progressUpdateInterval = Math.max(32 * 1024, fileSize / 20);
                long bytesSinceLastProgressUpdate = 0;
                log.info("Progress update interval: {} bytes (fileSize={})", progressUpdateInterval, fileSize);

                // Calculate sync interval BEFORE CONNECT so we can declare it in PI 7
                long plannedSyncIntervalBytes = calculateSyncPointInterval(request, config,
                                (int) Math.min(fileSize, Integer.MAX_VALUE));
                int plannedSyncIntervalKb = plannedSyncIntervalBytes > 0
                                ? (int) (plannedSyncIntervalBytes / 1024)
                                : 0; // 0 = no sync points

                // CONNECT - declare the SAME interval we will actually use for sending SYN
                ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                                .demandeur(request.getPartnerId())
                                .serveur(server.getServerId())
                                .writeAccess()
                                .syncPointsEnabled(syncPointsEnabled && plannedSyncIntervalKb > 0)
                                .syncIntervalKb(plannedSyncIntervalKb)
                                .resyncEnabled(resyncEnabled);
                if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                        String password = secretsService.decrypt(request.getPassword());
                        connectBuilder.password(password);
                }
                log.info("CONNECT (streaming): declaring sync interval = {} KB (syncEnabled={})",
                                plannedSyncIntervalKb, syncPointsEnabled && plannedSyncIntervalKb > 0);
                Fpdu connectFpdu = connectBuilder.build(connectionId);

                Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
                int serverConnectionId = aconnect.getIdSrc();

                // Parse negotiated sync points from ACONNECT (PI 7)
                // Format: [interval_high][interval_low][ack_window]
                int negotiatedSyncIntervalKb = 0;
                ParameterValue pi7 = aconnect.getParameter(ParameterIdentifier.PI_07_SYNC_POINTS);
                if (pi7 != null && pi7.getValue() != null && pi7.getValue().length >= 3) {
                        byte[] syncBytes = pi7.getValue();
                        negotiatedSyncIntervalKb = ((syncBytes[0] & 0xFF) << 8) | (syncBytes[1] & 0xFF);
                        int negotiatedSyncWindow = syncBytes[2] & 0xFF;
                        log.info("ACONNECT: Negotiated sync points - interval={}KB, window={}",
                                        negotiatedSyncIntervalKb, negotiatedSyncWindow);
                        // If server returns 0 for interval, sync points are disabled
                        if (negotiatedSyncIntervalKb == 0) {
                                syncPointsEnabled = false;
                                log.info("Server disabled sync points (interval=0)");
                        }
                }

                // Parse PI 25 from ACONNECT - server's max entity size capability
                int serverMaxEntitySize = 0;
                ParameterValue aconnectPi25 = aconnect.getParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE);
                if (aconnectPi25 != null && aconnectPi25.getValue() != null) {
                        serverMaxEntitySize = parseNumericValue(aconnectPi25.getValue());
                        log.info("ACONNECT: Server max entity size (PI 25) = {}", serverMaxEntitySize);
                }

                // CREATE - propose MAX values, server will respond with its limits in
                // ACK_CREATE
                int transferId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF;
                // PI 25 (maxEntitySize) = propose max (65535) or server limit from ACONNECT
                // PI 32 (recordLength) = PI 25 - 6 (FPDU header)
                int proposedMaxEntity = serverMaxEntitySize > 0 ? serverMaxEntitySize : 65535;
                int proposedRecordLength = proposedMaxEntity - 6;
                // PI 42 (maxReservation) = file size in KB (with PI 41 = 0 for KB unit)
                long fileSizeKB = (fileSize + 1023) / 1024; // Round up to KB
                log.info("CREATE (streaming): proposing PI25(entity)={}, PI32(article)={}, syncPointsEnabled={}",
                                proposedMaxEntity, proposedRecordLength, syncPointsEnabled);
                Fpdu createFpdu = new CreateMessageBuilder()
                                .filename(virtualFile)
                                .transferId(transferId)
                                .variableFormat()
                                .recordLength(proposedRecordLength)
                                .maxEntitySize(proposedMaxEntity)
                                .fileSizeKB(fileSizeKB)
                                .build(serverConnectionId);

                Fpdu ackCreateStreaming = session.sendFpduWithAck(createFpdu);

                // Check diagnostic - according to spec, non-zero = failure
                checkAckDiagnostic(ackCreateStreaming, "ACK_CREATE");

                // Use server's negotiated PI 25 from ACK_CREATE to determine actual chunk size
                int actualChunkSizeStreaming;
                ParameterValue pi25Streaming = ackCreateStreaming
                                .getParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE);
                if (pi25Streaming != null && pi25Streaming.getValue() != null) {
                        int negotiatedMaxEntity = parseNumericValue(pi25Streaming.getValue());
                        actualChunkSizeStreaming = negotiatedMaxEntity - 6; // Entity includes 6-byte header
                        log.info("ACK_CREATE (streaming): PI25 negotiated={}, using chunk size={}",
                                        negotiatedMaxEntity, actualChunkSizeStreaming);
                } else {
                        actualChunkSizeStreaming = proposedRecordLength;
                        log.info("ACK_CREATE (streaming): no PI25 in response, using proposed chunk size={}",
                                        actualChunkSizeStreaming);
                }

                // OPEN (ORF) - open file for writing
                Fpdu openFpdu = new Fpdu(FpduType.OPEN)
                                .withIdDst(serverConnectionId);
                Fpdu ackOpenStreaming = session.sendFpduWithAck(openFpdu);
                checkAckDiagnostic(ackOpenStreaming, "ACK_OPEN");

                // Check negotiated compression from ACK_OPEN (PI 21)
                ParameterValue pi21Streaming = ackOpenStreaming.getParameter(ParameterIdentifier.PI_21_COMPRESSION);
                if (pi21Streaming != null && pi21Streaming.getValue() != null && pi21Streaming.getValue().length >= 1) {
                        int compressionAccepted = pi21Streaming.getValue()[0] & 0xFF;
                        log.info("Streaming ACK_OPEN: Compression {}",
                                        compressionAccepted == 0 ? "refused" : "accepted");
                }

                // WRITE (signals start of data transfer, no data payload)
                Fpdu writeFpdu = new Fpdu(FpduType.WRITE)
                                .withIdDst(serverConnectionId);
                Fpdu ackWriteStreaming = session.sendFpduWithAck(writeFpdu);
                checkAckDiagnostic(ackWriteStreaming, "ACK_WRITE");

                // Check restart point from ACK_WRITE (PI 18)
                ParameterValue pi18Streaming = ackWriteStreaming.getParameter(ParameterIdentifier.PI_18_POINT_RELANCE);
                if (pi18Streaming != null && pi18Streaming.getValue() != null) {
                        int restartPoint = parseNumericValue(pi18Streaming.getValue());
                        log.info("Streaming ACK_WRITE: Restart point = {}", restartPoint);
                }

                // DTF - stream data in chunks from InputStream
                long totalSent = 0;
                long bytesSinceLastSync = 0;
                int syncPointNumber = 0;
                // Use server-negotiated sync interval (from ACONNECT PI 7)
                long negotiatedSyncIntervalBytes = negotiatedSyncIntervalKb * 1024L;
                long syncIntervalBytes = negotiatedSyncIntervalBytes > 0
                                ? negotiatedSyncIntervalBytes
                                : calculateSyncPointInterval(request, config,
                                                (int) Math.min(fileSize, Integer.MAX_VALUE));
                log.info("Sync interval: {} bytes (negotiated={}KB, syncEnabled={})",
                                syncIntervalBytes, negotiatedSyncIntervalKb, syncPointsEnabled);

                byte[] buffer = new byte[actualChunkSizeStreaming];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                        // Check for cancellation
                        if (isCancelled(historyId)) {
                                log.info("Streaming transfer {} cancelled at {} bytes", historyId, totalSent);
                                throw new RuntimeException("Transfer cancelled by user");
                        }

                        byte[] chunk = bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);

                        Fpdu dtfFpdu = new Fpdu(FpduType.DTF)
                                        .withIdDst(serverConnectionId);
                        session.sendFpduWithData(dtfFpdu, chunk);
                        totalSent += bytesRead;
                        bytesSinceLastSync += bytesRead;
                        bytesSinceLastProgressUpdate += bytesRead;
                        log.debug("Sent DTF chunk: {} bytes, total sent: {}/{}", bytesRead, totalSent, fileSize);

                        // Update progress in database periodically
                        if (bytesSinceLastProgressUpdate >= progressUpdateInterval) {
                                updateTransferProgress(historyId, totalSent, fileSize, syncPointNumber);
                                bytesSinceLastProgressUpdate = 0;
                        }

                        // Send sync point periodically for restart capability
                        if (syncPointsEnabled && syncIntervalBytes > 0 && bytesSinceLastSync >= syncIntervalBytes) {
                                syncPointNumber++;
                                Fpdu synFpdu = new Fpdu(FpduType.SYN)
                                                .withIdDst(serverConnectionId)
                                                .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncPointNumber));
                                session.sendFpduWithAck(synFpdu);
                                log.info("Sync point {} acknowledged at {} bytes", syncPointNumber, totalSent);
                                bytesSinceLastSync = 0;
                                updateTransferProgress(historyId, totalSent, fileSize, syncPointNumber);
                        }
                }

                log.info("Streaming complete: sent {} bytes in total", totalSent);

                // DTF_END - signal end of data transfer
                Fpdu dtfEndFpdu = new Fpdu(FpduType.DTF_END)
                                .withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
                session.sendFpdu(dtfEndFpdu);

                // TRANS_END
                Fpdu transendFpdu = new Fpdu(FpduType.TRANS_END)
                                .withIdDst(serverConnectionId);
                session.sendFpduWithAck(transendFpdu);

                // CLOSE (CRF)
                Fpdu closeFpdu = new Fpdu(FpduType.CLOSE)
                                .withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
                session.sendFpduWithAck(closeFpdu);

                // DESELECT
                Fpdu deselectFpdu = new Fpdu(FpduType.DESELECT)
                                .withIdDst(serverConnectionId)
                                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
                session.sendFpduWithAck(deselectFpdu);

                // RELEASE
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
                // No progress tracking for small messages
                executeSendTransfer(session, server, request, data, config, null);
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
         * Update transfer progress in database and send WebSocket notification.
         */
        public void updateTransferProgress(String historyId, long bytesTransferred, long fileSize, int lastSyncPoint) {
                if (historyId == null)
                        return;

                // Send WebSocket update immediately (doesn't need DB)
                progressService.sendProgress(historyId, bytesTransferred, fileSize, lastSyncPoint);

                // Also update database for persistence
                historyRepository.findById(historyId).ifPresent(history -> {
                        history.setBytesTransferred(bytesTransferred);
                        if (lastSyncPoint > 0) {
                                history.setLastSyncPoint(lastSyncPoint);
                                history.setBytesAtLastSyncPoint(bytesTransferred);
                        }
                        historyRepository.saveAndFlush(history);
                });
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
        /**
         * Check ACK FPDU diagnostic (PI 2). Throws exception if non-zero.
         * According to PeSIT spec, diagnostic code 0 = success, any other value =
         * failure.
         */
        private void checkAckDiagnostic(Fpdu ack, String fpduName) {
                ParameterValue diag = ack.getParameter(ParameterIdentifier.PI_02_DIAG);
                if (diag != null && diag.getValue() != null && diag.getValue().length >= 1) {
                        byte[] diagBytes = diag.getValue();
                        int diagCode = diagBytes[0] & 0xFF;
                        if (diagCode != 0) {
                                int diagReason = diagBytes.length >= 3
                                                ? ((diagBytes[1] & 0xFF) << 8) | (diagBytes[2] & 0xFF)
                                                : 0;
                                String msg = String.format("%s failed with diagnostic D%d_%d", fpduName, diagCode,
                                                diagReason);
                                log.error(msg);
                                throw new RuntimeException(msg);
                        }
                }
        }

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
