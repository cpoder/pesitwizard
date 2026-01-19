package com.pesitwizard.client.pesit;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.pesitwizard.client.connector.ConnectorRegistry;
import com.pesitwizard.client.dto.TransferRequest;
import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.client.entity.TransferConfig;
import com.pesitwizard.client.entity.TransferHistory.TransferStatus;
import com.pesitwizard.client.event.TransferEventBus;
import com.pesitwizard.client.repository.TransferHistoryRepository;
import com.pesitwizard.client.service.RestartRequiredException;
import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.StorageConnector;
import com.pesitwizard.exception.PesitException;
import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.security.SecretsService;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TransportChannel;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service dédié à la réception de fichiers via PeSIT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PesitReceiveService {

    private static final AtomicInteger TRANSFER_ID_COUNTER = new AtomicInteger(1);
    private static final int MAX_RESTART_ATTEMPTS = 3;

    private final PesitChannelFactory channelFactory;
    private final StorageConnectorFactory connectorFactory;
    private final ConnectorRegistry connectorRegistry;
    private final SecretsService secretsService;
    private final TransferHistoryRepository historyRepository;
    private final TransferEventBus eventBus;
    private final ObservationRegistry observationRegistry;

    /**
     * Exécute la réception de fichier de manière asynchrone avec télémétrie.
     */
    @Async("transferExecutor")
    public void receiveFileAsync(TransferRequest request, String historyId, PesitServer server,
            TransferConfig config, String resolvedFilename, String correlationId, Set<String> cancelledTransfers) {
        Observation.createNotStarted("pesit.receive", observationRegistry)
                .lowCardinalityKeyValue("pesit.direction", "RECEIVE")
                .highCardinalityKeyValue("pesit.server", request.getServer())
                .highCardinalityKeyValue("correlation.id", correlationId)
                .observe(() -> receiveFile(request, historyId, server, config, resolvedFilename, cancelledTransfers));
    }

    /**
     * Exécute la réception de fichier avec gestion des reprises.
     */
    public void receiveFile(TransferRequest request, String historyId, PesitServer server,
            TransferConfig config, String destPath, Set<String> cancelledTransfers) {
        String destConnId = request.getDestinationConnectionId();
        int restartPoint = 0;
        long restartBytePos = 0;
        TransferContext ctx = new TransferContext(historyId, eventBus);

        for (int attempt = 0; attempt <= MAX_RESTART_ATTEMPTS; attempt++) {
            StorageConnector connector = null;
            try {
                if (attempt > 0) {
                    log.info("Restart attempt {} - resuming from sync point {} at byte {}", attempt, restartPoint,
                            restartBytePos);
                    Thread.sleep(1000);
                }

                connector = destConnId != null
                        ? connectorFactory.createFromConnectionId(destConnId)
                        : connectorRegistry.createConnector("local", Map.of());

                TransportChannel channel = channelFactory.createChannel(server);
                try (PesitSession session = new PesitSession(channel, false)) {
                    long bytesReceived = executeTransfer(session, server, request, connector, destPath,
                            config, restartPoint, restartBytePos, ctx, cancelledTransfers);

                    updateHistorySuccess(historyId, bytesReceived);
                    ctx.completed();
                    return;
                }

            } catch (RestartRequiredException e) {
                log.info("Restart required: sync point {} at byte {}", e.getSyncPoint(), e.getBytePosition());
                if (attempt < MAX_RESTART_ATTEMPTS) {
                    restartPoint = e.getSyncPoint();
                    restartBytePos = e.getBytePosition();
                } else {
                    updateHistoryFailed(historyId, "Max restart attempts exceeded: " + e.getMessage(), null);
                    // Error event already published by TransferContext.error()
                    return;
                }
            } catch (PesitException e) {
                log.error("Receive {} FAILED: {} ({})", historyId, e.getMessage(), e.getDiagnosticCodeHex());
                updateHistoryFailed(historyId, e.getMessage(), e.getDiagnosticCodeHex());
                // Error event already published by TransferContext.error()
                return;
            } catch (Exception e) {
                log.error("Receive {} FAILED: {}", historyId, e.getMessage(), e);
                updateHistoryFailed(historyId, e.getMessage(), null);
                // Error event already published by TransferContext.error()
                return;
            } finally {
                closeQuietly(connector);
                cancelledTransfers.remove(historyId);
            }
        }
    }

    private long executeTransfer(PesitSession session, PesitServer server, TransferRequest request,
            StorageConnector connector, String destPath, TransferConfig config,
            int restartPoint, long restartBytePos, TransferContext ctx, Set<String> cancelledTransfers)
            throws IOException, InterruptedException, ConnectorException, RestartRequiredException {

        int connectionId = 1;
        int chunkSize = config.getChunkSize();
        String virtualFile = request.getVirtualFile() != null ? request.getVirtualFile() : request.getRemoteFilename();
        boolean syncEnabled = resolveSyncEnabled(request, config);
        boolean resyncEnabled = resolveResyncEnabled(request, config);

        // CONNECT
        ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                .demandeur(request.getPartnerId())
                .serveur(server.getServerId())
                .readAccess()
                .syncPointsEnabled(syncEnabled)
                .resyncEnabled(resyncEnabled);

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            connectBuilder.password(secretsService.decrypt(request.getPassword()));
        }

        ctx.connectSent();
        Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(connectionId));
        ctx.connectAck();
        int serverConnId = aconnect.getIdSrc();

        // SELECT
        int transferId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF;
        ParameterValue pgi9 = new ParameterValue(ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                new ParameterValue(PI_11_TYPE_FICHIER, 0),
                new ParameterValue(PI_12_NOM_FICHIER, virtualFile));

        Fpdu selectFpdu = new Fpdu(FpduType.SELECT)
                .withIdDst(serverConnId)
                .withParameter(pgi9)
                .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, transferId))
                .withParameter(new ParameterValue(PI_14_ATTRIBUTS_DEMANDES, 0))
                .withParameter(new ParameterValue(PI_17_PRIORITE, 0))
                .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, chunkSize));

        Fpdu ackSelect = session.sendFpduWithAck(selectFpdu);
        long expectedSize = parseFileSize(ackSelect);

        // OPEN
        session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));

        // READ avec point de reprise
        if (restartPoint > 0) {
            log.info("Sending READ with restart point {} (byte {})", restartPoint, restartBytePos);
        }
        session.sendFpduWithAck(new Fpdu(FpduType.READ).withIdDst(serverConnId)
                .withParameter(new ParameterValue(PI_18_POINT_RELANCE, restartPoint)));

        // Recevoir les données
        return receiveData(session, serverConnId, connectionId, connector, destPath, expectedSize,
                ctx, restartPoint, restartBytePos, cancelledTransfers);
    }

    private long receiveData(PesitSession session, int serverConnId, int connectionId,
            StorageConnector connector, String destPath, long expectedSize, TransferContext ctx,
            int restartPoint, long restartBytePos, Set<String> cancelledTransfers)
            throws IOException, InterruptedException, ConnectorException, RestartRequiredException {

        long totalBytes = restartBytePos;
        int lastSync = restartPoint;
        long lastSyncPos = restartBytePos;
        boolean interrupted = false;
        int restartCode = 0;

        OutputStream os = null;
        RandomAccessFile raf = null;

        try {
            if (restartBytePos > 0) {
                raf = new RandomAccessFile(destPath, "rw");
                raf.seek(restartBytePos);
                raf.setLength(restartBytePos);
            } else {
                os = connector.write(destPath, false);
            }

            FpduReader reader = new FpduReader(session);
            boolean receiving = true;
            long lastProgressUpdate = System.currentTimeMillis();

            while (receiving) {
                if (cancelledTransfers.contains(ctx.getTransferId())) {
                    ctx.cancelled();
                    throw new RuntimeException("Transfer cancelled by user");
                }

                Fpdu fpdu = reader.read();
                FpduType type = fpdu.getFpduType();

                if (isDtfType(type)) {
                    byte[] data = fpdu.getData();
                    if (data != null && data.length > 0) {
                        if (raf != null)
                            raf.write(data);
                        else
                            os.write(data);
                        totalBytes += data.length;

                        // Progress update via TransferContext
                        ctx.addBytes(data.length);
                        long now = System.currentTimeMillis();
                        if (now - lastProgressUpdate >= 100) {
                            lastProgressUpdate = now;
                        }
                    }
                } else if (type == FpduType.SYN) {
                    ParameterValue pi20 = fpdu.getParameter(PI_20_NUM_SYNC);
                    lastSync = pi20 != null && pi20.getValue() != null ? parseNumeric(pi20.getValue()) : lastSync + 1;
                    lastSyncPos = totalBytes;
                    session.sendFpdu(new Fpdu(FpduType.ACK_SYN).withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_20_NUM_SYNC, lastSync)));
                    log.debug("ACK_SYN {} at {} bytes", lastSync, totalBytes);
                } else if (type == FpduType.DTF_END || type == FpduType.TRANS_END || type == FpduType.CLOSE) {
                    receiving = false;
                } else if (type == FpduType.IDT) {
                    ParameterValue pi19 = fpdu.getParameter(PI_19_CODE_FIN_TRANSFERT);
                    restartCode = pi19 != null && pi19.getValue() != null ? pi19.getValue()[0] & 0xFF : 0;
                    session.sendFpdu(new Fpdu(FpduType.ACK_IDT).withIdDst(serverConnId)
                            .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
                    interrupted = true;
                    receiving = false;
                }
            }
        } finally {
            if (raf != null)
                raf.close();
            if (os != null)
                os.close();
        }

        if (!interrupted) {
            sendCleanupFpdus(session, serverConnId, connectionId);
        } else if (restartCode == 4) {
            throw new RestartRequiredException(lastSync, lastSyncPos, totalBytes);
        }

        log.info("Receive complete: {} bytes", totalBytes);
        return totalBytes;
    }

    private void sendCleanupFpdus(PesitSession session, int serverConnId, int connectionId)
            throws IOException, InterruptedException {
        session.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(serverConnId)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
        session.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(serverConnId)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
        session.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(serverConnId).withIdSrc(connectionId)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
    }

    // === Helpers ===

    private boolean isDtfType(FpduType type) {
        return type == FpduType.DTF || type == FpduType.DTFDA || type == FpduType.DTFMA || type == FpduType.DTFFA;
    }

    private boolean resolveSyncEnabled(TransferRequest req, TransferConfig cfg) {
        return req.getSyncPointsEnabled() != null ? req.getSyncPointsEnabled() : cfg.isSyncPointsEnabled();
    }

    private boolean resolveResyncEnabled(TransferRequest req, TransferConfig cfg) {
        return req.getResyncEnabled() != null ? req.getResyncEnabled() : cfg.isResyncEnabled();
    }

    private long parseFileSize(Fpdu ackSelect) {
        ParameterValue pgi40 = ackSelect.getParameter(ParameterGroupIdentifier.PGI_40_ATTR_PHYSIQUES);
        if (pgi40 != null && pgi40.getValues() != null) {
            for (ParameterValue pv : pgi40.getValues()) {
                if (pv.getParameter() == PI_42_MAX_RESERVATION) {
                    return parseNumeric(pv.getValue()) * 1024L;
                }
            }
        }
        return 0;
    }

    private int parseNumeric(byte[] b) {
        if (b == null)
            return 0;
        int v = 0;
        for (byte x : b)
            v = (v << 8) | (x & 0xFF);
        return v;
    }

    private void updateHistorySuccess(String historyId, long bytes) {
        historyRepository.findById(historyId).ifPresent(h -> {
            h.setStatus(TransferStatus.COMPLETED);
            h.setFileSize(bytes);
            h.setBytesTransferred(bytes);
            h.setCompletedAt(Instant.now());
            historyRepository.save(h);
        });
    }

    private void updateHistoryFailed(String historyId, String error, String diagCode) {
        historyRepository.findById(historyId).ifPresent(h -> {
            h.setStatus(TransferStatus.FAILED);
            h.setErrorMessage(error);
            h.setDiagnosticCode(diagCode);
            h.setCompletedAt(Instant.now());
            historyRepository.save(h);
        });
    }

    private void closeQuietly(AutoCloseable c) {
        if (c != null)
            try {
                c.close();
            } catch (Exception ignored) {
            }
    }
}
