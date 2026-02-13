package com.pesitwizard.server.config;

import static org.junit.jupiter.api.Assertions.*;

import com.pesitwizard.server.security.SecurityProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("ProductionCredentialValidator Tests")
class ProductionCredentialValidatorTest {

    private MockEnvironment environment;
    private SecurityProperties securityProperties;
    private CaProperties caProperties;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        securityProperties = new SecurityProperties();
        caProperties = new CaProperties();
    }

    private ProductionCredentialValidator createValidator() {
        return new ProductionCredentialValidator(environment, securityProperties, caProperties);
    }

    // ---- Non-production profile tests ----

    @Test
    @DisplayName("should skip validation when no profile is active")
    void shouldSkipValidationWhenNoProfileActive() {
        // Default passwords are fine in development mode
        setBasicAuthUsers("changeme", "changeme");
        caProperties.setCaKeystorePassword("changeit");
        caProperties.setCaTruststorePassword("changeit");

        ProductionCredentialValidator validator = createValidator();
        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    @DisplayName("should skip validation when only non-production profiles are active")
    void shouldSkipValidationForNonProductionProfiles() {
        environment.setActiveProfiles("dev", "test");
        setBasicAuthUsers("changeme", "changeme");
        caProperties.setCaKeystorePassword("changeit");

        ProductionCredentialValidator validator = createValidator();
        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    // ---- Production profile detection ----

    @Test
    @DisplayName("should detect postgres profile as production")
    void shouldDetectPostgresProfileAsProduction() {
        environment.setActiveProfiles("postgres");
        setBasicAuthUsers("changeme", "changeme");

        ProductionCredentialValidator validator = createValidator();
        assertThrows(
                IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    @DisplayName("should detect postgres profile among multiple profiles")
    void shouldDetectPostgresAmongMultipleProfiles() {
        environment.setActiveProfiles("logging", "postgres", "monitoring");
        setBasicAuthUsers("changeme", "changeme");

        ProductionCredentialValidator validator = createValidator();
        assertThrows(
                IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments()));
    }

    // ---- Basic auth password validation ----

    @Test
    @DisplayName("should reject default admin password in production")
    void shouldRejectDefaultAdminPassword() {
        environment.setActiveProfiles("postgres");
        setBasicAuthUser("admin", "changeme", List.of("ADMIN"));
        setBasicAuthUser("operator", "s3cureOp!", List.of("OPERATOR"));
        caProperties.setCaKeystorePassword("pr0d-ks-pass!");
        caProperties.setCaTruststorePassword("pr0d-ts-pass!");

        ProductionCredentialValidator validator = createValidator();
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> validator.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("admin"));
    }

    @Test
    @DisplayName("should reject default operator password in production")
    void shouldRejectDefaultOperatorPassword() {
        environment.setActiveProfiles("postgres");
        setBasicAuthUser("admin", "str0ngAdm!n", List.of("ADMIN"));
        setBasicAuthUser("operator", "changeme", List.of("OPERATOR"));
        caProperties.setCaKeystorePassword("pr0d-ks-pass!");
        caProperties.setCaTruststorePassword("pr0d-ts-pass!");

        ProductionCredentialValidator validator = createValidator();
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> validator.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("operator"));
    }

    // ---- CA password validation ----

    @Test
    @DisplayName("should reject default CA keystore password in production")
    void shouldRejectDefaultCaKeystorePassword() {
        environment.setActiveProfiles("postgres");
        setBasicAuthUsers("str0ng1!", "str0ng2!");
        caProperties.setCaKeystorePassword("changeit");
        caProperties.setCaTruststorePassword("pr0d-ts-pass!");

        ProductionCredentialValidator validator = createValidator();
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> validator.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("ca-keystore-password"));
    }

    @Test
    @DisplayName("should reject default CA truststore password in production")
    void shouldRejectDefaultCaTruststorePassword() {
        environment.setActiveProfiles("postgres");
        setBasicAuthUsers("str0ng1!", "str0ng2!");
        caProperties.setCaKeystorePassword("pr0d-ks-pass!");
        caProperties.setCaTruststorePassword("changeit");

        ProductionCredentialValidator validator = createValidator();
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> validator.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("ca-truststore-password"));
    }

    // ---- Multiple violations ----

    @Test
    @DisplayName("should report all violations at once")
    void shouldReportAllViolationsAtOnce() {
        environment.setActiveProfiles("postgres");
        setBasicAuthUsers("changeme", "changeme");
        caProperties.setCaKeystorePassword("changeit");
        caProperties.setCaTruststorePassword("changeit");

        ProductionCredentialValidator validator = createValidator();
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> validator.run(new DefaultApplicationArguments()));
        // All four violations should be reported
        String msg = ex.getMessage();
        assertTrue(
                msg.contains("4 default credential(s)"), "Expected 4 violations but got: " + msg);
    }

    // ---- Happy path ----

    @Test
    @DisplayName("should pass when all passwords have been changed in production")
    void shouldPassWhenAllPasswordsChanged() {
        environment.setActiveProfiles("postgres");
        setBasicAuthUsers("str0ngAdm!n", "s3cureOp!");
        caProperties.setCaKeystorePassword("pr0d-ks-pass!");
        caProperties.setCaTruststorePassword("pr0d-ts-pass!");

        ProductionCredentialValidator validator = createValidator();
        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    @DisplayName("should pass when basic auth has no users configured")
    void shouldPassWhenNoBasicAuthUsers() {
        environment.setActiveProfiles("postgres");
        // Leave basicAuth users empty (default)
        caProperties.setCaKeystorePassword("pr0d-ks-pass!");
        caProperties.setCaTruststorePassword("pr0d-ts-pass!");

        ProductionCredentialValidator validator = createValidator();
        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    // ---- Edge cases ----

    @Test
    @DisplayName("should handle null password in user entry gracefully")
    void shouldHandleNullPasswordGracefully() {
        environment.setActiveProfiles("postgres");

        SecurityProperties.UserEntry userEntry = new SecurityProperties.UserEntry();
        userEntry.setPassword(null);
        userEntry.setRoles(List.of("ADMIN"));
        securityProperties.getBasicAuth().getUsers().put("admin", userEntry);

        caProperties.setCaKeystorePassword("pr0d-ks-pass!");
        caProperties.setCaTruststorePassword("pr0d-ts-pass!");

        ProductionCredentialValidator validator = createValidator();
        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    // ---- Helper methods ----

    /** Set admin and operator users with the given passwords. */
    private void setBasicAuthUsers(String adminPassword, String operatorPassword) {
        setBasicAuthUser("admin", adminPassword, List.of("ADMIN"));
        setBasicAuthUser("operator", operatorPassword, List.of("OPERATOR"));
    }

    private void setBasicAuthUser(String username, String password, List<String> roles) {
        SecurityProperties.UserEntry user = new SecurityProperties.UserEntry();
        user.setPassword(password);
        user.setRoles(roles);
        securityProperties.getBasicAuth().getUsers().put(username, user);
    }
}
