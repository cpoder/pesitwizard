package com.pesitwizard.client.config;

import com.pesitwizard.security.SecretsService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration properties for PeSIT client */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pesit.client")
public class ClientConfig {

    @Autowired(required = false)
    private SecretsService secretsService;

    /** Default server host */
    private String host = "localhost";

    /** Default server port */
    private int port = 5000;

    /** Client ID (local partner ID) */
    private String clientId = "PESIT_CLIENT";

    /** Client password (if required) - can be encrypted with AES: or vault: prefix */
    private String password;

    /** Get password, auto-decrypting if encrypted */
    public String getPassword() {
        if (password == null || password.isEmpty()) {
            return password;
        }
        // Auto-decrypt if encrypted
        if (secretsService != null && secretsService.isEncrypted(password)) {
            return secretsService.decryptFromStorage(password);
        }
        return password;
    }

    /** Get raw password value without decryption (for storage) */
    public String getRawPassword() {
        return password;
    }

    /** Set password, optionally encrypting it */
    public void setPasswordEncrypted(String plainPassword) {
        if (secretsService != null && plainPassword != null && !plainPassword.isEmpty()) {
            this.password =
                    secretsService.encryptForStorage(plainPassword, "client", clientId, "password");
        } else {
            this.password = plainPassword;
        }
    }

    /** Connection timeout in milliseconds */
    private int connectionTimeout = 30000;

    /** Read timeout in milliseconds */
    private int readTimeout = 60000;

    /** Enable TLS/SSL */
    private boolean tlsEnabled = false;

    /** Path to keystore for TLS */
    private String keystorePath;

    /** Keystore password */
    private String keystorePassword;

    /** Path to truststore for TLS */
    private String truststorePath;

    /** Truststore password */
    private String truststorePassword;

    /** Enable strict protocol checking */
    private boolean strictMode = false;

    /** Default receive directory */
    private String receiveDirectory = "./received";

    /** Retry count for failed transfers */
    private int retryCount = 3;

    /** Retry delay in milliseconds */
    private int retryDelay = 5000;
}
