package com.pesitwizard.client.pesit;

import com.pesitwizard.client.event.TransferEventBus;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Contexte d'un transfert PeSIT encapsulant l'état et les événements.
 */
@Slf4j
public class TransferContext {

    @Getter
    private final String transferId;
    @Getter
    private ClientState state = ClientState.CN01_REPOS;
    @Getter
    private long bytesTransferred = 0;
    @Getter
    private long totalBytes = 0;
    @Getter
    private int lastSyncPoint = 0;

    private final TransferEventBus eventBus;
    private long lastProgressUpdate = 0;
    private static final long PROGRESS_INTERVAL_MS = 100;

    public TransferContext(String transferId, TransferEventBus eventBus) {
        this.transferId = transferId;
        this.eventBus = eventBus;
    }

    public TransferContext(String transferId, long totalBytes, TransferEventBus eventBus) {
        this.transferId = transferId;
        this.totalBytes = totalBytes;
        this.eventBus = eventBus;
    }

    public void transition(ClientState newState) {
        if (!state.canTransitionTo(newState)) {
            log.error("Invalid transition: {} -> {}", state.getCode(), newState.getCode());
            throw new IllegalStateException(state.getCode() + " -> " + newState.getCode());
        }
        ClientState prev = state;
        state = newState;
        log.debug("[{}] State: {} -> {}", transferId, prev.getCode(), newState.getCode());
        if (eventBus != null) {
            eventBus.stateChange(transferId, prev, newState);
        }
    }

    public void addBytes(long bytes) {
        bytesTransferred += bytes;
        long now = System.currentTimeMillis();
        if (eventBus != null && now - lastProgressUpdate >= PROGRESS_INTERVAL_MS) {
            eventBus.progress(transferId, bytesTransferred, totalBytes);
            lastProgressUpdate = now;
        }
    }

    public void syncPoint(int syncNum, long bytePos) {
        lastSyncPoint = syncNum;
        if (eventBus != null) {
            eventBus.syncPoint(transferId, syncNum, bytePos);
        }
    }

    public void error(String message, String diagCode) {
        state = ClientState.ERROR;
        if (eventBus != null) {
            eventBus.error(transferId, message, diagCode);
        }
    }

    public void completed() {
        if (eventBus != null) {
            eventBus.progress(transferId, bytesTransferred, totalBytes);
            eventBus.completed(transferId, bytesTransferred);
        }
    }

    public void cancelled() {
        if (eventBus != null) {
            eventBus.cancelled(transferId);
        }
    }

    // Transitions helpers
    public void connectSent() {
        transition(ClientState.CN02A_CONNECT_PENDING);
    }

    public void connectAck() {
        transition(ClientState.CN03_CONNECTED);
    }

    public void createSent() {
        transition(ClientState.SF01A_CREATE_PENDING);
    }

    public void createAck() {
        transition(ClientState.SF03_FILE_SELECTED);
    }

    public void selectSent() {
        transition(ClientState.SF02A_SELECT_PENDING);
    }

    public void selectAck() {
        transition(ClientState.SF03_FILE_SELECTED);
    }

    public void openSent() {
        transition(ClientState.OF01A_OPEN_PENDING);
    }

    public void openAck() {
        transition(ClientState.OF02_TRANSFER_READY);
    }

    public void writeSent() {
        transition(ClientState.TDE01A_WRITE_PENDING);
    }

    public void writeAck() {
        transition(ClientState.TDE02A_SENDING_DATA);
    }

    public void readSent() {
        transition(ClientState.TDL01A_READ_PENDING);
    }

    public void readAck() {
        transition(ClientState.TDL02A_RECEIVING_DATA);
    }

    public void syncSent() {
        transition(ClientState.TDE03_SYNC_PENDING);
    }

    public void syncAckSend() {
        transition(ClientState.TDE02A_SENDING_DATA);
    }

    public void dtfEndSent() {
        transition(ClientState.TDE07_DATA_END);
    }

    public void transEndSent() {
        transition(ClientState.TDE08A_TRANS_END_PENDING);
    }

    public void transEndAck() {
        transition(ClientState.OF02_TRANSFER_READY);
    }

    public void closeSent() {
        transition(ClientState.OF03A_CLOSE_PENDING);
    }

    public void closeAck() {
        transition(ClientState.SF03_FILE_SELECTED);
    }

    public void deselectSent() {
        transition(ClientState.SF04A_DESELECT_PENDING);
    }

    public void deselectAck() {
        transition(ClientState.CN03_CONNECTED);
    }

    public void releaseSent() {
        transition(ClientState.CN04A_RELEASE_PENDING);
    }

    public void releaseAck() {
        transition(ClientState.CN01_REPOS);
    }
}
