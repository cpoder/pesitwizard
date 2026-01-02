package com.pesitwizard.fpdu;

import static com.pesitwizard.fpdu.ParameterGroupIdentifier.*;
import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Builder for PESIT F.CREATE message
 * Used to initiate a write (send) file transfer
 */
public class CreateMessageBuilder {

    private String filename = "FILE";
    private int fileType = 0; // 0 for Hors-SIT profile
    private int transferId = 1;
    private int priority = 0; // 0=normal
    private int maxEntitySize = 4096;
    private int articleFormat = 0x80; // 0x80=variable, 0x00=fixed
    private int recordLength = 1024;
    private int allocationUnit = 0; // 0=Koctets
    private int maxReservation = 0; // 0=no limit
    private String creationDate = null;
    private boolean isRestart = false; // PI 15: indicates this is a resumed transfer

    public CreateMessageBuilder filename(String filename) {
        this.filename = filename;
        return this;
    }

    public CreateMessageBuilder transferId(int transferId) {
        this.transferId = transferId;
        return this;
    }

    public CreateMessageBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    public CreateMessageBuilder maxEntitySize(int maxEntitySize) {
        this.maxEntitySize = maxEntitySize;
        return this;
    }

    public CreateMessageBuilder variableFormat() {
        this.articleFormat = 0x80;
        return this;
    }

    public CreateMessageBuilder fixedFormat() {
        this.articleFormat = 0x00;
        return this;
    }

    public CreateMessageBuilder recordLength(int recordLength) {
        this.recordLength = recordLength;
        return this;
    }

    /**
     * Set max file size in KB (PI 42). Required to announce file size to server.
     * PI 41 (allocationUnit) = 0 means KB.
     */
    public CreateMessageBuilder fileSizeKB(long fileSizeKB) {
        this.maxReservation = (int) Math.min(fileSizeKB, Integer.MAX_VALUE);
        return this;
    }

    public CreateMessageBuilder creationDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss");
        this.creationDate = sdf.format(date);
        return this;
    }

    /**
     * Mark this transfer as a restart/resume (PI 15 = 1).
     * The actual restart point will come from ACK_WRITE (PI 18).
     */
    public CreateMessageBuilder restart() {
        this.isRestart = true;
        return this;
    }

    /**
     * Build complete CREATE FPDU with all parameters
     * 
     * @param serverConnectionId Server connection ID from ACONNECT
     * @return Complete FPDU byte array
     * @throws IOException if serialization fails
     */
    public Fpdu build(int serverConnectionId) throws IOException {
        ParameterValue pgi9 = new ParameterValue(PGI_09_ID_FICHIER,
                new ParameterValue(PI_11_TYPE_FICHIER, fileType),
                new ParameterValue(PI_12_NOM_FICHIER, filename));

        // PGI 30: Logical attributes
        // PI 32 (record length): must be > 0, CX crashes with "Null length for a
        // record" if 0
        // Use recordLength if set, otherwise default to 1024
        int effectiveRecordLength = recordLength > 0 ? recordLength : 1024;
        ParameterValue pgi30 = new ParameterValue(PGI_30_ATTR_LOGIQUES,
                new ParameterValue(PI_31_FORMAT_ARTICLE, articleFormat),
                new ParameterValue(PI_32_LONG_ARTICLE, effectiveRecordLength));
        ParameterValue pgi40 = new ParameterValue(PGI_40_ATTR_PHYSIQUES,
                new ParameterValue(PI_41_UNITE_RESERVATION, allocationUnit),
                new ParameterValue(PI_42_MAX_RESERVATION, maxReservation));

        // For file-level FPDUs, idSrc (octet 6) must be 0

        // PGI 50: Historical Attributes (wraps PI 51: Creation Date)
        if (creationDate == null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss");
            creationDate = sdf.format(new Date());
        }

        ParameterValue pgi50 = new ParameterValue(PGI_50_ATTR_HISTORIQUES,
                new ParameterValue(PI_51_DATE_CREATION, creationDate));
        // Individual PIs (not wrapped in PGI)
        ParameterValue pi13 = new ParameterValue(PI_13_ID_TRANSFERT, transferId);
        ParameterValue pi17 = new ParameterValue(PI_17_PRIORITE, priority);
        ParameterValue pi25 = new ParameterValue(PI_25_TAILLE_MAX_ENTITE, maxEntitySize);

        Fpdu fpdu = new Fpdu(FpduType.CREATE).withParameter(pgi9).withParameter(pi13)
                .withParameter(pi17).withParameter(pi25).withParameter(pgi30).withParameter(pgi40).withParameter(pgi50)
                .withIdDst(serverConnectionId);

        // Add PI 15 (transfert relancé) if this is a restart
        // Note: PI 18 (restart point) comes from ACK_WRITE, not from client
        if (isRestart) {
            fpdu.withParameter(new ParameterValue(PI_15_TRANSFERT_RELANCE, 1));
        }

        return fpdu;
    }
}
