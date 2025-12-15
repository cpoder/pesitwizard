package com.vectis.fpdu;

import static com.vectis.fpdu.ParameterGroupIdentifier.*;
import static com.vectis.fpdu.ParameterIdentifier.*;

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

    public CreateMessageBuilder creationDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss");
        this.creationDate = sdf.format(date);
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

        ParameterValue pgi30 = new ParameterValue(PGI_30_ATTR_LOGIQUES,
                new ParameterValue(PI_31_FORMAT_ARTICLE, articleFormat),
                new ParameterValue(PI_32_LONG_ARTICLE, recordLength));
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

        // Note: PI_15 and PI_16 not included - not in CREATE parameter requirements
        return new Fpdu(FpduType.CREATE).withParameter(pgi9).withParameter(pi13)
                .withParameter(pi17).withParameter(pi25).withParameter(pgi30).withParameter(pgi40).withParameter(pgi50)
                .withIdDst(serverConnectionId);
    }
}
