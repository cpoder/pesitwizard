package com.pesitwizard.fpdu;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FpduIO.
 */
@DisplayName("FpduIO Tests")
class FpduIOTest {

    @Nested
    @DisplayName("isDtf Detection")
    class IsDtfTests {

        @Test
        @DisplayName("should return false for null data")
        void shouldReturnFalseForNullData() {
            assertThat(FpduIO.isDtf(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for short data")
        void shouldReturnFalseForShortData() {
            assertThat(FpduIO.isDtf(new byte[] { 1, 2, 3 })).isFalse();
        }

        @Test
        @DisplayName("should return true for DTF type 0x00")
        void shouldReturnTrueForDtfType00() {
            byte[] data = new byte[] { 0, 0, 0x00, 0x00, 1, 1 };
            assertThat(FpduIO.isDtf(data)).isTrue();
        }

        @Test
        @DisplayName("should return true for DTF type 0x40")
        void shouldReturnTrueForDtfType40() {
            byte[] data = new byte[] { 0, 0, 0x00, 0x40, 1, 1 };
            assertThat(FpduIO.isDtf(data)).isTrue();
        }

        @Test
        @DisplayName("should return true for DTF type 0x41")
        void shouldReturnTrueForDtfType41() {
            byte[] data = new byte[] { 0, 0, 0x00, 0x41, 1, 1 };
            assertThat(FpduIO.isDtf(data)).isTrue();
        }

        @Test
        @DisplayName("should return true for DTF type 0x42")
        void shouldReturnTrueForDtfType42() {
            byte[] data = new byte[] { 0, 0, 0x00, 0x42, 1, 1 };
            assertThat(FpduIO.isDtf(data)).isTrue();
        }

        @Test
        @DisplayName("should return false for non-DTF phase")
        void shouldReturnFalseForNonDtfPhase() {
            byte[] data = new byte[] { 0, 0, 0x40, 0x00, 1, 1 };
            assertThat(FpduIO.isDtf(data)).isFalse();
        }

        @Test
        @DisplayName("should return false for non-DTF type")
        void shouldReturnFalseForNonDtfType() {
            byte[] data = new byte[] { 0, 0, 0x00, 0x10, 1, 1 };
            assertThat(FpduIO.isDtf(data)).isFalse();
        }
    }

    @Nested
    @DisplayName("isDtfEnd Detection")
    class IsDtfEndTests {

        @Test
        @DisplayName("should return false for null data")
        void shouldReturnFalseForNullData() {
            assertThat(FpduIO.isDtfEnd(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for short data")
        void shouldReturnFalseForShortData() {
            assertThat(FpduIO.isDtfEnd(new byte[] { 1, 2, 3 })).isFalse();
        }

        @Test
        @DisplayName("should return true for DTF.END")
        void shouldReturnTrueForDtfEnd() {
            byte[] data = new byte[] { 0, 0, (byte) 0xC0, 0x22, 1, 1 };
            assertThat(FpduIO.isDtfEnd(data)).isTrue();
        }

        @Test
        @DisplayName("should return false for non-DTF.END phase")
        void shouldReturnFalseForNonDtfEndPhase() {
            byte[] data = new byte[] { 0, 0, 0x00, 0x22, 1, 1 };
            assertThat(FpduIO.isDtfEnd(data)).isFalse();
        }

        @Test
        @DisplayName("should return false for non-DTF.END type")
        void shouldReturnFalseForNonDtfEndType() {
            byte[] data = new byte[] { 0, 0, (byte) 0xC0, 0x10, 1, 1 };
            assertThat(FpduIO.isDtfEnd(data)).isFalse();
        }
    }

    @Nested
    @DisplayName("extractDtfData")
    class ExtractDtfDataTests {

        @Test
        @DisplayName("should return empty array for null data")
        void shouldReturnEmptyArrayForNullData() {
            assertThat(FpduIO.extractDtfData(null)).isEmpty();
        }

        @Test
        @DisplayName("should return empty array for short data")
        void shouldReturnEmptyArrayForShortData() {
            assertThat(FpduIO.extractDtfData(new byte[] { 1, 2, 3, 4, 5, 6 })).isEmpty();
        }

        @Test
        @DisplayName("should extract data payload")
        void shouldExtractDataPayload() {
            byte[] rawData = new byte[] { 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5 };
            byte[] data = FpduIO.extractDtfData(rawData);
            assertThat(data).containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        @DisplayName("should handle exactly 7 byte data")
        void shouldHandleExactly7ByteData() {
            byte[] rawData = new byte[] { 0, 0, 0, 0, 0, 0, 42 };
            byte[] data = FpduIO.extractDtfData(rawData);
            assertThat(data).containsExactly(42);
        }
    }

    @Nested
    @DisplayName("getPhaseAndType")
    class GetPhaseAndTypeTests {

        @Test
        @DisplayName("should return null for null data")
        void shouldReturnNullForNullData() {
            assertThat(FpduIO.getPhaseAndType(null)).isNull();
        }

        @Test
        @DisplayName("should return null for short data")
        void shouldReturnNullForShortData() {
            assertThat(FpduIO.getPhaseAndType(new byte[] { 1, 2, 3 })).isNull();
        }

        @Test
        @DisplayName("should extract phase and type")
        void shouldExtractPhaseAndType() {
            byte[] data = new byte[] { 0, 0, 0x40, 0x21, 1, 1 };
            int[] result = FpduIO.getPhaseAndType(data);
            assertThat(result).containsExactly(0x40, 0x21);
        }

        @Test
        @DisplayName("should handle unsigned bytes")
        void shouldHandleUnsignedBytes() {
            byte[] data = new byte[] { 0, 0, (byte) 0xC0, (byte) 0xFF, 1, 1 };
            int[] result = FpduIO.getPhaseAndType(data);
            assertThat(result).containsExactly(0xC0, 0xFF);
        }
    }
}
