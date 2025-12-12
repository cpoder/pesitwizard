package com.vectis.server.service;

import static com.vectis.fpdu.ParameterGroupIdentifier.*;
import static com.vectis.fpdu.ParameterIdentifier.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import com.vectis.fpdu.DiagnosticCode;
import com.vectis.fpdu.Fpdu;
import com.vectis.fpdu.FpduType;
import com.vectis.fpdu.ParameterValue;
import com.vectis.server.model.SessionContext;
import com.vectis.server.model.TransferContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Builder for FPDU responses
 */
@Slf4j
public class FpduResponseBuilder {

    private static final byte[] DIAG_OK = DiagnosticCode.D0_000.toBytes();

    /**
     * Build ACONNECT response
     */
    public static Fpdu buildAconnect(SessionContext ctx, int protocolVersion,
            boolean syncPoints, boolean resync) {
        Fpdu response = new Fpdu(FpduType.ACONNECT)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(ctx.getServerConnectionId())
                .withParameter(new ParameterValue(PI_06_VERSION, protocolVersion));

        if (syncPoints) {
            // PI 7: sync points option - 3 bytes
            response.withParameter(new ParameterValue(PI_07_SYNC_POINTS,
                    new byte[] { 0x01, 0x00, 0x00 }));
        }

        if (resync) {
            // PI 23: resync option
            response.withParameter(new ParameterValue(PI_23_RESYNC, 1));
        }

        return response;
    }

    /**
     * Build RCONNECT (reject connection) response
     */
    public static Fpdu buildRconnect(SessionContext ctx, DiagnosticCode diagCode, String message) {
        byte[] diagBytes = diagCode.toBytes();
        log.info("[{}] Building RCONNECT with diag={} (code={}, reason={}, bytes=[0x{}, 0x{}, 0x{}]), message='{}'",
                ctx.getSessionId(), diagCode.name(), diagCode.getCode(), diagCode.getReason(),
                String.format("%02X", diagBytes[0]), String.format("%02X", diagBytes[1]),
                String.format("%02X", diagBytes[2]), message);

        Fpdu response = new Fpdu(FpduType.RCONNECT)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(ctx.getServerConnectionId())
                .withParameter(new ParameterValue(PI_02_DIAG, diagBytes));

        if (message != null && !message.isEmpty()) {
            response.withParameter(new ParameterValue(PI_99_MESSAGE_LIBRE, message));
        }

        return response;
    }

    /**
     * Build negative ACK for CREATE (file error)
     */
    public static Fpdu buildNackCreate(SessionContext ctx, DiagnosticCode diagCode, String message) {
        byte[] diagBytes = diagCode.toBytes();
        log.info("[{}] Building NACK_CREATE with diag={} (code={}, reason={}, bytes=[0x{}, 0x{}, 0x{}]), message='{}'",
                ctx.getSessionId(), diagCode.name(), diagCode.getCode(), diagCode.getReason(),
                String.format("%02X", diagBytes[0]), String.format("%02X", diagBytes[1]),
                String.format("%02X", diagBytes[2]), message);

        Fpdu response = new Fpdu(FpduType.ACK_CREATE)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, diagBytes));

        if (message != null && !message.isEmpty()) {
            response.withParameter(new ParameterValue(PI_99_MESSAGE_LIBRE, message));
        }

        return response;
    }

    /**
     * Build negative ACK for SELECT (file error)
     */
    public static Fpdu buildNackSelect(SessionContext ctx, DiagnosticCode diagCode, String message) {
        byte[] diagBytes = diagCode.toBytes();
        log.info("[{}] Building NACK_SELECT with diag={} (code={}, reason={}, bytes=[0x{}, 0x{}, 0x{}]), message='{}'",
                ctx.getSessionId(), diagCode.name(), diagCode.getCode(), diagCode.getReason(),
                String.format("%02X", diagBytes[0]), String.format("%02X", diagBytes[1]),
                String.format("%02X", diagBytes[2]), message);

        Fpdu response = new Fpdu(FpduType.ACK_SELECT)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, diagBytes));

        if (message != null && !message.isEmpty()) {
            response.withParameter(new ParameterValue(PI_99_MESSAGE_LIBRE, message));
        }

        return response;
    }

    /**
     * Build ACK(CREATE) response
     */
    public static Fpdu buildAckCreate(SessionContext ctx, int maxEntitySize) {
        return new Fpdu(FpduType.ACK_CREATE)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, DIAG_OK))
                .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, maxEntitySize));
    }

    /**
     * Build ACK(SELECT) response
     * 
     * Required parameters per PeSIT spec:
     * - PI_02_DIAG: Diagnostic code
     * - PGI_09_ID_FICHIER: File identification (PI_11 file type, PI_12 filename)
     * - PI_13_ID_TRANSFERT: Transfer ID
     * - PI_25_TAILLE_MAX_ENTITE: Max entity size
     * - PGI_30_ATTR_LOGIQUES: Logical attributes (PI_32 record length)
     * - PGI_40_ATTR_PHYSIQUES: Physical attributes (PI_42 max reservation)
     * - PGI_50_ATTR_HISTORIQUES: Historical attributes (PI_51 creation date)
     */
    public static Fpdu buildAckSelect(SessionContext ctx, int maxEntitySize) {
        TransferContext transfer = ctx.getCurrentTransfer();
        String filename = transfer != null ? transfer.getFilename() : "unknown";
        int transferId = transfer != null ? transfer.getTransferId() : 1;

        // Get file size if available
        long fileSize = 0;
        String creationDate = Instant.now().toString();
        if (transfer != null && transfer.getLocalPath() != null) {
            Path filePath = transfer.getLocalPath();
            try {
                if (Files.exists(filePath)) {
                    fileSize = Files.size(filePath);
                    creationDate = Files.getLastModifiedTime(filePath).toInstant().toString();
                }
            } catch (Exception e) {
                log.warn("Could not get file attributes for {}: {}", filePath, e.getMessage());
            }
        }

        // PGI 9: File Identification (PI_11 file type, PI_12 filename)
        ParameterValue pgi9 = new ParameterValue(PGI_09_ID_FICHIER,
                new ParameterValue(PI_11_TYPE_FICHIER, 0), // 0 = binary
                new ParameterValue(PI_12_NOM_FICHIER, filename));

        // PGI 30: Logical Attributes (PI_32 record length)
        ParameterValue pgi30 = new ParameterValue(PGI_30_ATTR_LOGIQUES,
                new ParameterValue(PI_32_LONG_ARTICLE, 0)); // 0 = variable length

        // PGI 40: Physical Attributes (PI_42 max reservation = file size)
        ParameterValue pgi40 = new ParameterValue(PGI_40_ATTR_PHYSIQUES,
                new ParameterValue(PI_42_MAX_RESERVATION, fileSize));

        // PGI 50: Historical Attributes (PI_51 creation date)
        ParameterValue pgi50 = new ParameterValue(PGI_50_ATTR_HISTORIQUES,
                new ParameterValue(PI_51_DATE_CREATION, creationDate));

        return new Fpdu(FpduType.ACK_SELECT)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, DIAG_OK))
                .withParameter(pgi9)
                .withParameter(new ParameterValue(PI_13_ID_TRANSFERT, transferId))
                .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, maxEntitySize))
                .withParameter(pgi30)
                .withParameter(pgi40)
                .withParameter(pgi50);
    }

    /**
     * Build ACK(ORF) - ACK Open response
     */
    public static Fpdu buildAckOpen(SessionContext ctx) {
        return new Fpdu(FpduType.ACK_OPEN)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, DIAG_OK));
    }

    /**
     * Build ACK(WRITE) response
     */
    public static Fpdu buildAckWrite(SessionContext ctx, int restartPoint) {
        return new Fpdu(FpduType.ACK_WRITE)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, DIAG_OK))
                .withParameter(new ParameterValue(PI_18_POINT_RELANCE, restartPoint));
    }

    /**
     * Build ACK(READ) response
     */
    public static Fpdu buildAckRead(SessionContext ctx) {
        return new Fpdu(FpduType.ACK_READ)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, DIAG_OK));
    }

    /**
     * Build ACK(CRF) - ACK Close response
     */
    public static Fpdu buildAckClose(SessionContext ctx) {
        return new Fpdu(FpduType.ACK_CLOSE)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, DIAG_OK));
    }

    /**
     * Build ACK(TRANS.END) response
     */
    public static Fpdu buildAckTransEnd(SessionContext ctx, long byteCount, int recordCount) {
        Fpdu response = new Fpdu(FpduType.ACK_TRANS_END)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, DIAG_OK));

        if (byteCount > 0) {
            response.withParameter(new ParameterValue(PI_27_NB_OCTETS, byteCount));
        }
        if (recordCount > 0) {
            response.withParameter(new ParameterValue(PI_28_NB_ARTICLES, recordCount));
        }

        return response;
    }

    /**
     * Build ACK(DESELECT) response
     */
    public static Fpdu buildAckDeselect(SessionContext ctx) {
        return new Fpdu(FpduType.ACK_DESELECT)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, DIAG_OK));
    }

    /**
     * Build RELCONF (Release Confirmation) response
     */
    public static Fpdu buildRelconf(SessionContext ctx) {
        return new Fpdu(FpduType.RELCONF)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(ctx.getServerConnectionId());
    }

    /**
     * Build ACK(SYN) response
     */
    public static Fpdu buildAckSyn(SessionContext ctx, int syncPointNumber) {
        return new Fpdu(FpduType.ACK_SYN)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_20_NUM_SYNC, syncPointNumber));
    }

    /**
     * Build ACK(IDT) response
     */
    public static Fpdu buildAckIdt(SessionContext ctx) {
        return new Fpdu(FpduType.ACK_IDT)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0);
    }

    /**
     * Build ACK(MSG) response for message transfer
     */
    public static Fpdu buildAckMsg(SessionContext ctx, String responseMessage) {
        Fpdu response = new Fpdu(FpduType.ACK_MSG)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, DIAG_OK));

        if (responseMessage != null && !responseMessage.isEmpty()) {
            response.withParameter(new ParameterValue(PI_91_MESSAGE, responseMessage));
        }

        return response;
    }

    /**
     * Build negative ACK(MSG) response
     */
    public static Fpdu buildNackMsg(SessionContext ctx, DiagnosticCode diagCode, String message) {
        Fpdu response = new Fpdu(FpduType.ACK_MSG)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, diagCode.toBytes()));

        if (message != null && !message.isEmpty()) {
            response.withParameter(new ParameterValue(PI_91_MESSAGE, message));
        }

        return response;
    }

    /**
     * Build ABORT response
     */
    public static Fpdu buildAbort(SessionContext ctx, DiagnosticCode diagCode) {
        byte[] diagBytes = diagCode.toBytes();
        log.info("[{}] Building ABORT with diag={} (code={}, reason={}, bytes=[0x{}, 0x{}, 0x{}])",
                ctx.getSessionId(), diagCode.name(), diagCode.getCode(), diagCode.getReason(),
                String.format("%02X", diagBytes[0]), String.format("%02X", diagBytes[1]),
                String.format("%02X", diagBytes[2]));

        return new Fpdu(FpduType.ABORT)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(ctx.getServerConnectionId())
                .withParameter(new ParameterValue(PI_02_DIAG, diagBytes));
    }

    /**
     * Build ABORT response with message
     */
    public static Fpdu buildAbort(SessionContext ctx, DiagnosticCode diagCode, String message) {
        byte[] diagBytes = diagCode.toBytes();
        log.info("[{}] Building ABORT with diag={} (code={}, reason={}, bytes=[0x{}, 0x{}, 0x{}]), message='{}'",
                ctx.getSessionId(), diagCode.name(), diagCode.getCode(), diagCode.getReason(),
                String.format("%02X", diagBytes[0]), String.format("%02X", diagBytes[1]),
                String.format("%02X", diagBytes[2]), message);

        Fpdu response = new Fpdu(FpduType.ABORT)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(ctx.getServerConnectionId())
                .withParameter(new ParameterValue(PI_02_DIAG, diagBytes));

        if (message != null && !message.isEmpty()) {
            response.withParameter(new ParameterValue(PI_99_MESSAGE_LIBRE, message));
        }

        return response;
    }

    /**
     * Build DTF (Data Transfer) FPDU with data payload
     * DTF FPDUs carry the actual file data and don't require an ACK
     */
    public static Fpdu buildDtf(SessionContext ctx, byte[] data) {
        Fpdu dtf = new Fpdu(FpduType.DTF)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0);
        dtf.setData(data);
        return dtf;
    }

    /**
     * Build DTF.END FPDU to signal end of data transfer
     * Sent after all DTF FPDUs, before TRANS.END
     */
    public static Fpdu buildDtfEnd(SessionContext ctx) {
        return new Fpdu(FpduType.DTF_END)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0)
                .withParameter(new ParameterValue(PI_02_DIAG, DIAG_OK));
    }

    /**
     * Build TRANS.END FPDU to signal end of transfer
     * Sent by the data sender (server in read mode) after DTF.END
     */
    public static Fpdu buildTransEnd(SessionContext ctx, long byteCount, int recordCount) {
        Fpdu response = new Fpdu(FpduType.TRANS_END)
                .withIdDst(ctx.getClientConnectionId())
                .withIdSrc(0);

        if (byteCount > 0) {
            response.withParameter(new ParameterValue(PI_27_NB_OCTETS, byteCount));
        }
        if (recordCount > 0) {
            response.withParameter(new ParameterValue(PI_28_NB_ARTICLES, recordCount));
        }

        return response;
    }
}
