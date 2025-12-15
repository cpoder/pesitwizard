package com.vectis.fpdu;

import static com.vectis.fpdu.ParameterGroupIdentifier.*;
import static com.vectis.fpdu.ParameterIdentifier.*;

import java.io.IOException;

/**
 * Builder for PESIT F.SELECT message
 * Used to initiate a read (receive) file transfer - request an existing file
 */
public class SelectMessageBuilder {

    private String filename = "FILE";
    private int fileType = 0; // 0 for Hors-SIT profile
    private int transferId = 1;
    private int priority = 0; // 0=normal
    private int maxEntitySize = 4096;

    public SelectMessageBuilder filename(String filename) {
        this.filename = filename;
        return this;
    }

    public SelectMessageBuilder transferId(int transferId) {
        this.transferId = transferId;
        return this;
    }

    public SelectMessageBuilder fileType(int fileType) {
        this.fileType = fileType;
        return this;
    }

    /**
     * Build complete SELECT FPDU with all parameters
     * 
     * @param serverConnectionId Server connection ID from ACONNECT
     * @return Complete FPDU
     * @throws IOException if serialization fails
     */
    public Fpdu build(int serverConnectionId) throws IOException {
        // PGI 9: File Identifier (wraps PI 11 and PI 12)
        ParameterValue pgi9 = new ParameterValue(PGI_09_ID_FICHIER,
                new ParameterValue(PI_11_TYPE_FICHIER, fileType),
                new ParameterValue(PI_12_NOM_FICHIER, filename));

        // PI 13: Transfer ID
        ParameterValue pi13 = new ParameterValue(PI_13_ID_TRANSFERT, transferId);
        // PI 17: Priority (mandatory for SELECT)
        ParameterValue pi17 = new ParameterValue(PI_17_PRIORITE, priority);
        // PI 25: Max Entity Size (mandatory for SELECT)
        ParameterValue pi25 = new ParameterValue(PI_25_TAILLE_MAX_ENTITE, maxEntitySize);

        // For file-level FPDUs, idSrc (octet 6) must be 0
        return new Fpdu(FpduType.SELECT)
                .withParameter(pgi9)
                .withParameter(pi13)
                .withParameter(pi17)
                .withParameter(pi25)
                .withIdDst(serverConnectionId);
    }
}
