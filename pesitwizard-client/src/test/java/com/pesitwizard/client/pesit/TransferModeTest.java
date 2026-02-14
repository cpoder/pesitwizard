package com.pesitwizard.client.pesit;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TransferModeTest {

    @Nested
    class SimpleMode {

        @Test
        void displayName() {
            assertThat(TransferMode.SIMPLE.getDisplayName()).isEqualTo("Simple");
        }

        @Test
        void description() {
            assertThat(TransferMode.SIMPLE.getDescription()).isNotEmpty();
        }

        @Test
        void syncInterval_smallFile_zero() {
            assertThat(TransferMode.SIMPLE.getDefaultSyncInterval(500_000)).isEqualTo(0);
        }

        @Test
        void syncInterval_mediumFile_256k() {
            assertThat(TransferMode.SIMPLE.getDefaultSyncInterval(5 * 1024 * 1024))
                    .isEqualTo(256 * 1024);
        }

        @Test
        void syncInterval_largeFile_1m() {
            assertThat(TransferMode.SIMPLE.getDefaultSyncInterval(50 * 1024 * 1024))
                    .isEqualTo(1024 * 1024);
        }

        @Test
        void syncInterval_veryLargeFile_5m() {
            assertThat(TransferMode.SIMPLE.getDefaultSyncInterval(200 * 1024 * 1024))
                    .isEqualTo(5 * 1024 * 1024);
        }

        @Test
        void defaultRecordLength_zero() {
            assertThat(TransferMode.SIMPLE.getDefaultRecordLength()).isEqualTo(0);
        }

        @Test
        void defaultEntitySize_65535() {
            assertThat(TransferMode.SIMPLE.getDefaultEntitySize()).isEqualTo(65535);
        }

        @Test
        void syncPointsEnabled_smallFile_false() {
            assertThat(TransferMode.SIMPLE.isSyncPointsEnabledByDefault(500_000)).isFalse();
        }

        @Test
        void syncPointsEnabled_largeFile_true() {
            assertThat(TransferMode.SIMPLE.isSyncPointsEnabledByDefault(2 * 1024 * 1024)).isTrue();
        }

        @Test
        void resync_disabled() {
            assertThat(TransferMode.SIMPLE.isResyncEnabledByDefault()).isFalse();
        }

        @Test
        void compression_disabled() {
            assertThat(TransferMode.SIMPLE.isCompressionEnabledByDefault()).isFalse();
        }
    }

    @Nested
    class AdvancedMode {

        @Test
        void displayName() {
            assertThat(TransferMode.ADVANCED.getDisplayName()).isEqualTo("Advanced");
        }

        @Test
        void syncInterval_alwaysZero() {
            assertThat(TransferMode.ADVANCED.getDefaultSyncInterval(100 * 1024 * 1024))
                    .isEqualTo(0);
        }

        @Test
        void defaultRecordLength_zero() {
            assertThat(TransferMode.ADVANCED.getDefaultRecordLength()).isEqualTo(0);
        }

        @Test
        void defaultEntitySize_zero() {
            assertThat(TransferMode.ADVANCED.getDefaultEntitySize()).isEqualTo(0);
        }

        @Test
        void syncPointsEnabled_false() {
            assertThat(TransferMode.ADVANCED.isSyncPointsEnabledByDefault(10 * 1024 * 1024))
                    .isFalse();
        }
    }
}
