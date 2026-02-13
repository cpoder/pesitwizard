package com.pesitwizard.fpdu;

import static com.pesitwizard.fpdu.ParameterGroupIdentifier.*;
import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import java.util.Map;
import java.util.TreeMap;

/**
 * PESIT FPDU (File Transfer Protocol Data Unit) Types Based on PESIT E specification (September
 * 1989) Each FPDU defines its parameter requirements (mandatory/optional PIs and PGIs)
 */
public enum FpduType {
    // Acknowledgments first (so they can be referenced)
    ACONNECT(
            0x40,
            0x21,
            "FPDU.ACONNECT",
            new ParameterRequirement(PI_05_CONTROLE_ACCES, false),
            new ParameterRequirement(PI_06_VERSION, true),
            new ParameterRequirement(PI_07_SYNC_POINTS, false),
            new ParameterRequirement(PI_23_RESYNC, false),
            new ParameterRequirement(PI_99_MESSAGE_LIBRE, false)),

    RELCONF(0x40, 0x24, "FPDU.RELCONF", new ParameterRequirement(PI_99_MESSAGE_LIBRE, false)),

    ACK_MSG(
            0xC0,
            0x3B,
            "FPDU.ACK(MSG)",
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PI_13_ID_TRANSFERT, false),
            new ParameterRequirement(PI_16_CODE_DONNEES, false),
            new ParameterRequirement(PI_91_MESSAGE, false)),

    ACK_CREATE(
            0xC0,
            0x30,
            "FPDU.ACK(CREATE)",
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PI_13_ID_TRANSFERT, false),
            new ParameterRequirement(PI_25_TAILLE_MAX_ENTITE, true),
            new ParameterRequirement(PI_72_ELEMS_AUTH, false),
            new ParameterRequirement(PI_80_ACCREDITATION, false),
            new ParameterRequirement(PI_83_ACCRED_2, false),
            new ParameterRequirement(PI_99_MESSAGE_LIBRE, false)),

    ACK_SELECT(
            0xC0,
            0x31,
            "FPDU.ACK(SELECT)",
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PGI_09_ID_FICHIER, true),
            new ParameterRequirement(PI_13_ID_TRANSFERT, true),
            new ParameterRequirement(PI_16_CODE_DONNEES, false),
            new ParameterRequirement(PI_25_TAILLE_MAX_ENTITE, true),
            new ParameterRequirement(PGI_30_ATTR_LOGIQUES, true),
            new ParameterRequirement(PGI_40_ATTR_PHYSIQUES, true),
            new ParameterRequirement(PGI_50_ATTR_HISTORIQUES, true),
            new ParameterRequirement(PI_72_ELEMS_AUTH, false),
            new ParameterRequirement(PI_80_ACCREDITATION, false),
            new ParameterRequirement(PI_83_ACCRED_2, false),
            new ParameterRequirement(PI_99_MESSAGE_LIBRE, false)),

    ACK_OPEN(
            0xC0,
            0x33,
            "FPDU.ACK(ORF)",
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PI_21_COMPRESSION, false),
            new ParameterRequirement(PI_74_ELEMS_SCELLEMENT, false),
            new ParameterRequirement(PI_76_ELEMS_CHIFFR, false)),

    ACK_WRITE(
            0xC0,
            0x36,
            "FPDU.ACK(WRITE)",
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PI_18_POINT_RELANCE, true)),

    ACK_READ(0xC0, 0x35, "FPDU.ACK(READ)", new ParameterRequirement(PI_02_DIAG, true)),

    ACK_CLOSE(0xC0, 0x34, "FPDU.ACK(CRF)", new ParameterRequirement(PI_02_DIAG, true)),

    ACK_TRANS_END(
            0xC0,
            0x37,
            "FPDU.ACK(TRANS.END)",
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PI_27_NB_OCTETS, false),
            new ParameterRequirement(PI_28_NB_ARTICLES, false),
            new ParameterRequirement(PI_81_ACCUSE_SIG, false)),

    ACK_DESELECT(
            0xC0,
            0x32,
            "FPDU.ACK(DESELECT)",
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PI_99_MESSAGE_LIBRE, false)),

    ACK_SYN(0xC0, 0x38, "FPDU.ACK(SYN)", new ParameterRequirement(PI_20_NUM_SYNC, true)),

    ACK_RESYN(0xC0, 0x39, "FPDU.ACK(RESYN)", new ParameterRequirement(PI_18_POINT_RELANCE, true)),

    ACK_IDT(0xC0, 0x3A, "FPDU.ACK(IDT)"),

    // Session-level FPDUs
    CONNECT(
            0x40,
            0x20,
            "FPDU.CONNECT",
            ACONNECT,
            new ParameterRequirement(PI_01_CRC, false),
            new ParameterRequirement(PI_03_DEMANDEUR, true),
            new ParameterRequirement(PI_04_SERVEUR, true),
            new ParameterRequirement(PI_05_CONTROLE_ACCES, false),
            new ParameterRequirement(PI_06_VERSION, true),
            new ParameterRequirement(PI_07_SYNC_POINTS, false),
            new ParameterRequirement(PI_22_TYPE_ACCES, true),
            new ParameterRequirement(PI_23_RESYNC, false),
            new ParameterRequirement(PI_99_MESSAGE_LIBRE, false)),

    RCONNECT(
            0x40,
            0x22,
            "FPDU.RCONNECT",
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PI_99_MESSAGE_LIBRE, false)),

    RELEASE(
            0x40,
            0x23,
            "FPDU.RELEASE",
            RELCONF,
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PI_99_MESSAGE_LIBRE, false)),

    ABORT(0x40, 0x25, "FPDU.ABORT", new ParameterRequirement(PI_02_DIAG, false)),

    // Message FPDUs - for sending messages without file transfer
    // MSG is used when message fits in single FPDU
    MSG(
            0xC0,
            0x16,
            "FPDU.MSG",
            ACK_MSG,
            new ParameterRequirement(PGI_09_ID_FICHIER, true),
            new ParameterRequirement(PI_13_ID_TRANSFERT, true),
            new ParameterRequirement(PI_14_ATTRIBUTS_DEMANDES, false),
            new ParameterRequirement(PI_16_CODE_DONNEES, false),
            new ParameterRequirement(PGI_50_ATTR_HISTORIQUES, false),
            new ParameterRequirement(PI_61_ID_CLIENT, false),
            new ParameterRequirement(PI_62_ID_BANQUE, false),
            new ParameterRequirement(PI_91_MESSAGE, false)),

    // MSGDM = Début de Message (start of segmented message)
    MSGDM(
            0xC0,
            0x17,
            "FPDU.MSGDM",
            new ParameterRequirement(PGI_09_ID_FICHIER, true),
            new ParameterRequirement(PI_13_ID_TRANSFERT, true),
            new ParameterRequirement(PI_14_ATTRIBUTS_DEMANDES, false),
            new ParameterRequirement(PI_16_CODE_DONNEES, false),
            new ParameterRequirement(PGI_50_ATTR_HISTORIQUES, false),
            new ParameterRequirement(PI_61_ID_CLIENT, false),
            new ParameterRequirement(PI_62_ID_BANQUE, false),
            new ParameterRequirement(PI_91_MESSAGE, false)),

    // MSGMM = Milieu de Message (middle segment of message)
    MSGMM(0xC0, 0x18, "FPDU.MSGMM", new ParameterRequirement(PI_91_MESSAGE, false)),

    // MSGFM = Fin de Message (end of segmented message) - triggers ACK_MSG
    MSGFM(0xC0, 0x19, "FPDU.MSGFM", ACK_MSG, new ParameterRequirement(PI_91_MESSAGE, false)),

    // File-level FPDUs
    SELECT(
            0xC0,
            0x12,
            "FPDU.SELECT",
            ACK_SELECT,
            new ParameterRequirement(PGI_09_ID_FICHIER, true),
            new ParameterRequirement(PI_13_ID_TRANSFERT, true),
            new ParameterRequirement(PI_14_ATTRIBUTS_DEMANDES, false),
            new ParameterRequirement(PI_15_TRANSFERT_RELANCE, false),
            new ParameterRequirement(PI_17_PRIORITE, true),
            new ParameterRequirement(PI_25_TAILLE_MAX_ENTITE, true),
            new ParameterRequirement(PI_61_ID_CLIENT, false),
            new ParameterRequirement(PI_62_ID_BANQUE, false),
            new ParameterRequirement(PI_63_ACCES_FICHIER, false),
            new ParameterRequirement(PI_71_TYPE_AUTH, false),
            new ParameterRequirement(PI_72_ELEMS_AUTH, false),
            new ParameterRequirement(PI_73_TYPE_SCELLEMENT, false),
            new ParameterRequirement(PI_75_TYPE_CHIFFR, false),
            new ParameterRequirement(PI_77_TYPE_SIG, false),
            new ParameterRequirement(PI_80_ACCREDITATION, false),
            new ParameterRequirement(PI_99_MESSAGE_LIBRE, false)),

    CREATE(
            0xC0,
            0x11,
            "FPDU.CREATE",
            ACK_CREATE,
            new ParameterRequirement(PGI_09_ID_FICHIER, true),
            new ParameterRequirement(PI_13_ID_TRANSFERT, true),
            new ParameterRequirement(PI_17_PRIORITE, true),
            new ParameterRequirement(PI_25_TAILLE_MAX_ENTITE, true),
            new ParameterRequirement(PGI_30_ATTR_LOGIQUES, true),
            new ParameterRequirement(PGI_40_ATTR_PHYSIQUES, true),
            new ParameterRequirement(PGI_50_ATTR_HISTORIQUES, true),
            new ParameterRequirement(PI_61_ID_CLIENT, false),
            new ParameterRequirement(PI_62_ID_BANQUE, false),
            new ParameterRequirement(PI_63_ACCES_FICHIER, false),
            new ParameterRequirement(PI_71_TYPE_AUTH, false),
            new ParameterRequirement(PI_72_ELEMS_AUTH, false),
            new ParameterRequirement(PI_73_TYPE_SCELLEMENT, false),
            new ParameterRequirement(PI_75_TYPE_CHIFFR, false),
            new ParameterRequirement(PI_77_TYPE_SIG, false),
            new ParameterRequirement(PI_80_ACCREDITATION, false),
            new ParameterRequirement(PI_99_MESSAGE_LIBRE, false)),

    OPEN(
            0xC0,
            0x14,
            "FPDU.ORF",
            ACK_OPEN,
            new ParameterRequirement(PI_21_COMPRESSION, false),
            new ParameterRequirement(PI_72_ELEMS_AUTH, false),
            new ParameterRequirement(PI_74_ELEMS_SCELLEMENT, false),
            new ParameterRequirement(PI_76_ELEMS_CHIFFR, false),
            new ParameterRequirement(PI_80_ACCREDITATION, false),
            new ParameterRequirement(PI_83_ACCRED_2, false)),

    CLOSE(0xC0, 0x15, "FPDU.CRF", ACK_CLOSE, new ParameterRequirement(PI_02_DIAG, true)),

    TRANS_END(
            0xC0,
            0x08,
            "FPDU.TRANS.END",
            ACK_TRANS_END,
            new ParameterRequirement(PI_27_NB_OCTETS, false),
            new ParameterRequirement(PI_28_NB_ARTICLES, false),
            new ParameterRequirement(PI_81_ACCUSE_SIG, false)),

    DESELECT(
            0xC0,
            0x13,
            "FPDU.DESELECT",
            ACK_DESELECT,
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PI_99_MESSAGE_LIBRE, false)),

    WRITE(0xC0, 0x02, "FPDU.WRITE", ACK_WRITE),

    READ(0xC0, 0x01, "FPDU.READ", ACK_READ, new ParameterRequirement(PI_18_POINT_RELANCE, true)),

    // Data transfer FPDUs (no ACK)
    DTF(0x00, 0x00, "FPDU.DTF"),

    DTFDA(0x00, 0x41, "FPDU.DTFDA"),

    DTFMA(0x00, 0x40, "FPDU.DTFMA"),

    DTFFA(0x00, 0x42, "FPDU.DTFFA"),

    DTF_END(
            0xC0,
            0x04,
            "FPDU.DTF.END",
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PI_78_SCEAU, false),
            new ParameterRequirement(PI_79_SIGNATURE, false)),

    SYN(
            0xC0,
            0x03,
            "FPDU.SYN",
            ACK_SYN,
            new ParameterRequirement(PI_20_NUM_SYNC, true),
            new ParameterRequirement(PI_78_SCEAU, false)),

    RESYN(
            0xC0,
            0x05,
            "FPDU.RESYN",
            ACK_RESYN,
            new ParameterRequirement(PI_02_DIAG, true),
            new ParameterRequirement(PI_18_POINT_RELANCE, true)),

    IDT(
            0xC0,
            0x06,
            "FPDU.IDT",
            ACK_IDT,
            new ParameterRequirement(PI_02_DIAG, false),
            new ParameterRequirement(PI_19_CODE_FIN_TRANSFERT, false));

    private final int phase;
    private final int type;
    private final String name;
    private final FpduType expectedAck;
    private final ParameterRequirement[] parameterRequirements;
    private final Map<Integer, Boolean> parameterRequirementMap = new TreeMap<>();

    FpduType(int phase, int type, String name, ParameterRequirement... parameterRequirements) {
        this(phase, type, name, null, parameterRequirements);
    }

    FpduType(
            int phase,
            int type,
            String name,
            FpduType expectedAck,
            ParameterRequirement... parameterRequirements) {
        this.phase = phase;
        this.type = type;
        this.name = name;
        this.expectedAck = expectedAck;
        this.parameterRequirements =
                parameterRequirements != null ? parameterRequirements : new ParameterRequirement[0];
        for (ParameterRequirement req : this.parameterRequirements) {
            parameterRequirementMap.put(req.getParameter().getId(), req.isMandatory());
            if (req.getParameter() instanceof ParameterGroupIdentifier) {
                for (ParameterRequirement pgiReq :
                        ((ParameterGroupIdentifier) req.getParameter()).getContainedPIs()) {
                    parameterRequirementMap.put(
                            pgiReq.getParameter().getId(), pgiReq.isMandatory());
                }
            }
        }
    }

    public int getPhase() {
        return phase;
    }

    public int getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public static FpduType from(int phase, int type) {
        for (FpduType fpdu : values()) {
            if (fpdu.phase == phase && fpdu.type == type) {
                return fpdu;
            }
        }
        return null;
    }

    public boolean isSessionLevel() {
        return phase == 0x40;
    }

    public boolean isFileLevel() {
        return phase == 0xC0;
    }

    /** Check if this FPDU expects an ACK */
    public boolean expectsAck() {
        return expectedAck != null;
    }

    /** Get expected ACK type */
    public FpduType getExpectedAck() {
        return expectedAck;
    }

    /** Check if response matches expected ACK */
    public boolean isExpectedAck(int responsePhase, int responseType) {
        if (!expectsAck()) {
            return false;
        }
        return responsePhase == expectedAck.phase && responseType == expectedAck.type;
    }

    /** Get parameter requirements for this FPDU */
    public ParameterRequirement[] getParameterRequirements() {
        return parameterRequirements;
    }

    /** Check if a PI is supported by this FPDU */
    public boolean supportsParameter(Parameter p) {
        return parameterRequirementMap.containsKey(p.getId());
    }

    /** Check if a PI is mandatory for this FPDU */
    public boolean requiresParameter(Parameter p) {
        return parameterRequirementMap.getOrDefault(p.getId(), false);
    }
}
