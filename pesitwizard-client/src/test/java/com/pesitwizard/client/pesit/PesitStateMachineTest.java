package com.pesitwizard.client.pesit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PesitStateMachine Tests")
class PesitStateMachineTest {

    private PesitStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new PesitStateMachine();
    }

    @Test
    @DisplayName("should start in CN01_REPOS")
    void shouldStartInRepos() {
        assertEquals(ClientState.CN01_REPOS, sm.getCurrentState());
    }

    @Test
    @DisplayName("should enforce full send cycle")
    void shouldEnforceFullSendCycle() {
        sm.transition(ClientState.CN02A_CONNECT_PENDING);
        sm.transition(ClientState.CN03_CONNECTED);
        sm.transition(ClientState.SF01A_CREATE_PENDING);
        sm.transition(ClientState.SF03_FILE_SELECTED);
        sm.transition(ClientState.OF01A_OPEN_PENDING);
        sm.transition(ClientState.OF02_TRANSFER_READY);
        sm.transition(ClientState.TDE01A_WRITE_PENDING);
        sm.transition(ClientState.TDE02A_SENDING_DATA);
        sm.transition(ClientState.TDE03_SYNC_PENDING);
        sm.transition(ClientState.TDE02A_SENDING_DATA);
        sm.transition(ClientState.TDE07_DATA_END);
        sm.transition(ClientState.TDE08A_TRANS_END_PENDING);
        sm.transition(ClientState.OF02_TRANSFER_READY);
        sm.transition(ClientState.OF03A_CLOSE_PENDING);
        sm.transition(ClientState.SF03_FILE_SELECTED);
        sm.transition(ClientState.SF04A_DESELECT_PENDING);
        sm.transition(ClientState.CN03_CONNECTED);
        sm.transition(ClientState.CN04A_RELEASE_PENDING);
        sm.transition(ClientState.CN01_REPOS);

        assertEquals(ClientState.CN01_REPOS, sm.getCurrentState());
    }

    @Test
    @DisplayName("should enforce full receive cycle")
    void shouldEnforceFullReceiveCycle() {
        sm.transition(ClientState.CN02A_CONNECT_PENDING);
        sm.transition(ClientState.CN03_CONNECTED);
        sm.transition(ClientState.SF02A_SELECT_PENDING);
        sm.transition(ClientState.SF03_FILE_SELECTED);
        sm.transition(ClientState.OF01A_OPEN_PENDING);
        sm.transition(ClientState.OF02_TRANSFER_READY);
        sm.transition(ClientState.TDL01A_READ_PENDING);
        sm.transition(ClientState.TDL02A_RECEIVING_DATA);
        sm.transition(ClientState.TDL03_SYNC_ACK);
        sm.transition(ClientState.TDL02A_RECEIVING_DATA);
        sm.transition(ClientState.TDL07_DATA_END);
        sm.transition(ClientState.TDL08A_TRANS_END_PENDING);
        sm.transition(ClientState.OF02_TRANSFER_READY);
        sm.transition(ClientState.OF03A_CLOSE_PENDING);
        sm.transition(ClientState.SF03_FILE_SELECTED);
        sm.transition(ClientState.SF04A_DESELECT_PENDING);
        sm.transition(ClientState.CN03_CONNECTED);
        sm.transition(ClientState.CN04A_RELEASE_PENDING);
        sm.transition(ClientState.CN01_REPOS);

        assertEquals(ClientState.CN01_REPOS, sm.getCurrentState());
    }

    @Test
    @DisplayName("should reject invalid transition")
    void shouldRejectInvalidTransition() {
        assertThrows(IllegalStateException.class,
                () -> sm.transition(ClientState.SF03_FILE_SELECTED));
    }

    @Test
    @DisplayName("should reset to REPOS")
    void shouldReset() {
        sm.transition(ClientState.CN02A_CONNECT_PENDING);
        sm.transition(ClientState.CN03_CONNECTED);
        sm.reset();
        assertEquals(ClientState.CN01_REPOS, sm.getCurrentState());
    }

    @Test
    @DisplayName("should set error state")
    void shouldSetErrorState() {
        sm.transition(ClientState.CN02A_CONNECT_PENDING);
        sm.error();
        assertEquals(ClientState.ERROR, sm.getCurrentState());
    }

    @Test
    @DisplayName("should report canSendData correctly")
    void shouldReportCanSendData() {
        assertFalse(sm.canSendData());
        sm.transition(ClientState.CN02A_CONNECT_PENDING);
        sm.transition(ClientState.CN03_CONNECTED);
        sm.transition(ClientState.SF01A_CREATE_PENDING);
        sm.transition(ClientState.SF03_FILE_SELECTED);
        sm.transition(ClientState.OF01A_OPEN_PENDING);
        sm.transition(ClientState.OF02_TRANSFER_READY);
        sm.transition(ClientState.TDE01A_WRITE_PENDING);
        sm.transition(ClientState.TDE02A_SENDING_DATA);
        assertTrue(sm.canSendData());
    }

    @Test
    @DisplayName("should report canReceiveData correctly")
    void shouldReportCanReceiveData() {
        assertFalse(sm.canReceiveData());
        sm.transition(ClientState.CN02A_CONNECT_PENDING);
        sm.transition(ClientState.CN03_CONNECTED);
        sm.transition(ClientState.SF02A_SELECT_PENDING);
        sm.transition(ClientState.SF03_FILE_SELECTED);
        sm.transition(ClientState.OF01A_OPEN_PENDING);
        sm.transition(ClientState.OF02_TRANSFER_READY);
        sm.transition(ClientState.TDL01A_READ_PENDING);
        sm.transition(ClientState.TDL02A_RECEIVING_DATA);
        assertTrue(sm.canReceiveData());
    }
}
