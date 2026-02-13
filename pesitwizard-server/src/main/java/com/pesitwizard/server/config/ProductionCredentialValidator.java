package com.pesitwizard.server.config;

import com.pesitwizard.server.security.SecurityProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Validates that default/insecure credentials have been changed before the application is allowed
 * to start in a production profile.
 *
 * <p>Production mode is detected when the "postgres" Spring profile is active (indicating a real
 * database rather than the H2 development default).
 *
 * <p>If any default passwords are still in use, this validator logs a FATAL error for each
 * violation and throws {@link IllegalStateException} to prevent the application from starting with
 * insecure defaults.
 *
 * @see SecurityProperties
 * @see CaProperties
 */
@Component
public class ProductionCredentialValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionCredentialValidator.class);

    /** Default passwords that must be changed before running in production. */
    private static final Set<String> INSECURE_BASIC_AUTH_PASSWORDS = Set.of("changeme");

    private static final Set<String> INSECURE_CA_PASSWORDS = Set.of("changeit");

    /** Spring profile that indicates production mode. */
    private static final String PRODUCTION_PROFILE = "postgres";

    private final Environment environment;
    private final SecurityProperties securityProperties;
    private final CaProperties caProperties;

    public ProductionCredentialValidator(
            Environment environment,
            SecurityProperties securityProperties,
            CaProperties caProperties) {
        this.environment = environment;
        this.securityProperties = securityProperties;
        this.caProperties = caProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProductionProfile()) {
            log.debug("Not running in production profile; skipping credential validation");
            return;
        }

        log.info(
                "Production profile detected — validating that default credentials have been changed");

        List<String> violations = new ArrayList<>();

        validateBasicAuthPasswords(violations);
        validateCaPasswords(violations);

        if (!violations.isEmpty()) {
            for (String violation : violations) {
                log.error("FATAL: {}", violation);
            }
            throw new IllegalStateException(
                    "Application startup blocked: "
                            + violations.size()
                            + " default credential(s) detected in production mode. "
                            + "Change the following before starting: "
                            + violations);
        }

        log.info("Production credential validation passed — no default passwords detected");
    }

    /** Checks whether the "postgres" profile is among the active Spring profiles. */
    private boolean isProductionProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if (PRODUCTION_PROFILE.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    /** Validates that no basic-auth user still has a default password. */
    private void validateBasicAuthPasswords(List<String> violations) {
        if (securityProperties.getBasicAuth() == null
                || securityProperties.getBasicAuth().getUsers() == null) {
            return;
        }

        Map<String, SecurityProperties.UserEntry> users =
                securityProperties.getBasicAuth().getUsers();

        for (Map.Entry<String, SecurityProperties.UserEntry> entry : users.entrySet()) {
            String username = entry.getKey();
            SecurityProperties.UserEntry user = entry.getValue();

            if (user.getPassword() != null
                    && INSECURE_BASIC_AUTH_PASSWORDS.contains(user.getPassword())) {
                violations.add(
                        "pesit.security.basic-auth.users."
                                + username
                                + ".password is still set to the default value \""
                                + user.getPassword()
                                + "\". Set the environment variable or override this property before "
                                + "running in production.");
            }
        }
    }

    /**
     * Validates that the CA keystore and truststore passwords have been changed from their insecure
     * defaults.
     */
    private void validateCaPasswords(List<String> violations) {
        if (caProperties == null) {
            return;
        }

        if (caProperties.getCaKeystorePassword() != null
                && INSECURE_CA_PASSWORDS.contains(caProperties.getCaKeystorePassword())) {
            violations.add(
                    "pesit.ca.ca-keystore-password is still set to the default value \""
                            + caProperties.getCaKeystorePassword()
                            + "\". Set PESIT_CA_PASSWORD or override this property before "
                            + "running in production.");
        }

        if (caProperties.getCaTruststorePassword() != null
                && INSECURE_CA_PASSWORDS.contains(caProperties.getCaTruststorePassword())) {
            violations.add(
                    "pesit.ca.ca-truststore-password is still set to the default value \""
                            + caProperties.getCaTruststorePassword()
                            + "\". Set PESIT_CA_TRUSTSTORE_PASSWORD or override this property before "
                            + "running in production.");
        }
    }
}
