package com.pesitwizard.fpdu;

import lombok.extern.slf4j.Slf4j;

/**
 * EBCDIC/ASCII converter for PeSIT EBCDIC encoding support.
 *
 * <p>PeSIT implementations on IBM mainframes send FPDUs in EBCDIC encoding (PURE EBCDIC), while
 * modern systems use ASCII. This class provides automatic detection and conversion.
 */
@Slf4j
public class EbcdicConverter {

    /** EBCDIC to ASCII conversion table (code page 037 - US EBCDIC) */
    private static final byte[] EBCDIC_TO_ASCII = new byte[256];

    /** ASCII to EBCDIC conversion table (code page 037 - US EBCDIC) */
    private static final byte[] ASCII_TO_EBCDIC = new byte[256];

    static {
        // Initialize EBCDIC to ASCII conversion table
        String ebcdicChars =
                "\u0000\u0001\u0002\u0003\u009C\t\u0086\u007F\u0097\u008D\u008E\u000B\f\r\u000E\u000F"
                        + "\u0010\u0011\u0012\u0013\u009D\u0085\b\u0087\u0018\u0019\u0092\u008F\u001C\u001D\u001E\u001F"
                        + "\u0080\u0081\u0082\u0083\u0084\n\u0017\u001B\u0088\u0089\u008A\u008B\u008C\u0005\u0006\u0007"
                        + "\u0090\u0091\u0016\u0093\u0094\u0095\u0096\u0004\u0098\u0099\u009A\u009B\u0014\u0015\u009E\u001A"
                        + " \u00A0\u00E2\u00E4\u00E0\u00E1\u00E3\u00E5\u00E7\u00F1\u00A2.<(+|"
                        + "&\u00E9\u00EA\u00EB\u00E8\u00ED\u00EE\u00EF\u00EC\u00DF!$*);^"
                        + "-/\u00C2\u00C4\u00C0\u00C1\u00C3\u00C5\u00C7\u00D1\u00A6,%_>?"
                        + "\u00F8\u00C9\u00CA\u00CB\u00C8\u00CD\u00CE\u00CF\u00CC`:#@'=\""
                        + "\u00D8abcdefghi\u00AB\u00BB\u00F0\u00FD\u00FE\u00B1"
                        + "\u00B0jklmnopqr\u00AA\u00BA\u00E6\u00B8\u00C6\u00A4"
                        + "\u00B5~stuvwxyz\u00A1\u00BF\u00D0\u00DD\u00DE\u00AE"
                        + "\u00A2\u00A3\u00A5\u00B7\u00A9\u00A7\u00B6\u00BC\u00BD\u00BE\u00AC\u00AF\u00A8\u00B4\u00D7\u00E6"
                        + "{ABCDEFGHI\u00AD\u00F4\u00F6\u00F2\u00F3\u00F5"
                        + "}JKLMNOPQR\u00B9\u00FB\u00FC\u00F9\u00FA\u00FF"
                        + "\\\u00F7STUVWXYZ\u00B2\u00D4\u00D6\u00D2\u00D3\u00D5"
                        + "0123456789\u00B3\u00DB\u00DC\u00D9\u00DA\u009F";

        for (int i = 0; i < 256; i++) {
            EBCDIC_TO_ASCII[i] = (byte) ebcdicChars.charAt(i);
        }

        // Initialize ASCII to EBCDIC conversion table (reverse mapping)
        for (int i = 0; i < 256; i++) {
            ASCII_TO_EBCDIC[i] = (byte) i; // Default: identity mapping
        }
        for (int ebcdic = 0; ebcdic < 256; ebcdic++) {
            int ascii = EBCDIC_TO_ASCII[ebcdic] & 0xFF;
            ASCII_TO_EBCDIC[ascii] = (byte) ebcdic;
        }
    }

    /**
     * Detect if data is EBCDIC or ASCII encoded.
     *
     * <p>Detection heuristic: - EBCDIC alphabetic characters are in range 0x81-0xE9 (mostly
     * 0xC0-0xE9) - ASCII printable characters are in range 0x20-0x7E - PeSIT FPDU header (first 6
     * bytes) is BINARY, not text-encoded - PeSIT parameter values (bytes 6+) are text-encoded
     * (ASCII or EBCDIC)
     *
     * <p>We look for EBCDIC patterns in PARAMETER data (skip header): - High bit set on most
     * parameter bytes (> 0x80) - Specific EBCDIC character ranges (0xC0-0xE9 for letters)
     *
     * @param data Raw FPDU data
     * @return true if data appears to be EBCDIC, false if ASCII
     */
    public static boolean isEbcdic(byte[] data) {
        if (data == null || data.length < 6) {
            return false;
        }

        // PURE EBCDIC mode: entire FPDU in EBCDIC encoding (standard PeSIT on IBM systems)
        // EBCDIC data has high bytes (>= 0x80) throughout, especially in what should be the header
        // Check first 6 bytes - if they have high bytes, it's EBCDIC
        int highBytesInHeader = 0;
        for (int i = 0; i < 6 && i < data.length; i++) {
            if ((data[i] & 0xFF) >= 0x80) {
                highBytesInHeader++;
            }
        }

        // If header has many high bytes (>= 4 out of 6), it's EBCDIC
        boolean isEbcdic = highBytesInHeader >= 4;

        if (isEbcdic) {
            log.debug("Detected EBCDIC encoding: {}/6 high bytes in header", highBytesInHeader);
        }

        return isEbcdic;
    }

    /**
     * Convert EBCDIC bytes to ASCII.
     *
     * @param ebcdicData Data in EBCDIC encoding
     * @return Data converted to ASCII
     */
    public static byte[] ebcdicToAscii(byte[] ebcdicData) {
        if (ebcdicData == null) {
            return null;
        }

        byte[] asciiData = new byte[ebcdicData.length];
        for (int i = 0; i < ebcdicData.length; i++) {
            asciiData[i] = EBCDIC_TO_ASCII[ebcdicData[i] & 0xFF];
        }

        return asciiData;
    }

    /**
     * Convert a PeSIT FPDU from EBCDIC to ASCII.
     *
     * <p>In PURE EBCDIC mode (standard PeSIT on IBM systems), the ENTIRE FPDU is in EBCDIC
     * encoding. All bytes must be converted from EBCDIC to ASCII.
     *
     * @param data FPDU data in EBCDIC encoding
     * @return FPDU converted to ASCII
     */
    public static byte[] convertFpduFromEbcdic(byte[] data) {
        if (data == null || data.length < 6) {
            return data;
        }

        byte[] result = new byte[data.length];

        // Convert ALL bytes from EBCDIC to ASCII (PURE EBCDIC mode)
        for (int i = 0; i < data.length; i++) {
            result[i] = EBCDIC_TO_ASCII[data[i] & 0xFF];
        }

        log.debug("Converted FPDU from EBCDIC: {} total bytes converted", data.length);
        return result;
    }

    /**
     * Automatically detect encoding and convert to ASCII if needed.
     *
     * @param data Raw FPDU data (may be EBCDIC or ASCII)
     * @return Data in ASCII encoding
     */
    public static byte[] toAscii(byte[] data) {
        if (data == null) {
            return null;
        }

        if (isEbcdic(data)) {
            log.debug("Detected EBCDIC FPDU, converting to ASCII");
            return convertFpduFromEbcdic(data);
        }

        // Already ASCII
        return data;
    }

    /**
     * Convert ASCII bytes to EBCDIC (for responses to IBM mainframe clients).
     *
     * @param asciiData Data in ASCII encoding
     * @return Data converted to EBCDIC
     */
    public static byte[] asciiToEbcdic(byte[] asciiData) {
        if (asciiData == null) {
            return null;
        }

        byte[] ebcdicData = new byte[asciiData.length];
        for (int i = 0; i < asciiData.length; i++) {
            ebcdicData[i] = ASCII_TO_EBCDIC[asciiData[i] & 0xFF];
        }

        return ebcdicData;
    }

    /**
     * Convert a PeSIT FPDU from ASCII to EBCDIC (for responses to EBCDIC clients).
     *
     * <p>In PURE EBCDIC mode (standard PeSIT on IBM systems), the ENTIRE FPDU must be in EBCDIC
     * encoding. All bytes must be converted from ASCII to EBCDIC.
     *
     * @param data FPDU data in ASCII encoding
     * @return FPDU converted to EBCDIC
     */
    public static byte[] convertFpduToEbcdic(byte[] data) {
        if (data == null || data.length < 6) {
            return data;
        }

        byte[] result = new byte[data.length];

        // Convert ALL bytes from ASCII to EBCDIC (PURE EBCDIC mode)
        for (int i = 0; i < data.length; i++) {
            result[i] = ASCII_TO_EBCDIC[data[i] & 0xFF];
        }

        log.debug("Converted FPDU to EBCDIC: {} total bytes converted", data.length);
        return result;
    }

    /**
     * Convert data to the appropriate encoding for a client.
     *
     * @param asciiData Data in ASCII encoding (server internal format)
     * @param clientUsesEbcdic true if client expects EBCDIC, false for ASCII
     * @return Data in appropriate encoding for the client
     */
    public static byte[] toClientEncoding(byte[] asciiData, boolean clientUsesEbcdic) {
        if (asciiData == null) {
            return null;
        }

        if (clientUsesEbcdic) {
            log.debug("Converting FPDU response to EBCDIC for mainframe client");
            return convertFpduToEbcdic(asciiData);
        }

        // Client uses ASCII
        return asciiData;
    }

    /**
     * Convert a subset of EBCDIC data to ASCII string (for debugging).
     *
     * @param data EBCDIC data
     * @param offset Start offset
     * @param length Number of bytes to convert
     * @return ASCII string
     */
    public static String ebcdicToAsciiString(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || offset + length > data.length) {
            return "";
        }

        StringBuilder sb = new StringBuilder(length);
        for (int i = offset; i < offset + length; i++) {
            char c = (char) (EBCDIC_TO_ASCII[data[i] & 0xFF] & 0xFF);
            sb.append(c);
        }

        return sb.toString();
    }
}
