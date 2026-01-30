package com.pesitwizard.fpdu;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class Fpdu {
    private FpduType fpduType;
    // private Map<Parameter, ParameterValue> parameters = new TreeMap<>();
    private List<ParameterValue> parameters = new ArrayList<>();
    private int idSrc = 0;
    private int idDst = 0;
    private byte[] data; // Raw data for DTF FPDUs

    public Fpdu() {
    }

    public Fpdu(FpduType fpduType) {
        this.fpduType = fpduType;
    }

    public boolean hasParameter(ParameterIdentifier parameter) {
        return parameters.stream().anyMatch(pv -> pv.getParameter().equals(parameter));
    }

    public ParameterValue getParameter(Parameter parameter) {
        return parameters.stream()
                .filter(pv -> pv.getParameter().equals(parameter))
                .findFirst()
                .orElse(null);
    }

    public Fpdu withParameter(ParameterValue parameterValue) {
        this.parameters.add(parameterValue);
        return this;
    }

    public Fpdu withIdSrc(int idSrc) {
        this.idSrc = idSrc;
        return this;
    }

    public Fpdu withIdDst(int idDst) {
        this.idDst = idDst;
        return this;
    }

    public Fpdu withData(byte[] data) {
        this.data = data;
        return this;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Fpdu{fpduType=").append(fpduType)
                .append(", idSrc=").append(idSrc)
                .append(", idDst=").append(idDst)
                .append(", parameters=").append(parameters)
                .append('}');
        return sb.toString();
    }
}
