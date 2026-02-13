package com.pesitwizard.server.model;

import com.pesitwizard.server.state.ServerState;

/**
 * Thrown when a PeSIT protocol state transition is invalid. This indicates a protocol error from
 * the remote peer (sending an FPDU that is not valid in the current session state).
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final String sessionId;
    private final ServerState fromState;
    private final ServerState toState;

    public InvalidStateTransitionException(
            String sessionId, ServerState fromState, ServerState toState) {
        super(
                String.format(
                        "[%s] Invalid state transition: %s -> %s (valid: %s)",
                        sessionId, fromState, toState, fromState.getValidTransitions()));
        this.sessionId = sessionId;
        this.fromState = fromState;
        this.toState = toState;
    }

    public String getSessionId() {
        return sessionId;
    }

    public ServerState getFromState() {
        return fromState;
    }

    public ServerState getToState() {
        return toState;
    }
}
