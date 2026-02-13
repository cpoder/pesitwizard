package com.pesitwizard.fpdu;

/**
 * PESIT Protocol Parameter Group Information (PGI) Groups related Parameter Information (PI)
 * elements
 */
public enum ParameterGroupIdentifier implements Parameter {
    // PGI identifiers from PESIT E specification
    PGI_09_ID_FICHIER(
            9,
            "File Identifier",
            new ParameterRequirement(ParameterIdentifier.PI_03_DEMANDEUR, false),
            new ParameterRequirement(ParameterIdentifier.PI_04_SERVEUR, false),
            new ParameterRequirement(ParameterIdentifier.PI_11_TYPE_FICHIER, true),
            new ParameterRequirement(ParameterIdentifier.PI_12_NOM_FICHIER, true)),

    PGI_30_ATTR_LOGIQUES(
            30,
            "Logical Attributes",
            new ParameterRequirement(ParameterIdentifier.PI_31_FORMAT_ARTICLE, false),
            new ParameterRequirement(ParameterIdentifier.PI_32_LONG_ARTICLE, true),
            new ParameterRequirement(ParameterIdentifier.PI_33_ORG_FICHIER, false),
            new ParameterRequirement(ParameterIdentifier.PI_34_SIGNATURE, false),
            new ParameterRequirement(ParameterIdentifier.PI_36_SCEAU_SIT, false),
            new ParameterRequirement(ParameterIdentifier.PI_37_LABEL_FICHIER, false),
            new ParameterRequirement(ParameterIdentifier.PI_38_LONG_CLE, false),
            new ParameterRequirement(ParameterIdentifier.PI_39_DEPL_CLE, false)),

    PGI_40_ATTR_PHYSIQUES(
            40,
            "Physical Attributes",
            new ParameterRequirement(ParameterIdentifier.PI_41_UNITE_RESERVATION, false),
            new ParameterRequirement(ParameterIdentifier.PI_42_MAX_RESERVATION, true)),

    PGI_50_ATTR_HISTORIQUES(
            50,
            "Historical Attributes",
            new ParameterRequirement(ParameterIdentifier.PI_51_DATE_CREATION, true),
            new ParameterRequirement(ParameterIdentifier.PI_52_DATE_EXTRACTION, false));

    private final int id;
    private final String name;
    private final ParameterRequirement[] containedPIs;

    ParameterGroupIdentifier(int id, String name, ParameterRequirement... pis) {
        this.id = id;
        this.name = name;
        this.containedPIs = pis;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ParameterRequirement[] getContainedPIs() {
        return containedPIs;
    }

    public boolean contains(ParameterIdentifier pi) {
        for (ParameterRequirement req : containedPIs) {
            if (req.getParameter().equals(pi)) {
                return true;
            }
        }
        return false;
    }

    public static ParameterGroupIdentifier fromId(int id) {
        for (ParameterGroupIdentifier pgi : values()) {
            if (pgi.id == id) {
                return pgi;
            }
        }
        return null;
    }
}
