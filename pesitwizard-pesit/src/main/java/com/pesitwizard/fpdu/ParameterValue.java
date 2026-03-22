package com.pesitwizard.fpdu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public class ParameterValue {
    private final Parameter parameter;
    private final byte[] bytes;
    private final byte[] value;
    private final List<ParameterValue> values = new ArrayList<>();

    public ParameterValue(ParameterIdentifier parameter, byte[] value) {
        this.parameter = parameter;
        this.value = value;
        byte[] bytes = null;
        try {
            bytes = ParameterBuilder.forParameter(parameter).value(value).build();
        } catch (IOException e) {
            throw new FpduBuildException("Failed to build parameter " + parameter.getName(), e);
        }
        this.bytes = bytes;
    }

    public ParameterValue(ParameterIdentifier parameter, String value) {
        this(parameter, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public ParameterValue(ParameterIdentifier parameter, int value) {
        this.parameter = parameter;
        byte[] bytes = null;
        byte[] valueBytes = null;
        try {
            ParameterBuilder builder = ParameterBuilder.forParameter(parameter).value(value);
            bytes = builder.build();
            valueBytes = builder.getValue();
        } catch (IOException e) {
            throw new FpduBuildException("Failed to build parameter " + parameter.getName(), e);
        }
        this.bytes = bytes;
        this.value = valueBytes;
    }

    public ParameterValue(ParameterIdentifier parameter, long value) {
        this.parameter = parameter;
        byte[] bytes = null;
        byte[] valueBytes = null;
        try {
            ParameterBuilder builder = ParameterBuilder.forParameter(parameter).value(value);
            bytes = builder.build();
            valueBytes = builder.getValue();
        } catch (IOException e) {
            throw new FpduBuildException("Failed to build parameter " + parameter.getName(), e);
        }
        this.bytes = bytes;
        this.value = valueBytes;
    }

    public ParameterValue(ParameterIdentifier parameter, boolean value) {
        this.parameter = parameter;
        this.value = new byte[] {(byte) (value ? 1 : 0)};
        byte[] bytes = null;
        try {
            bytes = ParameterBuilder.forParameter(parameter).value(value ? 1 : 0).build();
        } catch (IOException e) {
            throw new FpduBuildException("Failed to build parameter " + parameter.getName(), e);
        }
        this.bytes = bytes;
    }

    public ParameterValue(ParameterGroupIdentifier parameter, ParameterValue... values) {
        this.parameter = parameter;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            for (ParameterValue val : values) {
                this.values.add(val);
                if (!(val.getParameter() instanceof ParameterIdentifier pi)) {
                    throw new IllegalArgumentException(
                            "Parameter "
                                    + val.getParameter().getName()
                                    + " cannot be part of a PGI");
                }
                if (!parameter.contains(pi)) {
                    throw new IllegalArgumentException(
                            "Parameter "
                                    + pi.getName()
                                    + " is not part of PGI "
                                    + parameter.getName());
                }
                baos.write(val.getBytes());
            }
        } catch (IOException e) {
            throw new FpduBuildException("Failed to build PGI " + parameter.getName(), e);
        }
        ParameterBuilder builder =
                ParameterBuilder.forParameter(parameter).value(baos.toByteArray());
        this.value = builder.getValue();
        byte[] combined = null;
        try {
            combined = builder.build();
        } catch (IOException e) {
            throw new FpduBuildException("Failed to build PGI " + parameter.getName(), e);
        }
        this.bytes = combined;
    }

    /** Return a defensive copy of the encoded bytes. */
    public byte[] getBytes() {
        return bytes != null ? Arrays.copyOf(bytes, bytes.length) : null;
    }

    /** Return a defensive copy of the value bytes. */
    public byte[] getValue() {
        return value != null ? Arrays.copyOf(value, value.length) : null;
    }

    /**
     * Return an unmodifiable view of the sub-values list. Use {@link #addValue} to add sub-values.
     */
    public List<ParameterValue> getValues() {
        return Collections.unmodifiableList(values);
    }

    /** Add a sub-value to this PGI parameter. */
    public void addValue(ParameterValue parameterValue) {
        this.values.add(parameterValue);
    }

    /**
     * Decode the value bytes as a big-endian unsigned integer. Works correctly regardless of the
     * byte array length.
     */
    public int getIntValue() {
        if (value == null || value.length == 0) {
            return 0;
        }
        int result = 0;
        for (byte b : value) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    /**
     * Decode the value bytes as a big-endian unsigned long. Works correctly regardless of the byte
     * array length.
     */
    public long getLongValue() {
        if (value == null || value.length == 0) {
            return 0L;
        }
        long result = 0L;
        for (byte b : value) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    public boolean hasParameter(ParameterIdentifier parameter) {
        return values.stream().anyMatch(pv -> pv.getParameter().equals(parameter));
    }

    public ParameterValue getParameter(Parameter parameter) {
        return values.stream()
                .filter(pv -> pv.getParameter().equals(parameter))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        if (parameter instanceof ParameterGroupIdentifier) {
            return String.format("%s\n%s", parameter.getName(), values);
        } else if (parameter instanceof ParameterIdentifier pi) {
            return String.format("%s: %s", pi.getName(), pi.getType().renderValue(this));
        }
        return parameter.getName();
    }
}
