package com.pesitwizard.client.pesit;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ClientStateTest {

    // --- State codes ---

    @Test
    void reposState_hasCorrectCode() {
        assertThat(ClientState.CN01_REPOS.getCode()).isEqualTo("CN01");
    }

    @Test
    void errorState_hasCorrectCode() {
        assertThat(ClientState.ERROR.getCode()).isEqualTo("ERROR");
    }

    // --- Valid transitions from CN01_REPOS (idle) ---

    @Test
    void repos_canTransitionToConnectPending() {
        assertThat(ClientState.CN01_REPOS.canTransitionTo(ClientState.CN02A_CONNECT_PENDING))
                .isTrue();
    }

    @Test
    void repos_cannotTransitionToConnected() {
        assertThat(ClientState.CN01_REPOS.canTransitionTo(ClientState.CN03_CONNECTED)).isFalse();
    }

    @Test
    void repos_cannotTransitionToError() {
        // CN01_REPOS can only go to CN02A_CONNECT_PENDING
        assertThat(ClientState.CN01_REPOS.canTransitionTo(ClientState.ERROR)).isFalse();
    }

    // --- Connected state transitions ---

    @Test
    void connected_canCreateOrSelect() {
        assertThat(ClientState.CN03_CONNECTED.canTransitionTo(ClientState.SF01A_CREATE_PENDING))
                .isTrue();
        assertThat(ClientState.CN03_CONNECTED.canTransitionTo(ClientState.SF02A_SELECT_PENDING))
                .isTrue();
        assertThat(ClientState.CN03_CONNECTED.canTransitionTo(ClientState.CN04A_RELEASE_PENDING))
                .isTrue();
    }

    @Test
    void connected_cannotSendData() {
        assertThat(ClientState.CN03_CONNECTED.canTransitionTo(ClientState.TDE02A_SENDING_DATA))
                .isFalse();
    }

    // --- Send data flow ---

    @Test
    void sendingData_canSync() {
        assertThat(ClientState.TDE02A_SENDING_DATA.canTransitionTo(ClientState.TDE03_SYNC_PENDING))
                .isTrue();
    }

    @Test
    void sendingData_canEndData() {
        assertThat(ClientState.TDE02A_SENDING_DATA.canTransitionTo(ClientState.TDE07_DATA_END))
                .isTrue();
    }

    @Test
    void sendingData_canSelfTransition() {
        assertThat(ClientState.TDE02A_SENDING_DATA.canTransitionTo(ClientState.TDE02A_SENDING_DATA))
                .isTrue();
    }

    @Test
    void syncPending_returnsToSendingData() {
        assertThat(ClientState.TDE03_SYNC_PENDING.canTransitionTo(ClientState.TDE02A_SENDING_DATA))
                .isTrue();
    }

    // --- Receive data flow ---

    @Test
    void receivingData_canSync() {
        assertThat(ClientState.TDL02A_RECEIVING_DATA.canTransitionTo(ClientState.TDL03_SYNC_ACK))
                .isTrue();
    }

    @Test
    void receivingData_canEndData() {
        assertThat(ClientState.TDL02A_RECEIVING_DATA.canTransitionTo(ClientState.TDL07_DATA_END))
                .isTrue();
    }

    // --- Convenience methods ---

    @Test
    void canSendData_trulyOnlyForSending() {
        assertThat(ClientState.TDE02A_SENDING_DATA.canSendData()).isTrue();
        assertThat(ClientState.CN03_CONNECTED.canSendData()).isFalse();
        assertThat(ClientState.TDL02A_RECEIVING_DATA.canSendData()).isFalse();
    }

    @Test
    void canReceiveData_trulyOnlyForReceiving() {
        assertThat(ClientState.TDL02A_RECEIVING_DATA.canReceiveData()).isTrue();
        assertThat(ClientState.CN03_CONNECTED.canReceiveData()).isFalse();
        assertThat(ClientState.TDE02A_SENDING_DATA.canReceiveData()).isFalse();
    }

    @Test
    void isConnected_falseForReposAndError() {
        assertThat(ClientState.CN01_REPOS.isConnected()).isFalse();
        assertThat(ClientState.ERROR.isConnected()).isFalse();
    }

    @Test
    void isConnected_trueForOtherStates() {
        assertThat(ClientState.CN03_CONNECTED.isConnected()).isTrue();
        assertThat(ClientState.TDE02A_SENDING_DATA.isConnected()).isTrue();
        assertThat(ClientState.SF03_FILE_SELECTED.isConnected()).isTrue();
    }

    // --- Error transitions ---

    @Test
    void allStatesExceptRepos_canTransitionToError() {
        for (ClientState state : ClientState.values()) {
            if (state != ClientState.CN01_REPOS && state != ClientState.ERROR) {
                assertThat(state.canTransitionTo(ClientState.ERROR))
                        .as("State %s should be able to transition to ERROR", state)
                        .isTrue();
            }
        }
    }

    @Test
    void errorState_canOnlyReturnToRepos() {
        assertThat(ClientState.ERROR.canTransitionTo(ClientState.CN01_REPOS)).isTrue();
        assertThat(ClientState.ERROR.getValidNext()).hasSize(1);
    }

    // --- Full send flow ---

    @Test
    void fullSendFlow_allTransitionsValid() {
        assertThat(ClientState.CN01_REPOS.canTransitionTo(ClientState.CN02A_CONNECT_PENDING))
                .isTrue();
        assertThat(ClientState.CN02A_CONNECT_PENDING.canTransitionTo(ClientState.CN03_CONNECTED))
                .isTrue();
        assertThat(ClientState.CN03_CONNECTED.canTransitionTo(ClientState.SF01A_CREATE_PENDING))
                .isTrue();
        assertThat(ClientState.SF01A_CREATE_PENDING.canTransitionTo(ClientState.SF03_FILE_SELECTED))
                .isTrue();
        assertThat(ClientState.SF03_FILE_SELECTED.canTransitionTo(ClientState.OF01A_OPEN_PENDING))
                .isTrue();
        assertThat(ClientState.OF01A_OPEN_PENDING.canTransitionTo(ClientState.OF02_TRANSFER_READY))
                .isTrue();
        assertThat(
                        ClientState.OF02_TRANSFER_READY.canTransitionTo(
                                ClientState.TDE01A_WRITE_PENDING))
                .isTrue();
        assertThat(
                        ClientState.TDE01A_WRITE_PENDING.canTransitionTo(
                                ClientState.TDE02A_SENDING_DATA))
                .isTrue();
        assertThat(ClientState.TDE02A_SENDING_DATA.canTransitionTo(ClientState.TDE07_DATA_END))
                .isTrue();
        assertThat(ClientState.TDE07_DATA_END.canTransitionTo(ClientState.TDE08A_TRANS_END_PENDING))
                .isTrue();
        assertThat(
                        ClientState.TDE08A_TRANS_END_PENDING.canTransitionTo(
                                ClientState.OF02_TRANSFER_READY))
                .isTrue();
        assertThat(ClientState.OF02_TRANSFER_READY.canTransitionTo(ClientState.OF03A_CLOSE_PENDING))
                .isTrue();
        assertThat(ClientState.OF03A_CLOSE_PENDING.canTransitionTo(ClientState.SF03_FILE_SELECTED))
                .isTrue();
        assertThat(
                        ClientState.SF03_FILE_SELECTED.canTransitionTo(
                                ClientState.SF04A_DESELECT_PENDING))
                .isTrue();
        assertThat(ClientState.SF04A_DESELECT_PENDING.canTransitionTo(ClientState.CN03_CONNECTED))
                .isTrue();
        assertThat(ClientState.CN03_CONNECTED.canTransitionTo(ClientState.CN04A_RELEASE_PENDING))
                .isTrue();
        assertThat(ClientState.CN04A_RELEASE_PENDING.canTransitionTo(ClientState.CN01_REPOS))
                .isTrue();
    }

    // --- Full receive flow ---

    @Test
    void fullReceiveFlow_allTransitionsValid() {
        assertThat(ClientState.CN01_REPOS.canTransitionTo(ClientState.CN02A_CONNECT_PENDING))
                .isTrue();
        assertThat(ClientState.CN02A_CONNECT_PENDING.canTransitionTo(ClientState.CN03_CONNECTED))
                .isTrue();
        assertThat(ClientState.CN03_CONNECTED.canTransitionTo(ClientState.SF02A_SELECT_PENDING))
                .isTrue();
        assertThat(ClientState.SF02A_SELECT_PENDING.canTransitionTo(ClientState.SF03_FILE_SELECTED))
                .isTrue();
        assertThat(ClientState.SF03_FILE_SELECTED.canTransitionTo(ClientState.OF01A_OPEN_PENDING))
                .isTrue();
        assertThat(ClientState.OF01A_OPEN_PENDING.canTransitionTo(ClientState.OF02_TRANSFER_READY))
                .isTrue();
        assertThat(ClientState.OF02_TRANSFER_READY.canTransitionTo(ClientState.TDL01A_READ_PENDING))
                .isTrue();
        assertThat(
                        ClientState.TDL01A_READ_PENDING.canTransitionTo(
                                ClientState.TDL02A_RECEIVING_DATA))
                .isTrue();
    }
}
