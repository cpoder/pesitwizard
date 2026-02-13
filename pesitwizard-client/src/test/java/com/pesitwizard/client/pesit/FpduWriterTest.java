package com.pesitwizard.client.pesit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.session.PesitSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FpduWriter Tests")
class FpduWriterTest {

    @Mock private PesitSession session;

    private static final int SERVER_CONNECTION_ID = 0x42;
    private static final int DEFAULT_MAX_ENTITY_SIZE = 4096;

    @Nested
    @DisplayName("Constructor and Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("should calculate correct max data per DTF")
        void shouldCalculateMaxDataPerDtf() {
            FpduWriter writer = new FpduWriter(session, SERVER_CONNECTION_ID, 1000);
            // Max data = maxEntitySize - 6 (FPDU header)
            assertEquals(994, writer.getMaxDataPerDtf());
        }

        @Test
        @DisplayName("should use default entity size when 0 provided")
        void shouldUseDefaultEntitySize() {
            FpduWriter writer = new FpduWriter(session, SERVER_CONNECTION_ID, 0);
            // Default is 4096, so max data = 4096 - 6 = 4090
            assertEquals(4090, writer.getMaxDataPerDtf());
        }

        @Test
        @DisplayName("should track total bytes sent")
        void shouldTrackTotalBytesSent() {
            FpduWriter writer =
                    new FpduWriter(session, SERVER_CONNECTION_ID, DEFAULT_MAX_ENTITY_SIZE);
            assertEquals(0, writer.getTotalBytesSent());
        }
    }

    @Nested
    @DisplayName("Single DTF Writing")
    class SingleDtfTests {

        @Test
        @DisplayName("should send small chunk as single DTF")
        void shouldSendSmallChunkAsSingleDtf() throws Exception {
            FpduWriter writer = new FpduWriter(session, SERVER_CONNECTION_ID, 1000);
            byte[] smallData = new byte[100];

            writer.writeDtf(smallData);

            verify(session, times(1)).sendFpduWithData(any(), eq(smallData));
            assertEquals(100, writer.getTotalBytesSent());
        }

        @Test
        @DisplayName("should send chunk exactly at max size as single DTF")
        void shouldSendExactMaxSizeAsSingleDtf() throws Exception {
            int maxEntitySize = 1000;
            int maxDataSize = maxEntitySize - 6; // 994
            FpduWriter writer = new FpduWriter(session, SERVER_CONNECTION_ID, maxEntitySize);
            byte[] exactData = new byte[maxDataSize];

            writer.writeDtf(exactData);

            verify(session, times(1)).sendFpduWithData(any(), eq(exactData));
            assertEquals(maxDataSize, writer.getTotalBytesSent());
        }

        @Test
        @DisplayName("should handle empty data gracefully")
        void shouldHandleEmptyData() throws Exception {
            FpduWriter writer =
                    new FpduWriter(session, SERVER_CONNECTION_ID, DEFAULT_MAX_ENTITY_SIZE);

            writer.writeDtf(new byte[0]);
            writer.writeDtf(null);

            verify(session, never()).sendFpduWithData(any(), any());
            assertEquals(0, writer.getTotalBytesSent());
        }
    }

    @Nested
    @DisplayName("Multi-DTF Chunking")
    class MultiDtfChunkingTests {

        @Test
        @DisplayName("should split large chunk into multiple DTFs")
        void shouldSplitLargeChunkIntoMultipleDtfs() throws Exception {
            int maxEntitySize = 100; // Small for testing
            int maxDataSize = maxEntitySize - 6; // 94 bytes max per DTF
            FpduWriter writer = new FpduWriter(session, SERVER_CONNECTION_ID, maxEntitySize);

            // Send 250 bytes - should result in 3 DTFs (94 + 94 + 62)
            byte[] largeData = new byte[250];
            writer.writeDtf(largeData);

            // Verify multiple sends occurred
            verify(session, times(3)).sendFpduWithData(any(), any());
            assertEquals(250, writer.getTotalBytesSent());
        }

        @Test
        @DisplayName("should correctly calculate chunk count for large data")
        void shouldCorrectlyCalculateChunkCount() throws Exception {
            int maxEntitySize = 1006; // Max data = 1000
            FpduWriter writer = new FpduWriter(session, SERVER_CONNECTION_ID, maxEntitySize);

            // Send exactly 3000 bytes - should be 3 DTFs of 1000 each
            byte[] data = new byte[3000];
            writer.writeDtf(data);

            verify(session, times(3)).sendFpduWithData(any(), any());
            assertEquals(3000, writer.getTotalBytesSent());
        }
    }

    @Nested
    @DisplayName("Stream Writing")
    class StreamWritingTests {

        @Test
        @DisplayName("should write from InputStream with callback")
        void shouldWriteFromInputStreamWithCallback() throws Exception {
            int maxEntitySize = 1006; // Max data = 1000
            FpduWriter writer = new FpduWriter(session, SERVER_CONNECTION_ID, maxEntitySize);

            byte[] testData = new byte[2500];
            ByteArrayInputStream inputStream = new ByteArrayInputStream(testData);

            AtomicInteger callbackCount = new AtomicInteger(0);
            FpduWriter.WriteCallback callback =
                    (chunkSize, totalSent) -> {
                        callbackCount.incrementAndGet();
                        assertTrue(chunkSize > 0);
                        assertTrue(totalSent > 0);
                    };

            long totalWritten = writer.writeFromStream(inputStream, callback);

            assertEquals(2500, totalWritten);
            assertTrue(callbackCount.get() >= 1);
        }

        @Test
        @DisplayName("should write from InputStream without callback")
        void shouldWriteFromInputStreamWithoutCallback() throws Exception {
            FpduWriter writer =
                    new FpduWriter(session, SERVER_CONNECTION_ID, DEFAULT_MAX_ENTITY_SIZE);

            byte[] testData = new byte[1000];
            ByteArrayInputStream inputStream = new ByteArrayInputStream(testData);

            long totalWritten = writer.writeFromStream(inputStream, null);

            assertEquals(1000, totalWritten);
            assertEquals(1000, writer.getTotalBytesSent());
        }
    }

    @Nested
    @DisplayName("Multi-Article DTF (DTFMA)")
    class MultiArticleTests {

        @Test
        @DisplayName("should batch small articles into single DTFMA")
        void shouldBatchSmallArticlesIntoSingleDtfma() throws Exception {
            int maxEntitySize = 1000;
            FpduWriter writer =
                    new FpduWriter(session, SERVER_CONNECTION_ID, maxEntitySize, 100, true);

            List<byte[]> articles = new ArrayList<>();
            // 5 articles of 50 bytes each = 5 * (2 + 50) = 260 bytes total
            for (int i = 0; i < 5; i++) {
                articles.add(new byte[50]);
            }

            writer.writeMultiArticle(articles);

            // Should send one DTFMA via sendRawFpdu
            verify(session, times(1)).sendRawFpdu(any());
            assertEquals(250, writer.getTotalBytesSent()); // 5 * 50 data bytes
        }

        @Test
        @DisplayName("should split articles across multiple DTFMAs when needed")
        void shouldSplitArticlesAcrossMultipleDtfmas() throws Exception {
            int maxEntitySize = 100; // Very small for testing
            FpduWriter writer =
                    new FpduWriter(session, SERVER_CONNECTION_ID, maxEntitySize, 30, true);

            List<byte[]> articles = new ArrayList<>();
            // Each article: 2 (prefix) + 30 (data) = 32 bytes
            // Max data per DTFMA = 94 bytes, so ~2 articles per DTFMA
            for (int i = 0; i < 6; i++) {
                articles.add(new byte[30]);
            }

            writer.writeMultiArticle(articles);

            // Should need multiple sends
            verify(session, atLeast(2)).sendRawFpdu(any());
        }

        @Test
        @DisplayName("should handle empty article list")
        void shouldHandleEmptyArticleList() throws Exception {
            FpduWriter writer =
                    new FpduWriter(
                            session, SERVER_CONNECTION_ID, DEFAULT_MAX_ENTITY_SIZE, 100, true);

            writer.writeMultiArticle(new ArrayList<>());
            writer.writeMultiArticle(null);

            verify(session, never()).sendRawFpdu(any());
            verify(session, never()).sendFpduWithData(any(), any());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should propagate IOException from session")
        void shouldPropagateIoException() throws Exception {
            doThrow(new IOException("Connection lost"))
                    .when(session)
                    .sendFpduWithData(any(), any());

            FpduWriter writer =
                    new FpduWriter(session, SERVER_CONNECTION_ID, DEFAULT_MAX_ENTITY_SIZE);

            assertThrows(IOException.class, () -> writer.writeDtf(new byte[100]));
        }

        @Test
        @DisplayName("should convert InterruptedException to IOException")
        void shouldConvertInterruptedException() throws Exception {
            doThrow(new InterruptedException("Cancelled"))
                    .when(session)
                    .sendFpduWithData(any(), any());

            FpduWriter writer =
                    new FpduWriter(session, SERVER_CONNECTION_ID, DEFAULT_MAX_ENTITY_SIZE);

            IOException thrown =
                    assertThrows(IOException.class, () -> writer.writeDtf(new byte[100]));
            assertTrue(thrown.getMessage().contains("Interrupted"));
        }
    }
}
