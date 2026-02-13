package com.pesitwizard.fpdu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.Getter;

/**
 * Builder for constructing PI (Parameter Information) structures Used when building FPDU data with
 * parameters
 */
@Getter
public class ParameterBuilder {

    private final Parameter pi;
    private byte[] value;

    private ParameterBuilder(Parameter pi) {
        this.pi = pi;
    }

    /** Create builder for a specific PI */
    public static ParameterBuilder forParameter(Parameter pi) {
        return new ParameterBuilder(pi);
    }

    /** Set value as raw bytes */
    public ParameterBuilder value(byte[] value) {
        this.value = value;
        return this;
    }

    /** Set value as string (for variable-length PIs) */
    public ParameterBuilder value(String value) {
        this.value = value.getBytes(StandardCharsets.ISO_8859_1);
        return this;
    }

    /**
     * Set value as integer using minimum bytes needed (variable-length encoding). PeSIT wire format
     * includes a length byte, so the receiver knows how many bytes to read regardless of the
     * encoding length.
     */
    public ParameterBuilder value(int value) {
        if (!(pi instanceof ParameterIdentifier pi)) {
            throw new IllegalArgumentException(
                    "PI " + this.pi.getName() + " does not support integer values");
        }
        int maxLength = pi.getLength();
        if (maxLength == -1) {
            throw new IllegalArgumentException(
                    "Cannot use integer value for variable-length PI: " + pi.getName());
        }

        // Compute minimum bytes needed for this value
        int bytesNeeded;
        if (value == 0) {
            bytesNeeded = 1;
        } else {
            bytesNeeded = 0;
            int temp = value;
            while (temp != 0) {
                bytesNeeded++;
                temp >>>= 8;
            }
        }

        if (bytesNeeded > maxLength) {
            throw new IllegalArgumentException(
                    "Integer value requires "
                            + bytesNeeded
                            + " bytes but PI "
                            + pi.getName()
                            + " allows max "
                            + maxLength);
        }

        byte[] bytes = new byte[bytesNeeded];
        for (int i = 0; i < bytesNeeded; i++) {
            bytes[bytesNeeded - 1 - i] = (byte) (value >> (i * 8));
        }
        this.value = bytes;
        return this;
    }

    /**
     * Set value as long (for 8-byte PIs like file size). Supports values up to 2^63-1 (files >= 4GB
     * use 5-8 bytes).
     */
    public ParameterBuilder value(long value) {
        if (!(pi instanceof ParameterIdentifier pi)) {
            throw new IllegalArgumentException(
                    "PI " + this.pi.getName() + " does not support integer values");
        }

        int maxLength = pi.getLength();
        if (maxLength == -1) {
            throw new IllegalArgumentException(
                    "Cannot use long value for variable-length PI: " + pi.getName());
        }

        // For large values, check they fit in the declared length
        int needed;
        if (value < 0) {
            needed = 8;
        } else if (value < 256L) {
            needed = 1;
        } else if (value < 65536L) {
            needed = 2;
        } else if (value < 16777216L) {
            needed = 3;
        } else if (value < 4294967296L) {
            needed = 4;
        } else if (value < 1099511627776L) {
            needed = 5;
        } else if (value < 281474976710656L) {
            needed = 6;
        } else if (value < 72057594037927936L) {
            needed = 7;
        } else {
            needed = 8;
        }

        if (needed > maxLength) {
            throw new IllegalArgumentException(
                    "Long value requires "
                            + needed
                            + " bytes but PI "
                            + pi.getName()
                            + " allows max "
                            + maxLength);
        }

        // Use minimum bytes needed (variable-length encoding)
        byte[] bytes = new byte[needed];
        for (int i = 0; i < needed; i++) {
            bytes[needed - 1 - i] = (byte) (value >> (i * 8));
        }
        this.value = bytes;
        return this;
    }

    /**
     * Build PI structure as byte array Format: [PI_ID (1 byte)] [Length (1 byte)] [Value (n bytes)]
     */
    public byte[] build() throws IOException {
        if (value == null) {
            throw new IllegalStateException("PI value not set for: " + pi.getName());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // PI identifier
        baos.write(pi.getId() & 0xff); // Ensure single byte

        // Length byte
        if (value.length < 255) {
            baos.write(value.length & 0xFF); // Length as 1 byte
        } else {
            baos.write(0xFF); // Use 255 to indicate length is larger than 255
            baos.write((value.length >> 8) & 0xFF); // Length as 2 bytes
            baos.write(value.length & 0xFF);
        }

        // Value
        baos.write(value);

        return baos.toByteArray();
    }

    /** Get the PI enum this builder is for */
    public Parameter getPI() {
        return pi;
    }
}
