package com.pesitwizard.server.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.server.config.LogicalFileConfig;
import com.pesitwizard.server.config.PartnerConfig;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.entity.VirtualFile;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;
import com.pesitwizard.server.model.ValidationResult;
import com.pesitwizard.server.service.ConfigService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileValidator Tests")
class FileValidatorTest {

    @Mock private PesitServerProperties properties;

    @Mock private ConfigService configService;

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileValidator(properties, configService);
    }

    @Test
    @DisplayName("validateForCreate should return error for unknown file")
    void validateForCreateShouldReturnErrorForUnknownFile() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("UNKNOWN_FILE");

        when(configService.findVirtualFile("UNKNOWN_FILE")).thenReturn(Optional.empty());
        when(properties.getLogicalFile("UNKNOWN_FILE")).thenReturn(null);

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D2_205, result.getDiagCode());
    }

    @Test
    @DisplayName("validateForCreate should return error for disabled file")
    void validateForCreateShouldReturnErrorForDisabledFile() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        VirtualFile vf = createVirtualFile("FILE1", false);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D2_205, result.getDiagCode());
        assertTrue(result.getMessage().contains("disabled"));
    }

    @Test
    @DisplayName("validateForCreate should return error when direction is SEND only")
    void validateForCreateShouldReturnErrorWhenSendOnly() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.SEND);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D2_226, result.getDiagCode());
        assertTrue(result.getMessage().contains("does not allow receive"));
    }

    @Test
    @DisplayName("validateForCreate should return ok for RECEIVE direction")
    void validateForCreateShouldReturnOkForReceiveDirection() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.RECEIVE);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertTrue(result.isValid());
        assertNotNull(ctx.getLogicalFileConfig());
    }

    @Test
    @DisplayName("validateForCreate should return ok for BOTH direction")
    void validateForCreateShouldReturnOkForBothDirection() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.BOTH);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validateForCreate should return error when partner cannot access file")
    void validateForCreateShouldReturnErrorWhenPartnerCannotAccessFile() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        // Partner with restricted file access
        PartnerConfig partner = new PartnerConfig();
        partner.setAllowedFiles(new String[] {"OTHER_FILE"});
        ctx.setPartnerConfig(partner);

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.RECEIVE);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D2_226, result.getDiagCode());
        assertTrue(result.getMessage().contains("not authorized"));
    }

    @Test
    @DisplayName("validateForSelect should return error for unknown file")
    void validateForSelectShouldReturnErrorForUnknownFile() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("UNKNOWN_FILE");

        when(configService.findVirtualFile("UNKNOWN_FILE")).thenReturn(Optional.empty());
        when(properties.getLogicalFile("UNKNOWN_FILE")).thenReturn(null);

        ValidationResult result = validator.validateForSelect(ctx, transfer);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D2_205, result.getDiagCode());
    }

    @Test
    @DisplayName("validateForSelect should return error when direction is RECEIVE only")
    void validateForSelectShouldReturnErrorWhenReceiveOnly() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.RECEIVE);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForSelect(ctx, transfer);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D2_226, result.getDiagCode());
        assertTrue(result.getMessage().contains("does not allow send"));
    }

    @Test
    @DisplayName("validateForSelect should return ok for SEND direction")
    void validateForSelectShouldReturnOkForSendDirection() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.SEND);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForSelect(ctx, transfer);

        assertTrue(result.isValid());
        assertNotNull(ctx.getLogicalFileConfig());
    }

    @Test
    @DisplayName("validateForSelect should use YAML config when database has no entry")
    void validateForSelectShouldUseYamlConfig() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.empty());

        LogicalFileConfig yamlConfig =
                LogicalFileConfig.builder()
                        .id("FILE1")
                        .enabled(true)
                        .direction(LogicalFileConfig.Direction.SEND)
                        .build();
        when(properties.getLogicalFile("FILE1")).thenReturn(yamlConfig);

        ValidationResult result = validator.validateForSelect(ctx, transfer);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validateForCreate should use YAML config when database has no entry")
    void validateForCreateShouldUseYamlConfig() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.empty());

        LogicalFileConfig yamlConfig =
                LogicalFileConfig.builder()
                        .id("FILE1")
                        .enabled(true)
                        .direction(LogicalFileConfig.Direction.RECEIVE)
                        .build();
        when(properties.getLogicalFile("FILE1")).thenReturn(yamlConfig);

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validateForCreate should check partner access to file")
    void validateForCreateShouldCheckPartnerAccess() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        // Set up partner config that doesn't allow access to FILE1
        PartnerConfig partnerConfig = new PartnerConfig();
        partnerConfig.setId("PARTNER1");
        partnerConfig.setAllowedFiles(new String[] {"OTHER_FILE"});
        ctx.setPartnerConfig(partnerConfig);

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.RECEIVE);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D2_226, result.getDiagCode());
        assertTrue(result.getMessage().contains("not authorized"));
    }

    @Test
    @DisplayName("validateForSelect should check partner access to file")
    void validateForSelectShouldCheckPartnerAccess() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        // Set up partner config that doesn't allow access to FILE1
        PartnerConfig partnerConfig = new PartnerConfig();
        partnerConfig.setId("PARTNER1");
        partnerConfig.setAllowedFiles(new String[] {"OTHER_FILE"});
        ctx.setPartnerConfig(partnerConfig);

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.SEND);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForSelect(ctx, transfer);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D2_226, result.getDiagCode());
        assertTrue(result.getMessage().contains("not authorized"));
    }

    @Test
    @DisplayName("validateForCreate should return error for disabled YAML config file")
    void validateForCreateShouldReturnErrorForDisabledYamlFile() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.empty());

        LogicalFileConfig yamlConfig =
                LogicalFileConfig.builder()
                        .id("FILE1")
                        .enabled(false)
                        .direction(LogicalFileConfig.Direction.RECEIVE)
                        .build();
        when(properties.getLogicalFile("FILE1")).thenReturn(yamlConfig);

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D2_205, result.getDiagCode());
    }

    @Test
    @DisplayName("validateForSelect should return error for disabled YAML config file")
    void validateForSelectShouldReturnErrorForDisabledYamlFile() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.empty());

        LogicalFileConfig yamlConfig =
                LogicalFileConfig.builder()
                        .id("FILE1")
                        .enabled(false)
                        .direction(LogicalFileConfig.Direction.SEND)
                        .build();
        when(properties.getLogicalFile("FILE1")).thenReturn(yamlConfig);

        ValidationResult result = validator.validateForSelect(ctx, transfer);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D2_205, result.getDiagCode());
    }

    @Test
    @DisplayName("validateForCreate should allow partner with matching allowed file")
    void validateForCreateShouldAllowPartnerWithMatchingFile() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        PartnerConfig partner = new PartnerConfig();
        partner.setAllowedFiles(new String[] {"FILE1", "FILE2"});
        ctx.setPartnerConfig(partner);

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.RECEIVE);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validateForSelect should return error when partner cannot access file")
    void validateForSelectShouldReturnErrorWhenPartnerCannotAccessFile() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        PartnerConfig partner = new PartnerConfig();
        partner.setAllowedFiles(new String[] {"OTHER_FILE"});
        ctx.setPartnerConfig(partner);

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.SEND);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForSelect(ctx, transfer);

        assertFalse(result.isValid());
        assertEquals(DiagnosticCode.D2_226, result.getDiagCode());
    }

    @Test
    @DisplayName("validateForCreate should allow partner with empty allowed files list")
    void validateForCreateShouldAllowPartnerWithEmptyAllowedFiles() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        PartnerConfig partner = new PartnerConfig();
        partner.setAllowedFiles(new String[0]); // Empty list = all files allowed
        ctx.setPartnerConfig(partner);

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.RECEIVE);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("validateForCreate should allow partner with null allowed files")
    void validateForCreateShouldAllowPartnerWithNullAllowedFiles() {
        SessionContext ctx = new SessionContext("test-session");
        TransferContext transfer = new TransferContext();
        transfer.setFilename("FILE1");

        PartnerConfig partner = new PartnerConfig();
        partner.setAllowedFiles(null); // Null = all files allowed
        ctx.setPartnerConfig(partner);

        VirtualFile vf = createVirtualFile("FILE1", true);
        vf.setDirection(VirtualFile.Direction.RECEIVE);
        when(configService.findVirtualFile("FILE1")).thenReturn(Optional.of(vf));

        ValidationResult result = validator.validateForCreate(ctx, transfer);

        assertTrue(result.isValid());
    }

    private VirtualFile createVirtualFile(String id, boolean enabled) {
        VirtualFile vf = new VirtualFile();
        vf.setId(id);
        vf.setEnabled(enabled);
        vf.setDirection(VirtualFile.Direction.BOTH);
        return vf;
    }
}
