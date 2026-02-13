package com.pesitwizard.connector;

/** Configuration parameter definition for storage connectors. */
public class ConfigParameter {

    private String name;
    private String description;
    private ParameterType type;
    private String defaultValue;
    private boolean sensitive;
    private String example;

    public enum ParameterType {
        STRING,
        INTEGER,
        BOOLEAN,
        PASSWORD,
        PATH,
        URL
    }

    public ConfigParameter() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ParameterType getType() {
        return type;
    }

    public void setType(ParameterType type) {
        this.type = type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public static ConfigParameter required(String name, String description) {
        ConfigParameter p = new ConfigParameter();
        p.name = name;
        p.description = description;
        p.type = ParameterType.STRING;
        return p;
    }

    public static ConfigParameter password(String name, String description) {
        ConfigParameter p = new ConfigParameter();
        p.name = name;
        p.description = description;
        p.type = ParameterType.PASSWORD;
        p.sensitive = true;
        return p;
    }

    public static ConfigParameter optional(String name, String description, String defaultValue) {
        ConfigParameter p = new ConfigParameter();
        p.name = name;
        p.description = description;
        p.type = ParameterType.STRING;
        p.defaultValue = defaultValue;
        return p;
    }

    public static ConfigParameter integer(String name, String description, int defaultValue) {
        ConfigParameter p = new ConfigParameter();
        p.name = name;
        p.description = description;
        p.type = ParameterType.INTEGER;
        p.defaultValue = String.valueOf(defaultValue);
        return p;
    }

    public static ConfigParameter path(String name, String description) {
        ConfigParameter p = new ConfigParameter();
        p.name = name;
        p.description = description;
        p.type = ParameterType.PATH;
        return p;
    }
}
