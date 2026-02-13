package com.pesitwizard.server.service;

import static org.junit.jupiter.api.Assertions.*;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.model.TransferContext;
import com.pesitwizard.server.service.FpduValidator.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for FpduValidator. Tests validation rules from PeSIT E specification ANNEXE D. */
@DisplayName("FpduValidator Tests")
class FpduValidatorTest {

    private FpduValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FpduValidator();
    }

    @Nested
    @DisplayName("DTF Validation (D2-220: Article Length)")
    class DtfValidationTests {

        @Test
        @DisplayName("should accept data within record length limit")
        void shouldAcceptDataWithinLimit() {
            TransferContext transfer = new TransferContext();
            transfer.setRecordLength(1024);

            byte[] data = new byte[512];
            Fpdu fpdu = new Fpdu(FpduType.DTF);

            ValidationResult result = validator.validateDtf(fpdu, transfer, data);

            assertTrue(result.valid());
            assertNull(result.errorCode());
        }

        @Test
        @DisplayName("should accept data equal to record length")
        void shouldAcceptDataEqualToLimit() {
            TransferContext transfer = new TransferContext();
            transfer.setRecordLength(1024);

            byte[] data = new byte[1024];
            Fpdu fpdu = new Fpdu(FpduType.DTF);

            ValidationResult result = validator.validateDtf(fpdu, transfer, data);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should reject data exceeding record length (D2-220)")
        void shouldRejectDataExceedingLimit() {
            TransferContext transfer = new TransferContext();
            transfer.setRecordLength(1024);

            byte[] data = new byte[2048];
            Fpdu fpdu = new Fpdu(FpduType.DTF);

            ValidationResult result = validator.validateDtf(fpdu, transfer, data);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D2_220, result.errorCode());
            assertTrue(result.message().contains("2048"));
            assertTrue(result.message().contains("1024"));
        }

        @Test
        @DisplayName("should accept any data when record length is 0 (unlimited)")
        void shouldAcceptAnyDataWhenRecordLengthZero() {
            TransferContext transfer = new TransferContext();
            transfer.setRecordLength(0);

            byte[] data = new byte[1000000];
            Fpdu fpdu = new Fpdu(FpduType.DTF);

            ValidationResult result = validator.validateDtf(fpdu, transfer, data);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should reject when no transfer context")
        void shouldRejectWhenNoTransferContext() {
            byte[] data = new byte[100];
            Fpdu fpdu = new Fpdu(FpduType.DTF);

            ValidationResult result = validator.validateDtf(fpdu, null, data);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D3_311, result.errorCode());
        }

        @Test
        @DisplayName("should accept null data")
        void shouldAcceptNullData() {
            TransferContext transfer = new TransferContext();
            transfer.setRecordLength(1024);

            Fpdu fpdu = new Fpdu(FpduType.DTF);

            ValidationResult result = validator.validateDtf(fpdu, transfer, null);

            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("Max Entity Size Validation")
    class MaxEntitySizeValidationTests {

        @Test
        @DisplayName("should accept data within max entity size")
        void shouldAcceptDataWithinMaxEntitySize() {
            TransferContext transfer = new TransferContext();
            transfer.setMaxEntitySize(4096);

            byte[] data = new byte[2048];

            ValidationResult result = validator.validateMaxEntitySize(data, transfer);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should reject data exceeding max entity size")
        void shouldRejectDataExceedingMaxEntitySize() {
            TransferContext transfer = new TransferContext();
            transfer.setMaxEntitySize(4096);

            byte[] data = new byte[8192];

            ValidationResult result = validator.validateMaxEntitySize(data, transfer);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D2_220, result.errorCode());
        }

        @Test
        @DisplayName("should accept any data when max entity size is 0")
        void shouldAcceptAnyDataWhenMaxEntitySizeZero() {
            TransferContext transfer = new TransferContext();
            transfer.setMaxEntitySize(0);

            byte[] data = new byte[100000];

            ValidationResult result = validator.validateMaxEntitySize(data, transfer);

            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("TRANS.END Validation (D3-319: Byte/Article Count)")
    class TransEndValidationTests {

        @Test
        @DisplayName("should accept when byte count matches")
        void shouldAcceptWhenByteCountMatches() {
            TransferContext transfer = new TransferContext();
            transfer.setBytesTransferred(1000);
            transfer.setRecordsTransferred(10);

            Fpdu fpdu =
                    new Fpdu(FpduType.TRANS_END)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_27_NB_OCTETS,
                                            new byte[] {0x00, 0x00, 0x03, (byte) 0xE8})); // 1000

            ValidationResult result = validator.validateTransEnd(fpdu, transfer);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should reject when declared byte count differs (D3-319)")
        void shouldRejectWhenByteCountDiffers() {
            TransferContext transfer = new TransferContext();
            transfer.setBytesTransferred(1000);

            Fpdu fpdu =
                    new Fpdu(FpduType.TRANS_END)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_27_NB_OCTETS,
                                            new byte[] {0x00, 0x00, 0x07, (byte) 0xD0})); // 2000

            ValidationResult result = validator.validateTransEnd(fpdu, transfer);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D3_319, result.errorCode());
        }

        @Test
        @DisplayName("should accept when no byte count declared")
        void shouldAcceptWhenNoByteCountDeclared() {
            TransferContext transfer = new TransferContext();
            transfer.setBytesTransferred(1000);

            Fpdu fpdu = new Fpdu(FpduType.TRANS_END);

            ValidationResult result = validator.validateTransEnd(fpdu, transfer);

            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("CREATE Validation (D3-318: Required PIs)")
    class CreateValidationTests {

        @Test
        @DisplayName("should accept CREATE with required parameters")
        void shouldAcceptCreateWithRequiredParams() {
            Fpdu fpdu =
                    new Fpdu(FpduType.CREATE)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_12_NOM_FICHIER, "test.dat"));

            ValidationResult result = validator.validateCreate(fpdu);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should reject CREATE without filename (D3-318)")
        void shouldRejectCreateWithoutFilename() {
            Fpdu fpdu = new Fpdu(FpduType.CREATE);

            ValidationResult result = validator.validateCreate(fpdu);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D3_318, result.errorCode());
            assertTrue(result.message().contains("PI 12"));
        }

        @Test
        @DisplayName("should reject fixed format without record length")
        void shouldRejectFixedFormatWithoutRecordLength() {
            Fpdu fpdu =
                    new Fpdu(FpduType.CREATE)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_12_NOM_FICHIER, "test.dat"))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_31_FORMAT_ARTICLE,
                                            new byte[] {0x00})); // Fixed format

            ValidationResult result = validator.validateCreate(fpdu);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D3_318, result.errorCode());
            assertTrue(result.message().contains("record length"));
        }

        @Test
        @DisplayName("should accept fixed format with valid record length")
        void shouldAcceptFixedFormatWithRecordLength() {
            Fpdu fpdu =
                    new Fpdu(FpduType.CREATE)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_12_NOM_FICHIER, "test.dat"))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_31_FORMAT_ARTICLE,
                                            new byte[] {0x00})) // Fixed format
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_32_LONG_ARTICLE,
                                            new byte[] {0x00, 0x50})); // 80 bytes

            ValidationResult result = validator.validateCreate(fpdu);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should accept variable format without record length")
        void shouldAcceptVariableFormatWithoutRecordLength() {
            Fpdu fpdu =
                    new Fpdu(FpduType.CREATE)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_12_NOM_FICHIER, "test.dat"))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_31_FORMAT_ARTICLE,
                                            new byte[] {(byte) 0x80})); // Variable format

            ValidationResult result = validator.validateCreate(fpdu);

            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("SELECT Validation (D3-318: Required PIs)")
    class SelectValidationTests {

        @Test
        @DisplayName("should accept SELECT with filename")
        void shouldAcceptSelectWithFilename() {
            Fpdu fpdu =
                    new Fpdu(FpduType.SELECT)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_12_NOM_FICHIER, "test.dat"));

            ValidationResult result = validator.validateSelect(fpdu);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should reject SELECT without filename (D3-318)")
        void shouldRejectSelectWithoutFilename() {
            Fpdu fpdu = new Fpdu(FpduType.SELECT);

            ValidationResult result = validator.validateSelect(fpdu);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D3_318, result.errorCode());
        }
    }

    @Nested
    @DisplayName("CONNECT Validation (D3-318, D3-308)")
    class ConnectValidationTests {

        @Test
        @DisplayName("should accept CONNECT with requestor ID")
        void shouldAcceptConnectWithRequestorId() {
            Fpdu fpdu =
                    new Fpdu(FpduType.CONNECT)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_03_DEMANDEUR, "CLIENT001"));

            ValidationResult result = validator.validateConnect(fpdu);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should reject CONNECT without requestor ID (D3-318)")
        void shouldRejectConnectWithoutRequestorId() {
            Fpdu fpdu = new Fpdu(FpduType.CONNECT);

            ValidationResult result = validator.validateConnect(fpdu);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D3_318, result.errorCode());
            assertTrue(result.message().contains("PI 3"));
        }

        @Test
        @DisplayName("should reject unsupported version (D3-308)")
        void shouldRejectUnsupportedVersion() {
            Fpdu fpdu =
                    new Fpdu(FpduType.CONNECT)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_03_DEMANDEUR, "CLIENT001"))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_06_VERSION,
                                            new byte[] {0x00, 0x06})); // Version 6

            ValidationResult result = validator.validateConnect(fpdu);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D3_308, result.errorCode());
        }

        @Test
        @DisplayName("should accept supported version")
        void shouldAcceptSupportedVersion() {
            Fpdu fpdu =
                    new Fpdu(FpduType.CONNECT)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_03_DEMANDEUR, "CLIENT001"))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_06_VERSION,
                                            new byte[] {0x00, 0x05})); // Version 5 (PeSIT E)

            ValidationResult result = validator.validateConnect(fpdu);

            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("SYN Validation (D3-307, D3-318)")
    class SynValidationTests {

        @Test
        @DisplayName("should accept SYN with valid sync point number")
        void shouldAcceptSynWithValidSyncPoint() {
            TransferContext transfer = new TransferContext();
            transfer.setCurrentSyncPoint(5);

            Fpdu fpdu =
                    new Fpdu(FpduType.SYN)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_20_NUM_SYNC,
                                            new byte[] {0x00, 0x00, 0x06})); // Sync point 6

            ValidationResult result = validator.validateSyn(fpdu, transfer);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should reject SYN without sync point number (D3-318)")
        void shouldRejectSynWithoutSyncPoint() {
            TransferContext transfer = new TransferContext();

            Fpdu fpdu = new Fpdu(FpduType.SYN);

            ValidationResult result = validator.validateSyn(fpdu, transfer);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D3_318, result.errorCode());
            assertTrue(result.message().contains("PI 20"));
        }

        @Test
        @DisplayName("should reject SYN with non-increasing sync point (D3-307)")
        void shouldRejectSynWithNonIncreasingSyncPoint() {
            TransferContext transfer = new TransferContext();
            transfer.setCurrentSyncPoint(10);

            Fpdu fpdu =
                    new Fpdu(FpduType.SYN)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_20_NUM_SYNC,
                                            new byte[] {
                                                0x00, 0x00, 0x05
                                            })); // Sync point 5 (less than current 10)

            ValidationResult result = validator.validateSyn(fpdu, transfer);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D3_307, result.errorCode());
        }

        @Test
        @DisplayName("should reject SYN with same sync point number (D3-307)")
        void shouldRejectSynWithSameSyncPoint() {
            TransferContext transfer = new TransferContext();
            transfer.setCurrentSyncPoint(5);

            Fpdu fpdu =
                    new Fpdu(FpduType.SYN)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_20_NUM_SYNC,
                                            new byte[] {0x00, 0x00, 0x05})); // Same sync point 5

            ValidationResult result = validator.validateSyn(fpdu, transfer);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D3_307, result.errorCode());
        }
    }

    @Nested
    @DisplayName("D2-222: Too Much Data Without Sync Point")
    class DataWithoutSyncPointValidationTests {

        @Test
        @DisplayName("should accept data when sync points not required")
        void shouldAcceptDataWhenSyncPointsNotRequired() {
            TransferContext transfer = new TransferContext();
            transfer.setBytesTransferred(0);
            // No sync point interval configured = unlimited

            ValidationResult result = validator.validateDataWithoutSyncPoint(transfer, 100000, 0);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should accept data within sync point interval")
        void shouldAcceptDataWithinSyncPointInterval() {
            TransferContext transfer = new TransferContext();
            transfer.setBytesTransferred(0);

            // 50KB transferred, interval is 64KB, last sync at 0 -> OK
            ValidationResult result =
                    validator.validateDataWithoutSyncPoint(transfer, 50000, 65536);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should reject data exceeding sync point interval (D2-222)")
        void shouldRejectDataExceedingSyncPointInterval() {
            TransferContext transfer = new TransferContext();
            transfer.setBytesTransferred(0); // Last sync at 0

            // 100KB transferred since last sync, interval is 64KB -> ERROR
            ValidationResult result =
                    validator.validateDataWithoutSyncPoint(transfer, 100000, 65536);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D2_222, result.errorCode());
            assertTrue(result.message().contains("sync"));
        }

        @Test
        @DisplayName("should track bytes since last sync point")
        void shouldTrackBytesSinceLastSyncPoint() {
            TransferContext transfer = new TransferContext();
            transfer.setBytesTransferred(50000); // Already transferred 50KB
            transfer.setCurrentSyncPoint(1); // Had one sync at ~50KB

            // New chunk of 40KB, but we're counting from last sync
            // If last sync was at 50KB and we're now at 90KB, that's 40KB since sync
            ValidationResult result =
                    validator.validateDataWithoutSyncPoint(transfer, 40000, 65536);

            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("PI Order Validation")
    class PiOrderValidationTests {

        @Test
        @DisplayName("should accept CONNECT with correct PI order")
        void shouldAcceptConnectWithCorrectPiOrder() {
            Fpdu fpdu =
                    new Fpdu(FpduType.CONNECT)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_03_DEMANDEUR, "CLIENT"))
                            .withParameter(
                                    new ParameterValue(ParameterIdentifier.PI_04_SERVEUR, "SERVER"))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_05_CONTROLE_ACCES, "PASS"))
                            .withParameter(new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2))
                            .withParameter(
                                    new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 0));

            ValidationResult result = validator.validatePiOrder(fpdu);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should reject CONNECT with incorrect PI order")
        void shouldRejectConnectWithIncorrectPiOrder() {
            // PI 22 before PI 06 is incorrect
            Fpdu fpdu =
                    new Fpdu(FpduType.CONNECT)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_03_DEMANDEUR, "CLIENT"))
                            .withParameter(
                                    new ParameterValue(ParameterIdentifier.PI_22_TYPE_ACCES, 0))
                            .withParameter(
                                    new ParameterValue(ParameterIdentifier.PI_06_VERSION, 2));

            ValidationResult result = validator.validatePiOrder(fpdu);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D3_304, result.errorCode());
        }

        @Test
        @DisplayName("should accept CREATE with correct PI order")
        void shouldAcceptCreateWithCorrectPiOrder() {
            Fpdu fpdu =
                    new Fpdu(FpduType.CREATE)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_12_NOM_FICHIER, "test.dat"))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE, 4096))
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_32_LONG_ARTICLE, 1024));

            ValidationResult result = validator.validatePiOrder(fpdu);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should allow missing optional PIs")
        void shouldAllowMissingOptionalPis() {
            Fpdu fpdu =
                    new Fpdu(FpduType.CONNECT)
                            .withParameter(
                                    new ParameterValue(
                                            ParameterIdentifier.PI_03_DEMANDEUR, "CLIENT"));
            // Missing PI 04, 05, 06, etc - should be OK

            ValidationResult result = validator.validatePiOrder(fpdu);

            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("File Size Validation (D2-224)")
    class FileSizeValidationTests {

        @Test
        @DisplayName("should accept file size within announced size")
        void shouldAcceptFileSizeWithinLimit() {
            ValidationResult result = validator.validateFileSize(1000, 2000);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should accept file size equal to announced size")
        void shouldAcceptFileSizeEqualToAnnounced() {
            ValidationResult result = validator.validateFileSize(2000, 2000);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should reject file size exceeding announced size (D2-224)")
        void shouldRejectFileSizeExceedingAnnounced() {
            ValidationResult result = validator.validateFileSize(3000, 2000);

            assertFalse(result.valid());
            assertEquals(DiagnosticCode.D2_224, result.errorCode());
            assertTrue(result.message().contains("3000"));
            assertTrue(result.message().contains("2000"));
        }

        @Test
        @DisplayName("should accept any file size when announced size is 0")
        void shouldAcceptAnyFileSizeWhenAnnouncedZero() {
            ValidationResult result = validator.validateFileSize(1000000, 0);

            assertTrue(result.valid());
        }
    }
}
