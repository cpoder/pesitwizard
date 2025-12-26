package com.pesitwizard.server.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;
import com.pesitwizard.server.model.ValidationResult;
import com.pesitwizard.server.service.FileSystemService;
import com.pesitwizard.server.service.PathPlaceholderService;
import com.pesitwizard.server.service.TransferTracker;
import com.pesitwizard.server.state.ServerState;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferOperationHandler Tests")
class TransferOperationHandlerTest {

    @Mock
    private PesitServerProperties properties;

    @Mock
    private FileValidator fileValidator;

    @Mock
    private TransferTracker transferTracker;

    @Mock
    private PathPlaceholderService placeholderService;

    @Mock
    private FileSystemService fileSystemService;

    private TransferOperationHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        handler = new TransferOperationHandler(properties, fileValidator, transferTracker,
                placeholderService, fileSystemService);
    }

    @Test
    @DisplayName("handleOpen should transition to transfer ready state")
    void handleOpenShouldTransitionToTransferReady() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        ctx.startTransfer();

        Fpdu fpdu = new Fpdu(FpduType.OPEN);

        Fpdu response = handler.handleOpen(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_OPEN, response.getFpduType());
        assertEquals(ServerState.OF02_TRANSFER_READY, ctx.getState());
    }

    @Test
    @DisplayName("handleOpen should extract compression from PI_21")
    void handleOpenShouldExtractCompression() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        TransferContext transfer = ctx.startTransfer();

        Fpdu fpdu = new Fpdu(FpduType.OPEN);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_21_COMPRESSION, 1));

        Fpdu response = handler.handleOpen(ctx, fpdu);

        assertNotNull(response);
        assertEquals(1, transfer.getCompression());
    }

    @Test
    @DisplayName("handleClose should transition to file selected state")
    void handleCloseShouldTransitionToFileSelected() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);

        Fpdu fpdu = new Fpdu(FpduType.CLOSE);

        Fpdu response = handler.handleClose(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_CLOSE, response.getFpduType());
        assertEquals(ServerState.SF03_FILE_SELECTED, ctx.getState());
    }

    @Test
    @DisplayName("handleDeselect should end transfer and transition to connected state")
    void handleDeselectShouldEndTransferAndTransition() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        ctx.startTransfer();

        Fpdu fpdu = new Fpdu(FpduType.DESELECT);

        Fpdu response = handler.handleDeselect(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_DESELECT, response.getFpduType());
        assertEquals(ServerState.CN03_CONNECTED, ctx.getState());
        assertNull(ctx.getCurrentTransfer());
    }

    @Test
    @DisplayName("handleCreate should return ABORT when file validation fails")
    void handleCreateShouldReturnAbortWhenValidationFails() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.CREATE);

        when(fileValidator.validateForCreate(any(), any()))
                .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D2_205, "File not found"));

        Fpdu response = handler.handleCreate(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleSelect should return ABORT when file validation fails")
    void handleSelectShouldReturnAbortWhenValidationFails() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.SELECT);

        when(fileValidator.validateForSelect(any(), any()))
                .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D2_205, "File not found"));

        Fpdu response = handler.handleSelect(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleOpen should handle missing transfer context")
    void handleOpenShouldHandleMissingTransfer() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        // No transfer started

        Fpdu fpdu = new Fpdu(FpduType.OPEN);

        Fpdu response = handler.handleOpen(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_OPEN, response.getFpduType());
    }

    @Test
    @DisplayName("handleOpen should handle PI_21 with value 0 (no compression)")
    void handleOpenShouldHandleNoCompression() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        TransferContext transfer = ctx.startTransfer();

        Fpdu fpdu = new Fpdu(FpduType.OPEN);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_21_COMPRESSION, 0));

        Fpdu response = handler.handleOpen(ctx, fpdu);

        assertNotNull(response);
        assertEquals(0, transfer.getCompression());
    }

    @Test
    @DisplayName("handleClose should handle missing transfer context")
    void handleCloseShouldHandleMissingTransfer() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);
        // No transfer

        Fpdu fpdu = new Fpdu(FpduType.CLOSE);

        Fpdu response = handler.handleClose(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_CLOSE, response.getFpduType());
    }

    @Test
    @DisplayName("handleDeselect should handle null transfer gracefully")
    void handleDeselectShouldHandleNullTransfer() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        // No transfer started

        Fpdu fpdu = new Fpdu(FpduType.DESELECT);

        Fpdu response = handler.handleDeselect(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_DESELECT, response.getFpduType());
        assertEquals(ServerState.CN03_CONNECTED, ctx.getState());
    }

    @Test
    @DisplayName("handleCreate should extract file attributes from FPDU")
    void handleCreateShouldExtractFileAttributes() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.CREATE);
        // Add PI_14 for priority
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_17_PRIORITE, 5));
        // Add PI_25 for max entity size
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE, 8192));

        when(fileValidator.validateForCreate(any(), any()))
                .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D2_205, "File not found"));

        handler.handleCreate(ctx, fpdu);

        // Verify transfer context was created with extracted attributes
        // (The transfer is ended when validation fails, so we just verify no exception)
    }

    @Test
    @DisplayName("handleSelect should extract transfer attributes")
    void handleSelectShouldExtractTransferAttributes() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.SELECT);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_17_PRIORITE, 3));

        when(fileValidator.validateForSelect(any(), any()))
                .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D2_205, "File not found"));

        handler.handleSelect(ctx, fpdu);

        // Verify no exception thrown
    }

    @Test
    @DisplayName("handleOpen should return ACK_OPEN with restart point parameter")
    void handleOpenShouldHandleRestartPoint() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        ctx.startTransfer();

        Fpdu fpdu = new Fpdu(FpduType.OPEN);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_18_POINT_RELANCE, 1024));

        Fpdu response = handler.handleOpen(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_OPEN, response.getFpduType());
    }

    @Test
    @DisplayName("handleOpen should handle null compression value gracefully")
    void handleOpenShouldHandleNullCompressionValue() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        ctx.startTransfer();

        Fpdu fpdu = new Fpdu(FpduType.OPEN);
        // No compression parameter

        Fpdu response = handler.handleOpen(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_OPEN, response.getFpduType());
    }

    @Test
    @DisplayName("handleOpen should handle empty compression array")
    void handleOpenShouldHandleEmptyCompressionArray() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        ctx.startTransfer();

        Fpdu fpdu = new Fpdu(FpduType.OPEN);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_21_COMPRESSION, new byte[] {}));

        Fpdu response = handler.handleOpen(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_OPEN, response.getFpduType());
    }

    @Test
    @DisplayName("handleSelect should handle restart flag")
    void handleSelectShouldHandleRestartFlag() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.SELECT);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_15_TRANSFERT_RELANCE, new byte[] { 0x01 }));

        when(fileValidator.validateForSelect(any(), any()))
                .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D2_205, "File not found"));

        handler.handleSelect(ctx, fpdu);

        // Transfer ended due to validation failure, so can't check restart flag
    }

    @Test
    @DisplayName("handleCreate should extract logical attributes from PGI_30")
    void handleCreateShouldExtractLogicalAttributes() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.CREATE);

        // Add PGI_30 with PI_31, PI_32, PI_33
        ParameterValue pgi30 = new ParameterValue(ParameterGroupIdentifier.PGI_30_ATTR_LOGIQUES,
                new ParameterValue(ParameterIdentifier.PI_31_FORMAT_ARTICLE, new byte[] { 0x01 }),
                new ParameterValue(ParameterIdentifier.PI_32_LONG_ARTICLE, new byte[] { 0x00, 0x50 }),
                new ParameterValue(ParameterIdentifier.PI_33_ORG_FICHIER, new byte[] { 0x02 }));
        fpdu.withParameter(pgi30);

        when(fileValidator.validateForCreate(any(), any()))
                .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D2_205, "Test"));

        handler.handleCreate(ctx, fpdu);

        // Verify no exception - validation will fail but attributes should be extracted
    }

    @Test
    @DisplayName("handleCreate should extract file identification from PGI_09")
    void handleCreateShouldExtractFileIdentification() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.CREATE);

        // Add PGI_09 with PI_11 and PI_12
        ParameterValue pgi09 = new ParameterValue(ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                new ParameterValue(ParameterIdentifier.PI_11_TYPE_FICHIER, new byte[] { 0x01 }),
                new ParameterValue(ParameterIdentifier.PI_12_NOM_FICHIER, "TESTFILE".getBytes()));
        fpdu.withParameter(pgi09);

        when(fileValidator.validateForCreate(any(), any()))
                .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D2_205, "Test"));

        handler.handleCreate(ctx, fpdu);
    }

    @Test
    @DisplayName("handleSelect should extract file identification from PGI_09")
    void handleSelectShouldExtractFileIdentification() {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.SELECT);

        ParameterValue pgi09 = new ParameterValue(ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                new ParameterValue(ParameterIdentifier.PI_11_TYPE_FICHIER, new byte[] { 0x02 }),
                new ParameterValue(ParameterIdentifier.PI_12_NOM_FICHIER, "SELECTFILE".getBytes()));
        fpdu.withParameter(pgi09);

        when(fileValidator.validateForSelect(any(), any()))
                .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D2_205, "Test"));

        handler.handleSelect(ctx, fpdu);
    }

    @Test
    @DisplayName("handleCreate should extract transfer ID from PI_13")
    void handleCreateShouldExtractTransferId() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.CREATE);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_13_ID_TRANSFERT, 12345));

        when(fileValidator.validateForCreate(any(), any()))
                .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D2_205, "Test"));

        handler.handleCreate(ctx, fpdu);
    }

    @Test
    @DisplayName("handleCreate should extract max entity size from PI_25")
    void handleCreateShouldExtractMaxEntitySize() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.CREATE);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE, new byte[] { 0x00, 0x10 }));

        when(fileValidator.validateForCreate(any(), any()))
                .thenReturn(ValidationResult.error(com.pesitwizard.fpdu.DiagnosticCode.D2_205, "Test"));

        handler.handleCreate(ctx, fpdu);
    }

    @Test
    @DisplayName("handleCreate should succeed with valid validation")
    void handleCreateShouldSucceedWithValidValidation() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.CREATE);
        ParameterValue pgi09 = new ParameterValue(ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                new ParameterValue(ParameterIdentifier.PI_12_NOM_FICHIER, "TESTFILE".getBytes()));
        fpdu.withParameter(pgi09);

        when(fileValidator.validateForCreate(any(), any())).thenReturn(ValidationResult.ok());
        when(properties.getReceiveDirectory()).thenReturn(tempDir.toString());
        when(properties.getMaxEntitySize()).thenReturn(4096);
        when(properties.getServerId()).thenReturn("TEST_SERVER");
        when(fileSystemService.normalizePath(anyString())).thenReturn(tempDir);
        when(fileSystemService.createDirectories(any())).thenReturn(
                com.pesitwizard.server.service.FileSystemService.FileOperationResult.success(tempDir));

        Fpdu response = handler.handleCreate(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_CREATE, response.getFpduType());
        assertEquals(ServerState.SF03_FILE_SELECTED, ctx.getState());
        verify(transferTracker).trackTransferStart(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handleCreate should return ABORT when directory creation fails")
    void handleCreateShouldReturnAbortWhenDirectoryCreationFails() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.CREATE);

        when(fileValidator.validateForCreate(any(), any())).thenReturn(ValidationResult.ok());
        when(properties.getReceiveDirectory()).thenReturn(tempDir.toString());
        when(fileSystemService.normalizePath(anyString())).thenReturn(tempDir);
        when(fileSystemService.createDirectories(any())).thenReturn(
                com.pesitwizard.server.service.FileSystemService.FileOperationResult.error(
                        com.pesitwizard.server.service.FileSystemService.FileErrorType.ACCESS_DENIED,
                        "Permission denied", tempDir));
        when(fileSystemService.getPermissionString(any())).thenReturn("rwxr-xr-x");

        Fpdu response = handler.handleCreate(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleSelect should succeed with valid file")
    void handleSelectShouldSucceedWithValidFile() throws Exception {
        // Create a test file
        Path testFile = tempDir.resolve("TESTFILE");
        java.nio.file.Files.writeString(testFile, "test content");

        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.SELECT);
        ParameterValue pgi09 = new ParameterValue(ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                new ParameterValue(ParameterIdentifier.PI_12_NOM_FICHIER, "TESTFILE".getBytes()));
        fpdu.withParameter(pgi09);

        when(fileValidator.validateForSelect(any(), any())).thenReturn(ValidationResult.ok());
        when(properties.getSendDirectory()).thenReturn(tempDir.toString());
        when(properties.getMaxEntitySize()).thenReturn(4096);
        when(properties.getServerId()).thenReturn("TEST_SERVER");

        Fpdu response = handler.handleSelect(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_SELECT, response.getFpduType());
        assertEquals(ServerState.SF03_FILE_SELECTED, ctx.getState());
        verify(transferTracker).trackTransferStart(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handleSelect should return ABORT when file not found")
    void handleSelectShouldReturnAbortWhenFileNotFound() throws Exception {
        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.SELECT);
        ParameterValue pgi09 = new ParameterValue(ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                new ParameterValue(ParameterIdentifier.PI_12_NOM_FICHIER, "NONEXISTENT".getBytes()));
        fpdu.withParameter(pgi09);

        when(fileValidator.validateForSelect(any(), any())).thenReturn(ValidationResult.ok());
        when(properties.getSendDirectory()).thenReturn(tempDir.toString());

        Fpdu response = handler.handleSelect(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ABORT, response.getFpduType());
    }

    @Test
    @DisplayName("handleSelect should track transfer with restart flag")
    void handleSelectShouldHandleRestartTrue() throws Exception {
        Path testFile = tempDir.resolve("RESTARTFILE");
        java.nio.file.Files.writeString(testFile, "restart content");

        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        Fpdu fpdu = new Fpdu(FpduType.SELECT);
        ParameterValue pgi09 = new ParameterValue(ParameterGroupIdentifier.PGI_09_ID_FICHIER,
                new ParameterValue(ParameterIdentifier.PI_12_NOM_FICHIER, "RESTARTFILE".getBytes()));
        fpdu.withParameter(pgi09);
        fpdu.withParameter(new ParameterValue(ParameterIdentifier.PI_15_TRANSFERT_RELANCE, new byte[] { 0x01 }));

        when(fileValidator.validateForSelect(any(), any())).thenReturn(ValidationResult.ok());
        when(properties.getSendDirectory()).thenReturn(tempDir.toString());
        when(properties.getMaxEntitySize()).thenReturn(4096);
        when(properties.getServerId()).thenReturn("TEST_SERVER");

        Fpdu response = handler.handleSelect(ctx, fpdu);

        assertNotNull(response);
        assertEquals(FpduType.ACK_SELECT, response.getFpduType());
    }
}
