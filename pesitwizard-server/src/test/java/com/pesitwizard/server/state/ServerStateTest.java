package com.pesitwizard.server.state;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ServerState Tests")
class ServerStateTest {

    @Nested
    @DisplayName("Connection Phase Tests")
    class ConnectionPhaseTests {

        @Test
        @DisplayName("CN01_REPOS should be connection phase")
        void cn01ShouldBeConnectionPhase() {
            assertThat(ServerState.CN01_REPOS.isConnectionPhase()).isTrue();
            assertThat(ServerState.CN01_REPOS.getCode()).isEqualTo("CN01");
        }

        @Test
        @DisplayName("CN02B should be connection phase")
        void cn02bShouldBeConnectionPhase() {
            assertThat(ServerState.CN02B_CONNECT_PENDING.isConnectionPhase()).isTrue();
        }

        @Test
        @DisplayName("CN03 should be connection phase")
        void cn03ShouldBeConnectionPhase() {
            assertThat(ServerState.CN03_CONNECTED.isConnectionPhase()).isTrue();
        }

        @Test
        @DisplayName("CN04B should be connection phase")
        void cn04bShouldBeConnectionPhase() {
            assertThat(ServerState.CN04B_RELEASE_PENDING.isConnectionPhase()).isTrue();
        }
    }

    @Nested
    @DisplayName("Selection Phase Tests")
    class SelectionPhaseTests {

        @Test
        @DisplayName("SF01B should be selection phase")
        void sf01bShouldBeSelectionPhase() {
            assertThat(ServerState.SF01B_CREATE_PENDING.isSelectionPhase()).isTrue();
        }

        @Test
        @DisplayName("SF02B should be selection phase")
        void sf02bShouldBeSelectionPhase() {
            assertThat(ServerState.SF02B_SELECT_PENDING.isSelectionPhase()).isTrue();
        }

        @Test
        @DisplayName("SF03 should be selection phase")
        void sf03ShouldBeSelectionPhase() {
            assertThat(ServerState.SF03_FILE_SELECTED.isSelectionPhase()).isTrue();
        }

        @Test
        @DisplayName("CN states should not be selection phase")
        void cnShouldNotBeSelectionPhase() {
            assertThat(ServerState.CN01_REPOS.isSelectionPhase()).isFalse();
        }
    }

    @Nested
    @DisplayName("Open Phase Tests")
    class OpenPhaseTests {

        @Test
        @DisplayName("OF01B should be open phase")
        void of01bShouldBeOpenPhase() {
            assertThat(ServerState.OF01B_OPEN_PENDING.isOpenPhase()).isTrue();
        }

        @Test
        @DisplayName("OF02 should be open phase")
        void of02ShouldBeOpenPhase() {
            assertThat(ServerState.OF02_TRANSFER_READY.isOpenPhase()).isTrue();
        }

        @Test
        @DisplayName("OF03B should be open phase")
        void of03bShouldBeOpenPhase() {
            assertThat(ServerState.OF03B_CLOSE_PENDING.isOpenPhase()).isTrue();
        }
    }

    @Nested
    @DisplayName("Transfer Phase Tests")
    class TransferPhaseTests {

        @Test
        @DisplayName("TDE states should be transfer phase")
        void tdeStatesShouldBeTransferPhase() {
            assertThat(ServerState.TDE01B_WRITE_PENDING.isTransferPhase()).isTrue();
            assertThat(ServerState.TDE02B_RECEIVING_DATA.isTransferPhase()).isTrue();
            assertThat(ServerState.TDE03_RESYNC_PENDING.isTransferPhase()).isTrue();
        }

        @Test
        @DisplayName("TDL states should be transfer phase")
        void tdlStatesShouldBeTransferPhase() {
            assertThat(ServerState.TDL01B_READ_PENDING.isTransferPhase()).isTrue();
            assertThat(ServerState.TDL02B_SENDING_DATA.isTransferPhase()).isTrue();
        }

        @Test
        @DisplayName("CN states should not be transfer phase")
        void cnShouldNotBeTransferPhase() {
            assertThat(ServerState.CN01_REPOS.isTransferPhase()).isFalse();
        }
    }

    @Nested
    @DisplayName("Data Capability Tests")
    class DataCapabilityTests {

        @Test
        @DisplayName("TDE02B should allow receiving data")
        void tde02bShouldAllowReceivingData() {
            assertThat(ServerState.TDE02B_RECEIVING_DATA.canReceiveData()).isTrue();
        }

        @Test
        @DisplayName("Other states should not allow receiving data")
        void otherStatesShouldNotAllowReceivingData() {
            assertThat(ServerState.CN01_REPOS.canReceiveData()).isFalse();
            assertThat(ServerState.TDL02B_SENDING_DATA.canReceiveData()).isFalse();
        }

        @Test
        @DisplayName("TDL02B should allow sending data")
        void tdl02bShouldAllowSendingData() {
            assertThat(ServerState.TDL02B_SENDING_DATA.canSendData()).isTrue();
        }

        @Test
        @DisplayName("Other states should not allow sending data")
        void otherStatesShouldNotAllowSendingData() {
            assertThat(ServerState.CN01_REPOS.canSendData()).isFalse();
            assertThat(ServerState.TDE02B_RECEIVING_DATA.canSendData()).isFalse();
        }
    }

    @Nested
    @DisplayName("Enum Properties Tests")
    class EnumPropertiesTests {

        @Test
        @DisplayName("Should have correct code and description")
        void shouldHaveCorrectCodeAndDescription() {
            ServerState state = ServerState.CN01_REPOS;
            assertThat(state.getCode()).isEqualTo("CN01");
            assertThat(state.getDescription()).isEqualTo("REPOS - Non connecté");
        }

        @Test
        @DisplayName("toString should include code and description")
        void toStringShouldIncludeCodeAndDescription() {
            String str = ServerState.CN01_REPOS.toString();
            assertThat(str).contains("CN01");
            assertThat(str).contains("REPOS");
        }

        @Test
        @DisplayName("Should have all expected values")
        void shouldHaveAllExpectedValues() {
            assertThat(ServerState.values()).hasSizeGreaterThan(15);
        }

        @Test
        @DisplayName("ERROR state should exist")
        void errorStateShouldExist() {
            assertThat(ServerState.ERROR).isNotNull();
            assertThat(ServerState.ERROR.getCode()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("MSG_RECEIVING state should exist")
        void msgReceivingStateShouldExist() {
            assertThat(ServerState.MSG_RECEIVING).isNotNull();
            assertThat(ServerState.MSG_RECEIVING.getCode()).isEqualTo("MSG");
        }
    }
}
