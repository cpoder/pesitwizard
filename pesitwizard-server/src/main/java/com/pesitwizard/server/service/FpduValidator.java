package com.pesitwizard.server.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.model.TransferContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates FPDU content according to PeSIT specification.
 * Implements validation rules from ANNEXE D of the PeSIT E specification.
 */
@Slf4j
@Service
public class FpduValidator {

    /**
     * Result of a validation check.
     */
    public record ValidationResult(boolean valid, DiagnosticCode errorCode, String message) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult error(DiagnosticCode code, String message) {
            return new ValidationResult(false, code, message);
        }

        public static ValidationResult error(DiagnosticCode code) {
            return new ValidationResult(false, code, code.getMessage());
        }
    }

    /**
     * Validate DTF (Data Transfer) FPDU.
     * Rules:
     * - D2-220: Article length must not exceed announced record length (PI 32)
     * - D2-222: Data without sync point must not exceed configured limit
     */
    public ValidationResult validateDtf(Fpdu fpdu, TransferContext transfer, byte[] data) {
        if (transfer == null) {
            return ValidationResult.error(DiagnosticCode.D3_311, "No active transfer context");
        }

        // D2-220: Validate article/record length
        // Skip validation for binary files (format 0x80 = BU) which have no fixed
        // record length
        int recordFormat = transfer.getRecordFormat();
        int recordLength = transfer.getRecordLength();
        boolean isBinaryFormat = (recordFormat & 0x80) != 0;

        if (!isBinaryFormat && recordLength > 0 && data != null && data.length > recordLength) {
            log.warn("Article length {} exceeds announced record length {}", data.length, recordLength);
            return ValidationResult.error(DiagnosticCode.D2_220,
                    String.format("Article length %d exceeds announced record length %d", data.length, recordLength));
        }

        // D2-222: Check for too much data without sync point
        // This validation is typically done at a higher level based on transfer
        // configuration

        return ValidationResult.ok();
    }

    /**
     * Validate TRANS.END FPDU.
     * Rules:
     * - D2-224: File size must not exceed announced size
     * - D3-319: Byte count and article count must be correct
     */
    public ValidationResult validateTransEnd(Fpdu fpdu, TransferContext transfer) {
        if (transfer == null) {
            return ValidationResult.error(DiagnosticCode.D3_311, "No active transfer context");
        }

        // Extract PI 27 (byte count) and PI 28 (article count) from TRANS.END if
        // present
        Optional<Long> declaredBytes = extractLongParameter(fpdu, ParameterIdentifier.PI_27_NB_OCTETS);
        Optional<Integer> declaredArticles = extractIntParameter(fpdu, ParameterIdentifier.PI_28_NB_ARTICLES);

        // D3-319: Validate byte count if declared
        if (declaredBytes.isPresent()) {
            long actualBytes = transfer.getBytesTransferred();
            if (declaredBytes.get() != actualBytes) {
                log.warn("Declared byte count {} differs from actual {}", declaredBytes.get(), actualBytes);
                return ValidationResult.error(DiagnosticCode.D3_319,
                        String.format("Declared byte count %d differs from actual %d", declaredBytes.get(),
                                actualBytes));
            }
        }

        // D3-319: Validate article count if declared
        if (declaredArticles.isPresent()) {
            int actualArticles = transfer.getRecordsTransferred();
            if (declaredArticles.get() != actualArticles) {
                log.warn("Declared article count {} differs from actual {}", declaredArticles.get(), actualArticles);
                return ValidationResult.error(DiagnosticCode.D3_319,
                        String.format("Declared article count %d differs from actual %d", declaredArticles.get(),
                                actualArticles));
            }
        }

        return ValidationResult.ok();
    }

    /**
     * Validate CREATE FPDU.
     * Rules:
     * - D3-318: Required PIs must be present (PGI 9 with PI 11, PI 12)
     * - PI 32 (record length) must be positive if PI 31 indicates fixed format
     */
    public ValidationResult validateCreate(Fpdu fpdu) {
        // D3-318: Check required parameters
        if (!hasParameter(fpdu, ParameterIdentifier.PI_12_NOM_FICHIER)) {
            return ValidationResult.error(DiagnosticCode.D3_318, "Missing required PI 12 (filename)");
        }

        // Validate record length if present
        Optional<Integer> recordLength = extractIntParameter(fpdu, ParameterIdentifier.PI_32_LONG_ARTICLE);
        Optional<Integer> recordFormat = extractIntParameter(fpdu, ParameterIdentifier.PI_31_FORMAT_ARTICLE);

        // Fixed format (0x00) requires positive record length
        if (recordFormat.isPresent() && recordFormat.get() == 0x00) {
            if (recordLength.isEmpty() || recordLength.get() <= 0) {
                return ValidationResult.error(DiagnosticCode.D3_318,
                        "Fixed format requires positive record length (PI 32)");
            }
        }

        return ValidationResult.ok();
    }

    /**
     * Validate SELECT FPDU.
     * Rules:
     * - D3-318: Required PIs must be present (PGI 9 with PI 11, PI 12)
     */
    public ValidationResult validateSelect(Fpdu fpdu) {
        // D3-318: Check required parameters
        if (!hasParameter(fpdu, ParameterIdentifier.PI_12_NOM_FICHIER)) {
            return ValidationResult.error(DiagnosticCode.D3_318, "Missing required PI 12 (filename)");
        }

        return ValidationResult.ok();
    }

    /**
     * Validate CONNECT FPDU.
     * Rules:
     * - D3-318: PI 3 (requestor ID) and PI 4 (server ID) are required
     * - D3-308: Version must be supported
     */
    public ValidationResult validateConnect(Fpdu fpdu) {
        // D3-318: Check required parameters
        if (!hasParameter(fpdu, ParameterIdentifier.PI_03_DEMANDEUR)) {
            return ValidationResult.error(DiagnosticCode.D3_318, "Missing required PI 3 (requestor ID)");
        }

        // Version check (if present)
        Optional<Integer> version = extractIntParameter(fpdu, ParameterIdentifier.PI_06_VERSION);
        if (version.isPresent() && version.get() > 5) {
            // PeSIT E is version 5
            return ValidationResult.error(DiagnosticCode.D3_308,
                    String.format("Unsupported version %d (max supported: 5)", version.get()));
        }

        return ValidationResult.ok();
    }

    /**
     * Validate SYN (synchronization point) FPDU.
     * Rules:
     * - D3-318: PI 20 (sync point number) is required
     * - Sync point number must be greater than previous
     */
    public ValidationResult validateSyn(Fpdu fpdu, TransferContext transfer) {
        // D3-318: Check required PI 20
        Optional<Integer> syncPoint = extractIntParameter(fpdu, ParameterIdentifier.PI_20_NUM_SYNC);
        if (syncPoint.isEmpty()) {
            return ValidationResult.error(DiagnosticCode.D3_318, "Missing required PI 20 (sync point number)");
        }

        // Sync point must be strictly increasing
        if (transfer != null && syncPoint.get() <= transfer.getCurrentSyncPoint()) {
            return ValidationResult.error(DiagnosticCode.D3_307,
                    String.format("Sync point %d must be greater than current %d",
                            syncPoint.get(), transfer.getCurrentSyncPoint()));
        }

        return ValidationResult.ok();
    }

    /**
     * Validate max entity size constraint.
     * The data chunk must not exceed the negotiated max entity size (PI 25).
     */
    public ValidationResult validateMaxEntitySize(byte[] data, TransferContext transfer) {
        if (transfer == null || data == null) {
            return ValidationResult.ok();
        }

        int maxEntitySize = transfer.getMaxEntitySize();
        if (maxEntitySize > 0 && data.length > maxEntitySize) {
            log.warn("Data chunk {} exceeds max entity size {}", data.length, maxEntitySize);
            return ValidationResult.error(DiagnosticCode.D2_220,
                    String.format("Data chunk %d exceeds max entity size %d", data.length, maxEntitySize));
        }

        return ValidationResult.ok();
    }

    /**
     * Validate D2-222: Too much data without sync point.
     * Checks if bytes transferred since last sync point exceeds the configured
     * interval.
     * 
     * @param transfer           Current transfer context
     * @param bytesSinceLastSync Bytes received since last sync point
     * @param syncIntervalBytes  Configured sync point interval (0 = no limit)
     */
    public ValidationResult validateDataWithoutSyncPoint(TransferContext transfer,
            long bytesSinceLastSync, long syncIntervalBytes) {
        // No limit configured
        if (syncIntervalBytes <= 0) {
            return ValidationResult.ok();
        }

        if (bytesSinceLastSync > syncIntervalBytes) {
            log.warn("D2-222: {} bytes without sync point exceeds interval {}",
                    bytesSinceLastSync, syncIntervalBytes);
            return ValidationResult.error(DiagnosticCode.D2_222,
                    String.format("Too much data without sync point: %d bytes exceeds interval %d",
                            bytesSinceLastSync, syncIntervalBytes));
        }

        return ValidationResult.ok();
    }

    /**
     * Validate PI order in FPDU.
     * PIs must appear in ascending order of their identifier within each FPDU type.
     * D3-304: Invalid PI order.
     */
    public ValidationResult validatePiOrder(Fpdu fpdu) {
        var parameters = fpdu.getParameters();
        if (parameters == null || parameters.size() < 2) {
            return ValidationResult.ok();
        }

        int lastPiId = -1;
        for (ParameterValue pv : parameters) {
            int currentPiId = pv.getParameter().getId();
            if (currentPiId < lastPiId) {
                log.warn("D3-304: PI {} appears after PI {} (incorrect order)",
                        currentPiId, lastPiId);
                return ValidationResult.error(DiagnosticCode.D3_304,
                        String.format("Invalid PI order: PI %d after PI %d", currentPiId, lastPiId));
            }
            lastPiId = currentPiId;
        }

        return ValidationResult.ok();
    }

    /**
     * Validate file size against announced size (if any).
     * D2-224: File size must not exceed announced size in CREATE.
     */
    public ValidationResult validateFileSize(long actualSize, long announcedSize) {
        if (announcedSize > 0 && actualSize > announcedSize) {
            log.warn("Actual file size {} exceeds announced size {}", actualSize, announcedSize);
            return ValidationResult.error(DiagnosticCode.D2_224,
                    String.format("File size %d exceeds announced size %d", actualSize, announcedSize));
        }
        return ValidationResult.ok();
    }

    // Helper methods

    private boolean hasParameter(Fpdu fpdu, ParameterIdentifier pi) {
        return fpdu.getParameters().stream()
                .anyMatch(p -> p.getParameter() == pi);
    }

    private Optional<Integer> extractIntParameter(Fpdu fpdu, ParameterIdentifier pi) {
        return fpdu.getParameters().stream()
                .filter(p -> p.getParameter() == pi)
                .findFirst()
                .map(this::toInt);
    }

    private Optional<Long> extractLongParameter(Fpdu fpdu, ParameterIdentifier pi) {
        return fpdu.getParameters().stream()
                .filter(p -> p.getParameter() == pi)
                .findFirst()
                .map(this::toLong);
    }

    private int toInt(ParameterValue pv) {
        byte[] value = pv.getValue();
        if (value == null || value.length == 0) {
            return 0;
        }
        int result = 0;
        for (byte b : value) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    private long toLong(ParameterValue pv) {
        byte[] value = pv.getValue();
        if (value == null || value.length == 0) {
            return 0;
        }
        long result = 0;
        for (byte b : value) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }
}
