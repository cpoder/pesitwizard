package com.pesitwizard.fpdu;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Fpdu Model Tests")
public class FpduTest {

    @Test
    @DisplayName("should check if parameter exists with hasParameter")
    void shouldCheckParameterExists() {
        Fpdu fpdu = new Fpdu(FpduType.ACONNECT);
        fpdu.withParameter(new ParameterValue(PI_99_MESSAGE_LIBRE, "Test"));

        assertTrue(fpdu.hasParameter(PI_99_MESSAGE_LIBRE));
        assertFalse(fpdu.hasParameter(PI_02_DIAG));
    }

    @Test
    @DisplayName("should get parameter value with getParameter")
    void shouldGetParameterValue() {
        Fpdu fpdu = new Fpdu(FpduType.ACONNECT);
        ParameterValue pv = new ParameterValue(PI_99_MESSAGE_LIBRE, "Hello");
        fpdu.withParameter(pv);

        ParameterValue result = fpdu.getParameter(PI_99_MESSAGE_LIBRE);

        assertNotNull(result);
        assertEquals(PI_99_MESSAGE_LIBRE, result.getParameter());
    }

    @Test
    @DisplayName("should return null for missing parameter")
    void shouldReturnNullForMissingParameter() {
        Fpdu fpdu = new Fpdu(FpduType.ACONNECT);

        ParameterValue result = fpdu.getParameter(PI_02_DIAG);

        assertNull(result);
    }

    @Test
    @DisplayName("should support fluent API")
    void shouldSupportFluentApi() {
        Fpdu fpdu = new Fpdu(FpduType.CREATE)
                .withIdDst(1)
                .withIdSrc(2)
                .withParameter(new ParameterValue(PI_99_MESSAGE_LIBRE, "Test"));

        assertEquals(FpduType.CREATE, fpdu.getFpduType());
        assertEquals(1, fpdu.getIdDst());
        assertEquals(2, fpdu.getIdSrc());
        assertEquals(1, fpdu.getParameters().size());
    }

    @Test
    @DisplayName("should handle DTF data")
    void shouldHandleDtfData() {
        Fpdu fpdu = new Fpdu(FpduType.DTF);
        byte[] data = new byte[] { 0x01, 0x02, 0x03 };
        fpdu.setData(data);

        assertArrayEquals(data, fpdu.getData());
    }

    @Test
    @DisplayName("should provide meaningful toString")
    void shouldProvideMeaningfulToString() {
        Fpdu fpdu = new Fpdu(FpduType.ACONNECT);
        fpdu.setIdDst(1);
        fpdu.setIdSrc(2);

        String str = fpdu.toString();

        assertTrue(str.contains("ACONNECT"));
        assertTrue(str.contains("idSrc=2"));
        assertTrue(str.contains("idDst=1"));
    }
}
