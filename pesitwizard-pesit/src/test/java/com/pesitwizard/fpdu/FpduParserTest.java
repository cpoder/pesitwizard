package com.pesitwizard.fpdu;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

public class FpduParserTest {
    @Test
    void testFpduParser() {
        // FPDU with header: [len(2)][phase][type][idDst][idSrc][params...]
        // len = 11 (total FPDU length)
        // ACONNECT = phase 0x40, type 0x21
        // PI_99 = 99, length 3, data [0x01, 0x02, 0x03]
        byte[] data = new byte[] { 0x00, 11, 0x40, 0x21, 0x01, 0x01, 99, 0x03, 0x01, 0x02, 0x03 };

        FpduParser parser = new FpduParser(data);
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.ACONNECT, fpdu.getFpduType());
        assertNotNull(fpdu.getParameters());
        assertEquals(1, fpdu.getParameters().size(), "Expected one parameter");
        assertEquals(PI_99_MESSAGE_LIBRE, fpdu.getParameters().get(0).getParameter());
        assertEquals(3, fpdu.getParameters().get(0).getValue().length, "Expected parameter value length");
        assertEquals(1, fpdu.getIdDst(), "Expected idDst to be 1");
        assertEquals(1, fpdu.getIdSrc(), "Expected idSrc to be 1");
    }

    @Test
    void testFpduParserWithByteBuffer() {
        // Same test but using ByteBuffer constructor
        byte[] data = new byte[] { 0x00, 11, 0x40, 0x21, 0x01, 0x01, 99, 0x03, 0x01, 0x02, 0x03 };
        ByteBuffer buffer = ByteBuffer.wrap(data);

        FpduParser parser = new FpduParser(buffer);
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.ACONNECT, fpdu.getFpduType());
        assertEquals(1, fpdu.getParameters().size());
        // Buffer position should be advanced past the FPDU
        assertEquals(11, buffer.position());
    }

    @Test
    void testParseDtfWithData() {
        // DTF FPDU: [len(2)][phase=0][type=0][idDst][idSrc][data...]
        // len = 10, data = 4 bytes
        byte[] data = new byte[] { 0x00, 10, 0x00, 0x00, 0x01, 0x02, 0x41, 0x42, 0x43, 0x44 };

        FpduParser parser = new FpduParser(data);
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.DTF, fpdu.getFpduType());
        assertNotNull(fpdu.getData());
        assertEquals(4, fpdu.getData().length);
        assertArrayEquals(new byte[] { 0x41, 0x42, 0x43, 0x44 }, fpdu.getData());
    }

    @Test
    void testParseConcatenatedFpdus() {
        // Two FPDUs concatenated in one buffer
        // FPDU 1: DTF with 2 bytes data
        // FPDU 2: DTF_END
        byte[] data = new byte[] {
                // FPDU 1: DTF [len=8][phase=0][type=0][idDst=1][idSrc=2][data=AA BB]
                0x00, 8, 0x00, 0x00, 0x01, 0x02, (byte) 0xAA, (byte) 0xBB,
                // FPDU 2: DTF_END [len=9][phase=C0][type=4][idDst=1][idSrc=0][PI_02=3 bytes]
                0x00, 9, (byte) 0xC0, 0x04, 0x01, 0x00, 0x02, 0x03, 0x00
        };

        ByteBuffer buffer = ByteBuffer.wrap(data);

        // Parse first FPDU
        FpduParser parser1 = new FpduParser(buffer);
        Fpdu fpdu1 = parser1.parse();
        assertEquals(FpduType.DTF, fpdu1.getFpduType());
        assertEquals(2, fpdu1.getData().length);

        // Parse second FPDU
        FpduParser parser2 = new FpduParser(buffer);
        Fpdu fpdu2 = parser2.parse();
        assertEquals(FpduType.DTF_END, fpdu2.getFpduType());

        // Buffer should be fully consumed
        assertEquals(0, buffer.remaining());
    }
}
