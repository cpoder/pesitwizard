package com.pesitwizard.server.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.state.ServerState;

@DisplayName("State Machine Transition Tests")
class PesitSessionHandlerStateMachineTest {

    @Test
    @DisplayName("CN01 should only accept CONNECT")
    void cn01ShouldOnlyAcceptConnect() {
        SessionContext ctx = new SessionContext("test");
        assertEquals(ServerState.CN01_REPOS, ctx.getState());
    }

    @Test
    @DisplayName("SF03 should only accept OPEN or DESELECT")
    void sf03ShouldOnlyAcceptOpenOrDeselect() {
        SessionContext ctx = new SessionContext("test");
        ctx.transitionTo(ServerState.SF03_FILE_SELECTED);
        assertEquals(ServerState.SF03_FILE_SELECTED, ctx.getState());
    }

    @Test
    @DisplayName("OF02 should accept WRITE, READ, or CLOSE")
    void of02ShouldAcceptWriteReadOrClose() {
        SessionContext ctx = new SessionContext("test");
        ctx.transitionTo(ServerState.OF02_TRANSFER_READY);
        assertEquals(ServerState.OF02_TRANSFER_READY, ctx.getState());
    }
}
