package com.pesitwizard.fpdu;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ParameterParser.
 */
@DisplayName("ParameterParser Tests")
class ParameterParserTest {

    @Nested
    @DisplayName("parseNumeric")
    class ParseNumericTests {

        @Test
        @DisplayName("should return 0 for null")
        void shouldReturn0ForNull() {
            assertThat(ParameterParser.parseNumeric(null)).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 0 for empty array")
        void shouldReturn0ForEmptyArray() {
            assertThat(ParameterParser.parseNumeric(new byte[0])).isEqualTo(0);
        }

        @Test
        @DisplayName("should parse single byte")
        void shouldParseSingleByte() {
            assertThat(ParameterParser.parseNumeric(new byte[] { (byte) 0xFF })).isEqualTo(255);
        }

        @Test
        @DisplayName("should parse two bytes big-endian")
        void shouldParseTwoBytesBigEndian() {
            assertThat(ParameterParser.parseNumeric(new byte[] { 0x01, 0x00 })).isEqualTo(256);
            assertThat(ParameterParser.parseNumeric(new byte[] { 0x00, 0x64 })).isEqualTo(100);
        }

        @Test
        @DisplayName("should parse three bytes")
        void shouldParseThreeBytes() {
            assertThat(ParameterParser.parseNumeric(new byte[] { 0x01, 0x00, 0x00 })).isEqualTo(65536);
        }

        @Test
        @DisplayName("should handle unsigned bytes")
        void shouldHandleUnsignedBytes() {
            assertThat(ParameterParser.parseNumeric(new byte[] { (byte) 0xFF, (byte) 0xFF }))
                    .isEqualTo(65535);
        }
    }

    @Nested
    @DisplayName("parseNumericLong")
    class ParseNumericLongTests {

        @Test
        @DisplayName("should return 0 for null")
        void shouldReturn0ForNull() {
            assertThat(ParameterParser.parseNumericLong(null)).isEqualTo(0L);
        }

        @Test
        @DisplayName("should parse large values")
        void shouldParseLargeValues() {
            // 1GB in bytes
            byte[] bytes = new byte[] { 0x40, 0x00, 0x00, 0x00 };
            assertThat(ParameterParser.parseNumericLong(bytes)).isEqualTo(1073741824L);
        }
    }

    @Nested
    @DisplayName("parsePI07SyncInterval")
    class ParsePI07Tests {

        @Test
        @DisplayName("should return 0 if parameter not present")
        void shouldReturn0IfNotPresent() {
            Fpdu fpdu = new Fpdu(FpduType.ACONNECT);
            assertThat(ParameterParser.parsePI07SyncInterval(fpdu)).isEqualTo(0);
        }

        @Test
        @DisplayName("should parse sync interval from PI_07")
        void shouldParseSyncInterval() {
            Fpdu fpdu = new Fpdu(FpduType.ACONNECT)
                    .withParameter(new ParameterValue(PI_07_SYNC_POINTS, new byte[] { 0x00, 0x64 }));
            assertThat(ParameterParser.parsePI07SyncInterval(fpdu)).isEqualTo(100);
        }

        @Test
        @DisplayName("should return 0 if value too short")
        void shouldReturn0IfValueTooShort() {
            Fpdu fpdu = new Fpdu(FpduType.ACONNECT)
                    .withParameter(new ParameterValue(PI_07_SYNC_POINTS, new byte[] { 0x64 }));
            assertThat(ParameterParser.parsePI07SyncInterval(fpdu)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("parsePI18RestartPoint")
    class ParsePI18Tests {

        @Test
        @DisplayName("should return 0 if parameter not present")
        void shouldReturn0IfNotPresent() {
            Fpdu fpdu = new Fpdu(FpduType.READ);
            assertThat(ParameterParser.parsePI18RestartPoint(fpdu)).isEqualTo(0);
        }

        @Test
        @DisplayName("should parse restart point from PI_18")
        void shouldParseRestartPoint() {
            Fpdu fpdu = new Fpdu(FpduType.READ)
                    .withParameter(new ParameterValue(PI_18_POINT_RELANCE, new byte[] { 0x00, 0x05 }));
            assertThat(ParameterParser.parsePI18RestartPoint(fpdu)).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("parsePI20SyncNumber")
    class ParsePI20Tests {

        @Test
        @DisplayName("should parse sync number from PI_20")
        void shouldParseSyncNumber() {
            Fpdu fpdu = new Fpdu(FpduType.SYN)
                    .withParameter(new ParameterValue(PI_20_NUM_SYNC, new byte[] { 0x00, 0x0A }));
            assertThat(ParameterParser.parsePI20SyncNumber(fpdu)).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("parsePI25MaxEntitySize")
    class ParsePI25Tests {

        @Test
        @DisplayName("should parse max entity size from PI_25")
        void shouldParseMaxEntitySize() {
            Fpdu fpdu = new Fpdu(FpduType.ACK_CREATE)
                    .withParameter(new ParameterValue(PI_25_TAILLE_MAX_ENTITE, new byte[] { (byte) 0xFF, (byte) 0xFF }));
            assertThat(ParameterParser.parsePI25MaxEntitySize(fpdu)).isEqualTo(65535);
        }
    }

    @Nested
    @DisplayName("parsePI02DiagnosticCode")
    class ParsePI02Tests {

        @Test
        @DisplayName("should return null if parameter not present")
        void shouldReturnNullIfNotPresent() {
            Fpdu fpdu = new Fpdu(FpduType.ACONNECT);
            assertThat(ParameterParser.parsePI02DiagnosticCode(fpdu)).isNull();
        }

        @Test
        @DisplayName("should return diagnostic code")
        void shouldReturnDiagnosticCode() {
            Fpdu fpdu = new Fpdu(FpduType.ACONNECT)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x02, 0x01, 0x00 }));
            assertThat(ParameterParser.parsePI02DiagnosticCode(fpdu)).containsExactly(0x02, 0x01, 0x00);
        }
    }

    @Nested
    @DisplayName("hasError")
    class HasErrorTests {

        @Test
        @DisplayName("should return false if no diagnostic code")
        void shouldReturnFalseIfNoDiagCode() {
            Fpdu fpdu = new Fpdu(FpduType.ACONNECT);
            assertThat(ParameterParser.hasError(fpdu)).isFalse();
        }

        @Test
        @DisplayName("should return false for zero diagnostic code")
        void shouldReturnFalseForZeroDiagCode() {
            Fpdu fpdu = new Fpdu(FpduType.ACONNECT)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));
            assertThat(ParameterParser.hasError(fpdu)).isFalse();
        }

        @Test
        @DisplayName("should return true for non-zero diagnostic code")
        void shouldReturnTrueForNonZeroDiagCode() {
            Fpdu fpdu = new Fpdu(FpduType.ACONNECT)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x02, 0x01, 0x05 }));
            assertThat(ParameterParser.hasError(fpdu)).isTrue();
        }
    }
}
