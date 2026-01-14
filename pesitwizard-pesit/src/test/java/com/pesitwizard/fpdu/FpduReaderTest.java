package com.pesitwizard.fpdu;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

public class FpduReaderTest {

    @Test
    void testInjectSingleFpdu() throws IOException {
        // Single DTF FPDU with data - use injectRawData instead of read()
        byte[] data = new byte[] { 0x00, 10, 0x00, 0x00, 0x01, 0x02, 0x41, 0x42, 0x43, 0x44 };
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[0]));
        FpduReader reader = new FpduReader(in);

        reader.injectRawData(data);
        assertTrue(reader.hasPending());

        Fpdu fpdu = reader.read();
        assertEquals(FpduType.DTF, fpdu.getFpduType());
        assertNotNull(fpdu.getData());
        assertEquals(4, fpdu.getData().length);
        assertFalse(reader.hasPending());
    }

    @Test
    void testInjectConcatenatedFpdus() throws IOException {
        // Two FPDUs concatenated: DTF + DTFFA
        byte[] data = new byte[] {
                // FPDU 1: DTF [len=8][phase=0][type=0][idDst=1][idSrc=2][data=AA BB]
                0x00, 8, 0x00, 0x00, 0x01, 0x02, (byte) 0xAA, (byte) 0xBB,
                // FPDU 2: DTFFA [len=8][phase=0][type=0x42][idDst=1][idSrc=0][data=CC DD]
                0x00, 8, 0x00, 0x42, 0x01, 0x00, (byte) 0xCC, (byte) 0xDD
        };
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[0]));
        FpduReader reader = new FpduReader(in);

        reader.injectRawData(data);

        // First read returns first FPDU, second is buffered
        Fpdu fpdu1 = reader.read();
        assertEquals(FpduType.DTF, fpdu1.getFpduType());
        assertTrue(reader.hasPending());

        // Second read returns buffered FPDU
        Fpdu fpdu2 = reader.read();
        assertEquals(FpduType.DTFFA, fpdu2.getFpduType());
        assertFalse(reader.hasPending());
    }

    @Test
    void testInjectDtfSegments() throws IOException {
        // DTFDA (start) + DTFMA (middle) + DTFFA (end) - article segmentation
        byte[] data = new byte[] {
                // DTFDA [len=8][phase=0][type=0x41][idDst=1][idSrc=0][data=11 22]
                0x00, 8, 0x00, 0x41, 0x01, 0x00, 0x11, 0x22,
                // DTFMA [len=8][phase=0][type=0x40][idDst=1][idSrc=0][data=33 44]
                0x00, 8, 0x00, 0x40, 0x01, 0x00, 0x33, 0x44,
                // DTFFA [len=8][phase=0][type=0x42][idDst=1][idSrc=0][data=55 66]
                0x00, 8, 0x00, 0x42, 0x01, 0x00, 0x55, 0x66
        };
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[0]));
        FpduReader reader = new FpduReader(in);

        reader.injectRawData(data);

        // Each segment should be returned separately (no aggregation)
        Fpdu fpdu1 = reader.read();
        assertEquals(FpduType.DTFDA, fpdu1.getFpduType());
        assertArrayEquals(new byte[] { 0x11, 0x22 }, fpdu1.getData());

        Fpdu fpdu2 = reader.read();
        assertEquals(FpduType.DTFMA, fpdu2.getFpduType());
        assertArrayEquals(new byte[] { 0x33, 0x44 }, fpdu2.getData());

        Fpdu fpdu3 = reader.read();
        assertEquals(FpduType.DTFFA, fpdu3.getFpduType());
        assertArrayEquals(new byte[] { 0x55, 0x66 }, fpdu3.getData());
    }

    @Test
    void testHasPendingInitiallyFalse() {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[0]));
        FpduReader reader = new FpduReader(in);
        assertFalse(reader.hasPending());
    }
}
