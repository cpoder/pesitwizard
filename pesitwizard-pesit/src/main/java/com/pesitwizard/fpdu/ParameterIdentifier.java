package com.pesitwizard.fpdu;

import lombok.Getter;

/** PESIT Protocol Parameter Information (PI) Based on PESIT E specification */
@Getter
public enum ParameterIdentifier implements Parameter {
    // PI identifiers from PESIT E specification
    PI_01_CRC(0x01, ParameterValueType.S, 1, "CRC Usage"),
    PI_02_DIAG(0x02, ParameterValueType.A, 3, "Diagnostic"),
    PI_03_DEMANDEUR(0x03, ParameterValueType.C, 24, "Requestor Identification"),
    PI_04_SERVEUR(0x04, ParameterValueType.C, 24, "Server Identification"),
    PI_05_CONTROLE_ACCES(0x05, ParameterValueType.C, 16, "Access Control"),
    PI_06_VERSION(0x06, ParameterValueType.N, 2, "Version Number"),
    PI_07_SYNC_POINTS(0x07, ParameterValueType.A, 3, "Sync Points Option"),

    PI_11_TYPE_FICHIER(11, ParameterValueType.N, 2, "File Type"),
    PI_12_NOM_FICHIER(12, ParameterValueType.C, 24, "Filename"),
    PI_13_ID_TRANSFERT(13, ParameterValueType.N, 3, "Transfer Identifier"),
    PI_14_ATTRIBUTS_DEMANDES(14, ParameterValueType.M, 1, "Requested Attributes"),
    PI_15_TRANSFERT_RELANCE(15, ParameterValueType.S, 1, "Transfer Restarted"),
    PI_16_CODE_DONNEES(16, ParameterValueType.S, 1, "Data Code"),
    PI_17_PRIORITE(17, ParameterValueType.S, 1, "Transfer Priority"),
    PI_18_POINT_RELANCE(18, ParameterValueType.N, 3, "Restart Point"),
    PI_19_CODE_FIN_TRANSFERT(19, ParameterValueType.S, 1, "File Code"),

    PI_20_NUM_SYNC(20, ParameterValueType.N, 3, "Sync Point Number"),
    PI_21_COMPRESSION(21, ParameterValueType.A, 2, "Compression"),
    PI_22_TYPE_ACCES(22, ParameterValueType.S, 1, "Access Type"),
    PI_23_RESYNC(23, ParameterValueType.S, 1, "Resynchronization"),
    PI_25_TAILLE_MAX_ENTITE(25, ParameterValueType.N, 2, "Max Data Entity Size"),
    PI_26_TIMEOUT(26, ParameterValueType.N, 2, "Timeout"),
    PI_27_NB_OCTETS(27, ParameterValueType.N, 8, "Data Byte Count"),
    PI_28_NB_ARTICLES(28, ParameterValueType.N, 4, "Article Count"),
    PI_29_COMPLEMENT_DIAG(29, ParameterValueType.A, 254, "Diagnostic Complement"),

    PI_31_FORMAT_ARTICLE(31, ParameterValueType.M, 1, "Article Format"),
    PI_32_LONG_ARTICLE(32, ParameterValueType.N, 2, "Article Length"),
    PI_33_ORG_FICHIER(33, ParameterValueType.S, 1, "File Organization"),
    PI_34_SIGNATURE(34, ParameterValueType.N, 2, "Signature Handling"),
    PI_36_SCEAU_SIT(36, ParameterValueType.N, 64, "SIT Seal"),
    PI_37_LABEL_FICHIER(37, ParameterValueType.C, 80, "File Label"),
    PI_38_LONG_CLE(38, ParameterValueType.N, 2, "Key Length"),
    PI_39_DEPL_CLE(39, ParameterValueType.N, 2, "Key Offset in Record"),

    PI_41_UNITE_RESERVATION(41, ParameterValueType.S, 1, "Reservation Unit"),
    PI_42_MAX_RESERVATION(42, ParameterValueType.N, 4, "Max Space Reservation"),

    PI_51_DATE_CREATION(51, ParameterValueType.D, 12, "Creation Date/Time"),
    PI_52_DATE_EXTRACTION(52, ParameterValueType.D, 12, "Last Extraction Date/Time"),
    PI_61_ID_CLIENT(61, ParameterValueType.C, 24, "Client Identifier"),
    PI_62_ID_BANQUE(62, ParameterValueType.C, 24, "Bank Identifier"),
    PI_63_ACCES_FICHIER(63, ParameterValueType.C, 16, "File Access Control"),
    PI_64_DATE_SERVEUR(64, ParameterValueType.D, 12, "Server Date/Time"),

    PI_71_TYPE_AUTH(71, ParameterValueType.A, 3, "Authentication Type"),
    PI_72_ELEMS_AUTH(72, ParameterValueType.N, -1, "Authentication Elements"),
    PI_73_TYPE_SCELLEMENT(73, ParameterValueType.A, 4, "Sealing Type"),
    PI_74_ELEMS_SCELLEMENT(74, ParameterValueType.N, -1, "Sealing Elements"),
    PI_75_TYPE_CHIFFR(75, ParameterValueType.A, 4, "Encryption Type"),
    PI_76_ELEMS_CHIFFR(76, ParameterValueType.N, -1, "Encryption Elements"),
    PI_77_TYPE_SIG(77, ParameterValueType.A, 4, "Signature Type"),
    PI_78_SCEAU(78, ParameterValueType.N, 4, "Seal"),
    PI_79_SIGNATURE(79, ParameterValueType.N, 4, "Signature"),
    PI_80_ACCREDITATION(80, ParameterValueType.N, 168, "Accreditation"),
    PI_81_ACCUSE_SIG(81, ParameterValueType.N, 64, "Signature Receipt Acknowledgment"),
    PI_82_DEUXIEME_SIG(82, ParameterValueType.N, 64, "Deuture"),
    PI_83_ACCRED_2(83, ParameterValueType.N, 168, "Second Accreditation"),

    PI_91_MESSAGE(91, ParameterValueType.C, 4096, "Message"),
    PI_99_MESSAGE_LIBRE(99, ParameterValueType.C, 254, "Free Message");

    private final int id;
    private ParameterValueType type;
    private final int length; // -1 = variable length
    private final String name;

    ParameterIdentifier(int id, ParameterValueType type, int length, String name) {
        this.id = id;
        this.type = type;
        this.length = length;
        this.name = name;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public static ParameterIdentifier fromId(int id) {
        for (ParameterIdentifier pi : values()) {
            if (pi.id == id) {
                return pi;
            }
        }
        return null;
    }
}
