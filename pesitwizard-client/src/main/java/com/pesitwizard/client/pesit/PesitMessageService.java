package com.pesitwizard.client.pesit;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import com.pesitwizard.client.dto.MessageRequest;
import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.fpdu.ConnectMessageBuilder;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.session.PesitSession;
import com.pesitwizard.transport.TransportChannel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service dédié à l'envoi de messages via PeSIT. Supporte plusieurs méthodes d'envoi : FPDU MSG,
 * PI_91/PI_99, ou comme fichier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PesitMessageService {

    private static final AtomicInteger TRANSFER_ID_COUNTER = new AtomicInteger(1);

    private final PesitChannelFactory channelFactory;

    /** Envoie un message via PeSIT selon la méthode spécifiée. */
    public void sendMessage(MessageRequest request, PesitServer server)
            throws IOException, InterruptedException {
        try (TransportChannel channel = channelFactory.createChannel(server);
                PesitSession session = new PesitSession(channel, false)) {
            MessageRequest.MessageMode mode =
                    request.getMode() != null ? request.getMode() : MessageRequest.MessageMode.FPDU;

            switch (mode) {
                case FPDU ->
                        executeMessageFpdu(
                                session, server, request.getPartnerId(), request.getMessage());
                case PI99 ->
                        executeMessagePi99(
                                session,
                                server,
                                request.getPartnerId(),
                                request.getMessage(),
                                request.isUsePi91());
                case FILE ->
                        executeMessageAsFile(
                                session,
                                server,
                                request.getPartnerId(),
                                request.getMessage(),
                                request.getMessageName());
            }
        }
    }

    /** Envoie un message via FPDU MSG standard. */
    private void executeMessageFpdu(
            PesitSession session, PesitServer server, String partnerId, String message)
            throws IOException, InterruptedException {
        int connectionId = 1;

        // CONNECT
        Fpdu connectFpdu =
                new ConnectMessageBuilder()
                        .demandeur(partnerId)
                        .serveur(server.getServerId())
                        .writeAccess()
                        .build(connectionId);

        Fpdu aconnect = session.sendFpduWithAck(connectFpdu);
        int serverConnId = aconnect.getIdSrc();

        // MSG
        ParameterValue pgi9 =
                new ParameterValue(
                        ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                        new ParameterValue(PI_12_NOM_FICHIER, "MESSAGE"));

        int transferId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF;
        Fpdu msgFpdu =
                new Fpdu(FpduType.MSG)
                        .withIdDst(serverConnId)
                        .withParameter(pgi9)
                        .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, transferId))
                        .withParameter(new ParameterValue(PI_91_MESSAGE, message));

        session.sendFpduWithAck(msgFpdu);

        // RELEASE
        session.sendFpduWithAck(
                new Fpdu(FpduType.RELEASE)
                        .withIdDst(serverConnId)
                        .withIdSrc(connectionId)
                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] {0, 0, 0})));

        log.info("Message sent via FPDU MSG to {} (partner: {})", server.getName(), partnerId);
    }

    /** Envoie un message via PI_91 ou PI_99 dans le CONNECT. */
    private void executeMessagePi99(
            PesitSession session,
            PesitServer server,
            String partnerId,
            String message,
            boolean usePi91)
            throws IOException, InterruptedException {
        int connectionId = 1;

        // CONNECT avec message intégré
        Fpdu connectFpdu =
                new Fpdu(FpduType.CONNECT)
                        .withIdSrc(connectionId)
                        .withIdDst(0)
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
        int serverConnId = aconnect.getIdSrc();

        // RELEASE
        session.sendFpduWithAck(
                new Fpdu(FpduType.RELEASE)
                        .withIdDst(serverConnId)
                        .withIdSrc(connectionId)
                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] {0, 0, 0})));

        log.info(
                "Message sent via {} to {} (partner: {})",
                usePi91 ? "PI_91" : "PI_99",
                server.getName(),
                partnerId);
    }

    /** Envoie un message comme fichier texte. */
    private void executeMessageAsFile(
            PesitSession session,
            PesitServer server,
            String partnerId,
            String message,
            String messageName)
            throws IOException, InterruptedException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        String filename =
                messageName != null
                        ? messageName
                        : "message_" + System.currentTimeMillis() + ".txt";

        int connectionId = 1;

        // CONNECT
        Fpdu aconnect =
                session.sendFpduWithAck(
                        new ConnectMessageBuilder()
                                .demandeur(partnerId)
                                .serveur(server.getServerId())
                                .writeAccess()
                                .build(connectionId));
        int serverConnId = aconnect.getIdSrc();

        // CREATE
        int transferId = TRANSFER_ID_COUNTER.getAndIncrement() % 0xFFFFFF;
        session.sendFpduWithAck(
                new com.pesitwizard.fpdu.CreateMessageBuilder()
                        .filename(filename)
                        .transferId(transferId)
                        .variableFormat()
                        .recordLength(506)
                        .maxEntitySize(512)
                        .fileSizeKB((data.length + 1023) / 1024)
                        .build(serverConnId));

        // OPEN, WRITE
        session.sendFpduWithAck(new Fpdu(FpduType.OPEN).withIdDst(serverConnId));
        session.sendFpduWithAck(new Fpdu(FpduType.WRITE).withIdDst(serverConnId));

        // DTF
        FpduWriter writer = new FpduWriter(session, serverConnId, 512, 506, false);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
            byte[] buffer = new byte[506];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                byte[] chunk =
                        bytesRead == buffer.length
                                ? buffer
                                : java.util.Arrays.copyOf(buffer, bytesRead);
                writer.writeDtf(chunk);
            }
        }

        // Cleanup
        session.sendFpdu(
                new Fpdu(FpduType.DTF_END)
                        .withIdDst(serverConnId)
                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] {0, 0, 0})));
        session.sendFpduWithAck(new Fpdu(FpduType.TRANS_END).withIdDst(serverConnId));
        session.sendFpduWithAck(
                new Fpdu(FpduType.CLOSE)
                        .withIdDst(serverConnId)
                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] {0, 0, 0})));
        session.sendFpduWithAck(
                new Fpdu(FpduType.DESELECT)
                        .withIdDst(serverConnId)
                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] {0, 0, 0})));
        session.sendFpduWithAck(
                new Fpdu(FpduType.RELEASE)
                        .withIdDst(serverConnId)
                        .withIdSrc(connectionId)
                        .withParameter(new ParameterValue(PI_02_DIAG, new byte[] {0, 0, 0})));

        log.info(
                "Message sent as file '{}' ({} bytes) to {} (partner: {})",
                filename,
                data.length,
                server.getName(),
                partnerId);
    }
}
