package com.pesitwizard.server.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.config.PartnerConfig;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.entity.Partner;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.ValidationResult;
import com.pesitwizard.server.service.ConfigService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConnectionValidator Tests")
class ConnectionValidatorTest {

    @Mock
    private PesitServerProperties properties;

    @Mock
    private ConfigService configService;

    private ConnectionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConnectionValidator(properties, configService);
    }

    @Test
    @DisplayName("validateServerName should return ok when server name matches")
    void validateServerNameShouldReturnOkWhenMatches() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setServerIdentifier("SERVER1");
        ctx.setOurServerId("SERVER1");

        ValidationResult result = validator.validateServerName(ctx);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validateServerName should return ok when server name matches case-insensitive")
    void validateServerNameShouldReturnOkCaseInsensitive() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setServerIdentifier("server1");
        ctx.setOurServerId("SERVER1");

        ValidationResult result = validator.validateServerName(ctx);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validateServerName should return ok when no server specified")
    void validateServerNameShouldReturnOkWhenNoServerSpecified() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setServerIdentifier(null);

        ValidationResult result = validator.validateServerName(ctx);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validateServerName should return error when server name does not match")
    void validateServerNameShouldReturnErrorWhenMismatch() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setServerIdentifier("OTHER_SERVER");
        ctx.setOurServerId("SERVER1");

        ValidationResult result = validator.validateServerName(ctx);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D3_301, result.getDiagCode());
        assertTrue(result.getMessage().contains("OTHER_SERVER"));
    }

    @Test
    @DisplayName("validateProtocolVersion should return ok when version is 0")
    void validateProtocolVersionShouldReturnOkWhenZero() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setProtocolVersion(0);

        ValidationResult result = validator.validateProtocolVersion(ctx);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validateProtocolVersion should return ok when client version <= server version")
    void validateProtocolVersionShouldReturnOkWhenCompatible() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setProtocolVersion(2);
        when(properties.getProtocolVersion()).thenReturn(2);

        ValidationResult result = validator.validateProtocolVersion(ctx);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validateProtocolVersion should return error when client version > server version")
    void validateProtocolVersionShouldReturnErrorWhenIncompatible() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setProtocolVersion(3);
        when(properties.getProtocolVersion()).thenReturn(2);

        ValidationResult result = validator.validateProtocolVersion(ctx);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D3_308, result.getDiagCode());
    }

    @Test
    @DisplayName("validatePartner should return ok for unknown partner in non-strict mode")
    void validatePartnerShouldReturnOkForUnknownPartnerNonStrict() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setClientIdentifier("UNKNOWN_PARTNER");
        Fpdu fpdu = new Fpdu(FpduType.CONNECT);

        when(configService.findPartner("UNKNOWN_PARTNER")).thenReturn(Optional.empty());
        when(properties.isStrictPartnerCheck()).thenReturn(false);

        ValidationResult result = validator.validatePartner(ctx, fpdu);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validatePartner should return error for unknown partner in strict mode")
    void validatePartnerShouldReturnErrorForUnknownPartnerStrict() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setClientIdentifier("UNKNOWN_PARTNER");
        Fpdu fpdu = new Fpdu(FpduType.CONNECT);

        when(configService.findPartner("UNKNOWN_PARTNER")).thenReturn(Optional.empty());
        when(properties.isStrictPartnerCheck()).thenReturn(true);

        ValidationResult result = validator.validatePartner(ctx, fpdu);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D3_301, result.getDiagCode());
    }

    @Test
    @DisplayName("validatePartner should return error for disabled partner")
    void validatePartnerShouldReturnErrorForDisabledPartner() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setClientIdentifier("PARTNER1");
        Fpdu fpdu = new Fpdu(FpduType.CONNECT);

        Partner partner = createPartner("PARTNER1", false);
        when(configService.findPartner("PARTNER1")).thenReturn(Optional.of(partner));

        ValidationResult result = validator.validatePartner(ctx, fpdu);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D3_304, result.getDiagCode());
        assertTrue(result.getMessage().contains("disabled"));
    }

    @Test
    @DisplayName("validatePartner should return ok for enabled partner without password")
    void validatePartnerShouldReturnOkForEnabledPartner() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setClientIdentifier("PARTNER1");
        ctx.setAccessType(0); // Read access
        Fpdu fpdu = new Fpdu(FpduType.CONNECT);

        Partner partner = createPartner("PARTNER1", true);
        partner.setAccessType(Partner.AccessType.BOTH);
        when(configService.findPartner("PARTNER1")).thenReturn(Optional.of(partner));

        ValidationResult result = validator.validatePartner(ctx, fpdu);

        assertTrue(result.isValid());
        assertNotNull(ctx.getPartnerConfig());
    }

    @Test
    @DisplayName("validatePartner should return error for wrong password")
    void validatePartnerShouldReturnErrorForWrongPassword() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setClientIdentifier("PARTNER1");
        Fpdu fpdu = new Fpdu(FpduType.CONNECT);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_05_CONTROLE_ACCES, "wrong".getBytes()));

        Partner partner = createPartner("PARTNER1", true);
        partner.setPassword("secret");
        when(configService.findPartner("PARTNER1")).thenReturn(Optional.of(partner));

        ValidationResult result = validator.validatePartner(ctx, fpdu);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D3_304, result.getDiagCode());
        assertTrue(result.getMessage().contains("Invalid password"));
    }

    @Test
    @DisplayName("validatePartner should return ok for correct password")
    void validatePartnerShouldReturnOkForCorrectPassword() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setClientIdentifier("PARTNER1");
        ctx.setAccessType(0);
        Fpdu fpdu = new Fpdu(FpduType.CONNECT);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_05_CONTROLE_ACCES, "secret".getBytes()));

        Partner partner = createPartner("PARTNER1", true);
        partner.setPassword("secret");
        partner.setAccessType(Partner.AccessType.BOTH);
        when(configService.findPartner("PARTNER1")).thenReturn(Optional.of(partner));

        ValidationResult result = validator.validatePartner(ctx, fpdu);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validatePartner should return error when read access not allowed")
    void validatePartnerShouldReturnErrorWhenReadNotAllowed() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setClientIdentifier("PARTNER1");
        ctx.setAccessType(0); // Read access
        Fpdu fpdu = new Fpdu(FpduType.CONNECT);

        Partner partner = createPartner("PARTNER1", true);
        partner.setAccessType(Partner.AccessType.WRITE);
        when(configService.findPartner("PARTNER1")).thenReturn(Optional.of(partner));

        ValidationResult result = validator.validatePartner(ctx, fpdu);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D3_304, result.getDiagCode());
        assertTrue(result.getMessage().contains("read access"));
    }

    @Test
    @DisplayName("validatePartner should return error when write access not allowed")
    void validatePartnerShouldReturnErrorWhenWriteNotAllowed() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setClientIdentifier("PARTNER1");
        ctx.setAccessType(1); // Write access
        Fpdu fpdu = new Fpdu(FpduType.CONNECT);

        Partner partner = createPartner("PARTNER1", true);
        partner.setAccessType(Partner.AccessType.READ);
        when(configService.findPartner("PARTNER1")).thenReturn(Optional.of(partner));

        ValidationResult result = validator.validatePartner(ctx, fpdu);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D3_304, result.getDiagCode());
        assertTrue(result.getMessage().contains("write access"));
    }

    @Test
    @DisplayName("convertToPartnerConfig should convert Partner entity correctly")
    void convertToPartnerConfigShouldConvertCorrectly() {
        Partner partner = createPartner("PARTNER1", true);
        partner.setDescription("Test Partner");
        partner.setPassword("secret");
        partner.setMaxConnections(5);
        partner.setAllowedFiles("FILE1,FILE2");
        partner.setAccessType(Partner.AccessType.BOTH);

        PartnerConfig config = validator.convertToPartnerConfig(partner);

        assertEquals("PARTNER1", config.getId());
        assertEquals("Test Partner", config.getDescription());
        assertEquals("secret", config.getPassword());
        assertTrue(config.isEnabled());
        assertEquals(PartnerConfig.AccessType.BOTH, config.getAccessType());
        assertEquals(5, config.getMaxConnections());
        assertArrayEquals(new String[] { "FILE1", "FILE2" }, config.getAllowedFiles());
    }

    @Test
    @DisplayName("validateServerName should use properties serverId when ourServerId is null")
    void validateServerNameShouldUsePropertiesWhenOurServerIdNull() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setServerIdentifier("PROP_SERVER");
        ctx.setOurServerId(null);
        when(properties.getServerId()).thenReturn("PROP_SERVER");

        ValidationResult result = validator.validateServerName(ctx);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validateServerName should return ok for empty server identifier")
    void validateServerNameShouldReturnOkForEmptyServerIdentifier() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setServerIdentifier("");

        ValidationResult result = validator.validateServerName(ctx);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validatePartner should handle empty password in partner config")
    void validatePartnerShouldHandleEmptyPasswordInConfig() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setClientIdentifier("PARTNER1");
        ctx.setAccessType(0);
        Fpdu fpdu = new Fpdu(FpduType.CONNECT);

        Partner partner = createPartner("PARTNER1", true);
        partner.setPassword(""); // Empty password - should not require authentication
        partner.setAccessType(Partner.AccessType.BOTH);
        when(configService.findPartner("PARTNER1")).thenReturn(Optional.of(partner));

        ValidationResult result = validator.validatePartner(ctx, fpdu);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validatePartner should handle missing PI_05 when password required")
    void validatePartnerShouldHandleMissingPi05() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setClientIdentifier("PARTNER1");
        Fpdu fpdu = new Fpdu(FpduType.CONNECT);
        // No PI_05 parameter added

        Partner partner = createPartner("PARTNER1", true);
        partner.setPassword("secret");
        when(configService.findPartner("PARTNER1")).thenReturn(Optional.of(partner));

        ValidationResult result = validator.validatePartner(ctx, fpdu);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D3_304, result.getDiagCode());
    }

    @Test
    @DisplayName("convertToPartnerConfig should handle null allowedFiles")
    void convertToPartnerConfigShouldHandleNullAllowedFiles() {
        Partner partner = createPartner("PARTNER1", true);
        partner.setAllowedFiles(null);

        PartnerConfig config = validator.convertToPartnerConfig(partner);

        // When allowedFiles is null, no files are set (null or empty array)
        assertTrue(config.getAllowedFiles() == null || config.getAllowedFiles().length == 0);
    }

    @Test
    @DisplayName("convertToPartnerConfig should handle empty allowedFiles")
    void convertToPartnerConfigShouldHandleEmptyAllowedFiles() {
        Partner partner = createPartner("PARTNER1", true);
        partner.setAllowedFiles("");

        PartnerConfig config = validator.convertToPartnerConfig(partner);

        // When allowedFiles is empty, no files are set (null or empty array)
        assertTrue(config.getAllowedFiles() == null || config.getAllowedFiles().length == 0);
    }

    @Test
    @DisplayName("validateProtocolVersion should return ok when client version < server version")
    void validateProtocolVersionShouldReturnOkWhenLowerVersion() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.setProtocolVersion(1);
        when(properties.getProtocolVersion()).thenReturn(2);

        ValidationResult result = validator.validateProtocolVersion(ctx);

        assertTrue(result.isValid());
    }

    private Partner createPartner(String id, boolean enabled) {
        Partner partner = new Partner();
        partner.setId(id);
        partner.setEnabled(enabled);
        partner.setAccessType(Partner.AccessType.BOTH);
        return partner;
    }
}
