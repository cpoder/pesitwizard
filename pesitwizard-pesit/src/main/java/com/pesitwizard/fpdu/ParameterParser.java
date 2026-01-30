package com.pesitwizard.fpdu;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;

/**
 * Utility class for parsing PeSIT FPDU parameters.
 * Centralizes numeric byte parsing logic used by both client and server.
 */
public final class ParameterParser {

    private ParameterParser() {
        // Utility class
    }

    /**
     * Parse a byte array as a big-endian unsigned integer.
     *
     * @param bytes Byte array (1-4 bytes)
     * @return Parsed integer value, or 0 if bytes is null or empty
     */
    public static int parseNumeric(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return 0;
        }
        int value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }

    /**
     * Parse a byte array as a big-endian unsigned long.
     *
     * @param bytes Byte array (1-8 bytes)
     * @return Parsed long value, or 0 if bytes is null or empty
     */
    public static long parseNumericLong(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return 0;
        }
        long value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }

    /**
     * Extract PI_07 (Sync Points interval) from ACONNECT FPDU.
     *
     * @param fpdu ACONNECT FPDU
     * @return Sync interval in KB, or 0 if not present
     */
    public static int parsePI07SyncInterval(Fpdu fpdu) {
        ParameterValue pv = fpdu.getParameter(PI_07_SYNC_POINTS);
        if (pv != null && pv.getValue() != null && pv.getValue().length >= 2) {
            return ((pv.getValue()[0] & 0xFF) << 8) | (pv.getValue()[1] & 0xFF);
        }
        return 0;
    }

    /**
     * Extract PI_18 (Restart Point) from FPDU.
     *
     * @param fpdu FPDU containing PI_18
     * @return Restart point number, or 0 if not present
     */
    public static int parsePI18RestartPoint(Fpdu fpdu) {
        ParameterValue pv = fpdu.getParameter(PI_18_POINT_RELANCE);
        if (pv != null && pv.getValue() != null) {
            return parseNumeric(pv.getValue());
        }
        return 0;
    }

    /**
     * Extract PI_20 (Sync Number) from SYN/ACK_SYN FPDU.
     *
     * @param fpdu SYN or ACK_SYN FPDU
     * @return Sync point number, or 0 if not present
     */
    public static int parsePI20SyncNumber(Fpdu fpdu) {
        ParameterValue pv = fpdu.getParameter(PI_20_NUM_SYNC);
        if (pv != null && pv.getValue() != null) {
            return parseNumeric(pv.getValue());
        }
        return 0;
    }

    /**
     * Extract PI_25 (Max Entity Size) from FPDU.
     *
     * @param fpdu FPDU containing PI_25
     * @return Max entity size in bytes, or 0 if not present
     */
    public static int parsePI25MaxEntitySize(Fpdu fpdu) {
        ParameterValue pv = fpdu.getParameter(PI_25_TAILLE_MAX_ENTITE);
        if (pv != null && pv.getValue() != null) {
            return parseNumeric(pv.getValue());
        }
        return 0;
    }

    /**
     * Extract PI_32 (Record Length) from FPDU.
     *
     * @param fpdu FPDU containing PI_32
     * @return Record length in bytes, or 0 if not present
     */
    public static int parsePI32RecordLength(Fpdu fpdu) {
        ParameterValue pv = fpdu.getParameter(PI_32_LONG_ARTICLE);
        if (pv != null && pv.getValue() != null) {
            return parseNumeric(pv.getValue());
        }
        return 0;
    }

    /**
     * Extract PI_02 (Diagnostic Code) from FPDU.
     *
     * @param fpdu FPDU containing PI_02
     * @return 3-byte diagnostic code, or null if not present
     */
    public static byte[] parsePI02DiagnosticCode(Fpdu fpdu) {
        ParameterValue pv = fpdu.getParameter(PI_02_DIAG);
        if (pv != null && pv.getValue() != null) {
            return pv.getValue();
        }
        return null;
    }

    /**
     * Check if PI_02 diagnostic code indicates an error (non-zero).
     *
     * @param fpdu FPDU containing PI_02
     * @return true if diagnostic code indicates an error
     */
    public static boolean hasError(Fpdu fpdu) {
        byte[] diag = parsePI02DiagnosticCode(fpdu);
        if (diag == null) {
            return false;
        }
        for (byte b : diag) {
            if (b != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract PI_42 (File Size in KB) from PGI_40 in ACK_SELECT.
     *
     * @param fpdu ACK_SELECT FPDU
     * @return File size in bytes, or 0 if not present
     */
    public static long parseFileSizeFromAckSelect(Fpdu fpdu) {
        ParameterValue pgi40 = fpdu.getParameter(ParameterGroupIdentifier.PGI_40_ATTR_PHYSIQUES);
        if (pgi40 != null && pgi40.getValues() != null) {
            for (ParameterValue pv : pgi40.getValues()) {
                if (pv.getParameter() == PI_42_MAX_RESERVATION) {
                    return parseNumericLong(pv.getValue()) * 1024L;
                }
            }
        }
        return 0;
    }
}
