package com.vectis.server.model;

import java.time.Instant;

import com.vectis.server.config.LogicalFileConfig;
import com.vectis.server.config.PartnerConfig;
import com.vectis.server.state.ServerState;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Context for a PeSIT session
 */
@Slf4j
@Data
public class SessionContext {

    /** Unique session identifier */
    private String sessionId;

    /** Current state of the server state machine */
    private ServerState state = ServerState.CN01_REPOS;

    /** Client connection ID (X) - assigned by client in CONNECT */
    private int clientConnectionId;

    /** Server connection ID (Y) - assigned by server in ACONNECT */
    private int serverConnectionId;

    /** Client identifier (PI 3 - Demandeur) */
    private String clientIdentifier;

    /** Server identifier (PI 4 - Serveur) - requested by client */
    private String serverIdentifier;

    /** Our server ID - the actual server ID this session is running on */
    private String ourServerId;

    /** Protocol version (PI 6) */
    private int protocolVersion;

    /** Access type: 0=read, 1=write, 2=mixed (PI 22) */
    private int accessType;

    /** Sync points option negotiated (PI 7) */
    private boolean syncPointsEnabled;

    /** Resynchronization option negotiated (PI 23) */
    private boolean resyncEnabled;

    /** CRC enabled (PI 1) */
    private boolean crcEnabled;

    /** Remote address */
    private String remoteAddress;

    /** Session start time */
    private Instant startTime;

    /** Last activity time */
    private Instant lastActivityTime;

    /** Current transfer context (null if no transfer in progress) */
    private TransferContext currentTransfer;

    /** Transfer record ID for persistence tracking */
    private String transferRecordId;

    /** Flag indicating premature abort */
    private boolean aborted;

    /** Partner configuration (null if unknown partner allowed) */
    private PartnerConfig partnerConfig;

    /** Current logical file configuration */
    private LogicalFileConfig logicalFileConfig;

    /** Message buffer for segmented message reception (MSGDM/MSGMM/MSGFM) */
    private StringBuilder messageBuffer;

    /** Message filename for segmented message reception */
    private String messageFilename;

    /**
     * Create a new session context
     */
    public SessionContext(String sessionId) {
        this.sessionId = sessionId;
        this.startTime = Instant.now();
        this.lastActivityTime = Instant.now();
    }

    /**
     * Update last activity time
     */
    public void touch() {
        this.lastActivityTime = Instant.now();
    }

    /**
     * Transition to a new state
     */
    public void transitionTo(ServerState newState) {
        ServerState oldState = this.state;
        this.state = newState;
        log.info("[{}] State transition: {} -> {}", sessionId, oldState, newState);
        touch();
    }

    /**
     * Start a new transfer
     */
    public TransferContext startTransfer() {
        this.currentTransfer = new TransferContext();
        this.currentTransfer.setStartTime(Instant.now());
        return this.currentTransfer;
    }

    /**
     * End the current transfer
     */
    public void endTransfer() {
        if (this.currentTransfer != null) {
            this.currentTransfer.setEndTime(Instant.now());
        }
        this.currentTransfer = null;
    }

    /**
     * Check if a transfer is in progress
     */
    public boolean hasActiveTransfer() {
        return this.currentTransfer != null;
    }
}
