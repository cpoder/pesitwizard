package com.pesitwizard.server.handler;

import org.springframework.stereotype.Component;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.server.config.LogicalFileConfig;
import com.pesitwizard.server.config.PartnerConfig;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;
import com.pesitwizard.server.model.ValidationResult;
import com.pesitwizard.server.service.ConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates logical files for CREATE (receive) and SELECT (send) operations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileValidator {

    private final PesitServerProperties properties;
    private final ConfigService configService;

    /**
     * Validate logical file for CREATE (receive) operation
     */
    public ValidationResult validateForCreate(SessionContext ctx, TransferContext transfer) {
        String filename = transfer.getFilename();

        // Get virtual file from database first, then fall back to YAML config
        LogicalFileConfig fileConfig = resolveFileConfig(ctx, filename);

        if (fileConfig == null) {
            if (properties.isStrictFileCheck()) {
                return ValidationResult.error(DiagnosticCode.D2_205,
                        "Logical file '" + filename + "' not configured");
            }
            // Allow unknown file in non-strict mode
            log.info("[{}] Unknown logical file '{}' allowed (strict mode disabled)",
                    ctx.getSessionId(), filename);
            return ValidationResult.ok();
        }

        // Store config in session
        ctx.setLogicalFileConfig(fileConfig);

        // Check if file is enabled
        if (!fileConfig.isEnabled()) {
            return ValidationResult.error(DiagnosticCode.D2_205,
                    "Logical file '" + filename + "' is disabled");
        }

        // Check direction
        if (!fileConfig.canReceive()) {
            return ValidationResult.error(DiagnosticCode.D2_226,
                    "Logical file '" + filename + "' does not allow receive (CREATE)");
        }

        // Check partner access to this file
        PartnerConfig partner = ctx.getPartnerConfig();
        if (partner != null && !partner.canAccessFile(filename)) {
            return ValidationResult.error(DiagnosticCode.D2_226,
                    "Partner not authorized to access file '" + filename + "'");
        }

        log.info("[{}] Logical file '{}' validated for CREATE", ctx.getSessionId(), filename);
        return ValidationResult.ok();
    }

    /**
     * Validate logical file for SELECT (send) operation
     */
    public ValidationResult validateForSelect(SessionContext ctx, TransferContext transfer) {
        String filename = transfer.getFilename();

        // Get virtual file from database first, then fall back to YAML config
        LogicalFileConfig fileConfig = resolveFileConfig(ctx, filename);

        if (fileConfig == null) {
            if (properties.isStrictFileCheck()) {
                return ValidationResult.error(DiagnosticCode.D2_205,
                        "Logical file '" + filename + "' not configured");
            }
            // Allow unknown file in non-strict mode
            log.info("[{}] Unknown logical file '{}' allowed (strict mode disabled)",
                    ctx.getSessionId(), filename);
            return ValidationResult.ok();
        }

        // Store config in session
        ctx.setLogicalFileConfig(fileConfig);

        // Check if file is enabled
        if (!fileConfig.isEnabled()) {
            return ValidationResult.error(DiagnosticCode.D2_205,
                    "Logical file '" + filename + "' is disabled");
        }

        // Check direction
        if (!fileConfig.canSend()) {
            return ValidationResult.error(DiagnosticCode.D2_226,
                    "Logical file '" + filename + "' does not allow send (SELECT)");
        }

        // Check partner access to this file
        PartnerConfig partner = ctx.getPartnerConfig();
        if (partner != null && !partner.canAccessFile(filename)) {
            return ValidationResult.error(DiagnosticCode.D2_226,
                    "Partner not authorized to access file '" + filename + "'");
        }

        log.info("[{}] Logical file '{}' validated for SELECT", ctx.getSessionId(), filename);
        return ValidationResult.ok();
    }

    /**
     * Resolve file configuration from database or YAML
     */
    private LogicalFileConfig resolveFileConfig(SessionContext ctx, String filename) {
        // Get virtual file from database first
        var virtualFileOpt = configService.findVirtualFile(filename);

        if (virtualFileOpt.isPresent()) {
            var vf = virtualFileOpt.get();
            log.debug("[{}] Found virtual file '{}' in database", ctx.getSessionId(), filename);
            return LogicalFileConfig.builder()
                    .id(vf.getId())
                    .description(vf.getDescription())
                    .enabled(vf.isEnabled())
                    .direction(LogicalFileConfig.Direction.valueOf(vf.getDirection().name()))
                    .receiveDirectory(vf.getReceiveDirectory())
                    .sendDirectory(vf.getSendDirectory())
                    .receiveFilenamePattern(vf.getReceiveFilenamePattern())
                    .overwrite(vf.isOverwrite())
                    .maxFileSize(vf.getMaxFileSize())
                    .fileType(vf.getFileType())
                    .recordLength(vf.getRecordLength())
                    .recordFormat(vf.getRecordFormat())
                    .build();
        }

        // Fall back to YAML config
        LogicalFileConfig fileConfig = properties.getLogicalFile(filename);
        if (fileConfig != null) {
            log.debug("[{}] Found virtual file '{}' in YAML config", ctx.getSessionId(), filename);
        }
        return fileConfig;
    }
}
