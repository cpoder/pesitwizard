package com.pesitwizard.fpdu;

/**
 * Defines parameter requirements for an FPDU Specifies which PIs and PGIs are mandatory or optional
 */
public class ParameterRequirement {
    private final Parameter parameter;
    private final boolean isMandatory;

    public ParameterRequirement(Parameter parameter, boolean isMandatory) {
        this.parameter = parameter;
        this.isMandatory = isMandatory;
    }

    public ParameterRequirement(Parameter parameter) {
        this.parameter = parameter;
        this.isMandatory = true;
    }

    public Parameter getParameter() {
        return parameter;
    }

    public boolean isMandatory() {
        return isMandatory;
    }

    @Override
    public String toString() {
        return "ParameterRequirement{"
                + "parameter="
                + parameter
                + ", isMandatory="
                + isMandatory
                + '}';
    }
}
