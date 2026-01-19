package com.pesitwizard.client.pesit;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.pesitwizard.client.dto.TransferRequest;
import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.client.entity.TransferConfig;
import com.pesitwizard.client.entity.TransferHistory.TransferStatus;
import com.pesitwizard.client.event.TransferEventBus;
import com.pesitwizard.client.repository.TransferHistoryRepository;
import com.pesitwizard.connector.StorageConnector;
import com.pesitwizard.exception.PesitException;
import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.security.SecretsService;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TransportChannel;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PesitSendService {

    private static final AtomicInteger TRANSFER_ID_COUNTER = new AtomicInteger(1);

    private final PesitChannelFactory channelFactory;
    private final StorageConnectorFactory connectorFactory;
    private final SecretsService secretsService;
    private final TransferHistoryRepository historyRepository;
    private final TransferEventBus eventBus;
    private final ObservationRegistry observationRegistry;

    @Async("transferExecutor")
    public void sendFileAsync(TransferRequest request, String historyId, PesitServer server,
            TransferConfig config, long fileSize, String correlationId, Set<String> cancelledTransfers) {
        Observation.createNotStarted("pesit.send", observationRegistry)
                .lowCardinalityKeyValue("pesit.direction", "SEND")
                .highCardinalityKeyValue("pesit.server", request.getServer())
                .highCardinalityKeyValue("correlation.id", correlationId)
                .observe(() -> sendFile(request, historyId, server, config, fileSize, cancelledTransfers));
    }

    public void sendFile(TransferRequest request, String historyId, PesitServer server,
            TransferConfig config, long fileSize, Set<String> cancelledTransfers) {
        StorageConnector connector = null;
        InputStream inputStream = null;
        TransferContext ctx = new TransferContext(historyId, fileSize, eventBus);

        try {
            if (request.getSourceConnectionId() != null) {
                connector = connectorFactory.createFromConnectionId(request.getSourceConnectionId());
                inputStream = connector.read(request.getFilename(), 0);
            } else {
                inputStream = new BufferedInputStream(Files.newInputStream(Path.of(request.getFilename())), 64 * 1024);
            }

            TransportChannel channel = channelFactory.createChannel(server, fileSize);
            try (PesitSession session = new PesitSession(channel, false)) {
                executeTransfer(session, server, request, inputStream, config, ctx, cancelledTransfers);
            }
            updateHistorySuccess(historyId, ctx.getBytesTransferred());
            ctx.completed();
        } catch (PesitException e) {
            log.error("Transfer {} FAILED: {} ({})", historyId, e.getMessage(), e.getDiagnosticCodeHex());
            updateHistoryFailed(historyId, e.getMessage(), e.getDiagnosticCodeHex());
            ctx.error(e.getMessage(), e.getDiagnosticCodeHex());
        } catch (Exception e) {
            log.error("Transfer {} FAILED: {}", historyId, e.getMessage(), e);
            updateHistoryFailed(historyId, e.getMessage(), null);
            ctx.error(e.getMessage(), null);
        } finally {
            cancelledTransfers.remove(historyId);
            closeQuietly(inputStream);
            closeQuietly(connector);
        }
    }

    private void executeTransfer(PesitSession session, PesitServer server, TransferRequest request,
            InputStream inputStream, TransferConfig config, TransferContext ctx,
            Set<String> cancelledTransfers) throws IOException, InterruptedException {

        int connectionId = 1;
        String virtualFile = request.getVirtualFile() != null ? request.getVirtualFile() : request.getRemoteFilename();
        int recordLength = config.getRecordLength() != null ? config.getRecordLength() : 506;
        boolean syncEnabled = config.isSyncPointsEnabled();
        int syncIntervalKb = syncEnabled ? 10 : 0;

        ConnectMessageBuilder connectBuilder = new ConnectMessageBuilder()
                .demandeur(request.getPartnerId()).serveur(server.getServerId()).writeAccess()
                .syncPointsEnabled(syncEnabled).syncIntervalKb(syncIntervalKb).resyncEnabled(config.isResyncEnabled());

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            connectBuilder.password(secretsService.decrypt(request.getPassword()));
        }

        ctx.connectSent();
        Fpdu aconnect = session.sendFpduWithAck(connectBuilder.build(connectionId));
        ctx.connectAck();
        int serverConnId = aconnect.getIdSrc();

        int negotiatedSyncKb = parsePI7(aconnect);
        long syncIntervalBytes = negotiatedSyncKb * 1024L;
        if (negotiatedSyncKb == 0)
            syncEnabled = false;

        int serverMaxEntity = parsePI25(aconnect);
        int transferId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF;
        long fileSizeKB = (ctx.getTotalBytes() + 1023) / 1024;
        int initialPi25 = serverMaxEntity > 0 ? serverMaxEntity : 65535;

        ctx.createSent();
        int negotiatedPi25 = negotiateCreate(session, serverConnId, virtualFile, transferId, fileSizeKB, initialPi25,
                recordLength);
        ctx.createAck();

        ctx.openSent();
        session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));
        ctx.openAck();

        ctx.writeSent();
        session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverConnId));
        ctx.writeAck();

        sendData(session, serverConnId, inputStream, negotiatedPi25, recordLength, syncIntervalBytes, syncEnabled, ctx,
                cancelledTransfers);
        sendCleanup(session, serverConnId, connectionId, ctx);
    }

    private void sendData(PesitSession session, int serverConnId, InputStream inputStream,
            int entitySize, int chunkSize, long syncInterval, boolean syncEnabled,
            TransferContext ctx, Set<String> cancelledTransfers) throws IOException, InterruptedException {

        FpduWriter writer = new FpduWriter(session, serverConnId, entitySize, chunkSize, false);
        byte[] buffer = new byte[Math.min(chunkSize, writer.getMaxDataPerDtf())];
        long bytesSinceSync = 0;
        int syncNum = 0;
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            if (cancelledTransfers.contains(ctx.getTransferId())) {
                ctx.cancelled();
                throw new RuntimeException("Transfer cancelled");
            }
            if (syncEnabled && syncInterval > 0 && bytesSinceSync > 0 && bytesSinceSync + bytesRead > syncInterval) {
                syncNum++;
                ctx.syncSent();
                session.sendFpduWithAck(new Fpdu(FpduType.SYN).withIdDst(serverConnId)
                        .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncNum)));
                ctx.syncAckSend();
                ctx.syncPoint(syncNum, ctx.getBytesTransferred());
                bytesSinceSync = 0;
            }
            byte[] chunk = bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
            writer.writeDtf(chunk);
            ctx.addBytes(bytesRead);
            bytesSinceSync += bytesRead;
        }
        log.info("Send complete: {} bytes", ctx.getBytesTransferred());
    }

    private void sendCleanup(PesitSession session, int serverConnId, int connectionId, TransferContext ctx)
            throws IOException, InterruptedException {
        ctx.dtfEndSent();
        session.sendFpdu(new Fpdu(FpduType.DTF_END).withIdDst(serverConnId)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
        ctx.transEndSent();
        session.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId));
        ctx.transEndAck();
        ctx.closeSent();
        session.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(serverConnId)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
        ctx.closeAck();
        ctx.deselectSent();
        session.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(serverConnId)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
        ctx.deselectAck();
        ctx.releaseSent();
        session.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(serverConnId).withIdSrc(connectionId)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
        ctx.releaseAck();
    }

    private int negotiateCreate(PesitSession session, int serverConnId, String virtualFile,
            int transferId, long fileSizeKB, int initialPi25, int recordLength)
            throws IOException, InterruptedException {
        int pi25 = initialPi25;
        Fpdu create = new CreateMessageBuilder().filename(virtualFile).transferId(transferId)
                .variableFormat().recordLength(recordLength).maxEntitySize(pi25).fileSizeKB(fileSizeKB)
                .build(serverConnId);
        session.sendFpduWithAck(create);
        return pi25;
    }

    private int parsePI7(Fpdu fpdu) {
        ParameterValue pv = fpdu.getParameter(PI_07_SYNC_POINTS);
        if (pv != null && pv.getValue() != null && pv.getValue().length >= 2) {
            return ((pv.getValue()[0] & 0xFF) << 8) | (pv.getValue()[1] & 0xFF);
        }
        return 0;
    }

    private int parsePI25(Fpdu fpdu) {
        ParameterValue pv = fpdu.getParameter(PI_25_TAILLE_MAX_ENTITE);
        if (pv != null && pv.getValue() != null) {
            int val = 0;
            for (byte b : pv.getValue())
                val = (val << 8) | (b & 0xFF);
            return val;
        }
        return 0;
    }

    private void updateHistorySuccess(String historyId, long bytes) {
        historyRepository.findById(historyId).ifPresent(h -> {
            h.setStatus(TransferStatus.COMPLETED);
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
