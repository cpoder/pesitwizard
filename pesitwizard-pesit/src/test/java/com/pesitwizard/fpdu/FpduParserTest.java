package com.pesitwizard.fpdu;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FpduParser Tests")
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

    @Test
    @DisplayName("should parse parameter with extended length (0xFF prefix)")
    void shouldParseExtendedLengthParameter() {
        // Parameter with length > 254 uses 0xFF prefix + 2-byte length
        // PI_99, length = 300 bytes (0x012C)
        byte[] paramData = new byte[300];
        for (int i = 0; i < 300; i++)
            paramData[i] = (byte) i;

        // FPDU: [len][phase][type][idDst][idSrc][PI=99][0xFF][len_hi][len_lo][data...]
        // Total length = 2 + 1 + 1 + 1 + 1 + 1 + 1 + 2 + 300 = 310
        ByteBuffer buf = ByteBuffer.allocate(310);
        buf.putShort((short) 310); // FPDU length
        buf.put((byte) 0x40); // phase ACONNECT
        buf.put((byte) 0x21); // type
        buf.put((byte) 0x01); // idDst
        buf.put((byte) 0x01); // idSrc
        buf.put((byte) 99); // PI_99
        buf.put((byte) 0xFF); // extended length marker
        buf.putShort((short) 300); // actual length
        buf.put(paramData);
        buf.flip();

        FpduParser parser = new FpduParser(buf.array());
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.ACONNECT, fpdu.getFpduType());
        assertEquals(1, fpdu.getParameters().size());
        assertEquals(300, fpdu.getParameters().get(0).getValue().length);
    }

    @Test
    @DisplayName("should handle incomplete buffer gracefully")
    void shouldHandleIncompleteBuffer() {
        // Create a minimal valid FPDU but with truncated parameter
        // FPDU header says there's a param but buffer ends early
        byte[] data = new byte[] {
                0x00, 0x08, // length = 8
                0x40, 0x21, // phase/type (ACONNECT)
                0x01, 0x01, // idDst/idSrc
                99, 0x05 // PI_99, length=5 but no data follows
        };

        FpduParser parser = new FpduParser(data);
        // Parser should handle gracefully (log warning, return partial)
        Fpdu fpdu = parser.parse();
        assertNotNull(fpdu);
        assertEquals(FpduType.ACONNECT, fpdu.getFpduType());
    }

    @Test
    @DisplayName("should throw on unknown parameter ID")
    void shouldThrowOnUnknownParameter() {
        // Unknown PI = 250 (not defined)
        byte[] data = new byte[] { 0x00, 9, 0x40, 0x21, 0x01, 0x01, (byte) 250, 0x01, 0x00 };

        FpduParser parser = new FpduParser(data);
        assertThrows(UnknownParameterException.class, () -> parser.parse());
    }

    @Test
    @DisplayName("should handle parameter with zero length")
    void shouldHandleZeroLengthParameter() {
        // PI_99 with length 0
        byte[] data = new byte[] { 0x00, 8, 0x40, 0x21, 0x01, 0x01, 99, 0x00 };

        FpduParser parser = new FpduParser(data);
        Fpdu fpdu = parser.parse();

        assertEquals(1, fpdu.getParameters().size());
        assertEquals(0, fpdu.getParameters().get(0).getValue().length);
    }

    @Test
    @DisplayName("should parse PGI with nested parameters")
    void shouldParsePgiWithNestedParameters() {
        // PGI_09 (File Identifier) containing PI_11 and PI_12
        // Total length = 2 + 4 + 2 + 8 = 16
        byte[] data = new byte[] {
                0x00, 16, // FPDU length = 16
                0x40, 0x21, // phase/type (ACONNECT)
                0x01, 0x01, // idDst/idSrc
                0x09, 0x08, // PGI_09, length=8
                0x0B, 0x02, 0x41, 0x42, // PI_11 (file type), len=2, "AB"
                0x0C, 0x02, 0x43, 0x44 // PI_12 (file name), len=2, "CD"
        };

        FpduParser parser = new FpduParser(data);
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.ACONNECT, fpdu.getFpduType());
        assertEquals(1, fpdu.getParameters().size());

        ParameterValue pgi = fpdu.getParameters().get(0);
        assertEquals(ParameterGroupIdentifier.PGI_09_ID_FICHIER, pgi.getParameter());
        assertEquals(2, pgi.getValues().size());
    }
}
