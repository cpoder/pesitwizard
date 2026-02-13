package com.pesitwizard.fpdu;

import static com.pesitwizard.fpdu.ParameterGroupIdentifier.PGI_09_ID_FICHIER;
import static com.pesitwizard.fpdu.ParameterIdentifier.PI_02_DIAG;
import static com.pesitwizard.fpdu.ParameterIdentifier.PI_03_DEMANDEUR;
import static com.pesitwizard.fpdu.ParameterIdentifier.PI_04_SERVEUR;
import static com.pesitwizard.fpdu.ParameterIdentifier.PI_99_MESSAGE_LIBRE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class ParameterValueTest {
    @Test
    void testParameterValueCreation() {
        ParameterValue value1 = new ParameterValue(PI_02_DIAG, new byte[] {0x01, 0x02, 0x03});
        assertEquals(PI_02_DIAG, value1.getParameter());
        assertEquals(5, value1.getBytes().length);
        assertEquals(PI_02_DIAG.getId(), value1.getBytes()[0]);
        assertEquals(PI_02_DIAG.getLength(), value1.getBytes()[1]);
        assertEquals(0x01, value1.getBytes()[2]);
        assertEquals(0x02, value1.getBytes()[3]);
        assertEquals(0x03, value1.getBytes()[4]);
    }

    @Test()
    void testParameterValueWithString() {
        ParameterValue value1 = new ParameterValue(PI_02_DIAG, new byte[] {0x01, 0x02, 0x03});
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ParameterValue(
                                        PGI_09_ID_FICHIER,
                                        value1,
                                        new ParameterValue(PI_99_MESSAGE_LIBRE, "test message")));

        assertEquals(
                "Parameter Diagnostic is not part of PGI File Identifier", exception.getMessage());
    }

    @Test
    void testParameterValueWithMultipleValues() {
        ParameterValue value1 = new ParameterValue(PI_03_DEMANDEUR, "client");
        ParameterValue value2 = new ParameterValue(PI_04_SERVEUR, "server");
        ParameterValue pgiValue = new ParameterValue(PGI_09_ID_FICHIER, value1, value2);
        assertEquals(PGI_09_ID_FICHIER, pgiValue.getParameter());
        assertEquals(18, pgiValue.getBytes().length);
        assertEquals(PGI_09_ID_FICHIER.getId(), pgiValue.getBytes()[0]);
        assertEquals(16, pgiValue.getBytes()[1]);
        assertEquals(PI_03_DEMANDEUR.getId(), pgiValue.getBytes()[2]);
        assertEquals("client".length(), pgiValue.getBytes()[3]);
        assertEquals('c', pgiValue.getBytes()[4]);
        assertEquals('l', pgiValue.getBytes()[5]);
        assertEquals('i', pgiValue.getBytes()[6]);
        assertEquals('e', pgiValue.getBytes()[7]);
        assertEquals('n', pgiValue.getBytes()[8]);
        assertEquals('t', pgiValue.getBytes()[9]);
        assertEquals(PI_04_SERVEUR.getId(), pgiValue.getBytes()[10]);
        assertEquals("server".length(), pgiValue.getBytes()[11]);
        assertEquals('s', pgiValue.getBytes()[12]);
        assertEquals('e', pgiValue.getBytes()[13]);
        assertEquals('r', pgiValue.getBytes()[14]);
        assertEquals('v', pgiValue.getBytes()[15]);
        assertEquals('e', pgiValue.getBytes()[16]);
        assertEquals('r', pgiValue.getBytes()[17]);
    }
}
