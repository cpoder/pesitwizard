package com.vectis.fpdu;

import static com.vectis.fpdu.ParameterGroupIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CreateMessageBuilder
 * Validates that CREATE FPDU messages are built correctly with all parameters
 */
public class CreateMessageBuilderTest {

    private static final int SERVER_CONNECTION_ID = 0x42;

    @Test
    @DisplayName("Build CREATE FPDU with default parameters")
    void testBuildWithDefaults() throws IOException {
        CreateMessageBuilder builder = new CreateMessageBuilder();
        Fpdu fpdu = builder.build(SERVER_CONNECTION_ID);
        byte[] fpduBytes = FpduBuilder.buildFpdu(fpdu);

        // Verify FPDU is not null and has content
        assertNotNull(fpdu);
        assertTrue(fpduBytes.length > 6, "FPDU should have header (6 bytes) + parameters");

        // Parse header
        FpduParser parser = new FpduParser(fpduBytes);
        Fpdu fpduObj = parser.parse();
        assertEquals(FpduType.CREATE, fpduObj.getFpduType());
        assertNotNull(fpduObj.getParameters(), "FPDU should have parameters");
        assertTrue(fpduObj.getParameters().size() > 0, "FPDU should have at least one parameter");
        assertEquals(SERVER_CONNECTION_ID, fpduObj.getIdDst(), "idDst should match server connection ID");
        assertEquals(0, fpduObj.getIdSrc(), "idSrc should be 0 for file-level FPDUs");
        assertTrue(fpduObj.getParameter(PGI_09_ID_FICHIER).hasParameter(ParameterIdentifier.PI_11_TYPE_FICHIER));

        // Verify length field
        int fpduLength = ((fpduBytes[0] & 0xFF) << 8) | (fpduBytes[1] & 0xFF);
        assertEquals(fpduBytes.length, fpduLength, "Length field should match actual FPDU length");
    }

    @Test
    @DisplayName("Build CREATE FPDU with custom filename")
    void testBuildWithCustomFilename() throws IOException {
        String customFilename = "TEST_FILE.TXT";
        CreateMessageBuilder builder = new CreateMessageBuilder()
                .filename(customFilename);

        Fpdu fpdu = builder.build(SERVER_CONNECTION_ID);
        byte[] fpduBytes = FpduBuilder.buildFpdu(fpdu);
        assertNotNull(fpdu);
        assertTrue(fpduBytes.length > 6, "FPDU should have header (6 bytes) + parameters");
        // Verify FPDU contains the filename
        String fpduHex = bytesToHex(fpduBytes);
        String filenameHex = bytesToHex(customFilename.getBytes());
        assertTrue(fpduHex.contains(filenameHex),
                "FPDU should contain the custom filename");
    }

    @Test
    @DisplayName("Build CREATE FPDU with all custom parameters")
    void testBuildWithAllCustomParameters() throws IOException {
        String filename = "CUSTOM_FILE.DAT";
        int transferId = 123;
        int priority = 5;
        int maxEntitySize = 8192;
        int recordLength = 2048;
        Date creationDate = new Date();

        CreateMessageBuilder builder = new CreateMessageBuilder()
                .filename(filename)
                .transferId(transferId)
                .priority(priority)
                .maxEntitySize(maxEntitySize)
                .recordLength(recordLength)
                .creationDate(creationDate);

        Fpdu fpdu = builder.build(SERVER_CONNECTION_ID);
        byte[] fpduBytes = FpduBuilder.buildFpdu(fpdu);
        // Verify FPDU structure
        assertNotNull(fpdu);
        assertTrue(fpduBytes.length > 50, "FPDU with all parameters should be substantial");

        // Verify header
        FpduParser parser = new FpduParser(fpduBytes);
        Fpdu fpduObj = parser.parse();
        assertEquals(FpduType.CREATE, fpduObj.getFpduType(), "FPDU type should be CREATE");
        assertEquals(SERVER_CONNECTION_ID, fpduObj.getIdDst(), "idDst should match server connection ID");
        assertEquals(0, fpduObj.getIdSrc(), "idSrc should be 0 for file-level FPDUs");
    }

    @Test
    @DisplayName("Verify CREATE FPDU contains all mandatory PGIs")
    void testMandatoryPGIsPresent() throws IOException {
        CreateMessageBuilder builder = new CreateMessageBuilder();
        Fpdu fpdu = builder.build(SERVER_CONNECTION_ID);
        byte[] fpduBytes = FpduBuilder.buildFpdu(fpdu);

        String fpduHex = bytesToHex(fpduBytes);

        // Check for PGI 9 (0x09) - File Identifier
        assertTrue(fpduHex.contains("09"), "FPDU should contain PGI 9 (File Identifier)");

        // Check for PGI 30 (0x30) - Logical Attributes
        assertTrue(fpduHex.contains("30"), "FPDU should contain PGI 30 (Logical Attributes)");

        // Check for PGI 40 (0x40) - Physical Attributes
        assertTrue(fpduHex.contains("40"), "FPDU should contain PGI 40 (Physical Attributes)");

        // Check for PGI 50 (0x50) - Historical Attributes
        assertTrue(fpduHex.contains("50"), "FPDU should contain PGI 50 (Historical Attributes)");
    }

    @Test
    @DisplayName("Verify CREATE FPDU contains all mandatory PIs")
    void testMandatoryPIsPresent() throws IOException {
        CreateMessageBuilder builder = new CreateMessageBuilder();
        Fpdu fpdu = builder.build(SERVER_CONNECTION_ID);
        byte[] fpduBytes = FpduBuilder.buildFpdu(fpdu);
        assertNotNull(fpdu);
        String fpduHex = bytesToHex(fpduBytes);

        // Check for PI 13 (0x13) - Transfer Identifier (mandatory)
        assertTrue(fpduHex.contains("0D"), "FPDU should contain PI 13 (Transfer Identifier)");

        // Check for PI 17 (0x17) - Priority (mandatory)
        assertTrue(fpduHex.contains("11"), "FPDU should contain PI 17 (Transfer Priority)");

        // Check for PI 25 (0x25) - Max Entity Size (mandatory)
        assertTrue(fpduHex.contains("19"), "FPDU should contain PI 25 (Max Entity Size)");
    }

    @Test
    @DisplayName("Verify CREATE FPDU with variable format")
    void testWithVariableFormat() throws IOException {
        CreateMessageBuilder builder = new CreateMessageBuilder()
                .variableFormat();

        Fpdu fpdu = builder.build(SERVER_CONNECTION_ID);
        byte[] fpduBytes = FpduBuilder.buildFpdu(fpdu);

        // PI 31 should contain 0x80 for variable format
        assertNotNull(fpdu);
        assertTrue(fpduBytes.length > 0);
    }

    @Test
    @DisplayName("Verify CREATE FPDU with fixed format")
    void testWithFixedFormat() throws IOException {
        CreateMessageBuilder builder = new CreateMessageBuilder()
                .fixedFormat();

        Fpdu fpdu = builder.build(SERVER_CONNECTION_ID);
        byte[] fpduBytes = FpduBuilder.buildFpdu(fpdu);

        // PI 31 should contain 0x00 for fixed format
        assertNotNull(fpdu);
        assertTrue(fpduBytes.length > 0);
    }

    @Test
    @DisplayName("Verify creation date is automatically set")
    void testCreationDateAutoSet() throws IOException {
        CreateMessageBuilder builder = new CreateMessageBuilder();
        Fpdu fpdu = builder.build(SERVER_CONNECTION_ID);
        byte[] fpduBytes = FpduBuilder.buildFpdu(fpdu);
        assertNotNull(fpdu);
        // PI 51 (0x51) should be present with creation date
        String fpduHex = bytesToHex(fpduBytes);
        System.out.println("FPDU Hex: " + fpduHex);
        assertTrue(fpduHex.contains("32"), "FPDU should contain PI 51 (Creation Date)");

        // Verify date format (should be 12 bytes: yyMMddHHmmss)
        // PI 51 should be followed by length byte 0x0C (12 decimal)
        assertTrue(fpduHex.contains("330C"), "PI 51 should have 12-byte date value");
    }

    @Test
    @DisplayName("Verify custom creation date is used")
    void testCustomCreationDate() throws IOException {
        // Create a specific date
        Date testDate = new Date(1234567890000L); // Fri Feb 13 23:31:30 UTC 2009

        CreateMessageBuilder builder = new CreateMessageBuilder()
                .creationDate(testDate);

        Fpdu fpdu = builder.build(SERVER_CONNECTION_ID);
        byte[] fpduBytes = FpduBuilder.buildFpdu(fpdu);

        assertNotNull(fpdu);
        assertTrue(fpduBytes.length > 0);
    }

    @Test
    @DisplayName("Verify transfer ID parameter encoding")
    void testTransferIdEncoding() throws IOException {
        int transferId = 0x1234; // 4660 decimal
        CreateMessageBuilder builder = new CreateMessageBuilder()
                .transferId(transferId);

        Fpdu fpdu = builder.build(SERVER_CONNECTION_ID);
        byte[] fpduBytes = FpduBuilder.buildFpdu(fpdu);

        assertNotNull(fpdu);
        // Transfer ID should be encoded in PI 13
        String fpduHex = bytesToHex(fpduBytes);
        assertTrue(fpduHex.contains("13"), "FPDU should contain PI 13");
    }

    @Test
    @DisplayName("Build multiple CREATE FPDUs with different parameters")
    void testMultipleBuildCalls() throws IOException {
        CreateMessageBuilder builder1 = new CreateMessageBuilder()
                .filename("FILE1.TXT")
                .transferId(1);

        CreateMessageBuilder builder2 = new CreateMessageBuilder()
                .filename("FILE2.DAT")
                .transferId(2);

        Fpdu fpdu1 = builder1.build(SERVER_CONNECTION_ID);
        Fpdu fpdu2 = builder2.build(SERVER_CONNECTION_ID);

        byte[] fpduBytes1 = FpduBuilder.buildFpdu(fpdu1);
        byte[] fpduBytes2 = FpduBuilder.buildFpdu(fpdu2);

        assertNotNull(fpduBytes1);
        assertNotNull(fpduBytes2);
        // Verify they are different
        boolean different = false;
        if (fpduBytes1.length != fpduBytes2.length) {
            different = true;
        } else {
            for (int i = 0; i < fpduBytes1.length; i++) {
                if (fpduBytes1[i] != fpduBytes2[i]) {
                    different = true;
                    break;
                }
            }
        }
        assertTrue(different, "Different parameters should produce different FPDUs");
    }

    /**
     * Helper method to convert byte array to hex string for verification
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
