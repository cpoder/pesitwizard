package com.pesitwizard.server.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.pesitwizard.fpdu.DiagnosticCode;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduIO;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterGroupIdentifier;
import com.pesitwizard.fpdu.ParameterIdentifier;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.config.LogicalFileConfig;
import com.pesitwizard.server.config.PartnerConfig;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.entity.Partner;
import com.pesitwizard.server.entity.TransferRecord.TransferDirection;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.model.TransferContext;
import com.pesitwizard.server.model.ValidationResult;
import com.pesitwizard.server.service.ConfigService;
import com.pesitwizard.server.service.FpduResponseBuilder;
import com.pesitwizard.server.service.PathPlaceholderService;
import com.pesitwizard.server.service.TransferTracker;
import com.pesitwizard.server.state.ServerState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles PeSIT protocol session for a single client connection.
 * Implements the server-side state machine for Hors-SIT profile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PesitSessionHandler {

    private final PesitServerProperties properties;
    private final ConfigService configService;
    private final TransferTracker transferTracker;
    private final PathPlaceholderService placeholderService;

    /**
     * Create a new session context
     */
    public SessionContext createSession(String remoteAddress) {
        return createSession(remoteAddress, properties.getServerId());
    }

    /**
     * Create a new session context with a specific server ID
     */
    public SessionContext createSession(String remoteAddress, String serverId) {
        SessionContext ctx = new SessionContext(UUID.randomUUID().toString());
        ctx.setRemoteAddress(remoteAddress);
        ctx.setServerConnectionId(generateConnectionId());
        ctx.setOurServerId(serverId);
        log.info("[{}] New session created from {} (server: {})", ctx.getSessionId(), remoteAddress, serverId);
        return ctx;
    }

    /**
     * Process an incoming FPDU and return the response
     * 
     * @param ctx     Session context
     * @param rawData Raw FPDU bytes (without length prefix, but includes internal
     *                length)
     * @param out     Output stream for sending responses (used for streaming file
     *                data)
     * @return Response FPDU bytes (without length prefix), or null if no response
     *         needed
     */
    public byte[] processIncomingFpdu(SessionContext ctx, byte[] rawData, java.io.DataOutputStream out)
            throws IOException {
        ctx.touch();

        // Log raw data for debugging
        int[] phaseType = FpduIO.getPhaseAndType(rawData);
        if (phaseType != null) {
            log.debug("[{}] Raw FPDU: length={}, phase=0x{}, type=0x{}",
                    ctx.getSessionId(), rawData.length,
                    String.format("%02X", phaseType[0]), String.format("%02X", phaseType[1]));
        }

        // Check for DTF - needs special handling before parsing since it contains raw
        // data
        if (FpduIO.isDtf(rawData) && ctx.getState() == ServerState.TDE02B_RECEIVING_DATA) {
            byte[] data = FpduIO.extractDtfData(rawData);
            if (data.length > 0) {
                TransferContext transfer = ctx.getCurrentTransfer();
                if (transfer != null) {
                    transfer.appendData(data);
                    log.info("[{}] DTF: received {} bytes, total: {} bytes",
                            ctx.getSessionId(), data.length, transfer.getBytesTransferred());
                }
            }
            return null; // No response for DTF
        }

        // The rawData already contains the internal FPDU length at the start
        // FpduParser expects this format
        FpduParser parser = new FpduParser(rawData);
        Fpdu fpdu = parser.parse();

        log.info("[{}] Received {} in state {}", ctx.getSessionId(), fpdu.getFpduType(), ctx.getState());

        // Process based on current state and FPDU type
        Fpdu response = processStateMachine(ctx, fpdu, out);

        if (response != null) {
            log.info("[{}] Sending {} -> state {}", ctx.getSessionId(), response.getFpduType(), ctx.getState());
            return FpduBuilder.buildFpdu(response);
        }

        return null;
    }

    /**
     * Main state machine processing
     * Returns a single FPDU to send (or null if no response needed)
     * For READ, streams file data directly to the output stream before returning
     */
    private Fpdu processStateMachine(SessionContext ctx, Fpdu fpdu, java.io.DataOutputStream out) throws IOException {
        FpduType type = fpdu.getFpduType();

        // Handle ABORT from any state
        if (type == FpduType.ABORT) {
            return handleAbort(ctx, fpdu);
        }

        return switch (ctx.getState()) {
            case CN01_REPOS -> handleCN01(ctx, fpdu);
            case CN03_CONNECTED -> handleCN03(ctx, fpdu);
            case MSG_RECEIVING -> handleMsgReceiving(ctx, fpdu);
            case SF03_FILE_SELECTED -> handleSF03(ctx, fpdu);
            case OF02_TRANSFER_READY -> handleOF02(ctx, fpdu, out);
            case TDE02B_RECEIVING_DATA -> handleTDE02B(ctx, fpdu);
            case TDE07_WRITE_END -> handleTDE07(ctx, fpdu);
            case TDL02B_SENDING_DATA -> handleTDL02B(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in state {}", ctx.getSessionId(), type, ctx.getState());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311); // Protocol error
            }
        };
    }

    /**
     * CN01 - REPOS: Waiting for CONNECT
     */
    private Fpdu handleCN01(SessionContext ctx, Fpdu fpdu) {
        if (fpdu.getFpduType() != FpduType.CONNECT) {
            log.warn("[{}] Expected CONNECT, got {}", ctx.getSessionId(), fpdu.getFpduType());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
        }

        // Extract connection parameters
        ctx.setClientConnectionId(fpdu.getIdSrc());

        // Extract PI 3 (Demandeur)
        ParameterValue pi3 = fpdu.getParameter(ParameterIdentifier.PI_03_DEMANDEUR);
        if (pi3 != null) {
            ctx.setClientIdentifier(new String(pi3.getValue(), StandardCharsets.ISO_8859_1).trim());
        }

        // Extract PI 4 (Serveur)
        ParameterValue pi4 = fpdu.getParameter(ParameterIdentifier.PI_04_SERVEUR);
        if (pi4 != null) {
            ctx.setServerIdentifier(new String(pi4.getValue(), StandardCharsets.ISO_8859_1).trim());
        }

        // Extract PI 6 (Version)
        ParameterValue pi6 = fpdu.getParameter(ParameterIdentifier.PI_06_VERSION);
        if (pi6 != null && pi6.getValue().length > 0) {
            byte[] versionBytes = pi6.getValue();
            int version = 0;
            for (byte b : versionBytes) {
                version = (version << 8) | (b & 0xFF);
            }
            ctx.setProtocolVersion(version);
        }

        // Extract PI 22 (Access Type)
        ParameterValue pi22 = fpdu.getParameter(ParameterIdentifier.PI_22_TYPE_ACCES);
        if (pi22 != null && pi22.getValue().length >= 1) {
            ctx.setAccessType(pi22.getValue()[0] & 0xFF);
        }

        // Extract PI 7 (Sync Points)
        ctx.setSyncPointsEnabled(fpdu.hasParameter(ParameterIdentifier.PI_07_SYNC_POINTS));

        // Extract PI 23 (Resync)
        ctx.setResyncEnabled(fpdu.hasParameter(ParameterIdentifier.PI_23_RESYNC));

        // Extract PI 1 (CRC)
        ctx.setCrcEnabled(fpdu.hasParameter(ParameterIdentifier.PI_01_CRC));

        log.info("[{}] CONNECT from client '{}' to server '{}', version={}, accessType={}",
                ctx.getSessionId(), ctx.getClientIdentifier(), ctx.getServerIdentifier(),
                ctx.getProtocolVersion(), ctx.getAccessType());

        // Validate server name (PI 4)
        ValidationResult serverValidation = validateServerName(ctx);
        if (!serverValidation.isValid()) {
            log.warn("[{}] Server validation failed: {}", ctx.getSessionId(), serverValidation.getMessage());
            return FpduResponseBuilder.buildRconnect(ctx, serverValidation.getDiagCode(),
                    serverValidation.getMessage());
        }

        // Validate protocol version
        ValidationResult versionValidation = validateProtocolVersion(ctx);
        if (!versionValidation.isValid()) {
            log.warn("[{}] Version validation failed: {}", ctx.getSessionId(), versionValidation.getMessage());
            return FpduResponseBuilder.buildRconnect(ctx, versionValidation.getDiagCode(),
                    versionValidation.getMessage());
        }

        // Validate partner
        ValidationResult validation = validatePartner(ctx, fpdu);
        if (!validation.isValid()) {
            log.warn("[{}] Partner validation failed: {}", ctx.getSessionId(), validation.getMessage());
            return FpduResponseBuilder.buildRconnect(ctx, validation.getDiagCode(), validation.getMessage());
        }

        // Transition to CONNECTED state
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        return FpduResponseBuilder.buildAconnect(ctx,
                properties.getProtocolVersion(),
                properties.isSyncPointsEnabled() && ctx.isSyncPointsEnabled(),
                properties.isResyncEnabled() && ctx.isResyncEnabled());
    }

    /**
     * Validate partner on CONNECT
     */
    private ValidationResult validatePartner(SessionContext ctx, Fpdu fpdu) {
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

            if (!partner.getPassword().equals(providedPassword)) {
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
    private PartnerConfig convertToPartnerConfig(Partner partner) {
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
    private ValidationResult validateServerName(SessionContext ctx) {
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
    private ValidationResult validateProtocolVersion(SessionContext ctx) {
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

    /**
     * CN03 - CONNECTED: Waiting for CREATE, SELECT, MSG, or RELEASE
     */
    private Fpdu handleCN03(SessionContext ctx, Fpdu fpdu) throws IOException {
        return switch (fpdu.getFpduType()) {
            case CREATE -> handleCreate(ctx, fpdu);
            case SELECT -> handleSelect(ctx, fpdu);
            case MSG -> handleMsg(ctx, fpdu);
            case MSGDM -> handleMsgDm(ctx, fpdu);
            case RELEASE -> handleRelease(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in CN03", ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * Handle CREATE FPDU
     */
    private Fpdu handleCreate(SessionContext ctx, Fpdu fpdu) throws IOException {
        log.debug("[{}] handleCreate: starting", ctx.getSessionId());
        TransferContext transfer = ctx.startTransfer();
        transfer.setWriteMode(true);

        // Extract file identification (PGI 9)
        ParameterValue pgi9 = fpdu.getParameter(ParameterGroupIdentifier.PGI_09_ID_FICHIER);
        if (pgi9 != null) {
            // PI 11 - File Type
            ParameterValue pi11 = pgi9.getParameter(ParameterIdentifier.PI_11_TYPE_FICHIER);
            if (pi11 != null && pi11.getValue() != null) {
                transfer.setFileType(parseNumeric(pi11.getValue()));
            }
            // PI 12 - Filename
            ParameterValue pi12 = pgi9.getParameter(ParameterIdentifier.PI_12_NOM_FICHIER);
            if (pi12 != null && pi12.getValue() != null) {
                transfer.setFilename(new String(pi12.getValue(), StandardCharsets.ISO_8859_1).trim());
            }
        }

        // Extract PI 13 (Transfer ID)
        ParameterValue pi13 = fpdu.getParameter(ParameterIdentifier.PI_13_ID_TRANSFERT);
        if (pi13 != null) {
            transfer.setTransferId(parseNumeric(pi13.getValue()));
        }

        // Extract PI 17 (Priority)
        ParameterValue pi17 = fpdu.getParameter(ParameterIdentifier.PI_17_PRIORITE);
        if (pi17 != null && pi17.getValue().length >= 1) {
            transfer.setPriority(pi17.getValue()[0] & 0xFF);
        }

        // Extract PI 25 (Max Entity Size)
        ParameterValue pi25 = fpdu.getParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE);
        if (pi25 != null) {
            transfer.setMaxEntitySize(parseNumeric(pi25.getValue()));
        }

        // Extract PI 15 (Transfer Restart) - indicates this is a restart of a previous
        // transfer
        ParameterValue pi15 = fpdu.getParameter(ParameterIdentifier.PI_15_TRANSFERT_RELANCE);
        if (pi15 != null && pi15.getValue() != null && pi15.getValue().length > 0) {
            transfer.setRestart(pi15.getValue()[0] == 1);
            if (transfer.isRestart()) {
                log.info("[{}] CREATE: Transfer restart requested", ctx.getSessionId());
            }
        }

        // Extract logical attributes (PGI 30)
        ParameterValue pgi30 = fpdu.getParameter(ParameterGroupIdentifier.PGI_30_ATTR_LOGIQUES);
        if (pgi30 != null) {
            // PI 31 - Record Format
            ParameterValue pi31 = pgi30.getParameter(ParameterIdentifier.PI_31_FORMAT_ARTICLE);
            if (pi31 != null) {
                transfer.setRecordFormat(pi31.getValue()[0] & 0xFF);
            }
            // PI 32 - Record Length
            ParameterValue pi32 = pgi30.getParameter(ParameterIdentifier.PI_32_LONG_ARTICLE);
            if (pi32 != null) {
                transfer.setRecordLength(parseNumeric(pi32.getValue()));
            }
            // PI 33 - File Organization
            ParameterValue pi33 = pgi30.getParameter(ParameterIdentifier.PI_33_ORG_FICHIER);
            if (pi33 != null) {
                transfer.setFileOrganization(pi33.getValue()[0] & 0xFF);
            }
        }

        // Validate logical file for CREATE (receive)
        ValidationResult fileValidation = validateLogicalFileForCreate(ctx, transfer);
        if (!fileValidation.isValid()) {
            log.warn("[{}] Logical file validation failed for CREATE: {}",
                    ctx.getSessionId(), fileValidation.getMessage());
            ctx.endTransfer();
            // Send ABORT for file rejection (per PeSIT spec, negative response to CREATE is
            // ABORT)
            return FpduResponseBuilder.buildAbort(ctx, fileValidation.getDiagCode(),
                    fileValidation.getMessage());
        }

        // Prepare local file path based on logical file config or defaults
        Path receiveDir;
        String localFilename;

        LogicalFileConfig fileConfig = ctx.getLogicalFileConfig();
        if (fileConfig != null && fileConfig.getReceiveDirectory() != null) {
            receiveDir = Paths.get(fileConfig.getReceiveDirectory());
            // Use PathPlaceholderService for full placeholder support including partner
            localFilename = placeholderService.resolvePath(
                    fileConfig.getReceiveFilenamePattern(),
                    PathPlaceholderService.PlaceholderContext.builder()
                            .partnerId(ctx.getClientIdentifier())
                            .virtualFile(transfer.getFilename()) // PI 12 - virtual file name
                            .transferId((long) transfer.getTransferId())
                            .direction("RECEIVE")
                            .build());
        } else {
            receiveDir = Paths.get(properties.getReceiveDirectory());
            localFilename = (transfer.getFilename() != null ? transfer.getFilename()
                    : "transfer_" + transfer.getTransferId())
                    + "_" + System.currentTimeMillis();
        }

        try {
            Files.createDirectories(receiveDir);
        } catch (java.nio.file.AccessDeniedException e) {
            log.error("[{}] Access denied to receive directory '{}': {}",
                    ctx.getSessionId(), receiveDir, e.getMessage());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_211,
                    "Access denied to receive directory: " + receiveDir);
        } catch (java.io.IOException e) {
            log.error("[{}] Cannot create receive directory '{}': {}",
                    ctx.getSessionId(), receiveDir, e.getMessage());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_211,
                    "Cannot create receive directory: " + e.getMessage());
        }
        transfer.setLocalPath(receiveDir.resolve(localFilename));

        log.info("[{}] CREATE: file='{}', transferId={}, priority={}, localPath={}",
                ctx.getSessionId(), transfer.getFilename(), transfer.getTransferId(),
                transfer.getPriority(), transfer.getLocalPath());

        // Track transfer start
        transferTracker.trackTransferStart(ctx, properties.getServerId(), null,
                TransferDirection.RECEIVE, transfer.getFilename(), null,
                transfer.getLocalPath() != null ? transfer.getLocalPath().toString() : null);

        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);

        int maxSize = Math.min(properties.getMaxEntitySize(),
                transfer.getMaxEntitySize() > 0 ? transfer.getMaxEntitySize() : properties.getMaxEntitySize());
        log.debug("[{}] handleCreate: building ACK(CREATE) with maxEntitySize={}", ctx.getSessionId(), maxSize);
        Fpdu response = FpduResponseBuilder.buildAckCreate(ctx, maxSize);
        log.debug("[{}] handleCreate: returning response {}", ctx.getSessionId(), response);
        return response;
    }

    /**
     * Handle SELECT FPDU
     */
    private Fpdu handleSelect(SessionContext ctx, Fpdu fpdu) {
        TransferContext transfer = ctx.startTransfer();
        transfer.setWriteMode(false); // Read mode

        // Extract file identification similar to CREATE
        ParameterValue pgi9 = fpdu.getParameter(ParameterGroupIdentifier.PGI_09_ID_FICHIER);
        if (pgi9 != null) {
            ParameterValue pi12 = pgi9.getParameter(ParameterIdentifier.PI_12_NOM_FICHIER);
            if (pi12 != null) {
                transfer.setFilename(new String(pi12.getValue(), StandardCharsets.ISO_8859_1).trim());
            }
        }

        // Extract PI 13 (Transfer ID)
        ParameterValue pi13 = fpdu.getParameter(ParameterIdentifier.PI_13_ID_TRANSFERT);
        if (pi13 != null) {
            transfer.setTransferId(parseNumeric(pi13.getValue()));
        }

        // Extract PI 15 (Transfer Restart) - indicates this is a restart of a previous
        // transfer
        ParameterValue pi15 = fpdu.getParameter(ParameterIdentifier.PI_15_TRANSFERT_RELANCE);
        if (pi15 != null && pi15.getValue() != null && pi15.getValue().length > 0) {
            transfer.setRestart(pi15.getValue()[0] == 1);
        }

        // Extract PI 25 (Max Entity Size) for SELECT
        ParameterValue pi25 = fpdu.getParameter(ParameterIdentifier.PI_25_TAILLE_MAX_ENTITE);
        if (pi25 != null) {
            transfer.setMaxEntitySize(parseNumeric(pi25.getValue()));
        }

        if (transfer.isRestart()) {
            log.info("[{}] SELECT: Transfer restart requested", ctx.getSessionId());
        }

        // Validate logical file for SELECT (send)
        ValidationResult fileValidation = validateLogicalFileForSelect(ctx, transfer);
        if (!fileValidation.isValid()) {
            log.warn("[{}] Logical file validation failed for SELECT: {}",
                    ctx.getSessionId(), fileValidation.getMessage());
            ctx.endTransfer();
            // Send ABORT for file rejection (per PeSIT spec, negative response to SELECT is
            // ABORT)
            return FpduResponseBuilder.buildAbort(ctx, fileValidation.getDiagCode(),
                    fileValidation.getMessage());
        }

        // Determine file path based on logical file config or defaults
        Path filePath;
        LogicalFileConfig fileConfig = ctx.getLogicalFileConfig();
        if (fileConfig != null && fileConfig.getSendDirectory() != null) {
            filePath = Paths.get(fileConfig.getSendDirectory()).resolve(transfer.getFilename());
        } else {
            filePath = Paths.get(properties.getSendDirectory()).resolve(transfer.getFilename());
        }

        // Check if file exists and is readable
        if (!Files.exists(filePath)) {
            log.warn("[{}] SELECT: file '{}' not found at {}",
                    ctx.getSessionId(), transfer.getFilename(), filePath);
            ctx.endTransfer();
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_205,
                    "File '" + transfer.getFilename() + "' not found");
        }
        if (!Files.isReadable(filePath)) {
            log.error("[{}] SELECT: access denied to file '{}' at {}",
                    ctx.getSessionId(), transfer.getFilename(), filePath);
            ctx.endTransfer();
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_211,
                    "Access denied to file: " + transfer.getFilename());
        }

        transfer.setLocalPath(filePath);

        log.info("[{}] SELECT: file='{}', transferId={}, localPath={}",
                ctx.getSessionId(), transfer.getFilename(), transfer.getTransferId(), filePath);

        // Track transfer start
        try {
            long fileSize = Files.size(filePath);
            transferTracker.trackTransferStart(ctx, properties.getServerId(), null,
                    TransferDirection.SEND, transfer.getFilename(), fileSize, filePath.toString());
        } catch (IOException e) {
            log.warn("[{}] Could not get file size for tracking", ctx.getSessionId());
            transferTracker.trackTransferStart(ctx, properties.getServerId(), null,
                    TransferDirection.SEND, transfer.getFilename(), null, filePath.toString());
        }

        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);

        return FpduResponseBuilder.buildAckSelect(ctx, properties.getMaxEntitySize());
    }

    /**
     * Validate logical file for CREATE (receive) operation
     */
    private ValidationResult validateLogicalFileForCreate(SessionContext ctx, TransferContext transfer) {
        String filename = transfer.getFilename();

        // Get virtual file from database first, then fall back to YAML config
        var virtualFileOpt = configService.findVirtualFile(filename);
        LogicalFileConfig fileConfig = null;

        if (virtualFileOpt.isPresent()) {
            var vf = virtualFileOpt.get();
            fileConfig = LogicalFileConfig.builder()
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
                    .build();
            log.debug("[{}] Found virtual file '{}' in database", ctx.getSessionId(), filename);
        } else {
            // Fall back to YAML config
            fileConfig = properties.getLogicalFile(filename);
            if (fileConfig != null) {
                log.debug("[{}] Found virtual file '{}' in YAML config", ctx.getSessionId(), filename);
            }
        }

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
    private ValidationResult validateLogicalFileForSelect(SessionContext ctx, TransferContext transfer) {
        String filename = transfer.getFilename();

        // Get virtual file from database first, then fall back to YAML config
        var virtualFileOpt = configService.findVirtualFile(filename);
        LogicalFileConfig fileConfig = null;

        if (virtualFileOpt.isPresent()) {
            var vf = virtualFileOpt.get();
            fileConfig = LogicalFileConfig.builder()
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
                    .build();
            log.debug("[{}] Found virtual file '{}' in database", ctx.getSessionId(), filename);
        } else {
            // Fall back to YAML config
            fileConfig = properties.getLogicalFile(filename);
            if (fileConfig != null) {
                log.debug("[{}] Found virtual file '{}' in YAML config", ctx.getSessionId(), filename);
            }
        }

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
     * Handle RELEASE FPDU
     */
    private Fpdu handleRelease(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] RELEASE received, closing session", ctx.getSessionId());
        ctx.transitionTo(ServerState.CN01_REPOS);
        return FpduResponseBuilder.buildRelconf(ctx);
    }

    /**
     * Handle MSG FPDU - single message (fits in one FPDU)
     */
    private Fpdu handleMsg(SessionContext ctx, Fpdu fpdu) {
        // Extract message from PI_91
        ParameterValue pi91 = fpdu.getParameter(ParameterIdentifier.PI_91_MESSAGE);
        String message = null;
        if (pi91 != null && pi91.getValue() != null) {
            message = new String(pi91.getValue(), StandardCharsets.UTF_8);
        }

        // Extract file identification for logging
        ParameterValue pgi9 = fpdu.getParameter(ParameterGroupIdentifier.PGI_09_ID_FICHIER);
        String filename = "unknown";
        if (pgi9 != null) {
            ParameterValue pi12 = pgi9.getParameter(ParameterIdentifier.PI_12_NOM_FICHIER);
            if (pi12 != null && pi12.getValue() != null) {
                filename = new String(pi12.getValue(), StandardCharsets.UTF_8).trim();
            }
        }

        log.info("[{}] MSG received: file={}, message length={}",
                ctx.getSessionId(), filename, message != null ? message.length() : 0);

        if (message != null) {
            log.debug("[{}] Message content: {}", ctx.getSessionId(),
                    message.length() > 100 ? message.substring(0, 100) + "..." : message);
        }

        // TODO: Process message (e.g., store, forward, trigger action)
        // For now, just acknowledge receipt

        // Stay in CN03 state after message
        return FpduResponseBuilder.buildAckMsg(ctx, null);
    }

    /**
     * Handle MSGDM FPDU - start of segmented message
     * Sets up context to receive MSGMM and MSGFM
     */
    private Fpdu handleMsgDm(SessionContext ctx, Fpdu fpdu) {
        // Extract file identification
        ParameterValue pgi9 = fpdu.getParameter(ParameterGroupIdentifier.PGI_09_ID_FICHIER);
        String filename = "unknown";
        if (pgi9 != null) {
            ParameterValue pi12 = pgi9.getParameter(ParameterIdentifier.PI_12_NOM_FICHIER);
            if (pi12 != null && pi12.getValue() != null) {
                filename = new String(pi12.getValue(), StandardCharsets.UTF_8).trim();
            }
        }

        // Initialize message buffer in context
        StringBuilder messageBuffer = new StringBuilder();

        // Extract first segment from PI_91
        ParameterValue pi91 = fpdu.getParameter(ParameterIdentifier.PI_91_MESSAGE);
        if (pi91 != null && pi91.getValue() != null) {
            messageBuffer.append(new String(pi91.getValue(), StandardCharsets.UTF_8));
        }

        // Store in context for subsequent segments
        ctx.setMessageBuffer(messageBuffer);
        ctx.setMessageFilename(filename);

        log.info("[{}] MSGDM received: file={}, first segment length={}",
                ctx.getSessionId(), filename, messageBuffer.length());

        // Transition to message receiving state
        ctx.transitionTo(ServerState.MSG_RECEIVING);

        // No response for MSGDM - wait for MSGMM/MSGFM
        return null;
    }

    /**
     * Handle MSGMM FPDU - middle segment of message
     */
    private Fpdu handleMsgMm(SessionContext ctx, Fpdu fpdu) {
        StringBuilder messageBuffer = ctx.getMessageBuffer();
        if (messageBuffer == null) {
            log.warn("[{}] MSGMM received without MSGDM", ctx.getSessionId());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
        }

        // Append segment from PI_91
        ParameterValue pi91 = fpdu.getParameter(ParameterIdentifier.PI_91_MESSAGE);
        if (pi91 != null && pi91.getValue() != null) {
            messageBuffer.append(new String(pi91.getValue(), StandardCharsets.UTF_8));
        }

        log.debug("[{}] MSGMM received: total length now={}", ctx.getSessionId(), messageBuffer.length());

        // No response for MSGMM - wait for more segments or MSGFM
        return null;
    }

    /**
     * Handle MSGFM FPDU - end of segmented message
     */
    private Fpdu handleMsgFm(SessionContext ctx, Fpdu fpdu) {
        StringBuilder messageBuffer = ctx.getMessageBuffer();
        if (messageBuffer == null) {
            log.warn("[{}] MSGFM received without MSGDM", ctx.getSessionId());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
        }

        // Append final segment from PI_91
        ParameterValue pi91 = fpdu.getParameter(ParameterIdentifier.PI_91_MESSAGE);
        if (pi91 != null && pi91.getValue() != null) {
            messageBuffer.append(new String(pi91.getValue(), StandardCharsets.UTF_8));
        }

        String fullMessage = messageBuffer.toString();
        String filename = ctx.getMessageFilename();

        log.info("[{}] MSGFM received: file={}, total message length={}",
                ctx.getSessionId(), filename, fullMessage.length());

        // TODO: Process complete message
        log.debug("[{}] Complete message: {}", ctx.getSessionId(),
                fullMessage.length() > 100 ? fullMessage.substring(0, 100) + "..." : fullMessage);

        // Clear message buffer
        ctx.setMessageBuffer(null);
        ctx.setMessageFilename(null);

        // Return to connected state
        ctx.transitionTo(ServerState.CN03_CONNECTED);

        return FpduResponseBuilder.buildAckMsg(ctx, null);
    }

    /**
     * MSG_RECEIVING state - waiting for MSGMM or MSGFM
     */
    private Fpdu handleMsgReceiving(SessionContext ctx, Fpdu fpdu) {
        return switch (fpdu.getFpduType()) {
            case MSGMM -> handleMsgMm(ctx, fpdu);
            case MSGFM -> handleMsgFm(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} while receiving message", ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * SF03 - FILE SELECTED: Waiting for OPEN or DESELECT
     */
    private Fpdu handleSF03(SessionContext ctx, Fpdu fpdu) {
        return switch (fpdu.getFpduType()) {
            case OPEN -> handleOpen(ctx, fpdu);
            case DESELECT -> handleDeselect(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in SF03", ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * Handle OPEN (ORF) FPDU
     */
    private Fpdu handleOpen(SessionContext ctx, Fpdu fpdu) {
        // Extract PI 21 (Compression)
        ParameterValue pi21 = fpdu.getParameter(ParameterIdentifier.PI_21_COMPRESSION);
        if (pi21 != null && ctx.getCurrentTransfer() != null) {
            ctx.getCurrentTransfer().setCompression(pi21.getValue()[0] & 0xFF);
        }

        log.info("[{}] OPEN: file opened for transfer", ctx.getSessionId());

        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);

        return FpduResponseBuilder.buildAckOpen(ctx);
    }

    /**
     * Handle DESELECT FPDU
     */
    private Fpdu handleDeselect(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] DESELECT: file deselected", ctx.getSessionId());
        ctx.endTransfer();
        ctx.transitionTo(ServerState.CN03_CONNECTED);
        return FpduResponseBuilder.buildAckDeselect(ctx);
    }

    /**
     * OF02 - TRANSFER READY: Waiting for WRITE, READ, or CLOSE
     */
    private Fpdu handleOF02(SessionContext ctx, Fpdu fpdu, java.io.DataOutputStream out) throws IOException {
        return switch (fpdu.getFpduType()) {
            case WRITE -> handleWrite(ctx, fpdu);
            case READ -> handleRead(ctx, fpdu, out);
            case CLOSE -> handleClose(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in OF02", ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * Handle WRITE FPDU
     */
    private Fpdu handleWrite(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] WRITE: starting data reception", ctx.getSessionId());
        ctx.transitionTo(ServerState.TDE02B_RECEIVING_DATA);
        return FpduResponseBuilder.buildAckWrite(ctx, 0);
    }

    /**
     * Handle READ FPDU - streams file data to client
     * 1. Sends ACK(READ)
     * 2. Streams DTF chunks with file data directly to output
     * 3. Sends DTF.END
     * 4. Returns null (no additional response needed, waits for TRANS.END)
     */
    private Fpdu handleRead(SessionContext ctx, Fpdu fpdu, java.io.DataOutputStream out) throws IOException {
        TransferContext transfer = ctx.getCurrentTransfer();

        if (transfer == null || transfer.getLocalPath() == null) {
            log.error("[{}] READ: no file selected for transfer", ctx.getSessionId());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_205, "No file selected");
        }

        Path filePath = transfer.getLocalPath();
        if (!Files.exists(filePath)) {
            log.error("[{}] READ: file not found: {}", ctx.getSessionId(), filePath);
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_205, "File not found");
        }

        // Extract PI 18 (Restart Point) - position to resume from
        long restartPoint = 0;
        ParameterValue pi18 = fpdu.getParameter(ParameterIdentifier.PI_18_POINT_RELANCE);
        if (pi18 != null) {
            restartPoint = parseNumeric(pi18.getValue());
            transfer.setRestartPoint((int) restartPoint);
        }

        if (restartPoint > 0) {
            log.info("[{}] READ: resuming from position {} for {}", ctx.getSessionId(), restartPoint, filePath);
        } else {
            log.info("[{}] READ: starting data transmission for {}", ctx.getSessionId(), filePath);
        }

        // 1. Send ACK(READ)
        FpduIO.writeFpdu(out, FpduResponseBuilder.buildAckRead(ctx));
        log.info("[{}] Sent ACK(READ)", ctx.getSessionId());

        // 2. Stream file data as DTF chunks
        int maxChunkSize = properties.getMaxEntitySize();
        long totalBytes = 0;
        int recordCount = 0;
        byte[] buffer = new byte[maxChunkSize];

        final long startPosition = restartPoint;
        try (java.io.InputStream fileIn = Files.newInputStream(filePath)) {
            // Skip to restart point if resuming transfer
            if (startPosition > 0) {
                long skipped = fileIn.skip(startPosition);
                log.info("[{}] READ: skipped {} bytes to resume position", ctx.getSessionId(), skipped);
            }

            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                byte[] chunk = (bytesRead == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                FpduIO.writeFpduWithData(out, FpduType.DTF, ctx.getClientConnectionId(), 0, chunk);

                totalBytes += bytesRead;
                recordCount++;

                log.debug("[{}] DTF: sent {} bytes, total: {} bytes",
                        ctx.getSessionId(), bytesRead, totalBytes);
            }
        }

        log.info("[{}] READ: sent {} bytes in {} DTF chunk(s)", ctx.getSessionId(), totalBytes, recordCount);

        // 3. Send DTF.END
        FpduIO.writeFpdu(out, FpduResponseBuilder.buildDtfEnd(ctx));
        log.info("[{}] Sent DTF.END", ctx.getSessionId());

        // Store transfer stats for ACK(TRANS.END) response
        transfer.setBytesTransferred(totalBytes);
        transfer.setRecordsTransferred(recordCount);

        // Transition to waiting for TRANS.END from client
        ctx.transitionTo(ServerState.TDL02B_SENDING_DATA);

        // Return null - we already sent all responses, now waiting for TRANS.END
        return null;
    }

    /**
     * TDL02B - SENDING DATA: Waiting for TRANS.END from client after file data sent
     */
    private Fpdu handleTDL02B(SessionContext ctx, Fpdu fpdu) {
        return switch (fpdu.getFpduType()) {
            case TRANS_END -> handleTransEndFromClient(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in TDL02B (expected TRANS_END)",
                        ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * Handle TRANS.END from client (after server sent file data)
     */
    private Fpdu handleTransEndFromClient(SessionContext ctx, Fpdu fpdu) {
        TransferContext transfer = ctx.getCurrentTransfer();
        long byteCount = transfer != null ? transfer.getBytesTransferred() : 0;
        int recordCount = transfer != null ? transfer.getRecordsTransferred() : 0;

        log.info("[{}] TRANS.END received: {} bytes, {} records transferred",
                ctx.getSessionId(), byteCount, recordCount);

        // Track transfer completion for SEND transfers
        transferTracker.trackTransferComplete(ctx);

        // Transition back to file open state (ready for CLOSE)
        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);

        return FpduResponseBuilder.buildAckTransEnd(ctx, byteCount, recordCount);
    }

    /**
     * Handle CLOSE (CRF) FPDU
     */
    private Fpdu handleClose(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] CLOSE: file closed", ctx.getSessionId());
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        return FpduResponseBuilder.buildAckClose(ctx);
    }

    /**
     * TDE02B - RECEIVING DATA: Processing DTF, DTF.END, SYN, IDT
     */
    private Fpdu handleTDE02B(SessionContext ctx, Fpdu fpdu) throws IOException {
        return switch (fpdu.getFpduType()) {
            case DTF, DTFDA, DTFMA, DTFFA -> handleDtf(ctx, fpdu);
            case DTF_END -> handleDtfEnd(ctx, fpdu);
            case SYN -> handleSyn(ctx, fpdu);
            case IDT -> handleIdt(ctx, fpdu);
            default -> {
                log.warn("[{}] Unexpected FPDU {} in TDE02B", ctx.getSessionId(), fpdu.getFpduType());
                yield FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
            }
        };
    }

    /**
     * Handle DTF (Data Transfer) FPDU - no response needed
     */
    private Fpdu handleDtf(SessionContext ctx, Fpdu fpdu) {
        TransferContext transfer = ctx.getCurrentTransfer();
        if (transfer != null) {
            // The data is in the FPDU parameters area after the header
            // For DTF, the actual data follows the FPDU header
            // We need to extract it from the raw bytes
            // For now, we'll log that we received data
            log.debug("[{}] DTF: received data record", ctx.getSessionId());
            transfer.setRecordsTransferred(transfer.getRecordsTransferred() + 1);
        }
        return null; // No response for DTF
    }

    /**
     * Handle DTF.END FPDU - no response needed
     */
    private Fpdu handleDtfEnd(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] DTF.END: end of data transfer", ctx.getSessionId());
        ctx.transitionTo(ServerState.TDE07_WRITE_END);
        return null; // No response for DTF.END
    }

    /**
     * Handle SYN (Synchronization Point) FPDU
     * Sync points are checkpoints that allow transfer restart from a known
     * position.
     * When we acknowledge a sync point, we commit to having received all data up to
     * that point.
     */
    private Fpdu handleSyn(SessionContext ctx, Fpdu fpdu) {
        ParameterValue pi20 = fpdu.getParameter(ParameterIdentifier.PI_20_NUM_SYNC);
        int syncPoint = 0;
        if (pi20 != null) {
            syncPoint = parseNumeric(pi20.getValue());
        }

        TransferContext transfer = ctx.getCurrentTransfer();
        if (transfer != null) {
            transfer.setCurrentSyncPoint(syncPoint);

            // Persist checkpoint: flush data received so far to disk for restart capability
            long bytesAtCheckpoint = transfer.getBytesTransferred();

            // Track sync point in database for potential restart
            transferTracker.trackSyncPoint(ctx, bytesAtCheckpoint);

            log.info("[{}] SYN: checkpoint {} at {} bytes",
                    ctx.getSessionId(), syncPoint, bytesAtCheckpoint);
        } else {
            log.info("[{}] SYN: sync point {} (no active transfer)", ctx.getSessionId(), syncPoint);
        }

        return FpduResponseBuilder.buildAckSyn(ctx, syncPoint);
    }

    /**
     * Handle IDT (Interrupt Data Transfer) FPDU
     */
    private Fpdu handleIdt(SessionContext ctx, Fpdu fpdu) {
        log.info("[{}] IDT: transfer interrupted", ctx.getSessionId());
        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);
        return FpduResponseBuilder.buildAckIdt(ctx);
    }

    /**
     * TDE07 - WRITE END: Waiting for TRANS.END
     */
    private Fpdu handleTDE07(SessionContext ctx, Fpdu fpdu) throws IOException {
        if (fpdu.getFpduType() != FpduType.TRANS_END) {
            log.warn("[{}] Expected TRANS.END, got {}", ctx.getSessionId(), fpdu.getFpduType());
            return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D3_311);
        }

        TransferContext transfer = ctx.getCurrentTransfer();
        long byteCount = 0;
        int recordCount = 0;

        if (transfer != null) {
            byteCount = transfer.getBytesTransferred();
            recordCount = transfer.getRecordsTransferred();

            // Write received data to file
            if (transfer.getLocalPath() != null && transfer.getData().length > 0) {
                try {
                    // Ensure parent directory exists
                    Path parentDir = transfer.getLocalPath().getParent();
                    if (parentDir != null && !Files.exists(parentDir)) {
                        Files.createDirectories(parentDir);
                    }

                    Files.write(transfer.getLocalPath(), transfer.getData());
                    log.info("[{}] TRANS.END: wrote {} bytes to {}",
                            ctx.getSessionId(), transfer.getData().length, transfer.getLocalPath());
                } catch (java.nio.file.FileSystemException e) {
                    // Disk full or permission error
                    log.error("[{}] TRANS.END: failed to write file: {}", ctx.getSessionId(), e.getMessage());
                    ctx.transitionTo(ServerState.OF02_TRANSFER_READY);
                    if (e.getMessage() != null && e.getMessage().contains("No space")) {
                        return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_219,
                                "Disk full: " + e.getMessage());
                    }
                    return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_213,
                            "File write error: " + e.getMessage());
                } catch (IOException e) {
                    log.error("[{}] TRANS.END: I/O error writing file: {}", ctx.getSessionId(), e.getMessage());
                    ctx.transitionTo(ServerState.OF02_TRANSFER_READY);
                    return FpduResponseBuilder.buildAbort(ctx, DiagnosticCode.D2_213,
                            "I/O error: " + e.getMessage());
                }
            }
        }

        log.info("[{}] TRANS.END: transfer complete, {} bytes, {} records",
                ctx.getSessionId(), byteCount, recordCount);

        // Track transfer completion
        transferTracker.trackTransferComplete(ctx);

        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);

        return FpduResponseBuilder.buildAckTransEnd(ctx, byteCount, recordCount);
    }

    /**
     * Handle ABORT FPDU
     */
    private Fpdu handleAbort(SessionContext ctx, Fpdu fpdu) {
        log.warn("[{}] ABORT received", ctx.getSessionId());

        // Track transfer failure if there was an active transfer
        if (ctx.getTransferRecordId() != null) {
            ParameterValue pi2 = fpdu.getParameter(ParameterIdentifier.PI_02_DIAG);
            String errorCode = pi2 != null ? bytesToHex(pi2.getValue()) : "ABORT";
            transferTracker.trackTransferFailed(ctx, errorCode, "Transfer aborted by peer");
        }

        ctx.setAborted(true);
        ctx.transitionTo(ServerState.CN01_REPOS);
        return null; // No response for ABORT
    }

    /**
     * Convert bytes to hex string for diagnostic codes
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Generate a unique connection ID
     */
    private int generateConnectionId() {
        return (int) (System.currentTimeMillis() % 255) + 1;
    }

    /**
     * Parse numeric value from bytes (big-endian)
     */
    private int parseNumeric(byte[] bytes) {
        int value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }
}
