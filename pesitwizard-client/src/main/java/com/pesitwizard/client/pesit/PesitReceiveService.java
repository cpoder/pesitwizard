package com.pesitwizard.client.pesit;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import com.pesitwizard.client.connector.ConnectorRegistry;
import com.pesitwizard.client.dto.TransferRequest;
import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.client.entity.TransferConfig;
import com.pesitwizard.client.entity.TransferHistory.TransferStatus;
import com.pesitwizard.client.event.TransferEventBus;
import com.pesitwizard.client.repository.TransferHistoryRepository;
import com.pesitwizard.client.service.PartnerService;
import com.pesitwizard.client.service.RestartRequiredException;
import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.StorageConnector;
import com.pesitwizard.exception.PesitException;
import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduIO;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterParser;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.security.SecretsService;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TransportChannel;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Service dédié à la réception de fichiers via PeSIT. */
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
    private final PartnerService partnerService;
    private final TransferHistoryRepository historyRepository;
    private final TransferEventBus eventBus;
    private final ObservationRegistry observationRegistry;

    /** Exécute la réception de fichier de manière asynchrone avec télémétrie. */
    @Async("transferExecutor")
    public void receiveFileAsync(
            TransferRequest request,
            String historyId,
            PesitServer server,
            TransferConfig config,
            String resolvedFilename,
            String correlationId,
            Set<String> cancelledTransfers) {
        Observation.createNotStarted("pesit.receive", observationRegistry)
                .lowCardinalityKeyValue("pesit.direction", "RECEIVE")
                .highCardinalityKeyValue("pesit.server", request.getServer())
                .highCardinalityKeyValue("correlation.id", correlationId)
                .observe(
                        () ->
                                receiveFile(
                                        request,
                                        historyId,
                                        server,
                                        config,
                                        resolvedFilename,
                                        cancelledTransfers));
    }

    /** Exécute la réception de fichier avec gestion des reprises. */
    public void receiveFile(
            TransferRequest request,
            String historyId,
            PesitServer server,
            TransferConfig config,
            String destPath,
            Set<String> cancelledTransfers) {
        String destConnId = request.getDestinationConnectionId();
        int restartPoint = 0;
        long restartBytePos = 0;
        TransferContext ctx = new TransferContext(historyId, eventBus);

        for (int attempt = 0; attempt <= MAX_RESTART_ATTEMPTS; attempt++) {
            StorageConnector connector = null;
            try {
                if (attempt > 0) {
                    log.info(
                            "Restart attempt {} - resuming from sync point {} at byte {}",
                            attempt,
                            restartPoint,
                            restartBytePos);
                    Thread.sleep(1000);
                }

                connector =
                        destConnId != null
                                ? connectorFactory.createFromConnectionId(destConnId)
                                : connectorRegistry.createConnector("local", Map.of());

                try (TransportChannel channel = channelFactory.createChannel(server);
                        PesitSession session = new PesitSession(channel, false)) {
                    long bytesReceived =
                            executeTransfer(
                                    session,
                                    server,
                                    request,
                                    connector,
                                    destPath,
                                    config,
                                    restartPoint,
                                    restartBytePos,
                                    ctx,
                                    cancelledTransfers);

                    updateHistorySuccess(historyId, bytesReceived);
                    ctx.completed();
                    return;
                }

            } catch (RestartRequiredException e) {
                log.info(
                        "Restart required: sync point {} at byte {}",
                        e.getSyncPoint(),
                        e.getBytePosition());
                if (attempt < MAX_RESTART_ATTEMPTS) {
                    restartPoint = e.getSyncPoint();
                    restartBytePos = e.getBytePosition();
                } else {
                    updateHistoryFailed(
                            historyId,
                            "Max restart attempts exceeded: " + e.getMessage(),
                            null,
                            ctx);
                    ctx.error("Max restart attempts exceeded: " + e.getMessage(), null);
                    return;
                }
            } catch (PesitException e) {
                log.error(
                        "Receive {} FAILED: {} ({})",
                        historyId,
                        e.getMessage(),
                        e.getDiagnosticCodeHex());
                updateHistoryFailed(historyId, e.getMessage(), e.getDiagnosticCodeHex(), ctx);
                ctx.error(e.getMessage(), e.getDiagnosticCodeHex());
                return;
            } catch (IOException | InterruptedException | ConnectorException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("Receive {} FAILED: {}", historyId, e.getMessage(), e);
                updateHistoryFailed(historyId, e.getMessage(), null, ctx);
                ctx.error(e.getMessage(), null);
                return;
            } catch (RuntimeException e) {
                log.error("Receive {} FAILED: {}", historyId, e.getMessage(), e);
                updateHistoryFailed(historyId, e.getMessage(), null, ctx);
                ctx.error(e.getMessage(), null);
                return;
            } finally {
                closeQuietly(connector);
                cancelledTransfers.remove(historyId);
            }
        }
    }

    private long executeTransfer(
            PesitSession session,
            PesitServer server,
            TransferRequest request,
            StorageConnector connector,
            String destPath,
            TransferConfig config,
            int restartPoint,
            long restartBytePos,
            TransferContext ctx,
            Set<String> cancelledTransfers)
            throws IOException, InterruptedException, ConnectorException, RestartRequiredException {

        int connectionId = 1;
        int chunkSize = config.getChunkSize();
        String virtualFile =
                request.getVirtualFile() != null
                        ? request.getVirtualFile()
                        : request.getRemoteFilename();
        boolean syncEnabled = resolveSyncEnabled(request, config);
        boolean resyncEnabled = resolveResyncEnabled(request, config);

        // CONNECT
        ConnectMessageBuilder connectBuilder =
                new ConnectMessageBuilder()
                        .demandeur(request.getPartnerId())
                        .serveur(server.getServerId())
                        .readAccess()
                        .syncPointsEnabled(syncEnabled)
                        .resyncEnabled(resyncEnabled);

        String password = null;
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            password = secretsService.decrypt(request.getPassword());
        } else {
            password = partnerService.resolvePassword(request.getPartnerId());
        }
        if (password != null && !password.isEmpty()) {
            connectBuilder.password(password);
        }

        ctx.connectSent();
        Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(connectionId));
        ctx.connectAck();
        int serverConnId = aconnect.getIdSrc();

        // SELECT
        int transferId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF;
        ParameterValue pgi9 =
                new ParameterValue(
                        ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                        new ParameterValue(PI_11_TYPE_FICHIER, 0),
                        new ParameterValue(PI_12_NOM_FICHIER, virtualFile));

        Fpdu selectFpdu =
                new Fpdu(FpduType.SELECT)
                        .withIdDst(serverConnId)
                        .withParameter(pgi9)
                        .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, transferId))
                        .withParameter(new ParameterValue(PI_14_ATTRIBUTS_DEMANDES, 0))
                        .withParameter(new ParameterValue(PI_17_PRIORITE, 0))
                        .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, chunkSize));

        ctx.selectSent();
        Fpdu ackSelect = session.sendFpduWithAck(selectFpdu);
        ctx.selectAck();
        long expectedSize = ParameterParser.parseFileSizeFromAckSelect(ackSelect);

        // OPEN
        ctx.openSent();
        session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));
        ctx.openAck();

        // READ avec point de reprise
        if (restartPoint > 0) {
            log.info("Sending READ with restart point {} (byte {})", restartPoint, restartBytePos);
        }
        ctx.readSent();
        session.sendFpduWithAck(
                new Fpdu(FpduType.READ)
                        .withIdDst(serverConnId)
                        .withParameter(new ParameterValue(PI_18_POINT_RELANCE, restartPoint)));
        ctx.readAck();

        // Recevoir les données
        return receiveData(
                session,
                serverConnId,
                connectionId,
                connector,
                destPath,
                expectedSize,
                ctx,
                restartPoint,
                restartBytePos,
                cancelledTransfers);
    }

    private long receiveData(
            PesitSession session,
            int serverConnId,
            int connectionId,
            StorageConnector connector,
            String destPath,
            long expectedSize,
            TransferContext ctx,
            int restartPoint,
            long restartBytePos,
            Set<String> cancelledTransfers)
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

                if (FpduIO.isDtfType(type)) {
                    byte[] data = fpdu.getData();
                    if (data != null && data.length > 0) {
                        long bytesWritten = 0;

                        if (FpduIO.isMultiArticleDtf(fpdu)) {
                            // Multi-article DTF: extract articles without 2-byte length prefixes
                            List<byte[]> articles = FpduIO.extractArticles(data);
                            for (byte[] article : articles) {
                                if (raf != null) raf.write(article);
                                else os.write(article);
                                bytesWritten += article.length;
                            }
                        } else {
                            // Single article or raw data: write directly
                            if (raf != null) raf.write(data);
                            else os.write(data);
                            bytesWritten = data.length;
                        }
                        totalBytes += bytesWritten;

                        // Progress update via TransferContext
                        ctx.addBytes(bytesWritten);
                        long now = System.currentTimeMillis();
                        if (now - lastProgressUpdate >= 100) {
                            lastProgressUpdate = now;
                        }
                    }
                } else if (type == FpduType.SYN) {
                    int syncNum = ParameterParser.parsePI20SyncNumber(fpdu);
                    lastSync = syncNum > 0 ? syncNum : lastSync + 1;
                    lastSyncPos = totalBytes;
                    ctx.syncReceived();
                    ctx.syncPoint(lastSync, lastSyncPos);
                    session.sendFpdu(
                            new Fpdu(FpduType.ACK_SYN)
                                    .withIdDst(serverConnId)
                                    .withParameter(new ParameterValue(PI_20_NUM_SYNC, lastSync)));
                    ctx.syncAckSentReceive();
                    log.debug("ACK_SYN {} at {} bytes", lastSync, totalBytes);
                } else if (type == FpduType.RESYN) {
                    // Server requests resynchronization during READ
                    int resyncPoint = ParameterParser.parsePI18RestartPoint(fpdu);
                    log.info(
                            "RESYN from server: rollback to sync point {} (byte pos {})",
                            resyncPoint,
                            lastSyncPos);
                    // ACK the resync - we'll need to truncate and continue from that point
                    session.sendFpdu(
                            new Fpdu(FpduType.ACK_RESYN)
                                    .withIdDst(serverConnId)
                                    .withParameter(
                                            new ParameterValue(PI_18_POINT_RELANCE, resyncPoint)));
                    // Truncate local file to last sync position and update counters
                    if (raf != null) {
                        raf.seek(lastSyncPos);
                        raf.setLength(lastSyncPos);
                    }
                    totalBytes = lastSyncPos;
                    ctx.addBytes(-(ctx.getBytesTransferred() - lastSyncPos));
                    log.info("RESYN: truncated to {} bytes, continuing", lastSyncPos);
                } else if (type == FpduType.DTF_END
                        || type == FpduType.TRANS_END
                        || type == FpduType.CLOSE) {
                    ctx.dataEndReceived();
                    receiving = false;
                } else if (type == FpduType.IDT) {
                    ParameterValue pi19 = fpdu.getParameter(PI_19_CODE_FIN_TRANSFERT);
                    restartCode =
                            pi19 != null && pi19.getValue() != null ? pi19.getValue()[0] & 0xFF : 0;
                    session.sendFpdu(
                            new Fpdu(FpduType.ACK_IDT)
                                    .withIdDst(serverConnId)
                                    .withParameter(
                                            new ParameterValue(PI_02_DIAG, new byte[] {0, 0, 0})));
                    interrupted = true;
                    receiving = false;
                }
            }
        } finally {
            if (raf != null) raf.close();
            if (os != null) os.close();
        }

        if (!interrupted) {
            sendCleanupFpdus(session, serverConnId, connectionId, ctx);
        } else if (restartCode == 4) {
            throw new RestartRequiredException(lastSync, lastSyncPos, totalBytes);
        }

        log.info("Receive complete: {} bytes", totalBytes);
        return totalBytes;
    }

    private void sendCleanupFpdus(
            PesitSession session, int serverConnId, int connectionId, TransferContext ctx)
            throws IOException, InterruptedException {
        // Send TRANS.END to signal successful data reception (required before CLOSE)
        ctx.transEndSent();
        session.sendFpduWithAck(
                new Fpdu(FpduType.TRANS_END)
                        .withIdDst(serverConnId)
                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] {0, 0, 0})));
        ctx.transEndAck();
        log.debug("Sent TRANS.END");

        ctx.closeSent();
        session.sendFpduWithAck(
                new Fpdu(FpduType.CLOSE)
                        .withIdDst(serverConnId)
                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] {0, 0, 0})));
        ctx.closeAck();
        ctx.deselectSent();
        session.sendFpduWithAck(
                new Fpdu(FpduType.DESELECT)
                        .withIdDst(serverConnId)
                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] {0, 0, 0})));
        ctx.deselectAck();
        ctx.releaseSent();
        session.sendFpduWithAck(
                new Fpdu(FpduType.RELEASE)
                        .withIdDst(serverConnId)
                        .withIdSrc(connectionId)
                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] {0, 0, 0})));
        ctx.releaseAck();
    }

    // === Helpers ===

    private boolean resolveSyncEnabled(TransferRequest req, TransferConfig cfg) {
        return req.getSyncPointsEnabled() != null
                ? req.getSyncPointsEnabled()
                : cfg.isSyncPointsEnabled();
    }

    private boolean resolveResyncEnabled(TransferRequest req, TransferConfig cfg) {
        return req.getResyncEnabled() != null ? req.getResyncEnabled() : cfg.isResyncEnabled();
    }

    private void updateHistorySuccess(String historyId, long bytes) {
        historyRepository
                .findById(historyId)
                .ifPresent(
                        h -> {
                            h.setStatus(TransferStatus.COMPLETED);
                            h.setFileSize(bytes);
                            h.setBytesTransferred(bytes);
                            h.setCompletedAt(Instant.now());
                            historyRepository.save(h);
                        });
    }

    private void updateHistoryFailed(
            String historyId, String error, String diagCode, TransferContext ctx) {
        historyRepository
                .findById(historyId)
                .ifPresent(
                        h -> {
                            h.setStatus(TransferStatus.FAILED);
                            h.setErrorMessage(error);
                            h.setDiagnosticCode(diagCode);
                            h.setCompletedAt(Instant.now());
                            h.setBytesTransferred(ctx.getBytesTransferred());
                            if (ctx.getLastSyncPoint() > 0) {
                                h.setLastSyncPoint(ctx.getLastSyncPoint());
                                h.setBytesAtLastSyncPoint(ctx.getBytesAtLastSyncPoint());
                            }
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
