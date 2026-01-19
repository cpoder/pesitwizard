package com.pesitwizard.client.pesit;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.pesitwizard.client.dto.TransferRequest;
import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.client.entity.TransferConfig;
import com.pesitwizard.client.service.RestartRequiredException;
import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.StorageConnector;
import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.CreateMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.security.SecretsService;
import com.pesitwizard.session.PesitSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes PeSIT protocol transfers (send/receive).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PesitTransferExecutor {

    private static final AtomicInteger TRANSFER_ID_COUNTER = new AtomicInteger(1);
    private final SecretsService secretsService;

    public interface ProgressCallback {
        void onProgress(long bytesTransferred, long totalSize, int syncPoint);

        boolean isCancelled();
    }

    public long executeSend(PesitSession session, PesitServer server, TransferRequest request,
            InputStream inputStream, long fileSize, TransferConfig config, ProgressCallback callback)
            throws IOException, InterruptedException {
        return new SendOperation(session, server, request, inputStream, fileSize, config, callback).execute();
    }

    public long executeReceive(PesitSession session, PesitServer server, TransferRequest request,
            StorageConnector connector, String destPath, TransferConfig config,
            ProgressCallback callback, int restartPoint, long restartBytePosition)
            throws IOException, InterruptedException, ConnectorException, RestartRequiredException {
        return new ReceiveOperation(session, server, request, connector, destPath, config,
                callback, restartPoint, restartBytePosition).execute();
    }

    // Inner class for send operation
    private class SendOperation {
        private final PesitSession session;
        private final PesitServer server;
        private final TransferRequest request;
        private final InputStream inputStream;
        private final long fileSize;
        private final TransferConfig config;
        private final ProgressCallback callback;

        SendOperation(PesitSession session, PesitServer server, TransferRequest request,
                InputStream inputStream, long fileSize, TransferConfig config, ProgressCallback callback) {
            this.session = session;
            this.server = server;
            this.request = request;
            this.inputStream = inputStream;
            this.fileSize = fileSize;
            this.config = config;
            this.callback = callback;
        }

        long execute() throws IOException, InterruptedException {
            int connectionId = 1;
            String virtualFile = request.getVirtualFile() != null ? request.getVirtualFile()
                    : request.getRemoteFilename();
            int recordLength = request.getRecordLength() != null ? request.getRecordLength()
                    : (config.getRecordLength() != null ? config.getRecordLength() : 506);
            boolean syncEnabled = request.getSyncPointsEnabled() != null ? request.getSyncPointsEnabled()
                    : config.isSyncPointsEnabled();
            long syncInterval = calculateSyncInterval(fileSize, syncEnabled);

            // CONNECT
            ConnectMessageBuilder cb = new ConnectMessageBuilder()
                    .demandeur(request.getPartnerId()).serveur(server.getServerId())
                    .writeAccess().syncPointsEnabled(syncEnabled && syncInterval > 0)
                    .syncIntervalKb(syncInterval > 0 ? (int) (syncInterval / 1024) : 0);
            if (request.getPassword() != null)
                cb.password(secretsService.decrypt(request.getPassword()));

            Fpdu aconnect = session.sendFpduWithAck(cb.build(connectionId));
            int serverId = aconnect.getIdSrc();
            int negSyncKb = parsePI7(aconnect);
            if (negSyncKb == 0)
                syncEnabled = false;
            long negSyncBytes = negSyncKb * 1024L;

            // CREATE
            int txId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF;
            int pi25 = negotiateCreate(session, serverId, virtualFile, txId, (fileSize + 1023) / 1024, recordLength);

            // OPEN, WRITE
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverId));
            session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverId));

            // Send data
            long totalSent = sendData(session, serverId, pi25, recordLength, negSyncBytes, syncEnabled);

            // Cleanup
            session.sendFpdu(new Fpdu(FpduType.DTF_END).withIdDst(serverId)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
            session.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(serverId));
            cleanup(session, serverId, connectionId);
            return totalSent;
        }

        private long sendData(PesitSession session, int serverId, int entitySize, int recordLength,
                long syncInterval, boolean syncEnabled) throws IOException, InterruptedException {
            FpduWriter writer = new FpduWriter(session, serverId, entitySize, recordLength, false);
            byte[] buffer = new byte[Math.min(recordLength > 0 ? recordLength : 4096, writer.getMaxDataPerDtf())];
            long totalSent = 0, bytesSinceSync = 0;
            int syncNum = 0, bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (callback != null && callback.isCancelled())
                    throw new RuntimeException("Cancelled");
                byte[] chunk = bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                writer.writeDtf(chunk);
                totalSent = writer.getTotalBytesSent();
                bytesSinceSync += bytesRead;

                if (syncEnabled && syncInterval > 0 && bytesSinceSync >= syncInterval) {
                    syncNum++;
                    session.sendFpduWithAck(new Fpdu(FpduType.SYN).withIdDst(serverId)
                            .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncNum)));
                    bytesSinceSync = 0;
                }
                if (callback != null)
                    callback.onProgress(totalSent, fileSize, syncNum);
            }
            return totalSent;
        }
    }

    // Inner class for receive operation
    private class ReceiveOperation {
        private final PesitSession session;
        private final PesitServer server;
        private final TransferRequest request;
        private final StorageConnector connector;
        private final String destPath;
        private final TransferConfig config;
        private final ProgressCallback callback;
        private final int restartPoint;
        private final long restartBytePos;

        ReceiveOperation(PesitSession session, PesitServer server, TransferRequest request,
                StorageConnector connector, String destPath, TransferConfig config,
                ProgressCallback callback, int restartPoint, long restartBytePos) {
            this.session = session;
            this.server = server;
            this.request = request;
            this.connector = connector;
            this.destPath = destPath;
            this.config = config;
            this.callback = callback;
            this.restartPoint = restartPoint;
            this.restartBytePos = restartBytePos;
        }

        long execute() throws IOException, InterruptedException, ConnectorException, RestartRequiredException {
            int connectionId = 1;
            String virtualFile = request.getVirtualFile() != null ? request.getVirtualFile()
                    : request.getRemoteFilename();
            boolean syncEnabled = request.getSyncPointsEnabled() != null ? request.getSyncPointsEnabled()
                    : config.isSyncPointsEnabled();

            // CONNECT
            ConnectMessageBuilder cb = new ConnectMessageBuilder()
                    .demandeur(request.getPartnerId()).serveur(server.getServerId())
                    .readAccess().syncPointsEnabled(syncEnabled);
            if (request.getPassword() != null)
                cb.password(secretsService.decrypt(request.getPassword()));

            Fpdu aconnect = session.sendFpduWithAck(cb.build(connectionId));
            int serverId = aconnect.getIdSrc();

            // SELECT
            int txId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF;
            ParameterValue pgi9 = new ParameterValue(ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                    new ParameterValue(PI_11_TYPE_FICHIER, 0), new ParameterValue(PI_12_NOM_FICHIER, virtualFile));
            Fpdu select = new Fpdu(FpduType.SELECT).withIdDst(serverId).withParameter(pgi9)
                    .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, txId))
                    .withParameter(new ParameterValue(PI_14_ATTRIBUTS_DEMANDES, 0))
                    .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, config.getChunkSize()));
            Fpdu ackSelect = session.sendFpduWithAck(select);
            long expectedSize = parseFileSize(ackSelect);

            // OPEN, READ
            session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverId));
            session.sendFpduWithAck(new Fpdu(FpduType.READ).withIdDst(serverId)
                    .withParameter(new ParameterValue(PI_18_POINT_RELANCE, restartPoint)));

            // Receive data
            return receiveData(session, serverId, connectionId, expectedSize);
        }

        private long receiveData(PesitSession session, int serverId, int connId, long expectedSize)
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
                while (receiving) {
                    if (callback != null && callback.isCancelled())
                        throw new RuntimeException("Cancelled");
                    Fpdu fpdu = reader.read();
                    FpduType type = fpdu.getFpduType();

                    if (type == FpduType.DTF || type == FpduType.DTFDA || type == FpduType.DTFMA
                            || type == FpduType.DTFFA) {
                        byte[] data = fpdu.getData();
                        if (data != null && data.length > 0) {
                            if (raf != null)
                                raf.write(data);
                            else
                                os.write(data);
                            totalBytes += data.length;
                            if (callback != null)
                                callback.onProgress(totalBytes, expectedSize, lastSync);
                        }
                    } else if (type == FpduType.SYN) {
                        ParameterValue pv = fpdu.getParameter(PI_20_NUM_SYNC);
                        lastSync = pv != null && pv.getValue() != null ? parseNum(pv.getValue()) : lastSync + 1;
                        lastSyncPos = totalBytes;
                        session.sendFpdu(new Fpdu(FpduType.ACK_SYN).withIdDst(serverId)
                                .withParameter(new ParameterValue(PI_20_NUM_SYNC, lastSync)));
                    } else if (type == FpduType.DTF_END || type == FpduType.TRANS_END || type == FpduType.CLOSE) {
                        receiving = false;
                    } else if (type == FpduType.IDT) {
                        ParameterValue pi19 = fpdu.getParameter(PI_19_CODE_FIN_TRANSFERT);
                        restartCode = pi19 != null && pi19.getValue() != null ? pi19.getValue()[0] & 0xFF : 0;
                        session.sendFpdu(new Fpdu(FpduType.ACK_IDT).withIdDst(serverId)
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

            if (!interrupted)
                cleanup(session, serverId, connId);
            else if (restartCode == 4)
                throw new RestartRequiredException(lastSync, lastSyncPos, totalBytes);
            return totalBytes;
        }
    }

    // Shared helpers
    private void cleanup(PesitSession s, int srv, int conn) throws IOException, InterruptedException {
        s.sendFpduWithAck(new Fpdu(FpduType.CLOSE).withIdDst(srv)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
        s.sendFpduWithAck(new Fpdu(FpduType.DESELECT).withIdDst(srv)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
        s.sendFpduWithAck(new Fpdu(FpduType.RELEASE).withIdDst(srv).withIdSrc(conn)
                .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0, 0, 0 })));
    }

    private int negotiateCreate(PesitSession s, int srv, String vf, int txId, long sizeKB, int pi32)
            throws IOException, InterruptedException {
        int pi25 = 65535;
        while (pi25 >= pi32 + 6) {
            Fpdu create = new CreateMessageBuilder().filename(vf).transferId(txId).variableFormat()
                    .recordLength(pi32).maxEntitySize(pi25).fileSizeKB(sizeKB).build(srv);
            Fpdu ack = s.sendFpduWithAck(create);
            ParameterValue diag = ack.getParameter(PI_02_DIAG);
            if (diag != null && diag.getValue() != null && diag.getValue().length >= 2
                    && (diag.getValue()[0] != 0 || diag.getValue()[1] != 0)) {
                pi25 /= 2;
                continue;
            }
            ParameterValue ackPi25 = ack.getParameter(PI_25_TAILLE_MAX_ENTITE);
            return ackPi25 != null ? parseNum(ackPi25.getValue()) : pi25;
        }
        throw new RuntimeException("Cannot negotiate PI25");
    }

    private int parsePI7(Fpdu fpdu) {
        ParameterValue pi7 = fpdu.getParameter(PI_07_SYNC_POINTS);
        return pi7 != null && pi7.getValue() != null && pi7.getValue().length >= 2
                ? ((pi7.getValue()[0] & 0xFF) << 8) | (pi7.getValue()[1] & 0xFF)
                : 0;
    }

    private long parseFileSize(Fpdu ack) {
        ParameterValue pgi40 = ack.getParameter(ParameterGroupIdentifier.PGI_40_ATTR_PHYSIQUES);
        if (pgi40 != null && pgi40.getValues() != null) {
            for (ParameterValue pv : pgi40.getValues()) {
                if (pv.getParameter() == PI_42_MAX_RESERVATION)
                    return parseNum(pv.getValue()) * 1024L;
            }
        }
        return 0;
    }

    private long calculateSyncInterval(long fileSize, boolean enabled) {
        if (!enabled)
            return 0;
        if (fileSize < 1024 * 1024)
            return 0;
        if (fileSize < 10 * 1024 * 1024)
            return 256 * 1024;
        if (fileSize < 100 * 1024 * 1024)
            return 1024 * 1024;
        return 5 * 1024 * 1024;
    }

    private int parseNum(byte[] b) {
        if (b == null)
            return 0;
        int v = 0;
        for (byte x : b)
            v = (v << 8) | (x & 0xFF);
        return v;
    }
}
