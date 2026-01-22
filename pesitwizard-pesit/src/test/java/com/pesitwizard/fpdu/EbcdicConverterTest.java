package com.pesitwizard.fpdu;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for EbcdicConverter.
 */
@DisplayName("EbcdicConverter Tests")
class EbcdicConverterTest {

    @Nested
    @DisplayName("isEbcdic Detection")
    class IsEbcdicTests {

        @Test
        @DisplayName("should return false for null data")
        void shouldReturnFalseForNullData() {
            assertThat(EbcdicConverter.isEbcdic(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for data shorter than 6 bytes")
        void shouldReturnFalseForShortData() {
            assertThat(EbcdicConverter.isEbcdic(new byte[] { 1, 2, 3 })).isFalse();
        }

        @Test
        @DisplayName("should return false for ASCII data")
        void shouldReturnFalseForAsciiData() {
            byte[] asciiData = "HELLO WORLD".getBytes();
            assertThat(EbcdicConverter.isEbcdic(asciiData)).isFalse();
        }

        @Test
        @DisplayName("should return true for EBCDIC data with high bytes")
        void shouldReturnTrueForEbcdicData() {
            // Data with many high bytes (>= 0x80) in header
            byte[] ebcdicData = new byte[] { (byte) 0xC1, (byte) 0xC2, (byte) 0xC3, (byte) 0xC4, (byte) 0xC5,
                    (byte) 0xC6 };
            assertThat(EbcdicConverter.isEbcdic(ebcdicData)).isTrue();
        }

        @Test
        @DisplayName("should return false when only few high bytes in header")
        void shouldReturnFalseWhenFewHighBytes() {
            byte[] mixedData = new byte[] { 0x01, 0x02, (byte) 0x80, 0x04, 0x05, 0x06 };
            assertThat(EbcdicConverter.isEbcdic(mixedData)).isFalse();
        }
    }

    @Nested
    @DisplayName("EBCDIC to ASCII Conversion")
    class EbcdicToAsciiTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            assertThat(EbcdicConverter.ebcdicToAscii(null)).isNull();
        }

        @Test
        @DisplayName("should convert EBCDIC bytes to ASCII")
        void shouldConvertEbcdicBytesToAscii() {
            // EBCDIC 'A' is 0xC1, 'B' is 0xC2, etc.
            byte[] ebcdic = new byte[] { (byte) 0xC1, (byte) 0xC2, (byte) 0xC3 };
            byte[] ascii = EbcdicConverter.ebcdicToAscii(ebcdic);

            assertThat(ascii).isNotNull();
            assertThat(ascii.length).isEqualTo(3);
        }

        @Test
        @DisplayName("should handle empty array")
        void shouldHandleEmptyArray() {
            byte[] result = EbcdicConverter.ebcdicToAscii(new byte[0]);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("ASCII to EBCDIC Conversion")
    class AsciiToEbcdicTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            assertThat(EbcdicConverter.asciiToEbcdic(null)).isNull();
        }

        @Test
        @DisplayName("should convert ASCII to EBCDIC and back")
        void shouldConvertAsciiToEbcdicAndBack() {
            byte[] original = "ABC".getBytes();
            byte[] ebcdic = EbcdicConverter.asciiToEbcdic(original);
            byte[] back = EbcdicConverter.ebcdicToAscii(ebcdic);

            assertThat(new String(back)).isEqualTo("ABC");
        }

        @Test
        @DisplayName("should handle empty array")
        void shouldHandleEmptyArray() {
            byte[] result = EbcdicConverter.asciiToEbcdic(new byte[0]);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("FPDU Conversion")
    class FpduConversionTests {

        @Test
        @DisplayName("should return data as-is for null")
        void shouldReturnDataAsIsForNull() {
            assertThat(EbcdicConverter.convertFpduFromEbcdic(null)).isNull();
        }

        @Test
        @DisplayName("should return data as-is for short data")
        void shouldReturnDataAsIsForShortData() {
            byte[] shortData = new byte[] { 1, 2, 3 };
            assertThat(EbcdicConverter.convertFpduFromEbcdic(shortData)).isEqualTo(shortData);
        }

        @Test
        @DisplayName("should convert entire FPDU from EBCDIC")
        void shouldConvertEntireFpduFromEbcdic() {
            byte[] ebcdicFpdu = new byte[10];
            for (int i = 0; i < 10; i++) {
                ebcdicFpdu[i] = (byte) (0xC1 + i); // EBCDIC letters
            }

            byte[] result = EbcdicConverter.convertFpduFromEbcdic(ebcdicFpdu);
            assertThat(result).hasSize(10);
        }

        @Test
        @DisplayName("should convert FPDU to EBCDIC")
        void shouldConvertFpduToEbcdic() {
            byte[] asciiFpdu = "ABCDEFGHIJ".getBytes();
            byte[] result = EbcdicConverter.convertFpduToEbcdic(asciiFpdu);

            assertThat(result).hasSize(10);
        }

        @Test
        @DisplayName("convertFpduToEbcdic should return null for null")
        void convertFpduToEbcdicShouldReturnNullForNull() {
            assertThat(EbcdicConverter.convertFpduToEbcdic(null)).isNull();
        }

        @Test
        @DisplayName("convertFpduToEbcdic should return data as-is for short data")
        void convertFpduToEbcdicShouldReturnDataAsIsForShortData() {
            byte[] shortData = new byte[] { 1, 2, 3 };
            assertThat(EbcdicConverter.convertFpduToEbcdic(shortData)).isEqualTo(shortData);
        }
    }

    @Nested
    @DisplayName("toAscii Auto-detection")
    class ToAsciiTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            assertThat(EbcdicConverter.toAscii(null)).isNull();
        }

        @Test
        @DisplayName("should return ASCII data unchanged")
        void shouldReturnAsciiDataUnchanged() {
            byte[] asciiData = "HELLO".getBytes();
            byte[] result = EbcdicConverter.toAscii(asciiData);
            assertThat(result).isEqualTo(asciiData);
        }

        @Test
        @DisplayName("should convert EBCDIC data to ASCII")
        void shouldConvertEbcdicDataToAscii() {
            // High bytes that will be detected as EBCDIC
            byte[] ebcdicData = new byte[] { (byte) 0xC1, (byte) 0xC2, (byte) 0xC3, (byte) 0xC4, (byte) 0xC5,
                    (byte) 0xC6 };
            byte[] result = EbcdicConverter.toAscii(ebcdicData);
            assertThat(result).isNotEqualTo(ebcdicData);
        }
    }

    @Nested
    @DisplayName("toClientEncoding")
    class ToClientEncodingTests {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            assertThat(EbcdicConverter.toClientEncoding(null, true)).isNull();
            assertThat(EbcdicConverter.toClientEncoding(null, false)).isNull();
        }

        @Test
        @DisplayName("should return ASCII unchanged when client uses ASCII")
        void shouldReturnAsciiUnchangedForAsciiClient() {
            byte[] asciiData = "TEST".getBytes();
            byte[] result = EbcdicConverter.toClientEncoding(asciiData, false);
            assertThat(result).isEqualTo(asciiData);
        }

        @Test
        @DisplayName("should convert to EBCDIC for mainframe client")
        void shouldConvertToEbcdicForMainframeClient() {
            // Need >= 6 bytes for FPDU conversion
            byte[] asciiData = "TESTDATA".getBytes();
            byte[] result = EbcdicConverter.toClientEncoding(asciiData, true);
            assertThat(result).isNotEqualTo(asciiData);
        }
    }

    @Nested
    @DisplayName("ebcdicToAsciiString")
    class EbcdicToAsciiStringTests {

        @Test
        @DisplayName("should return empty string for null data")
        void shouldReturnEmptyStringForNullData() {
            assertThat(EbcdicConverter.ebcdicToAsciiString(null, 0, 5)).isEmpty();
        }

        @Test
        @DisplayName("should return empty string for invalid offset")
        void shouldReturnEmptyStringForInvalidOffset() {
            byte[] data = new byte[] { 1, 2, 3 };
            assertThat(EbcdicConverter.ebcdicToAsciiString(data, -1, 2)).isEmpty();
            assertThat(EbcdicConverter.ebcdicToAsciiString(data, 5, 2)).isEmpty();
        }

        @Test
        @DisplayName("should convert subset to string")
        void shouldConvertSubsetToString() {
            byte[] ebcdicData = new byte[] { (byte) 0xC1, (byte) 0xC2, (byte) 0xC3, (byte) 0xC4, (byte) 0xC5 };
            String result = EbcdicConverter.ebcdicToAsciiString(ebcdicData, 0, 3);
            assertThat(result).hasSize(3);
        }
    }
}
