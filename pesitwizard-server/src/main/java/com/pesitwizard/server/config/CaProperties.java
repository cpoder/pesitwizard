package com.pesitwizard.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration properties for the private Certificate Authority. */
@Data
@Component
@ConfigurationProperties(prefix = "pesit.ca")
public class CaProperties {

    /** Enable the private CA functionality */
    private boolean enabled = true;

    /** CA keystore name in database */
    private String caKeystoreName = "pesit-ca-keystore";

    /** CA truststore name in database (for distribution) */
    private String caTruststoreName = "pesit-ca-truststore";

    /** CA key alias */
    private String caKeyAlias = "pesit-ca";

    /** CA keystore password */
    private String caKeystorePassword = "changeit";

    /** CA truststore password */
    private String caTruststorePassword = "changeit";

    /** CA certificate validity in days */
    private int caValidityDays = 3650; // 10 years

    /** Default certificate validity in days */
    private int defaultCertValidityDays = 365;

    /** Key size for generated keys */
    private int keySize = 2048;

    // ========== CA Subject DN Components ==========

    /** CA Common Name */
    private String caCommonName = "PeSIT Private CA";

    /** Organization */
    private String organization = "PeSIT Wizard";

    /** Organizational Unit */
    private String organizationalUnit = "Certificate Authority";

    /** Locality (City) */
    private String locality = "Paris";

    /** State/Province */
    private String state = "IDF";

    /** Country Code (2 letters) */
    private String country = "FR";
}
