package com.pesitwizard.fpdu;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

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

    @Nested
    @DisplayName("isDtfType with FpduType")
    class IsDtfTypeEnumTests {

        @Test
        @DisplayName("should return true for DTF")
        void shouldReturnTrueForDtf() {
            assertThat(FpduIO.isDtfType(FpduType.DTF)).isTrue();
        }

        @Test
        @DisplayName("should return true for DTFDA")
        void shouldReturnTrueForDtfda() {
            assertThat(FpduIO.isDtfType(FpduType.DTFDA)).isTrue();
        }

        @Test
        @DisplayName("should return true for DTFMA")
        void shouldReturnTrueForDtfma() {
            assertThat(FpduIO.isDtfType(FpduType.DTFMA)).isTrue();
        }

        @Test
        @DisplayName("should return true for DTFFA")
        void shouldReturnTrueForDtffa() {
            assertThat(FpduIO.isDtfType(FpduType.DTFFA)).isTrue();
        }

        @Test
        @DisplayName("should return false for CONNECT")
        void shouldReturnFalseForConnect() {
            assertThat(FpduIO.isDtfType(FpduType.CONNECT)).isFalse();
        }

        @Test
        @DisplayName("should return false for DTF_END")
        void shouldReturnFalseForDtfEnd() {
            assertThat(FpduIO.isDtfType(FpduType.DTF_END)).isFalse();
        }
    }

    @Nested
    @DisplayName("bytesToHex")
    class BytesToHexTests {

        @Test
        @DisplayName("should return empty string for null")
        void shouldReturnEmptyForNull() {
            assertThat(FpduIO.bytesToHex(null)).isEmpty();
        }

        @Test
        @DisplayName("should return empty string for empty array")
        void shouldReturnEmptyForEmptyArray() {
            assertThat(FpduIO.bytesToHex(new byte[0])).isEmpty();
        }

        @Test
        @DisplayName("should convert single byte")
        void shouldConvertSingleByte() {
            assertThat(FpduIO.bytesToHex(new byte[] { (byte) 0xAB })).isEqualTo("AB");
        }

        @Test
        @DisplayName("should convert multiple bytes")
        void shouldConvertMultipleBytes() {
            assertThat(FpduIO.bytesToHex(new byte[] { 0x01, 0x23, (byte) 0xAB, (byte) 0xCD }))
                    .isEqualTo("0123ABCD");
        }

        @Test
        @DisplayName("should pad single digit with zero")
        void shouldPadSingleDigitWithZero() {
            assertThat(FpduIO.bytesToHex(new byte[] { 0x0F })).isEqualTo("0F");
        }
    }

    @Nested
    @DisplayName("Multi-Article DTF Support")
    class MultiArticleTests {

        @Test
        @DisplayName("isMultiArticleDtf should return false for null FPDU")
        void isMultiArticleDtf_shouldReturnFalseForNull() {
            assertThat(FpduIO.isMultiArticleDtf(null)).isFalse();
        }

        @Test
        @DisplayName("isMultiArticleDtf should return false for non-DTF type")
        void isMultiArticleDtf_shouldReturnFalseForNonDtf() {
            Fpdu fpdu = new Fpdu(FpduType.DTFDA).withIdSrc(3);
            assertThat(FpduIO.isMultiArticleDtf(fpdu)).isFalse();
        }

        @Test
        @DisplayName("isMultiArticleDtf should return false for single-article DTF (idSrc=1)")
        void isMultiArticleDtf_shouldReturnFalseForSingleArticle() {
            Fpdu fpdu = new Fpdu(FpduType.DTF).withIdSrc(1);
            assertThat(FpduIO.isMultiArticleDtf(fpdu)).isFalse();
        }

        @Test
        @DisplayName("isMultiArticleDtf should return true for multi-article DTF (idSrc>1)")
        void isMultiArticleDtf_shouldReturnTrueForMultiArticle() {
            Fpdu fpdu = new Fpdu(FpduType.DTF).withIdSrc(3);
            assertThat(FpduIO.isMultiArticleDtf(fpdu)).isTrue();
        }

        @Test
        @DisplayName("extractArticles should return empty list for null data")
        void extractArticles_shouldReturnEmptyForNull() {
            assertThat(FpduIO.extractArticles(null)).isEmpty();
        }

        @Test
        @DisplayName("extractArticles should return empty list for short data")
        void extractArticles_shouldReturnEmptyForShortData() {
            assertThat(FpduIO.extractArticles(new byte[] { 1 })).isEmpty();
        }

        @Test
        @DisplayName("extractArticles should extract single article")
        void extractArticles_shouldExtractSingleArticle() {
            // Multi-article format: [len(2)][article_data]
            // Article length: 3, Article data: "ABC"
            byte[] data = new byte[] { 0, 3, 'A', 'B', 'C' };
            List<byte[]> articles = FpduIO.extractArticles(data);

            assertThat(articles).hasSize(1);
            assertThat(articles.get(0)).containsExactly('A', 'B', 'C');
        }

        @Test
        @DisplayName("extractArticles should extract multiple articles")
        void extractArticles_shouldExtractMultipleArticles() {
            // Two articles: "ABC" (len=3) and "DE" (len=2)
            byte[] data = new byte[] { 0, 3, 'A', 'B', 'C', 0, 2, 'D', 'E' };
            List<byte[]> articles = FpduIO.extractArticles(data);

            assertThat(articles).hasSize(2);
            assertThat(articles.get(0)).containsExactly('A', 'B', 'C');
            assertThat(articles.get(1)).containsExactly('D', 'E');
        }

        @Test
        @DisplayName("extractArticles should stop on invalid article length")
        void extractArticles_shouldStopOnInvalidLength() {
            // First article is valid, second has invalid length
            byte[] data = new byte[] { 0, 2, 'A', 'B', 0, 99 }; // 99 bytes not available
            List<byte[]> articles = FpduIO.extractArticles(data);

            assertThat(articles).hasSize(1);
            assertThat(articles.get(0)).containsExactly('A', 'B');
        }

        @Test
        @DisplayName("extractArticles should stop on zero length")
        void extractArticles_shouldStopOnZeroLength() {
            byte[] data = new byte[] { 0, 2, 'A', 'B', 0, 0, 'X', 'Y' };
            List<byte[]> articles = FpduIO.extractArticles(data);

            assertThat(articles).hasSize(1);
            assertThat(articles.get(0)).containsExactly('A', 'B');
        }

        @Test
        @DisplayName("extractArticlesToStream should write articles to stream")
        void extractArticlesToStream_shouldWriteToStream() throws IOException {
            byte[] data = new byte[] { 0, 3, 'A', 'B', 'C', 0, 2, 'D', 'E' };
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            long bytesWritten = FpduIO.extractArticlesToStream(data, out);

            assertThat(bytesWritten).isEqualTo(5); // 3 + 2
            assertThat(out.toByteArray()).containsExactly('A', 'B', 'C', 'D', 'E');
        }

        @Test
        @DisplayName("extractArticlesToStream should return 0 for null data")
        void extractArticlesToStream_shouldReturn0ForNull() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertThat(FpduIO.extractArticlesToStream(null, out)).isEqualTo(0);
        }

        @Test
        @DisplayName("extractArticlesToStream should return 0 for null output")
        void extractArticlesToStream_shouldReturn0ForNullOutput() throws IOException {
            byte[] data = new byte[] { 0, 2, 'A', 'B' };
            assertThat(FpduIO.extractArticlesToStream(data, null)).isEqualTo(0);
        }

        @Test
        @DisplayName("processDtfData should return raw data for single-article DTF")
        void processDtfData_shouldReturnRawDataForSingleArticle() {
            byte[] rawData = new byte[] { 'A', 'B', 'C' };
            Fpdu fpdu = new Fpdu(FpduType.DTF).withIdSrc(1).withData(rawData);

            byte[] result = FpduIO.processDtfData(fpdu);

            assertThat(result).containsExactly('A', 'B', 'C');
        }

        @Test
        @DisplayName("processDtfData should extract articles for multi-article DTF")
        void processDtfData_shouldExtractArticlesForMultiArticle() {
            byte[] multiArticleData = new byte[] { 0, 2, 'A', 'B', 0, 3, 'C', 'D', 'E' };
            Fpdu fpdu = new Fpdu(FpduType.DTF).withIdSrc(2).withData(multiArticleData);

            byte[] result = FpduIO.processDtfData(fpdu);

            assertThat(result).containsExactly('A', 'B', 'C', 'D', 'E');
        }

        @Test
        @DisplayName("processDtfData should return empty array for FPDU with no data")
        void processDtfData_shouldReturnEmptyForNoData() {
            Fpdu fpdu = new Fpdu(FpduType.DTF).withIdSrc(1);

            byte[] result = FpduIO.processDtfData(fpdu);

            assertThat(result).isEmpty();
        }
    }
}
