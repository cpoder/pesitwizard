package com.pesitwizard.fpdu;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FpduBuilder Tests")
public class FpduBuilderTest {

    @Test
    @DisplayName("should build simple FPDU with no parameters")
    void shouldBuildSimpleFpdu() {
        // Use DTF which has no mandatory params
        byte[] fpdu = FpduBuilder.buildFpdu(FpduType.DTF, 1, 0);

        assertEquals(6, fpdu.length);
        ByteBuffer buf = ByteBuffer.wrap(fpdu);
        assertEquals(6, buf.getShort() & 0xFFFF); // length
        assertEquals(FpduType.DTF.getPhase(), buf.get() & 0xFF); // phase
        assertEquals(FpduType.DTF.getType(), buf.get() & 0xFF); // type
        assertEquals(1, buf.get() & 0xFF); // idDst
        assertEquals(0, buf.get() & 0xFF); // idSrc
    }

    @Test
    @DisplayName("should build FPDU with parameters")
    void shouldBuildFpduWithParameters() {
        // Use DTF_END which accepts PI_02 and PI_99
        ParameterValue pi99 = new ParameterValue(PI_99_MESSAGE_LIBRE, "TEST");
        ParameterValue pi02 = new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 });

        byte[] fpdu = FpduBuilder.buildFpdu(FpduType.DTF_END, 1, 0, pi02, pi99);

        assertTrue(fpdu.length > 6);
        // Parse it back to verify
        FpduParser parser = new FpduParser(fpdu);
        Fpdu parsed = parser.parse();

        assertEquals(FpduType.DTF_END, parsed.getFpduType());
        assertEquals(2, parsed.getParameters().size());
    }

    @Test
    @DisplayName("should build FPDU from Fpdu object")
    void shouldBuildFpduFromObject() {
        // Use DTF which has no mandatory params
        Fpdu fpdu = new Fpdu(FpduType.DTF);
        fpdu.setIdDst(1);
        fpdu.setIdSrc(2);

        byte[] bytes = FpduBuilder.buildFpdu(fpdu);

        // Parse back
        FpduParser parser = new FpduParser(bytes);
        Fpdu parsed = parser.parse();

        assertEquals(FpduType.DTF, parsed.getFpduType());
        assertEquals(1, parsed.getIdDst());
        assertEquals(2, parsed.getIdSrc());
    }

    @Test
    @DisplayName("should build multi-article DTF")
    void shouldBuildMultiArticleDtf() {
        List<byte[]> articles = Arrays.asList(
                new byte[] { 0x01, 0x02, 0x03 },
                new byte[] { 0x04, 0x05 },
                new byte[] { 0x06 });

        byte[] fpdu = FpduBuilder.buildMultiArticleDtf(1, articles, 1000);

        assertNotNull(fpdu);
        // Total: 6 (header) + (2+3) + (2+2) + (2+1) = 18
        assertEquals(18, fpdu.length);

        ByteBuffer buf = ByteBuffer.wrap(fpdu);
        assertEquals(18, buf.getShort() & 0xFFFF); // length
        assertEquals(FpduType.DTF.getPhase(), buf.get() & 0xFF);
        assertEquals(FpduType.DTF.getType(), buf.get() & 0xFF);
        assertEquals(1, buf.get() & 0xFF); // idDst
        assertEquals(3, buf.get() & 0xFF); // idSrc = article count

        // Verify first article length
        assertEquals(3, buf.getShort() & 0xFFFF);
    }

    @Test
    @DisplayName("should return null when articles exceed max entity size")
    void shouldReturnNullWhenArticlesExceedMaxSize() {
        List<byte[]> articles = Arrays.asList(
                new byte[500],
                new byte[500]);

        byte[] fpdu = FpduBuilder.buildMultiArticleDtf(1, articles, 100);

        assertNull(fpdu);
    }

    @Test
    @DisplayName("should calculate articles per entity correctly")
    void shouldCalculateArticlesPerEntity() {
        // Entity overhead: 6 bytes
        // Article overhead: 2 bytes (length prefix)
        // Article size: 10 bytes
        // Available: 100 - 6 = 94 bytes
        // Per article: 2 + 10 = 12 bytes
        // Articles: 94 / 12 = 7

        int count = FpduBuilder.calculateArticlesPerEntity(10, 100);
        assertEquals(7, count);
    }

    @Test
    @DisplayName("should return at least 1 article per entity")
    void shouldReturnAtLeastOneArticlePerEntity() {
        // Very small max size
        int count = FpduBuilder.calculateArticlesPerEntity(1000, 50);
        assertEquals(1, count);
    }

    @Test
    @DisplayName("should build raw data FPDU")
    void shouldBuildRawDataFpdu() {
        byte[] data = new byte[] { 0x41, 0x42, 0x43, 0x44 }; // "ABCD"

        byte[] fpdu = FpduBuilder.buildFpdu(FpduType.DTF, 1, 0, data);

        assertEquals(10, fpdu.length); // 6 header + 4 data

        // Parse back
        FpduParser parser = new FpduParser(fpdu);
        Fpdu parsed = parser.parse();

        assertEquals(FpduType.DTF, parsed.getFpduType());
        assertArrayEquals(data, parsed.getData());
    }
}
