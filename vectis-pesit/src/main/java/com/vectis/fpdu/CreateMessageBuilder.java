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
    private boolean restart = false;
    private int dataCode = 2; // 0=ASCII, 1=EBCDIC, 2=binary
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

    public CreateMessageBuilder restart(boolean restart) {
        this.restart = restart;
        return this;
    }

    public CreateMessageBuilder dataCode(int dataCode) {
        this.dataCode = dataCode;
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
        // PI 13: Transfer Identifier (mandatory) - 3 bytes
        ParameterValue pi13 = new ParameterValue(PI_13_ID_TRANSFERT, transferId);

        // PI 15: Transfer Restarted (optional)
        ParameterValue pi15 = new ParameterValue(PI_15_TRANSFERT_RELANCE, restart ? 1 : 0);

        // PI 16: Data Code (optional)
        ParameterValue pi16 = new ParameterValue(PI_16_CODE_DONNEES, dataCode);

        // PI 17: Transfer Priority (mandatory)
        ParameterValue pi17 = new ParameterValue(PI_17_PRIORITE, priority);

        // PI 25: Max Entity Size (mandatory) - 2 bytes
        ParameterValue pi25 = new ParameterValue(PI_25_TAILLE_MAX_ENTITE, maxEntitySize);

        return new Fpdu(FpduType.CREATE).withParameter(pgi9).withParameter(pi13).withParameter(pi15).withParameter(pi16)
                .withParameter(pi17).withParameter(pi25).withParameter(pgi30).withParameter(pgi40).withParameter(pgi50)
                .withIdDst(serverConnectionId);
    }
}
