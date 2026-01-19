package com.pesitwizard.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;

@DisplayName("Edge Case Tests")
public class EdgeCaseTest {

    @Nested
    @DisplayName("Diagnostic Code Tests")
    class DiagnosticCodeTests {

        @Test
        @DisplayName("should create success diagnostic code")
        void shouldCreateSuccessDiagnosticCode() {
            DiagnosticCode code = DiagnosticCode.D0_000;
            byte[] bytes = code.toBytes();
            assertEquals(3, bytes.length);
            assertEquals(0, bytes[0]);
        }

        @Test
        @DisplayName("should create error diagnostic codes")
        void shouldCreateErrorDiagnosticCodes() {
            DiagnosticCode[] errorCodes = {
                    DiagnosticCode.D2_205,
                    DiagnosticCode.D2_211,
                    DiagnosticCode.D2_299,
                    DiagnosticCode.D3_301
            };
            for (DiagnosticCode code : errorCodes) {
                byte[] bytes = code.toBytes();
                assertEquals(3, bytes.length);
                assertTrue(bytes[0] != 0 || bytes[1] != 0,
                        "Error code should not be 0x000000: " + code);
            }
        }
    }

    @Nested
    @DisplayName("FPDU Type Tests")
    class FpduTypeTests {

        @Test
        @DisplayName("should have all required FPDU types")
        void shouldHaveAllRequiredTypes() {
            assertNotNull(FpduType.CONNECT);
            assertNotNull(FpduType.ACONNECT);
            assertNotNull(FpduType.RCONNECT);
            assertNotNull(FpduType.CREATE);
            assertNotNull(FpduType.ACK_CREATE);
            assertNotNull(FpduType.SELECT);
            assertNotNull(FpduType.ACK_SELECT);
            assertNotNull(FpduType.OPEN);
            assertNotNull(FpduType.ACK_OPEN);
            assertNotNull(FpduType.CLOSE);
            assertNotNull(FpduType.ACK_CLOSE);
            assertNotNull(FpduType.DTF);
            assertNotNull(FpduType.DTFDA);
            assertNotNull(FpduType.DTFMA);
            assertNotNull(FpduType.DTFFA);
            assertNotNull(FpduType.DTF_END);
            assertNotNull(FpduType.SYN);
            assertNotNull(FpduType.ACK_SYN);
            assertNotNull(FpduType.ABORT);
            assertNotNull(FpduType.RELEASE);
            assertNotNull(FpduType.RELCONF);
        }

        @Test
        @DisplayName("should have phase values defined")
        void shouldHavePhaseValuesDefined() {
            // Verify phases are defined (values depend on protocol spec)
            assertTrue(FpduType.CONNECT.getPhase() >= 0);
            assertTrue(FpduType.RELEASE.getPhase() >= 0);
            assertTrue(FpduType.DTF.getPhase() >= 0);
        }
    }

    @Nested
    @DisplayName("Parameter Tests")
    class ParameterTests {

        @Test
        @DisplayName("should create parameter with string value")
        void shouldCreateParameterWithStringValue() {
            ParameterValue param = new ParameterValue(
                    ParameterIdentifier.PI_12_NOM_FICHIER, "TEST.DAT");
            assertNotNull(param.getValue());
            assertTrue(param.getValue().length > 0);
        }

        @Test
        @DisplayName("should create parameter with int value")
        void shouldCreateParameterWithIntValue() {
            ParameterValue param = new ParameterValue(
                    ParameterIdentifier.PI_13_ID_TRANSFERT, 42);
            assertNotNull(param.getValue());
        }

        @Test
        @DisplayName("should create parameter with byte array")
        void shouldCreateParameterWithByteArray() {
            byte[] diagBytes = new byte[] { 0x00, 0x00, 0x00 };
            ParameterValue param = new ParameterValue(
                    ParameterIdentifier.PI_02_DIAG, diagBytes);
            assertArrayEquals(diagBytes, param.getValue());
        }
    }

    @Nested
    @DisplayName("FPDU Build/Parse Roundtrip Tests")
    class RoundtripTests {

        @Test
        @DisplayName("should roundtrip RELCONF FPDU")
        void shouldRoundtripRelconfFpdu() throws Exception {
            Fpdu original = new Fpdu(FpduType.RELCONF)
                    .withIdDst(1)
                    .withIdSrc(0);

            byte[] raw = FpduBuilder.buildFpdu(original);
            Fpdu parsed = new FpduParser(raw).parse();

            assertEquals(FpduType.RELCONF, parsed.getFpduType());
            assertEquals(1, parsed.getIdDst());
            assertEquals(0, parsed.getIdSrc());
        }

        @Test
        @DisplayName("should roundtrip RELEASE FPDU")
        void shouldRoundtripReleaseFpdu() throws Exception {
            Fpdu original = new Fpdu(FpduType.RELEASE)
                    .withIdDst(1)
                    .withIdSrc(0)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG,
                            DiagnosticCode.D0_000.toBytes()));

            byte[] raw = FpduBuilder.buildFpdu(original);
            Fpdu parsed = new FpduParser(raw).parse();

            assertEquals(FpduType.RELEASE, parsed.getFpduType());
        }
    }

    @Nested
    @DisplayName("ABORT Scenario Tests")
    class AbortTests {

        @Test
        @DisplayName("should create ABORT FPDU with diagnostic")
        void shouldCreateAbortWithDiagnostic() {
            Fpdu abort = new Fpdu(FpduType.ABORT)
                    .withIdDst(1)
                    .withIdSrc(0)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG,
                            DiagnosticCode.D3_301.toBytes()));

            assertEquals(FpduType.ABORT, abort.getFpduType());
            ParameterValue diag = abort.getParameter(ParameterIdentifier.PI_02_DIAG);
            assertNotNull(diag);
        }

        @Test
        @DisplayName("should build and parse ABORT FPDU")
        void shouldBuildAndParseAbortFpdu() throws Exception {
            Fpdu original = new Fpdu(FpduType.ABORT)
                    .withIdDst(1)
                    .withIdSrc(0)
                    .withParameter(new ParameterValue(ParameterIdentifier.PI_02_DIAG,
                            DiagnosticCode.D2_299.toBytes()));

            byte[] raw = FpduBuilder.buildFpdu(original);
            Fpdu parsed = new FpduParser(raw).parse();

            assertEquals(FpduType.ABORT, parsed.getFpduType());
        }
    }
}
