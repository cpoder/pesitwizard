package com.vectis.server.state;

/**
 * PeSIT Server State Machine States
 * Based on PeSIT E specification - Server (Serveur) role
 * 
 * States ending with 'B' are server-specific states
 * States without suffix are common to both demandeur and serveur
 */
public enum ServerState {

    // ===== PHASE CONNEXION (CN) =====

    /** CN01 - REPOS: Not connected (initial state) */
    CN01_REPOS("CN01", "REPOS - Non connecté"),

    /** CN02B - Waiting for F.CONNECT response primitive */
    CN02B_CONNECT_PENDING("CN02B", "Connexion en attente d'une primitive F.CONNECT,R"),

    /** CN03 - CONNECTED: Ready for file operations */
    CN03_CONNECTED("CN03", "CONNECTE - Prêt pour opérations fichier"),

    /** CN04B - Release pending, waiting for F.RELEASE response */
    CN04B_RELEASE_PENDING("CN04B", "Libération en attente d'une primitive F.RELEASE,R"),

    // ===== PHASE SELECTION DE FICHIER (SF) =====

    /** SF01B - File creation pending, waiting for F.CREATE response */
    SF01B_CREATE_PENDING("SF01B", "Création de fichier en attente d'une primitive F.CREATE,R"),

    /** SF02B - File selection pending, waiting for F.SELECT response */
    SF02B_SELECT_PENDING("SF02B", "Sélection de fichier en attente d'une primitive F.SELECT,R"),

    /** SF03 - FILE SELECTED: Ready for open */
    SF03_FILE_SELECTED("SF03", "FICHIER SELECTIONNE"),

    /** SF04B - Deselection pending, waiting for F.DESELECT response */
    SF04B_DESELECT_PENDING("SF04B", "Libération de fichier en attente d'une primitive F.DESELECT,R"),

    // ===== PHASE OUVERTURE DE FICHIER (OF) =====

    /** OF01B - File opening pending, waiting for F.OPEN response */
    OF01B_OPEN_PENDING("OF01B", "Ouverture de fichier en attente d'une primitive F.OPEN,R"),

    /** OF02 - TRANSFER READY: File open, ready for data transfer */
    OF02_TRANSFER_READY("OF02", "TRANSFERT DE DONNEES - REPOS"),

    /** OF03B - File closing pending, waiting for F.CLOSE response */
    OF03B_CLOSE_PENDING("OF03B", "Fermeture de fichier en attente d'une primitive F.CLOSE,R"),

    // ===== PHASE TRANSFERT DE DONNEES EN ECRITURE - Serveur Récepteur (TDE) =====

    /** TDE01B - Write launch pending, waiting for F.WRITE response */
    TDE01B_WRITE_PENDING("TDE01B", "Lancement d'écriture en attente d'une primitive F.WRITE,R"),

    /** TDE02B - Receiving data (main data reception state) */
    TDE02B_RECEIVING_DATA("TDE02B", "Réception de données"),

    /** TDE03 - Resync pending, waiting for FPDU.ACK(RESYN) */
    TDE03_RESYNC_PENDING("TDE03", "Resynchronisation en attente d'une FPDU.ACK(RESYN)"),

    /** TDE04 - Resync pending, waiting for F.RESTART response */
    TDE04_RESYNC_RESPONSE_PENDING("TDE04", "Resynchronisation en attente d'une primitive F.RESTART,R"),

    /** TDE05 - Interruption pending, waiting for FPDU.ACK(IDT) */
    TDE05_IDT_PENDING("TDE05", "Interruption du transfert en attente d'une FPDU.ACK(IDT)"),

    /** TDE06 - Interruption pending, waiting for F.CANCEL response */
    TDE06_CANCEL_PENDING("TDE06", "Interruption du transfert en attente d'une primitive F.CANCEL,R"),

    /** TDE07 - End of write */
    TDE07_WRITE_END("TDE07", "Fin d'écriture"),

    /** TDE08B - End of transfer pending, waiting for F.TRANSFER.END response */
    TDE08B_TRANS_END_PENDING("TDE08B", "Fin de transfert d'écriture en attente d'une primitive F.TRANSFER.END,R"),

    // ===== PHASE TRANSFERT DE DONNEES EN LECTURE - Serveur Emetteur (TDL) =====

    /** TDL01B - Read launch pending, waiting for F.READ response */
    TDL01B_READ_PENDING("TDL01B", "Lancement de lecture en attente d'une primitive F.READ,R"),

    /** TDL02B - Sending data (main data emission state) */
    TDL02B_SENDING_DATA("TDL02B", "Emission de données"),

    /** TDL07 - End of read */
    TDL07_READ_END("TDL07", "Fin de lecture"),

    /** TDL08B - End of transfer pending, waiting for F.TRANSFER.END response */
    TDL08B_TRANS_END_PENDING("TDL08B", "Fin de transfert de lecture en attente d'une primitive F.TRANSFER.END,R"),

    // ===== PHASE MESSAGE (MSG) =====

    /**
     * MSG_RECEIVING - Receiving segmented message (after MSGDM, waiting for
     * MSGMM/MSGFM)
     */
    MSG_RECEIVING("MSG", "Réception de message segmenté"),

    // ===== ERROR STATE =====

    /** Error state - connection aborted */
    ERROR("ERROR", "Erreur - connexion interrompue");

    private final String code;
    private final String description;

    ServerState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this state is in the connection phase
     */
    public boolean isConnectionPhase() {
        return this.name().startsWith("CN");
    }

    /**
     * Check if this state is in the file selection phase
     */
    public boolean isSelectionPhase() {
        return this.name().startsWith("SF");
    }

    /**
     * Check if this state is in the file open phase
     */
    public boolean isOpenPhase() {
        return this.name().startsWith("OF");
    }

    /**
     * Check if this state is in the data transfer phase
     */
    public boolean isTransferPhase() {
        return this.name().startsWith("TDE") || this.name().startsWith("TDL");
    }

    /**
     * Check if this state allows receiving data
     */
    public boolean canReceiveData() {
        return this == TDE02B_RECEIVING_DATA;
    }

    /**
     * Check if this state allows sending data
     */
    public boolean canSendData() {
        return this == TDL02B_SENDING_DATA;
    }

    @Override
    public String toString() {
        return code + " - " + description;
    }
}
