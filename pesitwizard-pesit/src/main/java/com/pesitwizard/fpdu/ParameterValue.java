package com.pesitwizard.fpdu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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
            bytes = ParameterBuilder.forParameter(parameter)
                    .value(value).build();
        } catch (IOException e) {
            throw new FpduBuildException("Failed to build parameter " + parameter.getName(), e);
        }
        this.bytes = bytes;
    }

    public ParameterValue(ParameterIdentifier parameter, String value) {
        this(parameter, value.getBytes());
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
        this.value = new byte[] { (byte) (value ? 1 : 0) };
        byte[] bytes = null;
        try {
            bytes = ParameterBuilder.forParameter(parameter)
                    .value(value ? 1 : 0).build();
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
                    throw new IllegalArgumentException("Parameter " + val.getParameter().getName()
                            + " cannot be part of a PGI");
                }
                if (!parameter.contains(pi)) {
                    throw new IllegalArgumentException("Parameter " + pi.getName()
                            + " is not part of PGI " + parameter.getName());
                }
                baos.write(val.getBytes());
            }
        } catch (IOException e) {
            throw new FpduBuildException("Failed to build PGI " + parameter.getName(), e);
        }
        ParameterBuilder builder = ParameterBuilder.forParameter(parameter).value(baos.toByteArray());
        this.value = builder.getValue();
        byte[] combined = null;
        try {
            combined = builder.build();
        } catch (IOException e) {
            throw new FpduBuildException("Failed to build PGI " + parameter.getName(), e);
        }
        this.bytes = combined;
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

    public String toString() {
        if (parameter instanceof ParameterGroupIdentifier) {
            return String.format("%s\n%s", parameter.getName(), values);
        } else if (parameter instanceof ParameterIdentifier pi) {
            return String.format("%s: %s", pi.getName(), pi.getType().renderValue(this));
        }
        return parameter.getName();
    }
}
