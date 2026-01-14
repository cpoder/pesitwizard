package com.pesitwizard.server.handler;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.security.SecretsService;
import com.pesitwizard.server.config.PartnerConfig;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.entity.Partner;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.ValidationResult;
import com.pesitwizard.server.service.ConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates connection requests including partner authentication,
 * server name matching, and protocol version compatibility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionValidator {

    private final PesitServerProperties properties;
    private final ConfigService configService;
    private final SecretsService secretsService;

    /**
     * Validate partner on CONNECT
     */
    public ValidationResult validatePartner(SessionContext ctx, Fpdu fpdu) {
        String partnerId = ctx.getClientIdentifier();

        // Check if partner exists in database
        Partner partner = configService.findPartner(partnerId).orElse(null);

        if (partner == null) {
            if (properties.isStrictPartnerCheck()) {
                return ValidationResult.error(DiagnosticCode.D3_301,
                        "Partner '" + partnerId + "' not configured");
            }
            // Allow unknown partner in non-strict mode
            log.info("[{}] Unknown partner '{}' allowed (strict mode disabled)",
                    ctx.getSessionId(), partnerId);
            return ValidationResult.ok();
        }

        // Store partner in session (convert to PartnerConfig for backward
        // compatibility)
        PartnerConfig partnerConfig = convertToPartnerConfig(partner);
        ctx.setPartnerConfig(partnerConfig);

        // Check if partner is enabled
        if (!partner.isEnabled()) {
            return ValidationResult.error(DiagnosticCode.D3_304,
                    "Partner '" + partnerId + "' is disabled");
        }

        // Check password if required (PI 5 - Access Control)
        if (partner.getPassword() != null && !partner.getPassword().isEmpty()) {
            ParameterValue pi5 = fpdu.getParameter(ParameterIdentifier.PI_05_CONTROLE_ACCES);
            String providedPassword = pi5 != null ? new String(pi5.getValue(), StandardCharsets.ISO_8859_1).trim() : "";

            // Decrypt stored password (may be encrypted with vault: or ENC: prefix)
            String storedPassword = secretsService.decryptFromStorage(partner.getPassword());

            // Debug: log hex values
            if (!storedPassword.equals(providedPassword)) {
                byte[] providedBytes = providedPassword.getBytes(StandardCharsets.ISO_8859_1);
                byte[] storedBytes = storedPassword.getBytes(StandardCharsets.ISO_8859_1);
                StringBuilder providedHex = new StringBuilder();
                StringBuilder storedHex = new StringBuilder();
                for (byte b : providedBytes) providedHex.append(String.format("%02X ", b));
                for (byte b : storedBytes) storedHex.append(String.format("%02X ", b));
                log.debug("[{}] Password mismatch for partner '{}' (provided length: {}, stored length: {})",
                        ctx.getSessionId(), partnerId, providedPassword.length(), storedPassword.length());
                log.debug("[{}] Provided password hex: {}", ctx.getSessionId(), providedHex.toString());
                log.debug("[{}] Stored password hex: {}", ctx.getSessionId(), storedHex.toString());
                return ValidationResult.error(DiagnosticCode.D3_304,
                        "Invalid password for partner '" + partnerId + "'");
            }
        }

        // Check access type compatibility
        int requestedAccess = ctx.getAccessType();
        if (requestedAccess == 0 && !partner.canRead()) { // Read access
            return ValidationResult.error(DiagnosticCode.D3_304,
                    "Partner '" + partnerId + "' not authorized for read access");
        }
        if (requestedAccess == 1 && !partner.canWrite()) { // Write access
            return ValidationResult.error(DiagnosticCode.D3_304,
                    "Partner '" + partnerId + "' not authorized for write access");
        }

        log.info("[{}] Partner '{}' validated successfully", ctx.getSessionId(), partnerId);
        return ValidationResult.ok();
    }

    /**
     * Convert Partner entity to PartnerConfig for backward compatibility
     */
    public PartnerConfig convertToPartnerConfig(Partner partner) {
        PartnerConfig config = new PartnerConfig();
        config.setId(partner.getId());
        config.setDescription(partner.getDescription());
        config.setPassword(partner.getPassword());
        config.setEnabled(partner.isEnabled());
        config.setAccessType(PartnerConfig.AccessType.valueOf(partner.getAccessType().name()));
        config.setMaxConnections(partner.getMaxConnections());
        if (partner.getAllowedFiles() != null && !partner.getAllowedFiles().isEmpty()) {
            config.setAllowedFiles(partner.getAllowedFiles().split(","));
        }
        return config;
    }

    /**
     * Validate server name (PI 4) matches our configured server ID
     */
    public ValidationResult validateServerName(SessionContext ctx) {
        String requestedServer = ctx.getServerIdentifier();
        String ourServerId = ctx.getOurServerId() != null ? ctx.getOurServerId() : properties.getServerId();

        if (requestedServer == null || requestedServer.isEmpty()) {
            // No server specified - allow if not strict
            log.debug("[{}] No server name specified in CONNECT", ctx.getSessionId());
            return ValidationResult.ok();
        }

        // Check if server name matches (case-insensitive)
        if (!ourServerId.equalsIgnoreCase(requestedServer)) {
            return ValidationResult.error(DiagnosticCode.D3_301,
                    "Server '" + requestedServer + "' not found (this server is '" + ourServerId + "')");
        }

        return ValidationResult.ok();
    }

    /**
     * Validate protocol version compatibility
     */
    public ValidationResult validateProtocolVersion(SessionContext ctx) {
        int clientVersion = ctx.getProtocolVersion();
        int serverVersion = properties.getProtocolVersion();

        // Version 0 means not specified - accept
        if (clientVersion == 0) {
            return ValidationResult.ok();
        }

        // We support version 2 (PeSIT Hors-SIT)
        // Accept clients with version <= our version
        if (clientVersion > serverVersion) {
            return ValidationResult.error(DiagnosticCode.D3_308,
                    "Protocol version " + clientVersion + " not supported (max: " + serverVersion + ")");
        }

        return ValidationResult.ok();
    }
}
