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
        // ACONNECT = phase 0x40, type 0x21
        // PI_06_VERSION (mandatory) = id 6, length 2, value [0x00, 0x01]
        // PI_99 = 99, length 3, data [0x01, 0x02, 0x03]
        // Total: 2 + 4 + 4 + 5 = 15
        byte[] data =
                new byte[] {
                    0x00,
                    15,
                    0x40,
                    0x21, // ACONNECT
                    0x01,
                    0x01, // idDst, idSrc
                    0x06,
                    0x02,
                    0x00,
                    0x01, // PI_06 (mandatory), len=2, version=1
                    99,
                    0x03,
                    0x01,
                    0x02,
                    0x03 // PI_99, len=3
                };

        FpduParser parser = new FpduParser(data);
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.ACONNECT, fpdu.getFpduType());
        assertNotNull(fpdu.getParameters());
        assertEquals(2, fpdu.getParameters().size(), "Expected two parameters");
        assertEquals(PI_06_VERSION, fpdu.getParameters().get(0).getParameter());
        assertEquals(PI_99_MESSAGE_LIBRE, fpdu.getParameters().get(1).getParameter());
        assertEquals(
                3,
                fpdu.getParameters().get(1).getValue().length,
                "Expected parameter value length");
        assertEquals(1, fpdu.getIdDst(), "Expected idDst to be 1");
        assertEquals(1, fpdu.getIdSrc(), "Expected idSrc to be 1");
    }

    @Test
    void testFpduParserWithByteBuffer() {
        // ACONNECT with mandatory PI_06_VERSION
        // Total: 2 + 4 + 4 = 10
        byte[] data =
                new byte[] {
                    0x00,
                    10,
                    0x40,
                    0x21, // ACONNECT
                    0x01,
                    0x01, // idDst, idSrc
                    0x06,
                    0x02,
                    0x00,
                    0x01 // PI_06 (mandatory), len=2, version=1
                };
        ByteBuffer buffer = ByteBuffer.wrap(data);

        FpduParser parser = new FpduParser(buffer);
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.ACONNECT, fpdu.getFpduType());
        assertEquals(1, fpdu.getParameters().size());
        // Buffer position should be advanced past the FPDU
        assertEquals(10, buffer.position());
    }

    @Test
    void testParseDtfWithData() {
        // DTF FPDU: [len(2)][phase=0][type=0][idDst][idSrc][data...]
        // len = 10, data = 4 bytes
        // DTF has no mandatory parameters (raw data, not parameter-based)
        byte[] data = new byte[] {0x00, 10, 0x00, 0x00, 0x01, 0x02, 0x41, 0x42, 0x43, 0x44};

        FpduParser parser = new FpduParser(data);
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.DTF, fpdu.getFpduType());
        assertNotNull(fpdu.getData());
        assertEquals(4, fpdu.getData().length);
        assertArrayEquals(new byte[] {0x41, 0x42, 0x43, 0x44}, fpdu.getData());
    }

    @Test
    void testParseConcatenatedFpdus() {
        // Two FPDUs concatenated in one buffer
        // FPDU 1: DTF with 2 bytes data (no mandatory params)
        // FPDU 2: DTF_END with mandatory PI_02_DIAG
        byte[] data =
                new byte[] {
                    // FPDU 1: DTF [len=8][phase=0][type=0][idDst=1][idSrc=2][data=AA BB]
                    0x00,
                    8,
                    0x00,
                    0x00,
                    0x01,
                    0x02,
                    (byte) 0xAA,
                    (byte) 0xBB,
                    // FPDU 2: DTF_END [len=11][phase=C0][type=4][idDst=1][idSrc=0][PI_02=3 bytes,
                    // value=000000]
                    0x00,
                    11,
                    (byte) 0xC0,
                    0x04,
                    0x01,
                    0x00,
                    0x02,
                    0x03,
                    0x00,
                    0x00,
                    0x00 // PI_02_DIAG (mandatory), len=3, value=000000
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
        // Use RELCONF (phase=0x40, type=0x24) which has no mandatory parameters
        // Parameter with length > 254 uses 0xFF prefix + 2-byte length
        // PI_99, length = 300 bytes (0x012C)
        byte[] paramData = new byte[300];
        for (int i = 0; i < 300; i++) paramData[i] = (byte) i;

        // FPDU: [len][phase][type][idDst][idSrc][PI=99][0xFF][len_hi][len_lo][data...]
        // Total length = 2 + 1 + 1 + 1 + 1 + 1 + 1 + 2 + 300 = 310
        ByteBuffer buf = ByteBuffer.allocate(310);
        buf.putShort((short) 310); // FPDU length
        buf.put((byte) 0x40); // phase RELCONF
        buf.put((byte) 0x24); // type
        buf.put((byte) 0x01); // idDst
        buf.put((byte) 0x01); // idSrc
        buf.put((byte) 99); // PI_99
        buf.put((byte) 0xFF); // extended length marker
        buf.putShort((short) 300); // actual length
        buf.put(paramData);
        buf.flip();

        FpduParser parser = new FpduParser(buf.array());
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.RELCONF, fpdu.getFpduType());
        assertEquals(1, fpdu.getParameters().size());
        assertEquals(300, fpdu.getParameters().get(0).getValue().length);
    }

    @Test
    @DisplayName("should throw on incomplete buffer with missing mandatory parameters")
    void shouldThrowOnIncompleteBuffer() {
        // ACONNECT with truncated parameter - buffer ends before data
        // PI_06 (mandatory) is not fully present, so it won't be added to the FPDU,
        // which means mandatory parameter validation will fail
        byte[] data =
                new byte[] {
                    0x00, 0x08, // length = 8
                    0x40, 0x21, // phase/type (ACONNECT)
                    0x01, 0x01, // idDst/idSrc
                    0x06, 0x05 // PI_06, length=5 but no data follows (truncated)
                };

        FpduParser parser = new FpduParser(data);
        FpduParseException ex = assertThrows(FpduParseException.class, () -> parser.parse());
        assertTrue(ex.getMessage().contains("Missing mandatory parameter"));
    }

    @Test
    @DisplayName("should throw FpduParseException on unknown phase/type combination")
    void shouldThrowOnUnknownFpduType() {
        // Use an invalid phase/type combination (0xFF/0xFF)
        byte[] data = new byte[] {0x00, 6, (byte) 0xFF, (byte) 0xFF, 0x01, 0x01};

        FpduParser parser = new FpduParser(data);
        FpduParseException ex = assertThrows(FpduParseException.class, () -> parser.parse());
        assertTrue(ex.getMessage().contains("Unknown FPDU type"));
        assertTrue(ex.getMessage().contains("0xFF"));
    }

    @Test
    @DisplayName("should throw on unknown parameter ID")
    void shouldThrowOnUnknownParameter() {
        // Unknown PI = 250 (not defined)
        byte[] data = new byte[] {0x00, 9, 0x40, 0x21, 0x01, 0x01, (byte) 250, 0x01, 0x00};

        FpduParser parser = new FpduParser(data);
        assertThrows(UnknownParameterException.class, () -> parser.parse());
    }

    @Test
    @DisplayName("S3-08: should reject parameter with zero length")
    void shouldRejectZeroLengthParameter() {
        // PI_99 with length 0 - rejected by S3-08 LI=0 validation
        byte[] data = new byte[] {0x00, 8, 0x40, 0x21, 0x01, 0x01, 99, 0x00};

        FpduParser parser = new FpduParser(data);
        FpduParseException ex = assertThrows(FpduParseException.class, () -> parser.parse());
        assertTrue(ex.getMessage().contains("LI=0"));
    }

    @Test
    @DisplayName("should parse PGI with nested parameters")
    void shouldParsePgiWithNestedParameters() {
        // MSG (phase=0xC0, type=0x16) requires PGI_09 and PI_13 as mandatory
        // PGI_09 contains PI_11 (file type) and PI_12 (file name)
        // Total: 2(len) + 4(header) + 10(PGI_09) + 5(PI_13) = 21
        byte[] data =
                new byte[] {
                    0x00,
                    21, // FPDU length = 21
                    (byte) 0xC0,
                    0x16, // phase/type (MSG)
                    0x01,
                    0x01, // idDst/idSrc
                    0x09,
                    0x08, // PGI_09, length=8
                    0x0B,
                    0x02,
                    0x41,
                    0x42, // PI_11 (file type), len=2, "AB"
                    0x0C,
                    0x02,
                    0x43,
                    0x44, // PI_12 (file name), len=2, "CD"
                    0x0D,
                    0x03,
                    0x00,
                    0x00,
                    0x01 // PI_13 (transfer id), len=3, value=1
                };

        FpduParser parser = new FpduParser(data);
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.MSG, fpdu.getFpduType());
        assertEquals(2, fpdu.getParameters().size());

        ParameterValue pgi = fpdu.getParameters().get(0);
        assertEquals(ParameterGroupIdentifier.PGI_09_ID_FICHIER, pgi.getParameter());
        assertEquals(2, pgi.getValues().size());

        ParameterValue pi13 = fpdu.getParameters().get(1);
        assertEquals(PI_13_ID_TRANSFERT, pi13.getParameter());
    }

    // --- S3-07: Mandatory parameter validation tests ---

    @Test
    @DisplayName("S3-07: should throw when mandatory PI is missing")
    void shouldThrowWhenMandatoryPiIsMissing() {
        // ACONNECT requires PI_06_VERSION as mandatory
        // Send ACONNECT with only optional PI_99 (no PI_06)
        byte[] data =
                new byte[] {
                    0x00,
                    11,
                    0x40,
                    0x21, // ACONNECT
                    0x01,
                    0x01, // idDst, idSrc
                    99,
                    0x03,
                    0x01,
                    0x02,
                    0x03 // PI_99 only (optional)
                };

        FpduParser parser = new FpduParser(data);
        FpduParseException ex = assertThrows(FpduParseException.class, () -> parser.parse());
        assertTrue(
                ex.getMessage().contains("Missing mandatory parameter"),
                "Expected message about missing mandatory parameter, got: " + ex.getMessage());
        assertTrue(
                ex.getMessage().contains("PI_6"),
                "Expected message to mention PI_6, got: " + ex.getMessage());
        assertTrue(
                ex.getMessage().contains("FPDU.ACONNECT"),
                "Expected message to mention FPDU.ACONNECT, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("S3-07: should throw when mandatory PGI is missing")
    void shouldThrowWhenMandatoryPgiIsMissing() {
        // MSG requires PGI_09_ID_FICHIER and PI_13_ID_TRANSFERT as mandatory
        // Send MSG with only PI_13 (missing PGI_09)
        byte[] data =
                new byte[] {
                    0x00,
                    11,
                    (byte) 0xC0,
                    0x16, // MSG
                    0x01,
                    0x01, // idDst, idSrc
                    0x0D,
                    0x03,
                    0x00,
                    0x00,
                    0x01 // PI_13 (transfer id), len=3
                };

        FpduParser parser = new FpduParser(data);
        FpduParseException ex = assertThrows(FpduParseException.class, () -> parser.parse());
        assertTrue(
                ex.getMessage().contains("Missing mandatory parameter"),
                "Expected message about missing mandatory parameter, got: " + ex.getMessage());
        assertTrue(
                ex.getMessage().contains("PGI_9"),
                "Expected message to mention PGI_9, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("S3-07: should pass when all mandatory parameters are present")
    void shouldPassWhenAllMandatoryParametersPresent() {
        // CONNECT requires: PI_03, PI_04, PI_06, PI_22 as mandatory
        // PI_03 = id 3, type=C, len=24
        // PI_04 = id 4, type=C, len=24
        // PI_06 = id 6, type=N, len=2
        // PI_22 = id 22 (0x16), type=S, len=1
        byte[] pi03Data = "REQUESTER_ID            ".getBytes(); // 24 bytes
        byte[] pi04Data = "SERVER_ID               ".getBytes(); // 24 bytes

        // Total = 2 + 4 + (2+24) + (2+24) + (2+2) + (2+1) = 65
        ByteBuffer buf = ByteBuffer.allocate(65);
        buf.putShort((short) 65); // length
        buf.put((byte) 0x40); // phase CONNECT
        buf.put((byte) 0x20); // type
        buf.put((byte) 0x01); // idDst
        buf.put((byte) 0x01); // idSrc
        buf.put((byte) 3); // PI_03
        buf.put((byte) 24); // length
        buf.put(pi03Data);
        buf.put((byte) 4); // PI_04
        buf.put((byte) 24); // length
        buf.put(pi04Data);
        buf.put((byte) 6); // PI_06
        buf.put((byte) 2); // length
        buf.put(new byte[] {0x00, 0x01}); // version 1
        buf.put((byte) 22); // PI_22
        buf.put((byte) 1); // length
        buf.put((byte) 0x01); // write access
        buf.flip();

        FpduParser parser = new FpduParser(buf.array());
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.CONNECT, fpdu.getFpduType());
        assertEquals(4, fpdu.getParameters().size());
    }

    @Test
    @DisplayName("S3-07: should not validate mandatory parameters for DTF FPDUs")
    void shouldNotValidateMandatoryParamsForDtf() {
        // DTF FPDUs carry raw data, not parameters - validation should be skipped
        byte[] data = new byte[] {0x00, 8, 0x00, 0x00, 0x01, 0x02, 0x41, 0x42};

        FpduParser parser = new FpduParser(data);
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.DTF, fpdu.getFpduType());
        assertNotNull(fpdu.getData());
    }

    @Test
    @DisplayName("S3-07: should accept FPDU with no mandatory requirements and no parameters")
    void shouldAcceptFpduWithNoMandatoryRequirements() {
        // RELCONF (phase=0x40, type=0x24) has only optional PI_99
        byte[] data =
                new byte[] {
                    0x00, 6,
                    0x40, 0x24, // RELCONF
                    0x01, 0x01 // idDst, idSrc (no parameters)
                };

        FpduParser parser = new FpduParser(data);
        Fpdu fpdu = parser.parse();

        assertEquals(FpduType.RELCONF, fpdu.getFpduType());
        assertEquals(0, fpdu.getParameters().size());
    }
}
