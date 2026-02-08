package com.pesitwizard.server.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("TransferContext Tests")
class TransferContextTest {

    private TransferContext context;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        context = new TransferContext();
    }

    @Test
    @DisplayName("should append data and track bytes")
    void shouldAppendDataAndTrackBytes() throws IOException {
        byte[] data1 = "Hello ".getBytes();
        byte[] data2 = "World!".getBytes();

        // Setup streaming to temp file
        context.setLocalPath(tempDir.resolve("test.dat"));
        context.openOutputStream();

        context.appendData(data1);
        assertEquals(data1.length, context.getBytesTransferred());
        // Note: recordsTransferred is now managed by DataTransferHandler, not
        // appendData

        context.appendData(data2);
        assertEquals(data1.length + data2.length, context.getBytesTransferred());

        context.closeOutputStream();
    }

    @Test
    @DisplayName("should get accumulated data")
    void shouldGetAccumulatedData() throws IOException {
        // Setup streaming to temp file
        context.setLocalPath(tempDir.resolve("test2.dat"));
        context.openOutputStream();

        context.appendData("Hello ".getBytes());
        context.appendData("World!".getBytes());
        context.closeOutputStream();

        byte[] result = context.getData();
        assertEquals("Hello World!", new String(result));
    }

    @Test
    @DisplayName("should reset all fields")
    void shouldResetAllFields() throws IOException {
        // Set some values
        context.setTransferId(123);
        context.setFilename("test.txt");
        context.setLocalPath(tempDir.resolve("test3.dat"));
        context.setPriority(5);
        context.setWriteMode(true);
        context.setRestart(true);
        context.setStartTime(Instant.now());
        context.openOutputStream();
        context.appendData("test data".getBytes());

        // Reset
        context.reset();

        // Verify all fields are reset
        assertEquals(0, context.getTransferId());
        assertNull(context.getFilename());
        assertNull(context.getLocalPath());
        assertEquals(0, context.getPriority());
        assertFalse(context.isWriteMode());
        assertFalse(context.isRestart());
        assertNull(context.getStartTime());
        assertEquals(0, context.getBytesTransferred());
        assertEquals(0, context.getRecordsTransferred());
    }

    @Test
    @DisplayName("should store transfer attributes")
    void shouldStoreTransferAttributes() {
        context.setTransferId(42);
        context.setFileType(1);
        context.setFilename("DATA.DAT");
        context.setLocalPath(Path.of("/data/received/DATA.DAT"));
        context.setPriority(3);
        context.setDataCode(2); // BINARY
        context.setRecordFormat(1); // Variable
        context.setRecordLength(1024);
        context.setFileOrganization(0);
        context.setMaxEntitySize(32768);
        context.setCompression(0);
        context.setWriteMode(true);
        context.setRestart(false);
        context.setRestartPoint(0);
        context.setCurrentSyncPoint(5);
        context.setStartTime(Instant.now());
        context.setClientId("CLIENT1");
        context.setBankId("BANK1");

        assertEquals(42, context.getTransferId());
        assertEquals(1, context.getFileType());
        assertEquals("DATA.DAT", context.getFilename());
        assertEquals(Path.of("/data/received/DATA.DAT"), context.getLocalPath());
        assertEquals(3, context.getPriority());
        assertEquals(2, context.getDataCode());
        assertEquals(1, context.getRecordFormat());
        assertEquals(1024, context.getRecordLength());
        assertEquals(0, context.getFileOrganization());
        assertEquals(32768, context.getMaxEntitySize());
        assertEquals(0, context.getCompression());
        assertTrue(context.isWriteMode());
        assertFalse(context.isRestart());
        assertEquals(0, context.getRestartPoint());
        assertEquals(5, context.getCurrentSyncPoint());
        assertNotNull(context.getStartTime());
        assertEquals("CLIENT1", context.getClientId());
        assertEquals("BANK1", context.getBankId());
    }

    @Test
    @DisplayName("should handle empty data append")
    void shouldHandleEmptyDataAppend() throws IOException {
        context.setLocalPath(tempDir.resolve("empty.dat"));
        context.openOutputStream();
        context.appendData(new byte[0]);
        assertEquals(0, context.getBytesTransferred());
        // Note: recordsTransferred is now managed by DataTransferHandler, not
        // appendData
        context.closeOutputStream();
    }

    @Test
    @DisplayName("should track end time")
    void shouldTrackEndTime() {
        assertNull(context.getEndTime());

        Instant endTime = Instant.now();
        context.setEndTime(endTime);

        assertEquals(endTime, context.getEndTime());
    }

    // ===== RESYN Tests =====

    @Test
    @DisplayName("should record and retrieve sync point byte positions")
    void shouldRecordAndRetrieveSyncPointPositions() {
        context.recordSyncPointPosition(1, 1024);
        context.recordSyncPointPosition(2, 2048);
        context.recordSyncPointPosition(3, 4096);

        assertEquals(1024, context.getSyncPointBytePosition(1));
        assertEquals(2048, context.getSyncPointBytePosition(2));
        assertEquals(4096, context.getSyncPointBytePosition(3));
    }

    @Test
    @DisplayName("should return -1 for unknown sync point")
    void shouldReturnNegativeForUnknownSyncPoint() {
        assertEquals(-1, context.getSyncPointBytePosition(99));
    }

    @Test
    @DisplayName("should clear sync point positions on reset")
    void shouldClearSyncPointPositionsOnReset() throws IOException {
        context.recordSyncPointPosition(1, 1024);
        context.setLocalPath(tempDir.resolve("reset-sync.dat"));
        context.openOutputStream();
        context.reset();

        assertEquals(-1, context.getSyncPointBytePosition(1));
    }

    @Test
    @DisplayName("should truncate and reopen file for RESYN rollback")
    void shouldTruncateAndReopenForResyn() throws IOException {
        Path testFile = tempDir.resolve("resyn-truncate.dat");
        context.setLocalPath(testFile);
        context.openOutputStream();

        // Write 1000 bytes
        context.appendData(new byte[500]);
        context.appendData(new byte[500]);
        assertEquals(1000, context.getBytesTransferred());

        // Truncate to 500 bytes (RESYN rollback)
        context.truncateAndReopen(500);

        assertEquals(500, context.getBytesTransferred());
        assertEquals(0, context.getBytesSinceLastSync());

        // Verify we can still write
        context.appendData(new byte[200]);
        assertEquals(700, context.getBytesTransferred());

        context.closeOutputStream();

        // Verify file size
        assertEquals(700, java.nio.file.Files.size(testFile));
    }

    @Test
    @DisplayName("should throw if truncating without file")
    void shouldThrowIfTruncatingWithoutFile() {
        context.setLocalPath(tempDir.resolve("nonexistent.dat"));
        assertThrows(IllegalStateException.class, () -> context.truncateAndReopen(0));
    }
}
