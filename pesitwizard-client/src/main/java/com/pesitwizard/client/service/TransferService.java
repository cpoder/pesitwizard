package com.pesitwizard.client.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pesitwizard.client.dto.MessageRequest;
import com.pesitwizard.client.dto.TransferRequest;
import com.pesitwizard.client.dto.TransferResponse;
import com.pesitwizard.client.dto.TransferStats;
import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.client.entity.TransferConfig;
import com.pesitwizard.client.entity.TransferHistory;
import com.pesitwizard.client.entity.TransferHistory.TransferDirection;
import com.pesitwizard.client.entity.TransferHistory.TransferStatus;
import com.pesitwizard.client.pesit.PesitMessageService;
import com.pesitwizard.client.pesit.PesitReceiveService;
import com.pesitwizard.client.pesit.PesitSendService;
import com.pesitwizard.client.pesit.StorageConnectorFactory;
import com.pesitwizard.client.repository.TransferConfigRepository;
import com.pesitwizard.client.repository.TransferHistoryRepository;
import com.pesitwizard.connector.StorageConnector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service chapeau pour les transferts PeSIT.
 * Délègue l'exécution aux services spécialisés (send, receive, message).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

        private final Set<String> cancelledTransfers = ConcurrentHashMap.newKeySet();

        // Services spécialisés
        private final PesitSendService sendService;
        private final PesitReceiveService receiveService;
        private final PesitMessageService messageService;

        // Dépendances communes
        private final PesitServerService serverService;
        private final TransferConfigRepository configRepository;
        private final TransferHistoryRepository historyRepository;
        private final PathPlaceholderService placeholderService;
        private final StorageConnectorFactory connectorFactory;

        // ========== API Publique - Transferts ==========

        public TransferResponse sendFile(TransferRequest request) {
                String correlationId = resolveCorrelationId(request.getCorrelationId());
                PesitServer server = resolveServer(request.getServer());
                TransferConfig config = resolveConfig(request.getTransferConfig());

                long fileSize = getFileSize(request);
                TransferHistory history = createHistory(server, config, TransferDirection.SEND,
                                request.getFilename(), request.getRemoteFilename(), request.getPartnerId(),
                                correlationId);
                history.setFileSize(fileSize);
                history.setStatus(TransferStatus.IN_PROGRESS);
                history = historyRepository.save(history);

                // Déléguer au service d'envoi
                sendService.sendFileAsync(request, history.getId(), server, config, fileSize, correlationId,
                                cancelledTransfers);

                return mapToResponse(history);
        }

        public TransferResponse receiveFile(TransferRequest request) {
                String correlationId = resolveCorrelationId(request.getCorrelationId());
                PesitServer server = resolveServer(request.getServer());
                TransferConfig config = resolveConfig(request.getTransferConfig());

                String resolvedFilename = placeholderService.resolvePath(
                                request.getFilename(),
                                PathPlaceholderService.PlaceholderContext.builder()
                                                .partnerId(request.getPartnerId())
                                                .virtualFile(request.getRemoteFilename())
                                                .serverId(server.getId())
                                                .serverName(server.getName())
                                                .direction("RECEIVE")
                                                .build());

                TransferHistory history = createHistory(server, config, TransferDirection.RECEIVE,
                                resolvedFilename, request.getRemoteFilename(), request.getPartnerId(), correlationId);
                history.setStatus(TransferStatus.IN_PROGRESS);
                history.setBytesTransferred(0L);
                history = historyRepository.save(history);

                // Déléguer au service de réception
                receiveService.receiveFileAsync(request, history.getId(), server, config, resolvedFilename,
                                correlationId, cancelledTransfers);

                return mapToResponse(history);
        }

        public TransferResponse sendMessage(MessageRequest request) {
                try {
                        PesitServer server = resolveServer(request.getServer());
                        messageService.sendMessage(request, server);
                        return TransferResponse.builder()
                                        .status(TransferStatus.COMPLETED)
                                        .serverName(server.getName())
                                        .build();
                } catch (Exception e) {
                        log.error("Message send failed: {}", e.getMessage(), e);
                        return TransferResponse.builder()
                                        .status(TransferStatus.FAILED)
                                        .errorMessage(e.getMessage())
                                        .build();
                }
        }

        // ========== API Publique - Historique ==========

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

        // ========== API Publique - Gestion ==========

        @Transactional
        public Optional<TransferResponse> cancelTransfer(String transferId) {
                return historyRepository.findById(transferId)
                                .filter(h -> h.getStatus() == TransferStatus.IN_PROGRESS)
                                .map(history -> {
                                        cancelledTransfers.add(transferId);
                                        history.setStatus(TransferStatus.CANCELLED);
                                        history.setErrorMessage("Cancelled by user");
                                        history.setCompletedAt(Instant.now());
                                        // Cancellation event is published by transfer thread via TransferContext
                                        return mapToResponse(historyRepository.save(history));
                                });
        }

        @Transactional
        public Optional<TransferResponse> replayTransfer(String transferId) {
                return historyRepository.findById(transferId).map(original -> {
                        TransferRequest request = TransferRequest.builder()
                                        .server(original.getServerId())
                                        .partnerId(original.getPartnerId())
                                        .filename(original.getLocalFilename())
                                        .remoteFilename(original.getRemoteFilename())
                                        .transferConfig(original.getTransferConfigId())
                                        .correlationId(UUID.randomUUID().toString())
                                        .build();
                        return original.getDirection() == TransferDirection.SEND
                                        ? sendFile(request)
                                        : receiveFile(request);
                });
        }

        @Transactional
        public Optional<TransferResponse> resumeTransfer(String transferId) {
                return historyRepository.findById(transferId)
                                .filter(h -> h.getStatus() == TransferStatus.FAILED || h.getStatus() == TransferStatus.CANCELLED)
                                .filter(h -> h.getLastSyncPoint() != null && h.getLastSyncPoint() > 0)
                                .map(original -> {
                                        TransferRequest request = TransferRequest.builder()
                                                        .server(original.getServerId())
                                                        .partnerId(original.getPartnerId())
                                                        .filename(original.getLocalFilename())
                                                        .remoteFilename(original.getRemoteFilename())
                                                        .transferConfig(original.getTransferConfigId())
                                                        .resumeFromTransferId(transferId)
                                                        .correlationId(original.getCorrelationId())
                                                        .build();
                                        return original.getDirection() == TransferDirection.SEND
                                                        ? sendFile(request)
                                                        : receiveFile(request);
                                });
        }


        @Transactional(readOnly = true)
        public Page<TransferHistory> getResumableTransfers(Pageable pageable) {
                return historyRepository.findResumableTransfers(pageable);
        }

        public boolean isCancelled(String transferId) {
                return cancelledTransfers.contains(transferId);
        }

        public void clearCancellation(String transferId) {
                cancelledTransfers.remove(transferId);
        }

        // ========== Helpers privés ==========

        private String resolveCorrelationId(String correlationId) {
                return correlationId != null ? correlationId : UUID.randomUUID().toString();
        }

        private PesitServer resolveServer(String serverNameOrId) {
                return serverService.findServer(serverNameOrId)
                                .orElseGet(() -> serverService.getDefaultServer()
                                                .orElseThrow(() -> new IllegalArgumentException(
                                                                "Server not found: " + serverNameOrId)));
        }

        private TransferConfig resolveConfig(String configNameOrId) {
                if (configNameOrId == null) {
                        return configRepository.findByDefaultConfigTrue().orElse(createDefaultConfig());
                }
                return configRepository.findByName(configNameOrId)
                                .or(() -> configRepository.findById(configNameOrId))
                                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + configNameOrId));
        }

        private TransferConfig createDefaultConfig() {
                return TransferConfig.builder()
                                .name("default")
                                .chunkSize(32768)
                                .compressionEnabled(false)
                                .crcEnabled(true)
                                .syncPointsEnabled(true)
                                .resyncEnabled(true)
                                .build();
        }

        private long getFileSize(TransferRequest request) {
                try {
                        if (request.getSourceConnectionId() != null) {
                                try (StorageConnector c = connectorFactory
                                                .createFromConnectionId(request.getSourceConnectionId())) {
                                        return c.getMetadata(request.getFilename()).getSize();
                                }
                        }
                        return Files.size(Path.of(request.getFilename()));
                } catch (Exception e) {
                        throw new RuntimeException("Cannot determine file size: " + e.getMessage(), e);
                }
        }

        private TransferHistory createHistory(PesitServer server, TransferConfig config,
                        TransferDirection direction, String localPath, String remotePath,
                        String partnerId, String correlationId) {
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

        private TransferResponse mapToResponse(TransferHistory h) {
                return TransferResponse.builder()
                                .transferId(h.getId())
                                .correlationId(h.getCorrelationId())
                                .direction(h.getDirection())
                                .status(h.getStatus())
                                .serverName(h.getServerName())
                                .localFilename(h.getLocalFilename())
                                .remoteFilename(h.getRemoteFilename())
                                .fileSize(h.getFileSize())
                                .bytesTransferred(h.getBytesTransferred())
                                .checksum(h.getChecksum())
                                .errorMessage(h.getErrorMessage())
                                .diagnosticCode(h.getDiagnosticCode())
                                .startedAt(h.getStartedAt())
                                .completedAt(h.getCompletedAt())
                                .build();
        }
}
